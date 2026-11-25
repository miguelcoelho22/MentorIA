package br.com.mentoria.account;

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
import tools.jackson.databind.ObjectMapper;

@Component
public class CotaProducer {

    private static final Logger LOG = LoggerFactory.getLogger(CotaProducer.class);
    static final String ROUTING_KEY = "saga.cota.validada";

    private final JdbcTemplate jdbc;
    private final RabbitTemplate rabbit;
    private final ObjectMapper json;
    private final TransactionTemplate transaction;
    private final Duration confirmTimeout;

    CotaProducer(JdbcTemplate jdbc, RabbitTemplate rabbit, ObjectMapper json, PlatformTransactionManager manager,
            @Value("${account.quota-outbox.confirm-timeout}") Duration confirmTimeout) {
        this.jdbc = jdbc;
        this.rabbit = rabbit;
        this.json = json;
        this.transaction = new TransactionTemplate(manager);
        this.confirmTimeout = confirmTimeout;
    }

    void enqueue(CotaValidadaEvent event) {
        jdbc.update("INSERT INTO quota_result_outbox (event_id, payload) VALUES (?, ?)",
                UUID.randomUUID(), json.writeValueAsString(event));
    }

    @Scheduled(fixedDelayString = "${account.quota-outbox.interval-ms}")
    public void publishPending() {
        for (int index = 0; index < 20; index++) {
            if (!Boolean.TRUE.equals(transaction.execute(status -> publishNext()))) {
                return;
            }
        }
    }

    private boolean publishNext() {
        var event = jdbc.query("""
                SELECT event_id, payload FROM quota_result_outbox WHERE published_at IS NULL
                ORDER BY created_at, event_id LIMIT 1 FOR UPDATE SKIP LOCKED
                """, rows -> rows.next() ? new PendingResult(rows.getObject("event_id", UUID.class),
                        rows.getString("payload")) : null);
        if (event == null || !send(event)) {
            return false;
        }
        jdbc.update("UPDATE quota_result_outbox SET published_at = CURRENT_TIMESTAMP WHERE event_id = ?", event.id());
        return true;
    }

    private boolean send(PendingResult event) {
        var properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setMessageId(event.id().toString());
        var correlation = new CorrelationData(event.id().toString());
        try {
            var message = new Message(event.payload().getBytes(StandardCharsets.UTF_8), properties);
            rabbit.send(RabbitMQConfig.SAGA_EXCHANGE, ROUTING_KEY, message, correlation);
            var confirmation = correlation.getFuture().get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (confirmation.isAck() && correlation.getReturned() == null) {
                return true;
            }
            LOG.warn("Resultado de cota sem confirmação ou sem rota, eventId={}", event.id());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (AmqpException | TimeoutException | ExecutionException e) {
            LOG.warn("Falha no envio do resultado de cota, eventId={}, causa={}", event.id(), e.getClass().getSimpleName());
        }
        return false;
    }

    private record PendingResult(UUID id, String payload) { }
}
