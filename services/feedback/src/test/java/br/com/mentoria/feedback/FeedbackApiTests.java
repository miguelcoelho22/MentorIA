package br.com.mentoria.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import br.com.mentoria.feedback.FeedbackData.Competency;
import br.com.mentoria.feedback.FeedbackData.DraftRequest;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:feedback-api;MODE=PostgreSQL",
        "feedback.outbox.enabled=false"
})
@AutoConfigureMockMvc
class FeedbackApiTests {

    private static final String PATH = "/api/v1/feedbacks";
    private static final String EXCHANGE = "mentoria.events";
    private static final String ROUTING_KEY = "feedback.published";
    private static final UUID STUDENT = UUID.fromString("4155387b-4518-4657-bc51-d224aa1a69cb");
    private static final DraftRequest DRAFT = new DraftRequest(STUDENT, UUID.randomUUID(), "Tema", "Uma redação.");
    private static final List<Competency> SCORES = List.of(new Competency(1, 160, "Revisar a regência"),
            new Competency(2, 160, "Bom repertório"), new Competency(3, 140, "Detalhar argumento"),
            new Competency(4, 100, "Usar conectivos"), new Competency(5, 80, "Detalhar a intervenção"));

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    @Autowired private FeedbackService service;
    @Autowired private FeedbackOutboxPublisher publisher;
    @MockitoBean private FeedbackAnalysisClient analysis;
    @MockitoBean private RabbitTemplate rabbit;

    @Test
    @Transactional
    void createsDraftOnceAndKeepsItPrivateUntilAtomicPublication() throws Exception {
        UUID id = UUID.randomUUID();
        when(analysis.analyze(DRAFT)).thenReturn(SCORES);
        create(id).andExpect(status().isCreated()).andExpect(header().string(HttpHeaders.LOCATION, PATH + "/" + id))
                .andExpect(jsonPath("$.status").value("DRAFT")).andExpect(jsonPath("$.totalScore").value(640))
                .andExpect(jsonPath("$.competencies[0].comment").value("Revisar a regência"));
        create(id).andExpect(status().isOk()).andExpect(jsonPath("$.requestId").value(id.toString()));
        verify(analysis).analyze(DRAFT);
        mvc.perform(get(PATH + "/{id}", id)).andExpect(status().isNotFound());
        mvc.perform(get(PATH).param("studentId", STUDENT.toString())).andExpect(content().json("[]"));

        mvc.perform(put(PATH + "/{id}/publication", id)).andExpect(status().isNoContent());
        mvc.perform(put(PATH + "/{id}/publication", id)).andExpect(status().isNoContent());
        mvc.perform(get(PATH + "/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED")).andExpect(jsonPath("$.publishedAt", endsWith("Z")));
        mvc.perform(get(PATH).param("studentId", STUDENT.toString())).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].requestId").value(id.toString()));
        mvc.perform(get(PATH).param("studentId", UUID.randomUUID().toString())).andExpect(content().json("[]"));
        mvc.perform(delete(PATH + "/{id}", id)).andExpect(status().isConflict());

        assertThat(outboxStatus(id)).isEqualTo("PENDING");
        String payload = jdbc.queryForObject("SELECT payload FROM outbox_events WHERE request_id = ?", String.class, id);
        var event = json.readTree(payload);
        assertThat(event.get("eventId").asString()).isEqualTo(jdbc.queryForObject(
                "SELECT event_id FROM outbox_events WHERE request_id = ?", UUID.class, id).toString());
        assertThat(event.get("requestId").asString()).isEqualTo(id.toString());
        assertThat(event.get("studentId").asString()).isEqualTo(STUDENT.toString());
        assertThat(event.get("totalScore").asInt()).isEqualTo(640);
        assertThat(event.get("competencies").size()).isEqualTo(5);
        assertThat(event.get("competencies").get(0).has("comment")).isFalse();
    }

    @Test
    @Transactional
    void compensatesIdempotentlyAndRejectsPublicationOfCancelledDraft() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(delete(PATH + "/{id}", UUID.randomUUID())).andExpect(status().isNoContent());
        mvc.perform(put(PATH + "/{id}/publication", UUID.randomUUID())).andExpect(status().isNotFound());
        when(analysis.analyze(DRAFT)).thenReturn(SCORES);
        create(id).andExpect(status().isCreated());
        mvc.perform(delete(PATH + "/{id}", id)).andExpect(status().isNoContent());
        mvc.perform(delete(PATH + "/{id}", id)).andExpect(status().isNoContent());
        create(id).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(get(PATH + "/{id}", id)).andExpect(status().isNotFound());
        mvc.perform(put(PATH + "/{id}/publication", id)).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE request_id = ?", Integer.class, id)).isZero();
    }

    @Test
    void rejectsMalformedAndUnavailableAnalysisWithoutPersistingDraft() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(put(PATH + "/{id}", id).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        when(analysis.analyze(DRAFT)).thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE));
        create(id).andExpect(status().isServiceUnavailable());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM feedback_requests WHERE request_id = ?", Integer.class, id)).isZero();
    }

    @Test
    void rollsBackFeedbackPublicationWhenOutboxInsertFails() {
        UUID id = UUID.randomUUID();
        when(analysis.analyze(DRAFT)).thenReturn(SCORES);
        service.createDraft(id, DRAFT);
        jdbc.update("INSERT INTO outbox_events (event_id, request_id, event_type, payload) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), id, "FeedbackPublished", "{}");
        try {
            assertThatThrownBy(() -> service.publish(id)).isInstanceOf(DataIntegrityViolationException.class);
            assertThat(jdbc.queryForObject("SELECT status FROM feedbacks WHERE request_id = ?", String.class, id)).isEqualTo("DRAFT");
            assertThat(jdbc.queryForObject("SELECT published_at FROM feedbacks WHERE request_id = ?", Object.class, id)).isNull();
        } finally {
            jdbc.update("DELETE FROM outbox_events WHERE request_id = ?", id);
        }
    }

    @Test
    @Transactional
    void publishesDurableJsonAndKeepsTheSameEventIdUntilRoutedAndConfirmed() {
        UUID id = UUID.randomUUID();
        when(analysis.analyze(DRAFT)).thenReturn(SCORES);
        service.createDraft(id, DRAFT);
        service.publish(id);
        String storedId = jdbc.queryForObject("SELECT event_id FROM outbox_events WHERE request_id = ?", UUID.class, id).toString();
        String payload = jdbc.queryForObject("SELECT payload FROM outbox_events WHERE request_id = ?", String.class, id);
        var sent = new ArrayList<Message>();
        doAnswer(call -> {
            Message message = call.getArgument(2);
            CorrelationData correlation = call.getArgument(3);
            assertThat(correlation.getId()).isEqualTo(storedId);
            sent.add(message);
            if (sent.size() == 2) {
                correlation.setReturned(new ReturnedMessage(message, 312, "NO_ROUTE", EXCHANGE, ROUTING_KEY));
            }
            correlation.getFuture().complete(new CorrelationData.Confirm(sent.size() > 1, null));
            return null;
        }).when(rabbit).send(eq(EXCHANGE), eq(ROUTING_KEY), any(Message.class), any(CorrelationData.class));

        publisher.publishPending();
        assertThat(outboxStatus(id)).isEqualTo("PENDING");
        publisher.publishPending();
        assertThat(outboxStatus(id)).isEqualTo("PENDING");
        publisher.publishPending();
        publisher.publishPending();
        assertThat(outboxStatus(id)).isEqualTo("PUBLISHED");
        assertThat(jdbc.queryForObject("SELECT published_at FROM outbox_events WHERE request_id = ?", Object.class, id)).isNotNull();
        assertThat(sent).hasSize(3).allSatisfy(message -> {
            assertThat(new String(message.getBody(), StandardCharsets.UTF_8)).isEqualTo(payload);
            assertThat(message.getMessageProperties().getMessageId()).isEqualTo(storedId);
            assertThat(message.getMessageProperties().getType()).isEqualTo("FeedbackPublished");
            assertThat(message.getMessageProperties().getContentType()).isEqualTo("application/json");
            assertThat(message.getMessageProperties().getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        });
    }

    private ResultActions create(UUID id) throws Exception {
        return mvc.perform(put(PATH + "/{id}", id).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(DRAFT)));
    }

    private String outboxStatus(UUID id) {
        return jdbc.queryForObject("SELECT status FROM outbox_events WHERE request_id = ?", String.class, id);
    }
}
