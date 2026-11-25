package br.com.mentoria.feedback;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class FeedbackMessaging {

    @Bean
    TopicExchange feedbackExchange(@Value("${feedback.rabbitmq.exchange}") String name) {
        return new TopicExchange(name, true, false);
    }
}
