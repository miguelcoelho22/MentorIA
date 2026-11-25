package br.com.mentoria.saga_orchestrator.config;

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
        return new RabbitTemplate(connectionFactory);
    }
}