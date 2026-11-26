package br.com.mentoria.feedback;

import java.util.LinkedHashMap;

import br.com.mentoria.feedback.FeedbackData.AnalyzedDraftRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Passo da SAGA no Feedback: grava o rascunho com as notas da IA e publica o feedback (ponto pivô).
 * Se algo der errado, responde ao orquestrador em {@code saga.feedback.falhou} para que ele compense
 * (cancele o rascunho e libere a cota), em vez de devolver a mensagem para a fila em loop.
 */
@Component
@RequiredArgsConstructor
class FeedbackConsumer {

    static final String SAGA_EXCHANGE = "mentoria.saga";
    static final String FAILURE_ROUTING_KEY = "saga.feedback.falhou";

    private static final Logger LOG = LoggerFactory.getLogger(FeedbackConsumer.class);

    private final FeedbackService feedbackService;
    private final RabbitTemplate rabbit;

    @RabbitListener(queues = FeedbackMessaging.FEEDBACK_QUEUE)
    public void gerarFeedback(GerarFeedbackCommand command) {
        try {
            var request = new AnalyzedDraftRequest(
                    command.studentId(),
                    command.essayId(),
                    command.competencies()
            );

            feedbackService.createDraftFromAnalysis(
                    command.requestId(),
                    request
            );

            feedbackService.publish(
                    command.requestId(),
                    command.sagaId()
            );
        } catch (RuntimeException e) {
            LOG.warn("Falha ao gerar o feedback da SAGA {}, requestId={}: {}",
                    command.sagaId(), command.requestId(), e.getMessage());
            var failure = new LinkedHashMap<String, Object>();
            failure.put("sagaId", command.sagaId());
            failure.put("requestId", command.requestId());
            failure.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
            rabbit.convertAndSend(SAGA_EXCHANGE, FAILURE_ROUTING_KEY, failure);
        }
    }
}
