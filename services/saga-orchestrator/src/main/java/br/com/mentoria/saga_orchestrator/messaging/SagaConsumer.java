package br.com.mentoria.saga_orchestrator.messaging;

import br.com.mentoria.saga_orchestrator.config.RabbitMQConfig;
import br.com.mentoria.saga_orchestrator.service.SagaService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SagaConsumer {

    private final SagaService sagaService;

    @RabbitListener(queues = RabbitMQConfig.REDACAO_ENVIADA_QUEUE)
    public void receberRedacaoEnviada(RedacaoEnviadaEvent event) {

        System.out.println("Redação recebida: " + event.redacaoId());

        sagaService.iniciarSaga(event);
    }
}