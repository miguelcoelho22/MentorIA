package br.com.mentoria.feedback;

import java.net.URI;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import br.com.mentoria.feedback.FeedbackData.Competency;
import br.com.mentoria.feedback.FeedbackData.DraftRequest;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Component
class FeedbackAnalysisClient {

    private final RestClient http;
    private final String analysisPath;
    private final int attempts;

    FeedbackAnalysisClient(@Value("${feedback.ai.base-url}") String baseUrl,
            @Value("${feedback.ai.analysis-path}") String analysisPath,
            @Value("${feedback.ai.timeout}") Duration timeout,
            @Value("${feedback.ai.attempts}") int attempts) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.analysisPath = analysisPath;
        this.attempts = attempts;
    }

    List<Competency> analyze(DraftRequest draft) {
        for (int attempt = 0; attempt < attempts; attempt++) {
            try {
                var result = http.post().uri(analysisPath).contentType(MediaType.APPLICATION_JSON)
                        .body(draft).retrieve().body(Analysis.class);
                if (result != null && valid(result.competencies())) {
                    return result.competencies().stream().sorted(Comparator.comparingInt(Competency::id)).toList();
                }
            } catch (RestClientException e) {
                // Every attempt repeats the same analysis request.
            }
        }
        var error = new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "O AI Service não retornou uma análise válida após as tentativas configuradas.");
        error.setTitle("Serviço de IA indisponível");
        error.setType(URI.create("https://mentoria.app/errors/ai-unavailable"));
        throw error;
    }

    private static boolean valid(List<Competency> scores) {
        if (scores == null || scores.size() != 5) {
            return false;
        }
        var ids = new HashSet<Integer>();
        return scores.stream().allMatch(score -> score != null && score.id() != null && score.id() >= 1 && score.id() <= 5
                && ids.add(score.id()) && score.score() != null && score.score() >= 0 && score.score() <= 200);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Analysis(List<Competency> competencies) { }
}
