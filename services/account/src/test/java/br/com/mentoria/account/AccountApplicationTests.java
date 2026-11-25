package br.com.mentoria.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

import java.util.ArrayList;
import java.util.UUID;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:account-context;MODE=PostgreSQL",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "account.quota-outbox.enabled=false",
        "account.jwt.private-key=",
        "account.seed.enabled=false"
})
class AccountApplicationTests {

    @Autowired private AccountTokenIssuer tokens;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private CotaProducer producer;
    @Autowired private ObjectMapper json;
    @MockitoBean private RabbitTemplate rabbit;

    @Test
    void verifiesTokensAfterReloadingTheSigningKeyFromTheDatabase() throws Exception {
        var token = SignedJWT.parse(tokens.issue(UUID.randomUUID(), "STUDENT"));

        var reloaded = new AccountTokenIssuer(jdbc, "");
        var keys = JWKSet.parse(reloaded.publicJwks());
        var key = (RSAKey) keys.getKeyByKeyId(token.getHeader().getKeyID());

        assertThat(reloaded.publicJwks()).isEqualTo(tokens.publicJwks());
        assertThat(token.verify(new RSASSAVerifier(key))).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM account_signing_key", Integer.class)).isEqualTo(1);
    }

    @Test
    @Transactional
    void retriesTheSamePersistentJsonUntilRabbitRoutesAndConfirmsIt() {
        var result = new CotaValidadaEvent(1L, UUID.randomUUID(), UUID.randomUUID(), true, "2026-11", null);
        producer.enqueue(result);

        var sent = new ArrayList<Message>();
        doAnswer(call -> {
            Message message = call.getArgument(2);
            CorrelationData correlation = call.getArgument(3);
            sent.add(message);
            if (sent.size() == 1) {
                correlation.setReturned(new ReturnedMessage(message, 312, "NO_ROUTE",
                        RabbitMQConfig.SAGA_EXCHANGE, CotaProducer.ROUTING_KEY));
            }
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).send(eq(RabbitMQConfig.SAGA_EXCHANGE), eq(CotaProducer.ROUTING_KEY),
                any(Message.class), any(CorrelationData.class));

        String eventId = jdbc.queryForObject("SELECT event_id FROM quota_result_outbox", UUID.class).toString();
        producer.publishPending();
        assertThat(jdbc.queryForObject("SELECT published_at FROM quota_result_outbox", Object.class)).isNull();
        producer.publishPending();
        producer.publishPending();
        assertThat(jdbc.queryForObject("SELECT published_at FROM quota_result_outbox", Object.class)).isNotNull();
        assertThat(sent).hasSize(2).allSatisfy(message -> {
            var properties = message.getMessageProperties();
            assertThat(properties.getMessageId()).isEqualTo(eventId);
            assertThat(properties.getContentType()).isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
            assertThat(properties.getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
            assertThat(json.readValue(message.getBody(), CotaValidadaEvent.class)).isEqualTo(result);
        });
    }
}
