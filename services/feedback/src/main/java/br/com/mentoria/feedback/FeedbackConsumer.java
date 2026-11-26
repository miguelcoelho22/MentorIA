package br.com.mentoria.feedback;

import br.com.mentoria.feedback.FeedbackData.AnalyzedDraftRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class FeedbackConsumer {

    private final FeedbackService feedbackService;

    @RabbitListener(queues = FeedbackMessaging.FEEDBACK_QUEUE)
    public void gerarFeedback(GerarFeedbackCommand command) {

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
    }
}
