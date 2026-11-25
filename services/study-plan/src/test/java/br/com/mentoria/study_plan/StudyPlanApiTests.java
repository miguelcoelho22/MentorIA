package br.com.mentoria.study_plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:study-plan-api;MODE=PostgreSQL",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "study-plan.projection.enabled=false"
})
@AutoConfigureMockMvc
@Transactional
class StudyPlanApiTests {

    private static final UUID ANA = UUID.fromString("4155387b-4518-4657-bc51-d224aa1a69cb");
    private static final UUID BRUNO = UUID.fromString("e96b75e0-42ea-4f46-84db-5fbfb7ff4fe7");

    @Autowired private MockMvc mvc;
    @Autowired private StudyPlanProjector projector;
    @Autowired private StudyPlanSeed seed;
    @Autowired private FeedbackListener listener;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void projectsIdempotentDemoSeedAndServesPlanAndProgressFromReadModel() throws Exception {
        mvc.perform(get("/api/v1/students/{id}/plan", ANA)).andExpect(status().isNotFound());
        seed.run(null);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM plan_write.task", Integer.class)).isEqualTo(4);
        projector.projectPending();

        mvc.perform(get("/api/v1/students/{id}/plan", ANA)).andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(ANA.toString()))
                .andExpect(jsonPath("$.updatedAt", endsWith("Z")))
                .andExpect(jsonPath("$.tasks.length()").value(2))
                .andExpect(jsonPath("$.tasks[0].id").value("demo-ana-intervencao"));
        mvc.perform(get("/api/v1/students/{id}/progress", ANA)).andExpect(status().isOk())
                .andExpect(jsonPath("$.lastScore").value(640))
                .andExpect(jsonPath("$.trend").value("up"))
                .andExpect(jsonPath("$.weakestCompetency").value(5))
                .andExpect(jsonPath("$.averageByCompetency[0].average").value(150))
                .andExpect(jsonPath("$.tasksDone").value(1)).andExpect(jsonPath("$.tasksPending").value(1));
        mvc.perform(get("/api/v1/students/{id}/progress", UUID.randomUUID())).andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM plan_write.domain_event WHERE projected_at IS NULL",
                Integer.class)).isZero();
    }

    @Test
    void returnsBatchInRequestOrderIgnoringUnknownAndDuplicateStudents() throws Exception {
        projector.projectPending();

        mvc.perform(get("/api/v1/progress").param("studentIds", BRUNO + "," + UUID.randomUUID() + "," + ANA + "," + BRUNO))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].studentId").value(BRUNO.toString()))
                .andExpect(jsonPath("$[0].trend").value("down"))
                .andExpect(jsonPath("$[1].studentId").value(ANA.toString()));
        mvc.perform(get("/api/v1/progress").param("studentIds", UUID.randomUUID().toString()))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/v1/progress")).andExpect(status().isBadRequest());
    }

    @Test
    void completesTaskAndUpdatesReadModelOnlyAfterProjection() throws Exception {
        projector.projectPending();

        mvc.perform(patch("/api/v1/tasks/demo-ana-intervencao").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DONE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DONE"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM plan_write.domain_event WHERE event_type = 'TaskCompleted'",
                Integer.class)).isEqualTo(1);
        mvc.perform(get("/api/v1/students/{id}/progress", ANA)).andExpect(jsonPath("$.tasksPending").value(1));
        projector.projectPending();
        mvc.perform(get("/api/v1/students/{id}/progress", ANA)).andExpect(jsonPath("$.tasksDone").value(2))
                .andExpect(jsonPath("$.tasksPending").value(0));
        mvc.perform(get("/api/v1/students/{id}/plan", ANA)).andExpect(jsonPath("$.tasks[0].status").value("DONE"));
    }

    @Test
    void rejectsMissingCompletedAndInvalidTaskCommands() throws Exception {
        mvc.perform(patch("/api/v1/tasks/unknown").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DONE\"}")).andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/tasks/demo-ana-regencia").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DONE\"}")).andExpect(status().isConflict());
        mvc.perform(patch("/api/v1/tasks/demo-ana-intervencao").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"PENDING\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void deduplicatesFeedbackAndReusesPendingRecommendation() throws Exception {
        UUID student = UUID.randomUUID();
        listener.receive(feedback("test-event", student, "request-1", 80));
        listener.receive(feedback("test-event", student, "request-2", 10));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM plan_write.processed_events WHERE event_id = 'test-event'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM plan_write.competency_score WHERE student_id = ?",
                Integer.class, student)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM plan_write.domain_event WHERE student_id = ?",
                Integer.class, student)).isEqualTo(2);
        mvc.perform(get("/api/v1/students/{id}/plan", student)).andExpect(status().isNotFound());

        projector.projectPending();
        mvc.perform(get("/api/v1/students/{id}/plan", student)).andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks.length()").value(1)).andExpect(jsonPath("$.tasks[0].competency").value(5));
        mvc.perform(get("/api/v1/students/{id}/progress", student)).andExpect(status().isOk())
                .andExpect(jsonPath("$.lastScore").value(640));

        String taskId = jdbc.queryForObject("SELECT id FROM plan_write.task WHERE student_id = ?", String.class, student);
        listener.receive(feedback("test-event-next", student, "request-3", 60));
        projector.projectPending();
        mvc.perform(get("/api/v1/students/{id}/plan", student)).andExpect(jsonPath("$.tasks.length()").value(1))
                .andExpect(jsonPath("$.tasks[0].id").value(taskId));
    }

    private Message feedback(String eventId, UUID student, String requestId, int fifthScore) {
        String body = """
                {"eventId":"%s","studentId":"%s","requestId":"%s","publishedAt":"2026-10-20T14:02:41Z",
                 "competencies":[{"id":1,"score":160},{"id":2,"score":160},{"id":3,"score":140},
                                 {"id":4,"score":100},{"id":5,"score":%d}]}
                """.formatted(eventId, student, requestId, fifthScore);
        return new Message(body.getBytes(StandardCharsets.UTF_8), new MessageProperties());
    }
}
