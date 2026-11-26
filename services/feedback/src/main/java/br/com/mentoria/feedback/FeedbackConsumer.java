@Component
@RequiredArgsConstructor
class FeedbackConsumer {

    private final FeedbackService feedbackService;

    @RabbitListener(queues = FeedbackMessaging.FEEDBACK_QUEUE)
    public void gerarFeedback(GerarFeedbackCommand command) {

        var request = new FeedbackData.AnalyzedDraftRequest(
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