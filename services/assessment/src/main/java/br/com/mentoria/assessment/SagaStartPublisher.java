package br.com.mentoria.assessment;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
class SagaStartPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(SagaStartPublisher.class);

    private final JdbcTemplate jdbc;
    private final RabbitTemplate rabbit;
    private final TransactionTemplate transaction;
    private final String exchange;
    private final String routingKey;
    private final Duration timeout;

    SagaStartPublisher(JdbcTemplate jdbc, RabbitTemplate rabbit, PlatformTransactionManager manager,
            @Value("${assessment.saga.exchange}") String exchange,
            @Value("${assessment.saga.start-routing-key}") String routingKey,
            @Value("${assessment.outbox.confirm-timeout}") Duration timeout) {
        this.jdbc = jdbc;
        this.rabbit = rabbit;
        this.transaction = new TransactionTemplate(manager);
        this.exchange = exchange;
        this.routingKey = routingKey;
        this.timeout = timeout;
    }

    @Scheduled(fixedDelayString = "${assessment.outbox.interval-ms}")
    public void publishPending() {
        for (int index = 0; index < 20; index++) {
            if (!Boolean.TRUE.equals(transaction.execute(status -> publishNext()))) {
                return;
            }
        }
    }

    private boolean publishNext() {
        var message = jdbc.query("""
                SELECT request_id, payload FROM saga_start_outbox WHERE published_at IS NULL
                ORDER BY created_at, request_id LIMIT 1 FOR UPDATE SKIP LOCKED
                """, rows -> rows.next() ? new StartMessage(rows.getObject("request_id", UUID.class),
                        rows.getString("payload")) : null);
        if (message == null || !send(message)) {
            return false;
        }
        jdbc.update("UPDATE saga_start_outbox SET published_at = CURRENT_TIMESTAMP WHERE request_id = ?", message.requestId());
        LOG.info("redacao.enviada confirmado pelo RabbitMQ, requestId={}", message.requestId());
        return true;
    }

    private boolean send(StartMessage message) {
        var properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setMessageId(message.requestId().toString());
        var correlation = new CorrelationData(message.requestId().toString());

        try {
            rabbit.send(exchange, routingKey, new Message(message.payload().getBytes(StandardCharsets.UTF_8), properties), correlation);
            var confirmation = correlation.getFuture().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (confirmation.isAck() && correlation.getReturned() == null) {
                return true;
            }
            LOG.warn("Evento de início da SAGA não confirmado ou sem rota, requestId={}", message.requestId());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (AmqpException | TimeoutException | ExecutionException e) {
            LOG.warn("Falha no envio do início da SAGA, requestId={}, causa={}",
                    message.requestId(), e.getClass().getSimpleName());
        }
        return false;
    }

    private record StartMessage(UUID requestId, String payload) { }
}
