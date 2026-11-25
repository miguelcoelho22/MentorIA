package br.com.mentoria.study_plan;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import br.com.mentoria.study_plan.StudyPlanViews.Average;
import br.com.mentoria.study_plan.StudyPlanViews.Plan;
import br.com.mentoria.study_plan.StudyPlanViews.Progress;
import br.com.mentoria.study_plan.StudyPlanViews.Task;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Component
class StudyPlanProjector {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    StudyPlanProjector(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Scheduled(fixedDelayString = "${study-plan.projection.interval-ms}")
    @Transactional
    public void projectPending() {
        List<PendingEvent> events = jdbc.query("""
                SELECT id, student_id FROM plan_write.domain_event WHERE projected_at IS NULL
                ORDER BY id LIMIT 100 FOR UPDATE
                """, (row, index) -> new PendingEvent(row.getLong("id"), row.getObject("student_id", UUID.class)));
        if (events.isEmpty()) {
            return;
        }

        // A single snapshot per student covers all their events claimed in this batch.
        events.stream().map(PendingEvent::studentId).distinct().sorted().forEach(this::project);
        jdbc.batchUpdate("UPDATE plan_write.domain_event SET projected_at = CURRENT_TIMESTAMP WHERE id = ?",
                events.stream().map(event -> new Object[] {event.id()}).toList());
    }

    private void project(UUID studentId) {
        // Share the write lock so plan and progress describe the same committed state.
        jdbc.queryForObject("SELECT student_id FROM plan_write.plan WHERE student_id = ? FOR UPDATE", UUID.class, studentId);
        List<Task> tasks = jdbc.query("""
                SELECT id, title, competency, status FROM plan_write.task WHERE student_id = ?
                ORDER BY status DESC, updated_at DESC, id
                """, (row, index) -> new Task(row.getString("id"), row.getString("title"), row.getInt("competency"),
                        row.getString("status")), studentId);
        List<Integer> lastScores = jdbc.queryForList("""
                SELECT SUM(score) FROM plan_write.competency_score WHERE student_id = ?
                GROUP BY request_id ORDER BY MAX(published_at) DESC, request_id DESC LIMIT 2
                """, Integer.class, studentId);
        List<Average> averages = jdbc.query("""
                SELECT competency, AVG(score) AS average FROM plan_write.competency_score WHERE student_id = ?
                GROUP BY competency ORDER BY competency
                """, (row, index) -> new Average(row.getInt("competency"), row.getBigDecimal("average")), studentId);

        Instant updatedAt = Instant.now();
        int done = (int) tasks.stream().filter(task -> task.status().equals("DONE")).count();
        int lastScore = lastScores.isEmpty() ? 0 : lastScores.getFirst();
        Integer weakest = averages.stream().min(Comparator.comparing(Average::average).thenComparingInt(Average::id))
                .map(Average::id).orElse(null);
        var plan = new Plan(studentId, updatedAt, tasks);
        var progress = new Progress(studentId, lastScore, trend(lastScores), weakest, averages, done, tasks.size() - done,
                updatedAt);
        save("plan_read.student_plan_view", studentId, plan);
        save("plan_read.student_progress_view", studentId, progress);
    }

    private void save(String table, UUID studentId, Object view) {
        String payload = json.writeValueAsString(view);
        if (jdbc.update("UPDATE " + table + " SET payload = ? WHERE student_id = ?", payload, studentId) == 0) {
            jdbc.update("INSERT INTO " + table + " (student_id, payload) VALUES (?, ?)", studentId, payload);
        }
    }

    private static String trend(List<Integer> scores) {
        if (scores.size() < 2 || scores.get(0).equals(scores.get(1))) {
            return "stable";
        }
        return scores.get(0) > scores.get(1) ? "up" : "down";
    }

    private record PendingEvent(long id, UUID studentId) { }
}
