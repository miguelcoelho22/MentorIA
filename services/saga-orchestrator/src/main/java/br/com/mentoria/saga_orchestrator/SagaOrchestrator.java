package br.com.mentoria.saga_orchestrator;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import br.com.mentoria.saga_orchestrator.SagaMessages.AnalyzeEssay;
import br.com.mentoria.saga_orchestrator.SagaMessages.Competency;
import br.com.mentoria.saga_orchestrator.SagaMessages.CorrectionResult;
import br.com.mentoria.saga_orchestrator.SagaMessages.EssayAnalyzed;
import br.com.mentoria.saga_orchestrator.SagaMessages.FeedbackFailed;
import br.com.mentoria.saga_orchestrator.SagaMessages.FeedbackPublished;
import br.com.mentoria.saga_orchestrator.SagaMessages.GenerateFeedback;
import br.com.mentoria.saga_orchestrator.SagaMessages.QuotaValidated;
import br.com.mentoria.saga_orchestrator.SagaMessages.SagaStarted;
import br.com.mentoria.saga_orchestrator.SagaMessages.ValidateQuota;
import br.com.mentoria.saga_orchestrator.SagaStore.Saga;
import br.com.mentoria.saga_orchestrator.SagaStore.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * SAGA orquestrada de correção de redação.
 *
 * <pre>
 * STARTED → QUOTA_REQUESTED → ANALYSIS_REQUESTED → FEEDBACK_REQUESTED → CONFIRMING_QUOTA → COMPLETED
 *                 │                    │                   │
 *                 └→ REJECTED          └────────┬──────────┘
 *              (cota esgotada)                  ↓
 *                                  COMPENSATING → FAILED
 *                       (cancela o rascunho do Feedback e libera a cota no Account)
 * </pre>
 *
 * A publicação do feedback é o ponto pivô: depois dela a SAGA só avança (confirma a cota e conclui).
 */
@Component
class SagaOrchestrator {

    private static final Logger LOG = LoggerFactory.getLogger(SagaOrchestrator.class);

    static final String AI_UNAVAILABLE = "AI_UNAVAILABLE";
    static final String PUBLICATION_ERROR = "PUBLICATION_ERROR";
    static final String QUOTA_EXHAUSTED = "QUOTA_EXHAUSTED";

    private final SagaStore store;
    private final SagaClients clients;
    private final RabbitTemplate rabbit;
    private final ObjectMapper json;
    private final Duration analysisTimeout;
    private final Duration feedbackTimeout;
    private final Duration retryAfter;

    SagaOrchestrator(SagaStore store, SagaClients clients, RabbitTemplate rabbit, ObjectMapper json,
            @Value("${saga.analysis-timeout}") Duration analysisTimeout,
            @Value("${saga.feedback-timeout}") Duration feedbackTimeout,
            @Value("${saga.retry-after}") Duration retryAfter) {
        this.store = store;
        this.clients = clients;
        this.rabbit = rabbit;
        this.json = json;
        this.analysisTimeout = analysisTimeout;
        this.feedbackTimeout = feedbackTimeout;
        this.retryAfter = retryAfter;
    }

    // ------------------------------------------------------------------ 1. início

    @RabbitListener(queues = SagaMessaging.START_QUEUE)
    void onStart(Message message) {
        SagaStarted event = read(message, SagaStarted.class);
        if (event.requestId() == null || event.essayId() == null || event.studentId() == null) {
            throw new AmqpRejectAndDontRequeueException("redacao.enviada exige requestId, essayId e studentId.");
        }
        Long id = store.create(event.requestId(), event.essayId(), event.studentId());
        if (id == null) {
            LOG.info("SAGA já existente para requestId={}; mensagem repetida ignorada.", event.requestId());
            return;
        }
        store.step(id, "SAGA_INICIADA", "Assessment pediu a correção da redação " + event.essayId());

        SagaClients.EssayContent content;
        try {
            content = clients.essay(event.essayId());
        } catch (RestClientException e) {
            store.failure(id, AI_UNAVAILABLE, "Não foi possível ler a redação no Assessment: " + e.getMessage());
            if (store.move(id, Status.STARTED, Status.FAILED)) {
                store.step(id, "SAGA_FALHOU", "Redação indisponível no Assessment; nada a compensar.");
                notifyAssessment(event.requestId(), "FAILED", AI_UNAVAILABLE);
            }
            return;
        }
        store.content(id, content.theme(), content.text());

        if (store.move(id, Status.STARTED, Status.QUOTA_REQUESTED)) {
            send(SagaMessaging.VALIDATE_QUOTA, new ValidateQuota(id, event.requestId(), event.studentId()), null);
            store.step(id, "RESERVA_DE_COTA_SOLICITADA", "Comando enviado ao Account");
        }
    }

    // ------------------------------------------------------------------ 2. cota (Account)

    @RabbitListener(queues = SagaMessaging.QUOTA_RESULT_QUEUE)
    void onQuota(Message message) {
        QuotaValidated event = read(message, QuotaValidated.class);
        Saga saga = saga(event.requestId());
        if (saga == null) {
            return;
        }
        if (event.aprovada()) {
            if (store.moveWithDeadline(saga.id(), Status.QUOTA_REQUESTED, Status.ANALYSIS_REQUESTED, analysisTimeout)) {
                store.step(saga.id(), "COTA_RESERVADA", "Account reservou 1 correção de " + event.periodo());
                send(SagaMessaging.ANALYZE_ESSAY, new AnalyzeEssay(saga.id(), saga.requestId(), saga.studentId(),
                        saga.essayId(), saga.theme(), saga.text()), analysisTimeout);
                store.step(saga.id(), "ANALISE_SOLICITADA",
                        "Comando enviado ao AI Service (prazo de " + analysisTimeout.toSeconds() + " s)");
            }
            return;
        }
        if (store.move(saga.id(), Status.QUOTA_REQUESTED, Status.REJECTED)) {
            store.failure(saga.id(), QUOTA_EXHAUSTED, event.motivo());
            store.step(saga.id(), "COTA_RECUSADA", event.motivo());
            notifyAssessment(saga.requestId(), "REJECTED", QUOTA_EXHAUSTED);
        }
    }

    // ------------------------------------------------------------------ 3. análise (AI Service)

    @RabbitListener(queues = SagaMessaging.ANALYSIS_RESULT_QUEUE)
    void onAnalysis(Message message) {
        EssayAnalyzed event = read(message, EssayAnalyzed.class);
        Saga saga = saga(event.requestId());
        if (saga == null) {
            return;
        }
        List<Competency> scores = event.competencies();
        boolean ok = "OK".equals(event.status()) && scores != null && scores.size() == 5
                && scores.stream().allMatch(score -> score != null && score.id() != null && score.score() != null);
        if (!ok) {
            String detail = event.error() == null ? "análise sem as cinco competências" : event.error();
            compensate(saga.id(), Status.ANALYSIS_REQUESTED, AI_UNAVAILABLE, "AI Service falhou: " + detail);
            return;
        }
        if (store.moveWithDeadline(saga.id(), Status.ANALYSIS_REQUESTED, Status.FEEDBACK_REQUESTED, feedbackTimeout)) {
            int total = scores.stream().mapToInt(Competency::score).sum();
            store.step(saga.id(), "ANALISE_CONCLUIDA", "AI Service devolveu nota total " + total);
            send(SagaMessaging.GENERATE_FEEDBACK, new GenerateFeedback(saga.id(), saga.requestId(), saga.studentId(),
                    saga.essayId(), scores), feedbackTimeout);
            store.step(saga.id(), "FEEDBACK_SOLICITADO", "Comando enviado ao Feedback");
        } else {
            lateReply(saga.requestId(), "análise do AI Service");
        }
    }

    // ------------------------------------------------------------------ 4. feedback (Feedback)

    @RabbitListener(queues = SagaMessaging.FEEDBACK_FAILED_QUEUE)
    void onFeedbackFailed(Message message) {
        FeedbackFailed event = read(message, FeedbackFailed.class);
        Saga saga = saga(event.requestId());
        if (saga != null) {
            compensate(saga.id(), Status.FEEDBACK_REQUESTED, PUBLICATION_ERROR, "Feedback falhou: " + event.error());
        }
    }

    @RabbitListener(queues = SagaMessaging.FEEDBACK_PUBLISHED_QUEUE)
    void onFeedbackPublished(Message message) {
        FeedbackPublished event = read(message, FeedbackPublished.class);
        if (event.sagaId() == null || event.requestId() == null) {
            return; // feedback publicado fora da SAGA (ex.: pela API REST do Feedback)
        }
        Saga saga = saga(event.requestId());
        if (saga == null) {
            return;
        }
        if (store.move(saga.id(), Status.FEEDBACK_REQUESTED, Status.CONFIRMING_QUOTA)) {
            store.step(saga.id(), "FEEDBACK_PUBLICADO",
                    "Ponto pivô: nota " + event.totalScore() + " publicada; a SAGA agora só avança");
            confirmQuota(saga);
        } else {
            lateReply(saga.requestId(), "publicação do Feedback");
        }
    }

    // ------------------------------------------------------------------ 5. confirmação e conclusão

    private void confirmQuota(Saga saga) {
        try {
            clients.confirmQuota(saga.requestId());
            store.step(saga.id(), "COTA_CONFIRMADA", "Account: a reserva virou consumo");
        } catch (HttpClientErrorException.NotFound e) {
            store.step(saga.id(), "COTA_NAO_ENCONTRADA", "Account não tem reserva ativa; seguindo para a conclusão");
        } catch (RestClientException e) {
            store.touch(saga.id());
            store.step(saga.id(), "CONFIRMACAO_PENDENTE", "Account indisponível, nova tentativa automática: " + e.getMessage());
            return;
        }
        if (store.move(saga.id(), Status.CONFIRMING_QUOTA, Status.COMPLETED)) {
            store.step(saga.id(), "SAGA_CONCLUIDA", "Assessment notificado: COMPLETED");
            notifyAssessment(saga.requestId(), "COMPLETED", null);
        }
    }

    // ------------------------------------------------------------------ compensação

    void compensate(long id, Status from, String reason, String detail) {
        if (!store.move(id, from, Status.COMPENSATING)) {
            LOG.info("SAGA {} não está mais em {}; compensação ignorada.", id, from);
            return;
        }
        store.failure(id, reason, detail);
        store.step(id, "COMPENSACAO_INICIADA", detail);
        store.find(id).ifPresent(this::runCompensation);
    }

    private void runCompensation(Saga saga) {
        try {
            if (!clients.cancelFeedback(saga.requestId())) {
                store.step(saga.id(), "COMPENSACAO_INTERROMPIDA",
                        "O feedback já foi publicado (ponto pivô); a SAGA segue em frente até concluir");
                if (store.move(saga.id(), Status.COMPENSATING, Status.CONFIRMING_QUOTA)) {
                    confirmQuota(saga);
                }
                return;
            }
            store.step(saga.id(), "RASCUNHO_CANCELADO", "Feedback: rascunho cancelado (ou inexistente)");
            clients.releaseQuota(saga.requestId());
            store.step(saga.id(), "COTA_LIBERADA", "Account: a reserva foi devolvida ao aluno");
        } catch (RestClientException e) {
            store.touch(saga.id());
            store.step(saga.id(), "COMPENSACAO_PENDENTE", "Nova tentativa automática: " + e.getMessage());
            return;
        }
        if (store.move(saga.id(), Status.COMPENSATING, Status.FAILED)) {
            String reason = Objects.requireNonNullElse(saga.failureReason(), AI_UNAVAILABLE);
            store.step(saga.id(), "SAGA_COMPENSADA", "Assessment notificado: FAILED (" + reason + ")");
            notifyAssessment(saga.requestId(), "FAILED", reason);
        }
    }

    // ------------------------------------------------------------------ prazos e retentativas

    @Scheduled(fixedDelayString = "${saga.watchdog.interval-ms}")
    public void watchdog() {
        safely("reenvio da reserva", () -> store.stale(Status.QUOTA_REQUESTED, retryAfter).forEach(saga -> {
            store.touch(saga.id());
            send(SagaMessaging.VALIDATE_QUOTA, new ValidateQuota(saga.id(), saga.requestId(), saga.studentId()), null);
            store.step(saga.id(), "RESERVA_REENVIADA", "Account ainda não respondeu; comando reenviado (idempotente)");
        }));
        safely("prazo da análise", () -> store.expired(Status.ANALYSIS_REQUESTED).forEach(saga ->
                compensate(saga.id(), Status.ANALYSIS_REQUESTED, AI_UNAVAILABLE,
                        "AI Service não respondeu em " + analysisTimeout.toSeconds() + " s")));
        safely("prazo do feedback", () -> store.expired(Status.FEEDBACK_REQUESTED).forEach(saga ->
                compensate(saga.id(), Status.FEEDBACK_REQUESTED, PUBLICATION_ERROR,
                        "Feedback não respondeu em " + feedbackTimeout.toSeconds() + " s")));
        safely("compensações pendentes", () -> store.stale(Status.COMPENSATING, retryAfter).forEach(this::runCompensation));
        safely("confirmações pendentes", () -> store.stale(Status.CONFIRMING_QUOTA, retryAfter).forEach(this::confirmQuota));
    }

    private static void safely(String task, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            LOG.warn("Falha no watchdog da SAGA ({}): {}", task, e.getMessage());
        }
    }

    // ------------------------------------------------------------------ apoio

    private Saga saga(UUID requestId) {
        if (requestId == null) {
            throw new AmqpRejectAndDontRequeueException("Mensagem da SAGA sem requestId.");
        }
        Saga saga = store.find(requestId).orElse(null);
        if (saga == null) {
            LOG.warn("Mensagem para SAGA desconhecida, requestId={}; ignorada.", requestId);
        }
        return saga;
    }

    private void lateReply(UUID requestId, String what) {
        store.find(requestId).ifPresent(current -> store.step(current.id(), "RESPOSTA_TARDIA_IGNORADA",
                "Chegou a " + what + " com a SAGA em " + current.status()));
    }

    private void notifyAssessment(UUID requestId, String status, String reason) {
        send(SagaMessaging.CORRECTION_RESULT, new CorrectionResult(requestId, status, reason), null);
        LOG.info("SAGA requestId={} terminou com {} {}", requestId, status, reason == null ? "" : reason);
    }

    private void send(String routingKey, Object payload, Duration timeToLive) {
        var properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        if (timeToLive != null) {
            // Um comando que ninguém consumiu dentro do prazo expira: a SAGA já terá compensado.
            properties.setExpiration(Long.toString(timeToLive.toMillis()));
        }
        rabbit.send(SagaMessaging.SAGA_EXCHANGE, routingKey, new Message(json.writeValueAsBytes(payload), properties));
    }

    private <T> T read(Message message, Class<T> type) {
        try {
            T value = json.readValue(message.getBody(), type);
            if (value == null) {
                throw new AmqpRejectAndDontRequeueException("Mensagem vazia para " + type.getSimpleName() + ".");
            }
            return value;
        } catch (JacksonException e) {
            throw new AmqpRejectAndDontRequeueException("JSON inválido para " + type.getSimpleName() + ".", e);
        }
    }
}
