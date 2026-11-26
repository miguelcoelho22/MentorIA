package br.com.mentoria.saga_orchestrator;

import java.util.ArrayList;
import java.util.List;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Filas da SAGA. O orquestrador declara também as filas de comando dos participantes, para que
 * nenhum comando se perca por falta de rota quando um participante ainda não subiu.
 */
@Configuration(proxyBeanMethods = false)
class SagaMessaging {

    static final String SAGA_EXCHANGE = "mentoria.saga";
    static final String EVENTS_EXCHANGE = "mentoria.events";

    // Respostas que o orquestrador consome.
    static final String START_QUEUE = "saga.redacao.enviada";
    static final String START_ROUTING_KEY = "redacao.enviada";
    static final String QUOTA_RESULT_QUEUE = "saga.cota.validada";
    static final String ANALYSIS_RESULT_QUEUE = "saga.redacao.analisada";
    static final String FEEDBACK_FAILED_QUEUE = "saga.feedback.falhou";
    static final String FEEDBACK_PUBLISHED_QUEUE = "saga.feedback.publicado";
    static final String FEEDBACK_PUBLISHED_ROUTING_KEY = "feedback.published";

    // Comandos que o orquestrador envia (a routing key é igual ao nome da fila).
    static final String VALIDATE_QUOTA = "account.validar.cota";
    static final String ANALYZE_ESSAY = "ai.processar.redacao";
    static final String GENERATE_FEEDBACK = "feedback.gerar";
    static final String CORRECTION_RESULT = "assessment.correcao.resultado";

    @Bean
    DirectExchange sagaExchange() {
        return new DirectExchange(SAGA_EXCHANGE, true, false);
    }

    @Bean
    TopicExchange eventsExchange() {
        return new TopicExchange(EVENTS_EXCHANGE, true, false);
    }

    @Bean
    Declarables sagaQueues(DirectExchange sagaExchange, TopicExchange eventsExchange) {
        List<Declarable> declarables = new ArrayList<>();
        bind(declarables, sagaExchange, START_QUEUE, START_ROUTING_KEY);
        for (String name : List.of(QUOTA_RESULT_QUEUE, ANALYSIS_RESULT_QUEUE, FEEDBACK_FAILED_QUEUE,
                VALIDATE_QUOTA, ANALYZE_ESSAY, GENERATE_FEEDBACK, CORRECTION_RESULT)) {
            bind(declarables, sagaExchange, name, name);
        }
        Queue published = new Queue(FEEDBACK_PUBLISHED_QUEUE, true);
        declarables.add(published);
        declarables.add(BindingBuilder.bind(published).to(eventsExchange).with(FEEDBACK_PUBLISHED_ROUTING_KEY));
        return new Declarables(declarables);
    }

    private static void bind(List<Declarable> declarables, DirectExchange exchange, String queueName, String routingKey) {
        Queue queue = new Queue(queueName, true);
        Binding binding = BindingBuilder.bind(queue).to(exchange).with(routingKey);
        declarables.add(queue);
        declarables.add(binding);
    }
}
