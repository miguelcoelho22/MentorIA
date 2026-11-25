package br.com.mentoria.account;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CotaProducer {

    private final RabbitTemplate rabbitTemplate;

    public void publicarResultado(CotaValidadaEvent event) {

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.SAGA_EXCHANGE,
                "saga.cota.validada",
                event
        );
    }
}