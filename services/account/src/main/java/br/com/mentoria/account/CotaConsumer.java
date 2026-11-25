package br.com.mentoria.account;

import br.com.mentoria.account.QuotaReservationService.Status;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class CotaConsumer {

    private final QuotaReservationService quotaReservationService;
    private final CotaProducer cotaProducer;
    private final ObjectMapper json;
    private final TransactionTemplate transaction;

    CotaConsumer(QuotaReservationService quotaReservationService, CotaProducer cotaProducer,
            ObjectMapper json, PlatformTransactionManager manager) {
        this.quotaReservationService = quotaReservationService;
        this.cotaProducer = cotaProducer;
        this.json = json;
        this.transaction = new TransactionTemplate(manager);
    }

    @RabbitListener(queues = RabbitMQConfig.VALIDAR_COTA_QUEUE)
    public void validarCota(Message message) {
        ValidarCotaCommand command;
        try {
            command = json.readValue(message.getBody(), ValidarCotaCommand.class);
        } catch (JacksonException e) {
            throw new AmqpRejectAndDontRequeueException("JSON inválido para ValidarCota.", e);
        }
        if (command == null || command.sagaId() == null || command.requestId() == null || command.studentId() == null) {
            throw new AmqpRejectAndDontRequeueException("ValidarCota exige sagaId, requestId e studentId.");
        }

        try {
            transaction.executeWithoutResult(status -> {
                var reserva = quotaReservationService.reserve(command.requestId(), command.studentId()).reservation();
                boolean aprovada = reserva.status() == Status.RESERVED;
                cotaProducer.enqueue(new CotaValidadaEvent(command.sagaId(), command.requestId(), command.studentId(),
                        aprovada, aprovada ? reserva.period() : null, aprovada ? null : "RESERVA_JA_FINALIZADA"));
            });
        } catch (ResponseStatusException exception) {
            // A rejeição de negócio só é gravada depois do rollback da tentativa de reserva.
            cotaProducer.enqueue(new CotaValidadaEvent(command.sagaId(), command.requestId(), command.studentId(),
                    false, null, exception.getReason()));
        }
    }
}
