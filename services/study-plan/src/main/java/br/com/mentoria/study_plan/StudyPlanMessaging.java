package br.com.mentoria.study_plan;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class StudyPlanMessaging {

    @Bean
    TopicExchange feedbackExchange(@Value("${study-plan.rabbitmq.exchange}") String name) {
        return new TopicExchange(name, true, false);
    }

    @Bean
    Queue feedbackQueue(@Value("${study-plan.rabbitmq.queue}") String name) {
        return new Queue(name, true);
    }

    @Bean
    Binding feedbackBinding(TopicExchange feedbackExchange, Queue feedbackQueue,
            @Value("${study-plan.rabbitmq.routing-key}") String routingKey) {
        return BindingBuilder.bind(feedbackQueue).to(feedbackExchange).with(routingKey);
    }
}
