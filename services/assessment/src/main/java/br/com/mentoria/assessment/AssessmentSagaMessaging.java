package br.com.mentoria.assessment;

import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class AssessmentSagaMessaging {

    @Bean
    DirectExchange sagaExchange(@Value("${assessment.saga.exchange}") String name) {
        return new DirectExchange(name, true, false);
    }

    @Bean
    Queue sagaStartQueue(@Value("${assessment.saga.start-queue}") String name) {
        return new Queue(name, true);
    }

    @Bean
    Queue correctionResultQueue(@Value("${assessment.saga.result-queue}") String name) {
        return new Queue(name, true);
    }

    @Bean
    Binding sagaStartBinding(DirectExchange sagaExchange, @Qualifier("sagaStartQueue") Queue queue,
            @Value("${assessment.saga.start-routing-key}") String routingKey) {
        return BindingBuilder.bind(queue).to(sagaExchange).with(routingKey);
    }

    @Bean
    Binding correctionResultBinding(DirectExchange sagaExchange, @Qualifier("correctionResultQueue") Queue queue,
            @Value("${assessment.saga.result-routing-key}") String routingKey) {
        return BindingBuilder.bind(queue).to(sagaExchange).with(routingKey);
    }
}
