package br.com.mentoria.assessment;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.mentoria.assessment.AssessmentData.CorrectionRequest;
import br.com.mentoria.assessment.AssessmentData.CorrectionResult;
import br.com.mentoria.assessment.AssessmentData.Essay;
import br.com.mentoria.assessment.AssessmentData.EssayCreate;
import br.com.mentoria.assessment.AssessmentData.Link;
import br.com.mentoria.assessment.AssessmentData.Reason;
import br.com.mentoria.assessment.AssessmentData.RedacaoEnviadaEvent;
import br.com.mentoria.assessment.AssessmentData.Status;
import br.com.mentoria.assessment.AssessmentData.Theme;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
class AssessmentService {

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final AssessmentAccountClient account;
    private final ObjectMapper json;

    AssessmentService(JdbcTemplate jdbc, NamedParameterJdbcTemplate namedJdbc, AssessmentAccountClient account,
            ObjectMapper json) {
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
        this.account = account;
        this.json = json;
    }

    List<Theme> themes() {
        return jdbc.query("SELECT id, title, description FROM themes ORDER BY id",
                (row, index) -> new Theme(row.getString("id"), row.getString("title"), row.getString("description")));
    }

    Essay createEssay(UUID studentId, EssayCreate request) {
        UUID id = UUID.randomUUID();
        OffsetDateTime submittedAt = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        int inserted = jdbc.update("""
                INSERT INTO essays (id, student_id, theme_id, text, submitted_at)
                SELECT ?, ?, id, ?, ? FROM themes WHERE id = ?
                """, id, studentId, request.text(), submittedAt, request.themeId());
        if (inserted == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tema inválido.");
        }
        return new Essay(id, studentId, request.themeId(), null, submittedAt.toInstant());
    }

    List<Essay> listEssays(UUID studentId, String classId) {
        if ((studentId == null && classId == null) || (classId != null && classId.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe studentId ou classId.");
        }
        List<UUID> ids = classId == null ? List.of(studentId) : account.studentsOf(classId);
        if (studentId != null && classId != null) {
            ids = ids.contains(studentId) ? List.of(studentId) : List.of();
        }
        if (ids.isEmpty()) {
            return List.of();
        }
        return namedJdbc.query("""
                SELECT id, student_id, theme_id, submitted_at FROM essays
                WHERE student_id IN (:ids) ORDER BY submitted_at DESC, id
                """, Map.of("ids", ids), (row, index) -> new Essay(row.getObject("id", UUID.class),
                        row.getObject("student_id", UUID.class), row.getString("theme_id"), null,
                        row.getObject("submitted_at", OffsetDateTime.class).toInstant()));
    }

    Essay essay(UUID essayId) {
        return jdbc.query("SELECT id, student_id, theme_id, text, submitted_at FROM essays WHERE id = ?",
                (row, index) -> new Essay(row.getObject("id", UUID.class), row.getObject("student_id", UUID.class),
                        row.getString("theme_id"), row.getString("text"),
                        row.getObject("submitted_at", OffsetDateTime.class).toInstant()), essayId)
                .stream().findFirst().orElseThrow(() -> notFound("Redação não encontrada."));
    }

    @Transactional
    public CorrectionRequest requestCorrection(UUID essayId, UUID studentId) {
        var owned = jdbc.queryForList("SELECT id FROM essays WHERE id = ? AND student_id = ? FOR UPDATE",
                UUID.class, essayId, studentId);
        if (owned.isEmpty()) {
            throw notFound("Redação não encontrada.");
        }

        var active = jdbc.queryForList("""
                SELECT id FROM correction_requests WHERE essay_id = ? AND status IN ('PENDING', 'PROCESSING') LIMIT 1
                """, UUID.class, essayId);
        if (!active.isEmpty()) {
            var error = new ResponseStatusException(HttpStatus.CONFLICT, "A redação já tem uma solicitação em andamento.");
            error.setTitle("Correção já em andamento");
            error.setType(URI.create("https://mentoria.app/errors/correction-in-progress"));
            throw error;
        }

        UUID requestId = UUID.randomUUID();
        OffsetDateTime requestedAt = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        jdbc.update("INSERT INTO correction_requests (id, essay_id, status, requested_at) VALUES (?, ?, 'PROCESSING', ?)",
                requestId, essayId, requestedAt);
        jdbc.update("INSERT INTO saga_start_outbox (request_id, payload) VALUES (?, ?)", requestId,
                json.writeValueAsString(new RedacaoEnviadaEvent(essayId, studentId)));
        return view(requestId, essayId, studentId, Status.PROCESSING, requestedAt, null);
    }

    CorrectionRequest correction(UUID requestId) {
        return jdbc.query("""
                SELECT r.id, r.essay_id, r.status, r.requested_at, r.reason, e.student_id
                FROM correction_requests r JOIN essays e ON r.essay_id = e.id WHERE r.id = ?
                """, (row, index) -> {
                    String reason = row.getString("reason");
                    return view(row.getObject("id", UUID.class), row.getObject("essay_id", UUID.class),
                            row.getObject("student_id", UUID.class), Status.valueOf(row.getString("status")),
                            row.getObject("requested_at", OffsetDateTime.class), reason == null ? null : Reason.valueOf(reason));
                }, requestId).stream().findFirst().orElseThrow(() -> notFound("Solicitação de correção não encontrada."));
    }

    @Transactional
    public boolean applyResult(CorrectionResult result) {
        Reason reason = result.status() == Status.REJECTED ? Reason.QUOTA_EXHAUSTED : result.reason();
        return jdbc.update("""
                UPDATE correction_requests SET status = ?, reason = ?
                WHERE id = ? AND status IN ('PENDING', 'PROCESSING')
                """, result.status().name(), reason == null ? null : reason.name(), result.requestId()) == 1;
    }

    private static CorrectionRequest view(UUID requestId, UUID essayId, UUID studentId, Status status,
            OffsetDateTime requestedAt, Reason reason) {
        var links = new LinkedHashMap<String, Link>();
        String essayPath = "/api/v1/essays/" + essayId;
        links.put("self", new Link("/api/v1/correction-requests/" + requestId));
        links.put("essay", new Link(essayPath));
        switch (status) {
            case COMPLETED -> {
                links.put("feedback", new Link("/api/v1/feedbacks/" + requestId));
                links.put("study-plan", new Link("/api/v1/students/" + studentId + "/plan"));
            }
            case REJECTED -> links.put("quota", new Link("/api/v1/students/" + studentId + "/quota"));
            case FAILED -> links.put("retry", new Link(essayPath + "/correction-requests"));
            default -> { }
        }
        Status responseStatus = status == Status.PENDING ? Status.PROCESSING : status;
        return new CorrectionRequest(requestId, essayId, responseStatus, requestedAt.toInstant(), reason, links);
    }

    private static ResponseStatusException notFound(String detail) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, detail);
    }
}
