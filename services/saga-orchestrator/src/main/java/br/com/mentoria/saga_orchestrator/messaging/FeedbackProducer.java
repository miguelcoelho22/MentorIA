package br.com.mentoria.saga_orchestrator.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FeedbackProducer {

    private final RabbitTemplate rabbitTemplate;

    public void solicitarGeracaoFeedback(GerarFeedbackCommand command) {
        rabbitTemplate.convertAndSend(
                "mentoria.saga",
                "feedback.gerar",
                command
        );
    }
}