package br.com.mentoria.saga_orchestrator.messaging;

public record CompetencyScore(
        Integer id,
        Integer score,
        String comment
) {
}
