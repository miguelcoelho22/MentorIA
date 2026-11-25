package br.com.mentoria.saga_orchestrator.messaging;

public record ValidarCotaCommand(
        Long sagaId,
        Long redacaoId,
        Long alunoId
) {
}
