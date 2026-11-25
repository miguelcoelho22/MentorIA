package br.com.mentoria.saga_orchestrator.messaging;

import java.util.UUID;

public record CotaValidadaEvent(
        Long sagaId,
        UUID requestId,
        UUID studentId,
        boolean aprovada,
        String periodo,
        String motivo
) {
}
