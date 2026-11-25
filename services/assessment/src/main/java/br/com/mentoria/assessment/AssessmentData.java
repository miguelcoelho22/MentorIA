package br.com.mentoria.assessment;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

final class AssessmentData {

    private AssessmentData() { }

    record Theme(String id, String title, String description) { }

    record EssayCreate(@NotBlank String themeId, @NotBlank String text) { }

    record Essay(UUID id, UUID studentId, String themeId,
            @JsonInclude(JsonInclude.Include.NON_NULL) String text, Instant submittedAt) { }

    enum Status {
        PENDING, PROCESSING, COMPLETED, REJECTED, FAILED;

        boolean terminal() {
            return this == COMPLETED || this == REJECTED || this == FAILED;
        }
    }

    enum Reason { QUOTA_EXHAUSTED, AI_UNAVAILABLE, PUBLICATION_ERROR }

    record Link(String href) { }

    record CorrectionRequest(UUID id, UUID essayId, Status status, Instant requestedAt,
            @JsonInclude(JsonInclude.Include.NON_NULL) Reason reason, @JsonProperty("_links") Map<String, Link> links) { }

    record RedacaoEnviadaEvent(UUID redacaoId, UUID alunoId) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CorrectionResult(UUID requestId, Status status, Reason reason) { }
}
