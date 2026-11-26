package br.com.mentoria.saga_orchestrator.messaging;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FeedbackPublishedEvent(
        Long sagaId,
        UUID eventId,
        UUID requestId,
        UUID studentId,
        Instant publishedAt,
        int totalScore,
        List<EventScore> competencies
) {
    public record EventScore(
            int id,
            int score
    ) {}
}