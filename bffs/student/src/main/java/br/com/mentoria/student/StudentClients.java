package br.com.mentoria.student;

import static br.com.mentoria.student.BffWebConfiguration.USER_ID_HEADER;

import java.util.List;
import java.util.UUID;

import br.com.mentoria.student.StudentData.AssessmentEssay;
import br.com.mentoria.student.StudentData.Correction;
import br.com.mentoria.student.StudentData.EssayCreate;
import br.com.mentoria.student.StudentData.Plan;
import br.com.mentoria.student.StudentData.Progress;
import br.com.mentoria.student.StudentData.PublishedFeedback;
import br.com.mentoria.student.StudentData.Quota;
import br.com.mentoria.student.StudentData.TaskUpdate;
import br.com.mentoria.student.StudentData.Theme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
class StudentClients {

    private final RestClient account;
    private final RestClient assessment;
    private final RestClient feedback;
    private final RestClient studyPlan;

    StudentClients(DownstreamHttp http,
            @Value("${mentoria.account.url}") String accountUrl,
            @Value("${mentoria.assessment.url}") String assessmentUrl,
            @Value("${mentoria.feedback.url}") String feedbackUrl,
            @Value("${mentoria.study-plan.url}") String studyPlanUrl) {
        account = http.builder(accountUrl).build();
        assessment = http.builder(assessmentUrl).build();
        feedback = http.builder(feedbackUrl).build();
        studyPlan = http.builder(studyPlanUrl).build();
    }

    Quota quota(UUID studentId) {
        return account.get().uri("/api/v1/students/{studentId}/quota", studentId).retrieve().requiredBody(Quota.class);
    }

    List<Theme> themes() {
        return assessment.get().uri("/api/v1/themes").retrieve().requiredBody(new ParameterizedTypeReference<>() { });
    }

    List<AssessmentEssay> essays(UUID studentId) {
        return assessment.get().uri("/api/v1/essays?studentId={studentId}", studentId)
                .retrieve().requiredBody(new ParameterizedTypeReference<>() { });
    }

    AssessmentEssay createEssay(UUID studentId, EssayCreate draft) {
        return assessment.post().uri("/api/v1/essays").header(USER_ID_HEADER, studentId.toString())
                .contentType(MediaType.APPLICATION_JSON).body(draft).retrieve().requiredBody(AssessmentEssay.class);
    }

    AssessmentEssay essay(UUID essayId) {
        return assessment.get().uri("/api/v1/essays/{essayId}", essayId).retrieve().requiredBody(AssessmentEssay.class);
    }

    Correction requestCorrection(UUID studentId, UUID essayId) {
        return assessment.post().uri("/api/v1/essays/{essayId}/correction-requests", essayId)
                .header(USER_ID_HEADER, studentId.toString()).retrieve().requiredBody(Correction.class);
    }

    Correction correction(UUID requestId) {
        return assessment.get().uri("/api/v1/correction-requests/{requestId}", requestId)
                .retrieve().requiredBody(Correction.class);
    }

    PublishedFeedback feedback(UUID requestId) {
        return feedback.get().uri("/api/v1/feedbacks/{requestId}", requestId).retrieve().requiredBody(PublishedFeedback.class);
    }

    Plan plan(UUID studentId) {
        return studyPlan.get().uri("/api/v1/students/{studentId}/plan", studentId).retrieve().requiredBody(Plan.class);
    }

    Progress progress(UUID studentId) {
        return studyPlan.get().uri("/api/v1/students/{studentId}/progress", studentId).retrieve().requiredBody(Progress.class);
    }

    void completeTask(String taskId) {
        studyPlan.patch().uri("/api/v1/tasks/{taskId}", taskId)
                .contentType(MediaType.APPLICATION_JSON).body(new TaskUpdate("DONE")).retrieve().toBodilessEntity();
    }
}
