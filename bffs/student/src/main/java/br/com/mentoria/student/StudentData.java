package br.com.mentoria.student;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

final class StudentData {

    private StudentData() { }

    record Link(String href) { }

    record Home(HomeQuota quota, List<HomeTask> nextTasks, Progress progress,
            @JsonProperty("_links") Map<String, Link> links) { }

    record HomeQuota(int available, Instant renewsOn) { }

    record HomeTask(String id, String title, int competency, boolean done) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Progress(int lastScore, String trend) { }

    record Theme(String id, String title, String description) { }

    record EssayCreate(String themeId, String text) { }

    record AssessmentEssay(UUID id, UUID studentId, String themeId, String text, Instant submittedAt) {
        Essay view() {
            return new Essay(id, themeId, submittedAt, text);
        }
    }

    record Essay(UUID id, String themeId, Instant submittedAt,
            @JsonInclude(JsonInclude.Include.NON_NULL) String text) { }

    record Correction(UUID id, UUID essayId, String status, Instant requestedAt,
            @JsonInclude(JsonInclude.Include.NON_NULL) String reason,
            @JsonInclude(JsonInclude.Include.NON_NULL) @JsonProperty("_links") Map<String, Link> links) { }

    record Competency(int id, int score, String comment) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PublishedFeedback(UUID requestId, UUID studentId, UUID essayId, int totalScore, Instant publishedAt,
            List<Competency> competencies) {
        Feedback view() {
            return new Feedback(requestId, essayId, totalScore, publishedAt, competencies);
        }
    }

    record Feedback(UUID requestId, UUID essayId, int totalScore,
            @JsonInclude(JsonInclude.Include.NON_NULL) Instant publishedAt, List<Competency> competencies) { }

    record Task(String id, String title, int competency, String status) { }

    record TaskUpdate(String status) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Plan(Instant updatedAt, List<Task> tasks) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Quota(String period, int limit, int reserved, int consumed, int available) { }
}
