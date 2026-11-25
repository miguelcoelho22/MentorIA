package br.com.mentoria.account;

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
