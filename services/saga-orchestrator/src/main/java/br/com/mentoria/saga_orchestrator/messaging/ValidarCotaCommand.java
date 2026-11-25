package br.com.mentoria.saga_orchestrator.messaging;

import java.util.UUID;

public record ValidarCotaCommand(
        Long sagaId,
        UUID requestId,
        UUID studentId
) {
}
