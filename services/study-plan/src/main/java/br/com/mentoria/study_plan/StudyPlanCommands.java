package br.com.mentoria.study_plan;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import br.com.mentoria.study_plan.FeedbackPublished.CompetencyScore;
import br.com.mentoria.study_plan.StudyPlanViews.Task;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class StudyPlanCommands {

    private static final List<String> RECOMMENDATIONS = List.of(
            "Revisar regência e concordância verbal", "Relacionar repertório ao tema em dois parágrafos",
            "Organizar tese e argumentos em um esquema", "Reescrever um parágrafo usando conectivos variados",
            "Proposta de intervenção: 2 exercícios");

    private final JdbcTemplate jdbc;

    StudyPlanCommands(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public boolean recordFeedback(FeedbackPublished feedback) {
        if (jdbc.update("INSERT INTO plan_write.processed_events (event_id) VALUES (?) ON CONFLICT DO NOTHING",
                feedback.eventId()) == 0) {
            return false;
        }

        UUID studentId = feedback.studentId();
        jdbc.update("INSERT INTO plan_write.plan (student_id) VALUES (?) ON CONFLICT DO NOTHING", studentId);
        lockPlan(studentId);

        String requestId = feedback.requestId();
        OffsetDateTime publishedAt = feedback.publishedAt() != null ? feedback.publishedAt() : OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("DELETE FROM plan_write.competency_score WHERE student_id = ? AND request_id = ?", studentId, requestId);
        jdbc.batchUpdate("""
                INSERT INTO plan_write.competency_score (student_id, request_id, competency, score, published_at)
                VALUES (?, ?, ?, ?, ?)
                """, feedback.competencies().stream()
                .map(score -> new Object[] {studentId, requestId, score.id(), score.score(), publishedAt}).toList());
        enqueueProjection(studentId, "ScoreRecorded");

        int weakest = feedback.competencies().stream()
                .min(Comparator.comparing(CompetencyScore::score).thenComparing(CompetencyScore::id)).orElseThrow().id();
        String title = RECOMMENDATIONS.get(weakest - 1);
        int updated = jdbc.update("""
                UPDATE plan_write.task SET title = ?, updated_at = CURRENT_TIMESTAMP
                WHERE student_id = ? AND competency = ? AND status = 'PENDING'
                """, title, studentId, weakest);
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO plan_write.task (id, student_id, title, competency, status)
                    VALUES (?, ?, ?, ?, 'PENDING')
                    """, UUID.randomUUID().toString(), studentId, title, weakest);
        }
        enqueueProjection(studentId, "TasksRecommended");
        return true;
    }

    @Transactional
    public Task completeTask(String taskId) {
        UUID studentId = jdbc.query("SELECT student_id FROM plan_write.task WHERE id = ?",
                rows -> rows.next() ? rows.getObject(1, UUID.class) : null, taskId);
        if (studentId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tarefa não encontrada.");
        }
        lockPlan(studentId);

        if (jdbc.update("""
                UPDATE plan_write.task SET status = 'DONE', updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'PENDING'
                """, taskId) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A tarefa já está concluída.");
        }
        enqueueProjection(studentId, "TaskCompleted");
        return jdbc.queryForObject("SELECT id, title, competency, status FROM plan_write.task WHERE id = ?",
                (row, index) -> new Task(row.getString("id"), row.getString("title"), row.getInt("competency"),
                        row.getString("status")), taskId);
    }

    private void lockPlan(UUID studentId) {
        jdbc.queryForObject("SELECT student_id FROM plan_write.plan WHERE student_id = ? FOR UPDATE", UUID.class, studentId);
    }

    private void enqueueProjection(UUID studentId, String type) {
        jdbc.update("INSERT INTO plan_write.domain_event (student_id, event_type) VALUES (?, ?)", studentId, type);
    }
}
