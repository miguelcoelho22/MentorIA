package br.com.mentoria.saga_orchestrator.messaging;

import java.util.UUID;

public record RedacaoEnviadaEvent(
        UUID redacaoId,
        UUID alunoId,
        String theme,
        String text
) {
}
