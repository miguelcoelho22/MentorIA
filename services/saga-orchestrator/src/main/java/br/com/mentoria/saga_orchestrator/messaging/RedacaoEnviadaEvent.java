package br.com.mentoria.saga_orchestrator.messaging;

public record RedacaoEnviadaEvent(
        Long redacaoId,
        Long alunoId
) {
}
