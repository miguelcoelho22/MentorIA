package br.com.mentoria.teacher;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import br.com.mentoria.teacher.TeacherData.ClassSummary;
import br.com.mentoria.teacher.TeacherData.Essay;
import br.com.mentoria.teacher.TeacherData.Feedback;
import br.com.mentoria.teacher.TeacherData.Student;
import br.com.mentoria.teacher.TeacherData.StudyProgress;
import br.com.mentoria.teacher.TeacherData.Theme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
class TeacherClients {

    private final RestClient account;
    private final RestClient assessment;
    private final RestClient feedback;
    private final RestClient studyPlan;

    TeacherClients(DownstreamHttp http,
            @Value("${mentoria.account.url}") String accountUrl,
            @Value("${mentoria.assessment.url}") String assessmentUrl,
            @Value("${mentoria.feedback.url}") String feedbackUrl,
            @Value("${mentoria.study-plan.url}") String studyPlanUrl) {
        account = http.builder(accountUrl).build();
        assessment = http.builder(assessmentUrl).build();
        feedback = http.builder(feedbackUrl).build();
        studyPlan = http.builder(studyPlanUrl).build();
    }

    List<ClassSummary> classes(UUID teacherId) {
        return account.get().uri("/api/v1/classes?teacherId={teacherId}", teacherId)
                .retrieve().requiredBody(new ParameterizedTypeReference<>() { });
    }

    List<Student> classStudents(String classId) {
        return account.get().uri("/api/v1/classes/{classId}/students", classId)
                .retrieve().requiredBody(new ParameterizedTypeReference<>() { });
    }

    Student student(UUID studentId) {
        return account.get().uri("/api/v1/students/{studentId}", studentId).retrieve().requiredBody(Student.class);
    }

    StudyProgress progress(UUID studentId) {
        return studyPlan.get().uri("/api/v1/students/{studentId}/progress", studentId)
                .retrieve().requiredBody(StudyProgress.class);
    }

    List<StudyProgress> classProgress(Collection<UUID> studentIds) {
        String ids = studentIds.stream().map(UUID::toString).collect(Collectors.joining(","));
        return studyPlan.get().uri(uri -> uri.path("/api/v1/progress").queryParam("studentIds", ids).build())
                .retrieve().requiredBody(new ParameterizedTypeReference<>() { });
    }

    List<Essay> essays(UUID studentId) {
        return assessment.get().uri("/api/v1/essays?studentId={studentId}", studentId)
                .retrieve().requiredBody(new ParameterizedTypeReference<>() { });
    }

    List<Theme> themes() {
        return assessment.get().uri("/api/v1/themes").retrieve().requiredBody(new ParameterizedTypeReference<>() { });
    }

    List<Feedback> feedbacks(UUID studentId) {
        return feedback.get().uri("/api/v1/feedbacks?studentId={studentId}", studentId)
                .retrieve().requiredBody(new ParameterizedTypeReference<>() { });
    }
}
