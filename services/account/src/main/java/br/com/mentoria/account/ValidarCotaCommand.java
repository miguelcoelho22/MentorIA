package br.com.mentoria.account;

import java.util.UUID;

public record ValidarCotaCommand(
        Long sagaId,
        UUID requestId,
        UUID studentId
) {
}
