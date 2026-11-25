package br.com.mentoria.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:assessment-api;MODE=PostgreSQL",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "assessment.outbox.enabled=false"
})
@AutoConfigureMockMvc
@Transactional
class AssessmentApiTests {

    private static final String ESSAYS = "/api/v1/essays";
    private static final String CORRECTIONS = "/api/v1/correction-requests";
    private static final String REQUEST_CORRECTION = ESSAYS + "/{id}/correction-requests";
    private static final UUID ANA = UUID.fromString("4155387b-4518-4657-bc51-d224aa1a69cb");
    private static final UUID BRUNO = UUID.fromString("e96b75e0-42ea-4f46-84db-5fbfb7ff4fe7");
    private static final UUID ANA_ESSAY = UUID.fromString("9085f1fe-cac4-4813-bf06-203a68270d82");

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    @Autowired private SagaStartPublisher publisher;
    @Autowired private CorrectionResultListener results;
    @MockitoBean private AssessmentAccountClient account;
    @MockitoBean private RabbitTemplate rabbit;

    @Test
    void listsThemesAndUsesTheDemoStudents() throws Exception {
        mvc.perform(get("/api/v1/themes")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("t07"))
                .andExpect(jsonPath("$[0].title").value("Desafios da mobilidade urbana no Brasil"))
                .andExpect(jsonPath("$.length()").value(3));
        mvc.perform(get(ESSAYS).param("studentId", ANA.toString())).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(ANA_ESSAY.toString()));
        mvc.perform(get(ESSAYS).param("studentId", BRUNO.toString())).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].studentId").value(BRUNO.toString()));
    }

    @Test
    void createsEssayAndOmitsItsTextUntilRequestedInDetail() throws Exception {
        var created = mvc.perform(post(ESSAYS).header("X-User-Id", ANA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"themeId\":\"t07\",\"text\":\"Uma nova redação.\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.studentId").value(ANA.toString()))
                .andExpect(jsonPath("$.text").doesNotExist()).andReturn().getResponse();
        String id = JsonPath.read(created.getContentAsString(), "$.id");
        assertThat(created.getHeader(HttpHeaders.LOCATION)).isEqualTo(ESSAYS + "/" + id);
        mvc.perform(get(ESSAYS + "/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Uma nova redação."));
        mvc.perform(get(ESSAYS).param("studentId", ANA.toString())).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].text").doesNotExist());
        mvc.perform(get(ESSAYS + "/{id}", UUID.randomUUID())).andExpect(status().isNotFound());
        mvc.perform(post(ESSAYS).header("X-User-Id", ANA).contentType(MediaType.APPLICATION_JSON)
                .content("{\"themeId\":\"unknown\",\"text\":\"Texto\"}")).andExpect(status().isBadRequest());
        mvc.perform(post(ESSAYS).header("X-User-Id", ANA).contentType(MediaType.APPLICATION_JSON)
                .content("{\"themeId\":\"t07\",\"text\":\" \"}")).andExpect(status().isBadRequest());
    }

    @Test
    void filtersEssaysByCurrentClassAndStudentIntersection() throws Exception {
        when(account.studentsOf("demo-3A")).thenReturn(List.of(ANA, BRUNO));
        mvc.perform(get(ESSAYS).param("classId", "demo-3A")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get(ESSAYS).param("classId", "demo-3A").param("studentId", BRUNO.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].studentId").value(BRUNO.toString()));
        mvc.perform(get(ESSAYS).param("classId", "demo-3A").param("studentId", UUID.randomUUID().toString()))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get(ESSAYS)).andExpect(status().isBadRequest());
    }

    @Test
    void acceptsCorrectionWithExactSagaPayloadAndRetriesUntilBrokerConfirms() throws Exception {
        String id = openCorrection();
        mvc.perform(get(CORRECTIONS + "/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$._links.self.href").value(CORRECTIONS + "/" + id));
        mvc.perform(post(REQUEST_CORRECTION, ANA_ESSAY).header("X-User-Id", ANA)).andExpect(status().isConflict());
        mvc.perform(post(REQUEST_CORRECTION, ANA_ESSAY).header("X-User-Id", BRUNO)).andExpect(status().isNotFound());
        mvc.perform(post(REQUEST_CORRECTION, UUID.randomUUID()).header("X-User-Id", ANA)).andExpect(status().isNotFound());
        mvc.perform(get(CORRECTIONS + "/{id}", UUID.randomUUID())).andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM saga_start_outbox", Integer.class)).isEqualTo(1);
        var sent = new ArrayList<Message>();
        doAnswer(call -> {
            sent.add(call.getArgument(2));
            CorrelationData confirmation = call.getArgument(3);
            confirmation.getFuture().complete(new CorrelationData.Confirm(sent.size() == 2, null));
            return null;
        }).when(rabbit).send(eq("mentoria.saga"), eq("redacao.enviada"), any(Message.class), any(CorrelationData.class));

        publisher.publishPending();
        assertThat(jdbc.queryForObject("SELECT published_at FROM saga_start_outbox WHERE request_id = ?",
                Object.class, UUID.fromString(id))).isNull();
        publisher.publishPending();
        publisher.publishPending();
        assertThat(jdbc.queryForObject("SELECT published_at FROM saga_start_outbox WHERE request_id = ?",
                Object.class, UUID.fromString(id))).isNotNull();
        assertThat(sent).hasSize(2).allSatisfy(message -> {
            assertThat(message.getMessageProperties().getMessageId()).isEqualTo(id);
            assertThat(message.getMessageProperties().getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
            assertThat(message.getMessageProperties().getContentType()).isEqualTo("application/json");
            assertThat(json.readTree(message.getBody())).isEqualTo(json.readTree("""
                    {"redacaoId":"%s","alunoId":"%s"}
                    """.formatted(ANA_ESSAY, ANA)));
        });
    }

    @Test
    void appliesFailureAndCompletionWithoutAllowingLateResultsToOverwriteTerminalStates() throws Exception {
        String first = openCorrection();
        results.receive(message(first, "FAILED", ",\"reason\":\"AI_UNAVAILABLE\""));
        mvc.perform(get(CORRECTIONS + "/{id}", first)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED")).andExpect(jsonPath("$.reason").value("AI_UNAVAILABLE"))
                .andExpect(jsonPath("$._links.retry.href").value(ESSAYS + "/" + ANA_ESSAY + "/correction-requests"));

        String retry = openCorrection();
        assertThat(retry).isNotEqualTo(first);
        results.receive(message(first, "COMPLETED", ""));
        results.receive(message(retry, "COMPLETED", ""));
        mvc.perform(get(CORRECTIONS + "/{id}", retry)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$._links.feedback.href").value("/api/v1/feedbacks/" + retry));
        mvc.perform(get(CORRECTIONS + "/{id}", first)).andExpect(jsonPath("$.status").value("FAILED"));
    }

    @Test
    void recordsQuotaRejectionAndRejectsMalformedResults() throws Exception {
        String id = openCorrection();
        assertThatThrownBy(() -> results.receive(message(id, "PROCESSING", "")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        results.receive(message(id, "REJECTED", ""));
        mvc.perform(get(CORRECTIONS + "/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED")).andExpect(jsonPath("$.reason").value("QUOTA_EXHAUSTED"))
                .andExpect(jsonPath("$._links.quota.href").value("/api/v1/students/" + ANA + "/quota"));
    }

    private String openCorrection() throws Exception {
        var response = mvc.perform(post(REQUEST_CORRECTION, ANA_ESSAY).header("X-User-Id", ANA))
                .andExpect(status().isAccepted()).andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.status").value("PROCESSING")).andReturn().getResponse();
        String id = JsonPath.read(response.getContentAsString(), "$.id");
        assertThat(response.getHeader(HttpHeaders.LOCATION)).isEqualTo(CORRECTIONS + "/" + id);
        return id;
    }

    private static Message message(String id, String status, String extra) {
        return new Message("{\"requestId\":\"%s\",\"status\":\"%s\"%s}".formatted(id, status, extra)
                .getBytes(StandardCharsets.UTF_8), new MessageProperties());
    }
}
