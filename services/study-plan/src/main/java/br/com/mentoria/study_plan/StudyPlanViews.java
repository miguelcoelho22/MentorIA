package br.com.mentoria.study_plan;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

final class StudyPlanViews {

    private StudyPlanViews() { }

    record Task(String id, String title, int competency, String status) { }

    record Plan(UUID studentId, Instant updatedAt, List<Task> tasks) { }

    record Average(int id, BigDecimal average) { }

    record Progress(UUID studentId, int lastScore, String trend,
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer weakestCompetency,
            List<Average> averageByCompetency, int tasksDone, int tasksPending, Instant updatedAt) { }
}
