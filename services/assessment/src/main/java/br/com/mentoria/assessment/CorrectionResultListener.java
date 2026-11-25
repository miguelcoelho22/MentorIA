package br.com.mentoria.assessment;

import br.com.mentoria.assessment.AssessmentData.CorrectionResult;
import br.com.mentoria.assessment.AssessmentData.Reason;
import br.com.mentoria.assessment.AssessmentData.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
class CorrectionResultListener {

    private static final Logger LOG = LoggerFactory.getLogger(CorrectionResultListener.class);

    private final AssessmentService service;
    private final ObjectMapper json;

    CorrectionResultListener(AssessmentService service, ObjectMapper json) {
        this.service = service;
        this.json = json;
    }

    @RabbitListener(queues = "${assessment.saga.result-queue}")
    void receive(Message message) {
        CorrectionResult result;
        try {
            result = json.readValue(message.getBody(), CorrectionResult.class);
        } catch (JacksonException e) {
            throw new AmqpRejectAndDontRequeueException("JSON inválido para o resultado da correção.", e);
        }
        if (result == null || result.requestId() == null || result.status() == null || !result.status().terminal()
                || !validReason(result.status(), result.reason())) {
            throw new AmqpRejectAndDontRequeueException("Resultado de correção inválido.");
        }

        if (service.applyResult(result)) {
            LOG.info("Resultado da correção aplicado, requestId={}, status={}", result.requestId(), result.status());
        } else {
            LOG.info("Resultado ignorado para correção desconhecida ou terminal, requestId={}", result.requestId());
        }
    }

    private static boolean validReason(Status status, Reason reason) {
        return switch (status) {
            case COMPLETED -> reason == null;
            case REJECTED -> reason == null || reason == Reason.QUOTA_EXHAUSTED;
            case FAILED -> reason == null || reason == Reason.AI_UNAVAILABLE || reason == Reason.PUBLICATION_ERROR;
            default -> false;
        };
    }
}
