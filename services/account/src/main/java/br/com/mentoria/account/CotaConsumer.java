package br.com.mentoria.account;

import br.com.mentoria.account.QuotaReservationService.Status;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequiredArgsConstructor
public class CotaConsumer {

    private final QuotaReservationService quotaReservationService;
    private final CotaProducer cotaProducer;

    @RabbitListener(queues = RabbitMQConfig.VALIDAR_COTA_QUEUE)
    public void validarCota(ValidarCotaCommand command) {
        CotaValidadaEvent event;
        try {
            var reserva = quotaReservationService.reserve(
                    command.requestId(),
                    command.studentId()
            ).reservation();
            boolean aprovada = reserva.status() == Status.RESERVED;
            event = new CotaValidadaEvent(
                    command.sagaId(),
                    command.requestId(),
                    command.studentId(),
                    aprovada,
                    aprovada ? reserva.period() : null,
                    aprovada ? null : "RESERVA_JA_FINALIZADA"
            );
        } catch (ResponseStatusException exception) {
            event = new CotaValidadaEvent(
                    command.sagaId(),
                    command.requestId(),
                    command.studentId(),
                    false,
                    null,
                    exception.getReason()
            );
        }
        cotaProducer.publicarResultado(event);
    }
}
