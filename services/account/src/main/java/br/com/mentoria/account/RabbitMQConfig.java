package br.com.mentoria.account;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String SAGA_EXCHANGE = "mentoria.saga";

    public static final String VALIDAR_COTA_QUEUE = "account.validar.cota";

    @Bean
    public DirectExchange sagaExchange() {
        return new DirectExchange(SAGA_EXCHANGE);
    }

    @Bean
    public Queue validarCotaQueue() {
        return new Queue(VALIDAR_COTA_QUEUE, true);
    }

    @Bean
    public Binding validarCotaBinding() {
        return BindingBuilder
                .bind(validarCotaQueue())
                .to(sagaExchange())
                .with("account.validar.cota");
    }
}