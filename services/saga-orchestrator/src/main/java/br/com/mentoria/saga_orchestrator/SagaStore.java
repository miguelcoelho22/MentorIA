package br.com.mentoria.saga_orchestrator;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Estado das SAGAs no banco próprio do orquestrador. Cada transição é um compare-and-set
 * ({@code UPDATE ... WHERE status = ?}): se duas respostas chegarem juntas (ex.: a análise
 * atrasada e o fim do prazo), só uma delas avança a SAGA.
 */
@Repository
class SagaStore {

    enum Status {
        STARTED, QUOTA_REQUESTED, ANALYSIS_REQUESTED, FEEDBACK_REQUESTED, CONFIRMING_QUOTA,
        COMPLETED, REJECTED, COMPENSATING, FAILED
    }

    record Saga(long id, UUID requestId, UUID essayId, UUID studentId, String theme, String text, Status status,
            String failureReason, String failureDetail, OffsetDateTime deadline, OffsetDateTime createdAt,
            OffsetDateTime updatedAt) { }

    record Step(String step, String detail, OffsetDateTime at) { }

    private static final String COLUMNS = """
            SELECT id, request_id, essay_id, student_id, theme, essay_text, status, failure_reason, failure_detail,
                   deadline, created_at, updated_at FROM saga_instances
            """;

    private static final RowMapper<Saga> SAGA_ROW = (row, index) -> new Saga(row.getLong("id"),
            row.getObject("request_id", UUID.class), row.getObject("essay_id", UUID.class),
            row.getObject("student_id", UUID.class), row.getString("theme"), row.getString("essay_text"),
            Status.valueOf(row.getString("status")), row.getString("failure_reason"), row.getString("failure_detail"),
            row.getObject("deadline", OffsetDateTime.class), row.getObject("created_at", OffsetDateTime.class),
            row.getObject("updated_at", OffsetDateTime.class));

    private final JdbcTemplate jdbc;

    SagaStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @return o id da nova SAGA, ou {@code null} se já existe uma para esta solicitação. */
    Long create(UUID requestId, UUID essayId, UUID studentId) {
        return jdbc.query("""
                INSERT INTO saga_instances (request_id, essay_id, student_id, status) VALUES (?, ?, ?, 'STARTED')
                ON CONFLICT (request_id) DO NOTHING RETURNING id
                """, rows -> rows.next() ? Long.valueOf(rows.getLong(1)) : null, requestId, essayId, studentId);
    }

    Optional<Saga> find(UUID requestId) {
        return jdbc.query(COLUMNS + " WHERE request_id = ?", SAGA_ROW, requestId).stream().findFirst();
    }

    Optional<Saga> find(long id) {
        return jdbc.query(COLUMNS + " WHERE id = ?", SAGA_ROW, id).stream().findFirst();
    }

    List<Saga> recent(int limit) {
        return jdbc.query(COLUMNS + " ORDER BY id DESC LIMIT ?", SAGA_ROW, limit);
    }

    boolean move(long id, Status from, Status to) {
        return jdbc.update("""
                UPDATE saga_instances SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND status = ?
                """, to.name(), id, from.name()) == 1;
    }

    boolean moveWithDeadline(long id, Status from, Status to, Duration timeout) {
        return jdbc.update("""
                UPDATE saga_instances SET status = ?, updated_at = CURRENT_TIMESTAMP,
                       deadline = CURRENT_TIMESTAMP + make_interval(secs => ?)
                WHERE id = ? AND status = ?
                """, to.name(), seconds(timeout), id, from.name()) == 1;
    }

    void touch(long id) {
        jdbc.update("UPDATE saga_instances SET updated_at = CURRENT_TIMESTAMP WHERE id = ?", id);
    }

    void content(long id, String theme, String text) {
        jdbc.update("UPDATE saga_instances SET theme = ?, essay_text = ? WHERE id = ?", theme, text, id);
    }

    void failure(long id, String reason, String detail) {
        jdbc.update("UPDATE saga_instances SET failure_reason = ?, failure_detail = ? WHERE id = ?",
                reason, truncate(detail), id);
    }

    void step(long id, String step, String detail) {
        jdbc.update("INSERT INTO saga_steps (saga_id, step, detail) VALUES (?, ?, ?)", id, step, truncate(detail));
    }

    List<Step> steps(long id) {
        return jdbc.query("SELECT step, detail, created_at FROM saga_steps WHERE saga_id = ? ORDER BY id",
                (row, index) -> new Step(row.getString("step"), row.getString("detail"),
                        row.getObject("created_at", OffsetDateTime.class)), id);
    }

    /** SAGAs paradas em {@code status} cujo prazo já venceu. */
    List<Saga> expired(Status status) {
        return jdbc.query(COLUMNS + " WHERE status = ? AND deadline < CURRENT_TIMESTAMP ORDER BY id LIMIT 50",
                SAGA_ROW, status.name());
    }

    /** SAGAs paradas em {@code status} sem atualização há mais de {@code age}. */
    List<Saga> stale(Status status, Duration age) {
        return jdbc.query(COLUMNS + """
                 WHERE status = ? AND updated_at < CURRENT_TIMESTAMP - make_interval(secs => ?) ORDER BY id LIMIT 50
                """, SAGA_ROW, status.name(), seconds(age));
    }

    private static double seconds(Duration duration) {
        return duration.toMillis() / 1000.0;
    }

    private static String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= 1000 ? text : text.substring(0, 1000);
    }
}
