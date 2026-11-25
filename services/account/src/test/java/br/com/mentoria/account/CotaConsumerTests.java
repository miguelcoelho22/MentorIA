package br.com.mentoria.account;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import br.com.mentoria.account.QuotaReservationService.Reservation;
import br.com.mentoria.account.QuotaReservationService.ReservationResult;
import br.com.mentoria.account.QuotaReservationService.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class CotaConsumerTests {

    private final ValidarCotaCommand command = new ValidarCotaCommand(1L, UUID.randomUUID(), UUID.randomUUID());

    @Mock private QuotaReservationService reservations;
    @Mock private CotaProducer producer;
    @Mock private PlatformTransactionManager manager;
    private final JsonMapper json = JsonMapper.builder().build();
    private final SimpleTransactionStatus transaction = new SimpleTransactionStatus();
    private CotaConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new CotaConsumer(reservations, producer, json, manager);
    }

    @Test
    void approvesRetryForReservedQuota() {
        stubExisting(Status.RESERVED);

        consumer.validarCota(message());

        var calls = inOrder(producer, manager);
        calls.verify(producer).enqueue(new CotaValidadaEvent(command.sagaId(), command.requestId(),
                command.studentId(), true, "2026-11", null));
        calls.verify(manager).commit(transaction);
    }

    @ParameterizedTest
    @EnumSource(value = Status.class, names = {"CONSUMED", "RELEASED"})
    void rejectsRetryForFinalizedQuota(Status status) {
        stubExisting(status);

        consumer.validarCota(message());

        verify(producer).enqueue(new CotaValidadaEvent(command.sagaId(), command.requestId(),
                command.studentId(), false, null, "RESERVA_JA_FINALIZADA"));
    }

    @Test
    void recordsBusinessRejectionAfterRollingBackReservation() {
        when(manager.getTransaction(any())).thenReturn(transaction);
        when(reservations.reserve(command.requestId(), command.studentId()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Cota esgotada."));

        consumer.validarCota(message());

        var calls = inOrder(manager, producer);
        calls.verify(manager).rollback(transaction);
        calls.verify(producer).enqueue(new CotaValidadaEvent(command.sagaId(), command.requestId(),
                command.studentId(), false, null, "Cota esgotada."));
    }

    @Test
    void rollsBackReservationWhenRecordingTheResultFails() {
        stubExisting(Status.RESERVED);
        doThrow(new DataIntegrityViolationException("Falha na outbox")).when(producer).enqueue(any());

        assertThatThrownBy(() -> consumer.validarCota(message())).isInstanceOf(DataIntegrityViolationException.class);
        verify(manager).rollback(transaction);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "null", "{\"sagaId\":1}"})
    void rejectsMalformedCommandsBeforeStartingATransaction(String payload) {
        var message = new Message(payload.getBytes(StandardCharsets.UTF_8), new MessageProperties());

        assertThatThrownBy(() -> consumer.validarCota(message)).isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(reservations, producer, manager);
    }

    private void stubExisting(Status status) {
        when(manager.getTransaction(any())).thenReturn(transaction);
        var reservation = new Reservation(command.requestId(), command.studentId(), status, "2026-11");
        when(reservations.reserve(command.requestId(), command.studentId())).thenReturn(new ReservationResult(reservation, false));
    }

    private Message message() {
        var properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        return new Message(json.writeValueAsBytes(command), properties);
    }
}
