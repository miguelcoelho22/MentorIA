package br.com.mentoria.saga_orchestrator.messaging;

import br.com.mentoria.saga_orchestrator.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AIProducer {

    private final RabbitTemplate rabbitTemplate;

    public void solicitarProcessamento(ProcessarRedacaoCommand command) {

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.SAGA_EXCHANGE,
                "ai.processar.redacao",
                command
        );
    }
}