package br.com.mentoria.feedback;

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
class FeedbackOutboxPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(FeedbackOutboxPublisher.class);

    private final JdbcTemplate jdbc;
    private final RabbitTemplate rabbit;
    private final TransactionTemplate transaction;
    private final String exchange;
    private final String routingKey;
    private final Duration confirmTimeout;

    FeedbackOutboxPublisher(JdbcTemplate jdbc, RabbitTemplate rabbit, PlatformTransactionManager manager,
            @Value("${feedback.rabbitmq.exchange}") String exchange,
            @Value("${feedback.rabbitmq.routing-key}") String routingKey,
            @Value("${feedback.outbox.confirm-timeout}") Duration confirmTimeout) {
        this.jdbc = jdbc;
        this.rabbit = rabbit;
        this.transaction = new TransactionTemplate(manager);
        this.exchange = exchange;
        this.routingKey = routingKey;
        this.confirmTimeout = confirmTimeout;
    }

    @Scheduled(fixedDelayString = "${feedback.outbox.interval-ms}")
    public void publishPending() {
        for (int index = 0; index < 20; index++) {
            if (!Boolean.TRUE.equals(transaction.execute(status -> publishNext()))) {
                return;
            }
        }
    }

    private boolean publishNext() {
        OutboxEvent event = jdbc.query("""
                SELECT event_id, event_type, payload FROM outbox_events WHERE status = 'PENDING'
                ORDER BY created_at, event_id LIMIT 1 FOR UPDATE SKIP LOCKED
                """, rows -> rows.next() ? new OutboxEvent(rows.getObject("event_id", UUID.class),
                        rows.getString("event_type"), rows.getString("payload")) : null);
        if (event == null || !send(event)) {
            return false;
        }

        jdbc.update("UPDATE outbox_events SET status = 'PUBLISHED', published_at = CURRENT_TIMESTAMP WHERE event_id = ?",
                event.id());
        LOG.info("{} confirmado pelo RabbitMQ, eventId={}", event.type(), event.id());
        return true;
    }

    private boolean send(OutboxEvent event) {
        var properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setMessageId(event.id().toString());
        properties.setType(event.type());
        var correlation = new CorrelationData(event.id().toString());
        try {
            rabbit.send(exchange, routingKey, new Message(event.payload().getBytes(StandardCharsets.UTF_8), properties), correlation);
            var confirm = correlation.getFuture().get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (confirm.isAck() && correlation.getReturned() == null) {
                return true;
            }
            LOG.warn("Evento da Outbox não confirmado ou sem rota, eventId={}", event.id());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (AmqpException | TimeoutException | ExecutionException e) {
            LOG.warn("Falha no envio da Outbox, eventId={}, causa={}", event.id(), e.getClass().getSimpleName());
        }
        return false;
    }

    private record OutboxEvent(UUID id, String type, String payload) { }
}
