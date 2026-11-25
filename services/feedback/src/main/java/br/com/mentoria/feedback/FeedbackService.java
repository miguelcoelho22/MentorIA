package br.com.mentoria.feedback;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import br.com.mentoria.feedback.FeedbackData.Competency;
import br.com.mentoria.feedback.FeedbackData.DraftRequest;
import br.com.mentoria.feedback.FeedbackData.DraftResult;
import br.com.mentoria.feedback.FeedbackData.EventScore;
import br.com.mentoria.feedback.FeedbackData.Feedback;
import br.com.mentoria.feedback.FeedbackData.FeedbackPublished;
import br.com.mentoria.feedback.FeedbackData.Status;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
class FeedbackService {

    private static final String SELECT_FEEDBACK = """
            SELECT request_id, student_id, essay_id, status, total_score, published_at FROM feedbacks
            """;
    private static final RowMapper<StoredFeedback> FEEDBACK_ROW = (row, index) -> new StoredFeedback(
            row.getObject("request_id", UUID.class), row.getObject("student_id", UUID.class),
            row.getObject("essay_id", UUID.class), Status.valueOf(row.getString("status")),
            row.getInt("total_score"), row.getObject("published_at", OffsetDateTime.class));

    private final JdbcTemplate jdbc;
    private final FeedbackAnalysisClient analysis;
    private final ObjectMapper json;

    FeedbackService(JdbcTemplate jdbc, FeedbackAnalysisClient analysis, ObjectMapper json) {
        this.jdbc = jdbc;
        this.analysis = analysis;
        this.json = json;
    }

    @Transactional
    public DraftResult createDraft(UUID requestId, DraftRequest request) {
        lockRequest(requestId);
        StoredFeedback existing = find(requestId);
        if (existing != null) {
            if (existing.status() != Status.DRAFT) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "O feedback já foi publicado ou cancelado.");
            }
            if (!existing.studentId().equals(request.studentId()) || !existing.essayId().equals(request.essayId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "O rascunho pertence a outro aluno ou a outra redação.");
            }
            return new DraftResult(view(existing), false);
        }

        List<Competency> competencies = analysis.analyze(request);
        int total = competencies.stream().mapToInt(Competency::score).sum();
        jdbc.update("""
                INSERT INTO feedbacks (request_id, student_id, essay_id, status, total_score)
                VALUES (?, ?, ?, 'DRAFT', ?)
                """, requestId, request.studentId(), request.essayId(), total);
        jdbc.batchUpdate("INSERT INTO competency_scores (request_id, competency, score, comment) VALUES (?, ?, ?, ?)",
                competencies.stream().map(score -> new Object[] {requestId, score.id(), score.score(), score.comment()}).toList());
        return new DraftResult(new Feedback(requestId, request.studentId(), request.essayId(), Status.DRAFT,
                total, null, competencies), true);
    }

    Feedback getPublished(UUID requestId) {
        StoredFeedback feedback = find(requestId);
        if (feedback == null || feedback.status() != Status.PUBLISHED) {
            throw notFound();
        }
        return view(feedback);
    }

    List<Feedback> listPublished(UUID studentId) {
        return jdbc.query(SELECT_FEEDBACK + "WHERE student_id = ? AND status = 'PUBLISHED' ORDER BY published_at DESC, request_id",
                FEEDBACK_ROW, studentId).stream().map(this::view).toList();
    }

    @Transactional
    public void cancel(UUID requestId) {
        lockRequest(requestId);
        StoredFeedback existing = find(requestId);
        if (existing == null || existing.status() == Status.CANCELLED) {
            return;
        }
        if (existing.status() == Status.PUBLISHED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Um feedback publicado não pode ser cancelado.");
        }
        jdbc.update("UPDATE feedbacks SET status = 'CANCELLED' WHERE request_id = ?", requestId);
    }

    @Transactional
    public void publish(UUID requestId) {
        lockRequest(requestId);
        StoredFeedback existing = find(requestId);
        if (existing == null) {
            throw notFound();
        }
        if (existing.status() == Status.PUBLISHED) {
            return;
        }
        if (existing.status() == Status.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Um feedback cancelado não pode ser publicado.");
        }

        OffsetDateTime publishedAt = OffsetDateTime.now(ZoneOffset.UTC);
        List<EventScore> scores = competencies(requestId).stream()
                .map(score -> new EventScore(score.id(), score.score())).toList();
        var event = new FeedbackPublished(UUID.randomUUID(), requestId, existing.studentId(), publishedAt.toInstant(),
                existing.totalScore(), scores);

        jdbc.update("UPDATE feedbacks SET status = 'PUBLISHED', published_at = ? WHERE request_id = ?", publishedAt, requestId);
        jdbc.update("INSERT INTO outbox_events (event_id, request_id, event_type, payload) VALUES (?, ?, ?, ?)",
                event.eventId(), requestId, "FeedbackPublished", json.writeValueAsString(event));
    }

    private void lockRequest(UUID requestId) {
        // Serialize commands even while the first draft is still being analyzed.
        jdbc.update("INSERT INTO feedback_requests (request_id) VALUES (?) ON CONFLICT DO NOTHING", requestId);
        jdbc.queryForObject("SELECT request_id FROM feedback_requests WHERE request_id = ? FOR UPDATE", UUID.class, requestId);
    }

    private StoredFeedback find(UUID requestId) {
        return jdbc.query(SELECT_FEEDBACK + "WHERE request_id = ?", FEEDBACK_ROW, requestId).stream().findFirst().orElse(null);
    }

    private Feedback view(StoredFeedback feedback) {
        return new Feedback(feedback.requestId(), feedback.studentId(), feedback.essayId(), feedback.status(),
                feedback.totalScore(), feedback.publishedAt() == null ? null : feedback.publishedAt().toInstant(),
                competencies(feedback.requestId()));
    }

    private List<Competency> competencies(UUID requestId) {
        return jdbc.query("SELECT competency, score, comment FROM competency_scores WHERE request_id = ? ORDER BY competency",
                (row, index) -> new Competency(row.getInt("competency"), row.getInt("score"), row.getString("comment")), requestId);
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Feedback não encontrado.");
    }

    private record StoredFeedback(UUID requestId, UUID studentId, UUID essayId, Status status, int totalScore,
            OffsetDateTime publishedAt) { }
}
