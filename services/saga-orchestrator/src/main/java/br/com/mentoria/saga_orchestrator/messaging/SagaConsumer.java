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

    @RabbitListener(queues = RabbitMQConfig.COTA_VALIDADA_QUEUE)
    public void receberResultadoCota(CotaValidadaEvent event) {

        System.out.println("Resultado da validação de cota: "
                + event.aprovada());

        sagaService.processarResultadoCota(event);
    }

    @RabbitListener(queues = RabbitMQConfig.REDACAO_ANALISADA_QUEUE)
    public void receberResultadoAnalise(RedacaoAnalisadaEvent event) {
        System.out.println("Análise da redação recebida: " + event.essayId());
        sagaService.processarResultadoAnalise(event);
    }

    @RabbitListener(queues = RabbitMQConfig.FEEDBACK_PUBLISHED_QUEUE)
    public void receberFeedbackPublicado(FeedbackPublishedEvent event) {

        System.out.println("Feedback publicado para a redação: " + event.requestId());

        sagaService.processarFeedbackPublicado(event);
    }
}