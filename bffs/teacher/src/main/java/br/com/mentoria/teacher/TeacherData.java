package br.com.mentoria.teacher;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

final class TeacherData {

    private TeacherData() { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ClassSummary(String id, String name, int studentCount) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Student(UUID id, String name, String classId) { }

    record Average(int id, BigDecimal average) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record StudyProgress(UUID studentId, int lastScore, String trend, Integer weakestCompetency,
            List<Average> averageByCompetency, int tasksDone, int tasksPending) {
        Progress view() {
            return new Progress(lastScore, trend, weakestCompetency, averageByCompetency, tasksDone, tasksPending);
        }
    }

    record Progress(int lastScore, String trend,
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer weakestCompetency,
            List<Average> averageByCompetency, int tasksDone, int tasksPending) { }

    record ClassOverview(String classId, int students, double averageScore, Integer weakestCompetency,
            List<StudentNeedingSupport> needSupport) { }

    record StudentNeedingSupport(UUID studentId, String name, int lastScore, String trend, int pendingTasks) { }

    record StudentDetail(UUID studentId, String name, Progress progress, List<EssaySummary> essays) { }

    record EssaySummary(UUID essayId, String theme, Instant submittedAt, Integer totalScore) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Essay(UUID id, String themeId, Instant submittedAt) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Theme(String id, String title) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Feedback(UUID essayId, String status, int totalScore, Instant publishedAt) { }
}
