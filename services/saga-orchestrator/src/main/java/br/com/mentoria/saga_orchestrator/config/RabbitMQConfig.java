package br.com.mentoria.saga_orchestrator.config;

import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String SAGA_EXCHANGE = "mentoria.saga";

    public static final String REDACAO_ENVIADA_QUEUE = "saga.redacao.enviada";

    public static final String VALIDAR_COTA_QUEUE = "account.validar.cota";

    public static final String COTA_VALIDADA_QUEUE = "saga.cota.validada";

    public static final String PROCESSAR_REDACAO_QUEUE = "ai.processar.redacao";

    public static final String REDACAO_ANALISADA_QUEUE = "saga.redacao.analisada";

    @Bean
    public DirectExchange sagaExchange() {
        return new DirectExchange(SAGA_EXCHANGE);
    }

    @Bean
    public Queue redacaoEnviadaQueue() {
        return new Queue(REDACAO_ENVIADA_QUEUE, true);
    }

    @Bean
    public Queue validarCotaQueue() {
        return new Queue(VALIDAR_COTA_QUEUE, true);
    }

    @Bean
    public Binding redacaoEnviadaBinding() {
        return BindingBuilder
                .bind(redacaoEnviadaQueue())
                .to(sagaExchange())
                .with("redacao.enviada");
    }

    @Bean
    public Binding validarCotaBinding() {
        return BindingBuilder
                .bind(validarCotaQueue())
                .to(sagaExchange())
                .with("account.validar.cota");
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMandatory(true);
        return template;
    }

    @Bean
    public Queue cotaValidadaQueue() {
        return new Queue(COTA_VALIDADA_QUEUE, true);
    }

    @Bean
    public Binding cotaValidadaBinding() {
        return BindingBuilder
                .bind(cotaValidadaQueue())
                .to(sagaExchange())
                .with("saga.cota.validada");
    }

    @Bean
    public Queue processarRedacaoQueue() {
        return new Queue(PROCESSAR_REDACAO_QUEUE, true);
    }

    @Bean
    public Binding processarRedacaoBinding() {
        return BindingBuilder
                .bind(processarRedacaoQueue())
                .to(sagaExchange())
                .with("ai.processar.redacao");
    }

    @Bean
    public Queue redacaoAnalisadaQueue() {
        return new Queue(REDACAO_ANALISADA_QUEUE, true);
    }

    @Bean
    public Binding redacaoAnalisadaBinding() {
        return BindingBuilder
                .bind(redacaoAnalisadaQueue())
                .to(sagaExchange())
                .with("saga.redacao.analisada");
    }

    public static final String FEEDBACK_PUBLISHED_QUEUE = "saga.feedback.publicado";

    @Bean
    public Queue feedbackPublishedQueue() {
        return new Queue(FEEDBACK_PUBLISHED_QUEUE, true);
    }

    @Bean
    public Binding feedbackPublishedBinding() {
        return BindingBuilder
                .bind(feedbackPublishedQueue())
                .to(eventsExchange())
                .with("feedback.published");
    }
    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange("mentoria.events", true, false);
    }

    @Bean
    public JacksonJsonMessageConverter jacksonJsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}