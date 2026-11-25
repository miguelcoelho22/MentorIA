package br.com.mentoria.study_plan;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
record FeedbackPublished(String eventId, UUID studentId, String requestId, OffsetDateTime publishedAt,
        Integer totalScore, List<CompetencyScore> competencies) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CompetencyScore(Integer id, Integer score) { }
}
