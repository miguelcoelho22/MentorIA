package br.com.mentoria.saga_orchestrator.messaging;

import java.util.List;
import java.util.UUID;

public record GerarFeedbackCommand(
        Long sagaId,
        UUID requestId,
        UUID studentId,
        UUID essayId,
        List<CompetencyScore> competencies
) {
}
