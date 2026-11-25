package br.com.mentoria.account;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import br.com.mentoria.account.QuotaReservationService.Reservation;
import br.com.mentoria.account.QuotaReservationService.ReservationResult;
import br.com.mentoria.account.QuotaReservationService.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CotaConsumerTests {

    private final ValidarCotaCommand command = new ValidarCotaCommand(1L, UUID.randomUUID(), UUID.randomUUID());

    @Mock private QuotaReservationService reservations;
    @Mock private CotaProducer producer;
    @InjectMocks private CotaConsumer consumer;

    @Test
    void approvesRetryForReservedQuota() {
        stubExisting(Status.RESERVED);

        consumer.validarCota(command);

        verify(producer).publicarResultado(new CotaValidadaEvent(command.sagaId(), command.requestId(),
                command.studentId(), true, "2026-11", null));
    }

    @ParameterizedTest
    @EnumSource(value = Status.class, names = {"CONSUMED", "RELEASED"})
    void rejectsRetryForFinalizedQuota(Status status) {
        stubExisting(status);

        consumer.validarCota(command);

        verify(producer).publicarResultado(new CotaValidadaEvent(command.sagaId(), command.requestId(),
                command.studentId(), false, null, "RESERVA_JA_FINALIZADA"));
    }

    private void stubExisting(Status status) {
        var reservation = new Reservation(command.requestId(), command.studentId(), status, "2026-11");
        when(reservations.reserve(command.requestId(), command.studentId())).thenReturn(new ReservationResult(reservation, false));
    }
}
