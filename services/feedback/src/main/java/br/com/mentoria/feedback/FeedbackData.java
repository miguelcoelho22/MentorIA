package br.com.mentoria.feedback;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

final class FeedbackData {

    private FeedbackData() { }

    record DraftRequest(@NotNull UUID studentId, @NotNull UUID essayId, @NotBlank String theme, @NotBlank String text) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Competency(Integer id, Integer score, String comment) { }

    enum Status { DRAFT, PUBLISHED, CANCELLED }

    record Feedback(UUID requestId, UUID studentId, UUID essayId, Status status, int totalScore,
            @JsonInclude(JsonInclude.Include.NON_NULL) Instant publishedAt, List<Competency> competencies) { }

    record DraftResult(Feedback feedback, boolean created) { }

    record EventScore(int id, int score) { }

    record AnalyzedDraftRequest(
            UUID studentId,
            UUID essayId,
            List<Competency> competencies
    ) { }

    record FeedbackPublished(
            UUID eventId,
            @JsonInclude(JsonInclude.Include.NON_NULL) Long sagaId,
            UUID requestId,
            UUID studentId,
            Instant publishedAt,
            int totalScore,
            List<EventScore> competencies
    ) { }
}
