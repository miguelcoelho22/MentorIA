package br.com.mentoria.feedback;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class FeedbackMessaging {

    public static final String FEEDBACK_QUEUE = "feedback.gerar";

    @Bean
    Jackson2JsonMessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter(GerarFeedbackCommand.class.getPackageName());
    }

    @Bean
    TopicExchange feedbackExchange(
            @Value("${feedback.rabbitmq.exchange}") String name
    ) {
        return new TopicExchange(name, true, false);
    }

    @Bean
    Queue feedbackQueue() {
        return new Queue(FEEDBACK_QUEUE, true);
    }

    @Bean
    DirectExchange commandExchange(
            @Value("${feedback.rabbitmq.command-exchange}") String name) {
        return new DirectExchange(name, true, false);
    }

    @Bean
    Binding feedbackBinding(
            Queue feedbackQueue,
            @Qualifier("commandExchange") DirectExchange commandExchange
    ) {
        return BindingBuilder
                .bind(feedbackQueue)
                .to(commandExchange)
                .with(FEEDBACK_QUEUE);
    }
}
