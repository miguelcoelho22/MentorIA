package br.com.mentoria.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import br.com.mentoria.feedback.FeedbackData.Competency;
import br.com.mentoria.feedback.FeedbackData.DraftRequest;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

class FeedbackAnalysisClientTests {

    private static final DraftRequest DRAFT = new DraftRequest(UUID.randomUUID(), UUID.randomUUID(), "Tema", "Uma redação.");

    @Test
    void sendsDraftToTheAnalysisEndpointAndRetriesTemporaryFailure() throws IOException {
        var calls = new AtomicInteger();
        var request = new AtomicReference<String>();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext("/api/v1/analysis", exchange -> {
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            if (calls.incrementAndGet() == 1) {
                reply(exchange, 503, "{}");
                return;
            }
            reply(exchange, 200, """
                    {"competencies":[{"id":5,"score":80},{"id":2,"score":160},{"id":1,"score":160},
                                      {"id":3,"score":140},{"id":4,"score":100}]}
                    """);
        });
        server.start();
        try {
            var result = client(server).analyze(DRAFT);
            assertThat(calls.get()).isEqualTo(2);
            assertThat(result).extracting(Competency::id).containsExactly(1, 2, 3, 4, 5);
            assertThat(new JsonMapper().readValue(request.get(), DraftRequest.class)).isEqualTo(DRAFT);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void reportsServiceUnavailableAfterRepeatedInvalidResults() throws IOException {
        var calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext("/api/v1/analysis", exchange -> {
            calls.incrementAndGet();
            reply(exchange, 200, "{\"competencies\":[{\"id\":1,\"score\":210}]}");
        });
        server.start();
        try {
            var error = assertThrows(ResponseStatusException.class, () -> client(server).analyze(DRAFT));
            assertThat(calls.get()).isEqualTo(3);
            assertThat(error.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(error.getBody().getType()).hasToString("https://mentoria.app/errors/ai-unavailable");
        } finally {
            server.stop(0);
        }
    }

    private static FeedbackAnalysisClient client(HttpServer server) {
        return new FeedbackAnalysisClient("http://127.0.0.1:" + server.getAddress().getPort(),
                "/api/v1/analysis", Duration.ofSeconds(2), 3);
    }

    private static void reply(HttpExchange exchange, int status, String body) throws IOException {
        try (exchange) {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }
}
