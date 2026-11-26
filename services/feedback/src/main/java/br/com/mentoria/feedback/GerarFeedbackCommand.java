package br.com.mentoria.feedback;

import java.util.List;
import java.util.UUID;

public record GerarFeedbackCommand(
        Long sagaId,
        UUID requestId,
        UUID studentId,
        UUID essayId,
        List<FeedbackData.Competency> competencies
) {}