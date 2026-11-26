package br.com.mentoria.saga_orchestrator;

import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Comandos síncronos e idempotentes que o orquestrador envia aos participantes. */
@Component
class SagaClients {

    private static final Logger LOG = LoggerFactory.getLogger(SagaClients.class);

    record EssayContent(String theme, String text) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Essay(UUID id, String themeId, String text) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Theme(String id, String title) { }

    private final RestClient assessment;
    private final RestClient account;
    private final RestClient feedback;

    SagaClients(@Value("${saga.assessment.url}") String assessmentUrl,
            @Value("${saga.account.url}") String accountUrl,
            @Value("${saga.feedback.url}") String feedbackUrl,
            @Value("${saga.http.timeout}") Duration timeout) {
        assessment = client(assessmentUrl, timeout);
        account = client(accountUrl, timeout);
        feedback = client(feedbackUrl, timeout);
    }

    private static RestClient client(String baseUrl, Duration timeout) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    /** Lê o texto e o título do tema da redação no Assessment. */
    EssayContent essay(UUID essayId) {
        Essay essay = assessment.get().uri("/api/v1/essays/{essayId}", essayId).retrieve().body(Essay.class);
        if (essay == null || essay.text() == null || essay.text().isBlank()) {
            throw new RestClientException("O Assessment não devolveu o texto da redação " + essayId + ".");
        }
        Theme[] themes = assessment.get().uri("/api/v1/themes").retrieve().body(Theme[].class);
        String title = themes == null ? null : Arrays.stream(themes)
                .filter(theme -> theme != null && theme.id() != null && theme.id().equals(essay.themeId()))
                .map(Theme::title).findFirst().orElse(null);
        return new EssayContent(title == null ? "Tema livre" : title, essay.text());
    }

    /** Compensação no Account: devolve a reserva da cota. Idempotente. */
    void releaseQuota(UUID requestId) {
        try {
            account.delete().uri("/api/v1/quota-reservations/{requestId}", requestId).retrieve().toBodilessEntity();
        } catch (HttpClientErrorException.Conflict e) {
            LOG.warn("Reserva {} já consumida; não há o que liberar.", requestId);
        }
    }

    /** Passo final no Account: a reserva vira consumo. Idempotente. */
    void confirmQuota(UUID requestId) {
        account.put().uri("/api/v1/quota-reservations/{requestId}/confirmation", requestId).retrieve().toBodilessEntity();
    }

    /**
     * Compensação no Feedback: cancela o rascunho, se existir. Idempotente.
     *
     * @return {@code false} se o feedback já foi publicado: a SAGA passou do ponto pivô e não pode mais voltar.
     */
    boolean cancelFeedback(UUID requestId) {
        try {
            feedback.delete().uri("/api/v1/feedbacks/{requestId}", requestId).retrieve().toBodilessEntity();
            return true;
        } catch (HttpClientErrorException.Conflict e) {
            return false;
        }
    }
}
