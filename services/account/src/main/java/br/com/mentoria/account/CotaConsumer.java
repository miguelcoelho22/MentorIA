package br.com.mentoria.account;

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

        try {
            var resultado = quotaReservationService.reserve(
                    command.requestId(),
                    command.studentId()
            );

            var event = new CotaValidadaEvent(
                    command.sagaId(),
                    command.requestId(),
                    command.studentId(),
                    true,
                    resultado.reservation().period(),
                    null
            );

            cotaProducer.publicarResultado(event);

        } catch (ResponseStatusException exception) {

            var event = new CotaValidadaEvent(
                    command.sagaId(),
                    command.requestId(),
                    command.studentId(),
                    false,
                    null,
                    exception.getReason()
            );

            cotaProducer.publicarResultado(event);
        }
    }
}