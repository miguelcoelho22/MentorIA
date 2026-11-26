package br.com.mentoria.student;

import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.mentoria.student.StudentData.AssessmentEssay;
import br.com.mentoria.student.StudentData.Correction;
import br.com.mentoria.student.StudentData.Essay;
import br.com.mentoria.student.StudentData.EssayCreate;
import br.com.mentoria.student.StudentData.Feedback;
import br.com.mentoria.student.StudentData.Home;
import br.com.mentoria.student.StudentData.HomeQuota;
import br.com.mentoria.student.StudentData.HomeTask;
import br.com.mentoria.student.StudentData.Link;
import br.com.mentoria.student.StudentData.Plan;
import br.com.mentoria.student.StudentData.PublishedFeedback;
import br.com.mentoria.student.StudentData.Quota;
import br.com.mentoria.student.StudentData.Task;
import br.com.mentoria.student.StudentData.Theme;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.server.ResponseStatusException;

@Service
class StudentService {

    private static final String ESSAYS_PATH = StudentController.PREFIX + "/essays";
    private final StudentClients clients;

    StudentService(StudentClients clients) {
        this.clients = clients;
    }

    Home home(UUID studentId) {
        HomeQuota quota = homeQuota(studentId);
        List<HomeTask> nextTasks = clients.plan(studentId).tasks().stream()
                .sorted(Comparator.comparing((Task task) -> !"PENDING".equals(task.status())))
                .map(task -> new HomeTask(task.id(), task.title(), task.competency(), "DONE".equals(task.status())))
                .toList();
        return new Home(quota, nextTasks, clients.progress(studentId), Map.of("newEssay", new Link(ESSAYS_PATH)));
    }

    private HomeQuota homeQuota(UUID studentId) {
        try {
            Quota quota = clients.quota(studentId);
            var renewal = YearMonth.parse(quota.period()).plusMonths(1).atDay(1)
                    .atTime(LocalTime.NOON).toInstant(ZoneOffset.UTC);
            return new HomeQuota(quota.available(), renewal);
        } catch (ResourceAccessException unavailable) {
            return null;
        }
    }

    List<Theme> themes() {
        return clients.themes();
    }

    List<Essay> essays(UUID studentId) {
        return clients.essays(studentId).stream().map(AssessmentEssay::view).toList();
    }

    Essay createEssay(UUID studentId, EssayCreate draft) {
        return clients.createEssay(studentId, draft).view();
    }

    Essay essay(UUID studentId, UUID essayId) {
        AssessmentEssay essay = clients.essay(essayId);
        requireOwner(studentId, essay.studentId(), "Redação não encontrada.");
        return essay.view();
    }

    Correction requestCorrection(UUID studentId, UUID essayId) {
        return withPublicLinks(clients.requestCorrection(studentId, essayId));
    }

    Correction correction(UUID studentId, UUID requestId) {
        Correction correction = clients.correction(requestId);
        AssessmentEssay essay = clients.essay(correction.essayId());
        requireOwner(studentId, essay.studentId(), "Pedido de correção não encontrado.");
        return withPublicLinks(correction);
    }

    Feedback feedback(UUID studentId, UUID requestId) {
        PublishedFeedback feedback = clients.feedback(requestId);
        requireOwner(studentId, feedback.studentId(), "Feedback não encontrado.");
        return feedback.view();
    }

    Plan plan(UUID studentId) {
        return clients.plan(studentId);
    }

    void completeTask(UUID studentId, String taskId) {
        boolean owned = plan(studentId).tasks().stream().anyMatch(task -> task.id().equals(taskId));
        if (!owned) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tarefa não encontrada.");
        }
        clients.completeTask(taskId);
    }

    Quota quota(UUID studentId) {
        return clients.quota(studentId);
    }

    private static void requireOwner(UUID studentId, UUID owner, String detail) {
        if (!studentId.equals(owner)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, detail);
        }
    }

    private static Correction withPublicLinks(Correction correction) {
        if (correction.links() == null) {
            return correction;
        }
        Map<String, Link> publicLinks = new LinkedHashMap<>();
        String essayPath = ESSAYS_PATH + "/" + correction.essayId();
        for (String relation : correction.links().keySet()) {
            String href = switch (relation) {
                case "self" -> StudentController.PREFIX + "/correction-requests/" + correction.id();
                case "essay" -> essayPath;
                case "feedback" -> StudentController.PREFIX + "/feedbacks/" + correction.id();
                case "study-plan" -> StudentController.PREFIX + "/plan";
                case "quota" -> StudentController.PREFIX + "/quota";
                case "retry" -> essayPath + "/correction-requests";
                default -> null;
            };
            if (href != null) {
                publicLinks.put(relation, new Link(href));
            }
        }
        return new Correction(correction.id(), correction.essayId(), correction.status(), correction.requestedAt(),
                correction.reason(), publicLinks);
    }
}
