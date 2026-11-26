package br.com.mentoria.student;

import static br.com.mentoria.student.BffWebConfiguration.USER_ID_ATTRIBUTE;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import br.com.mentoria.student.StudentData.Correction;
import br.com.mentoria.student.StudentData.Essay;
import br.com.mentoria.student.StudentData.EssayCreate;
import br.com.mentoria.student.StudentData.Feedback;
import br.com.mentoria.student.StudentData.Home;
import br.com.mentoria.student.StudentData.Plan;
import br.com.mentoria.student.StudentData.Quota;
import br.com.mentoria.student.StudentData.TaskUpdate;
import br.com.mentoria.student.StudentData.Theme;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping(StudentController.PREFIX)
class StudentController {

    static final String PREFIX = "/api/v1/student";
    private final StudentService service;

    StudentController(StudentService service) {
        this.service = service;
    }

    @GetMapping("/home")
    Home home(@RequestAttribute(USER_ID_ATTRIBUTE) UUID studentId) {
        return service.home(studentId);
    }

    @GetMapping("/themes")
    List<Theme> themes() {
        return service.themes();
    }

    @GetMapping("/essays")
    List<Essay> essays(@RequestAttribute(USER_ID_ATTRIBUTE) UUID studentId) {
        return service.essays(studentId);
    }

    @PostMapping("/essays")
    ResponseEntity<Essay> createEssay(@RequestAttribute(USER_ID_ATTRIBUTE) UUID studentId, @RequestBody EssayCreate draft) {
        Essay essay = service.createEssay(studentId, draft);
        return ResponseEntity.created(URI.create(PREFIX + "/essays/" + essay.id())).body(essay);
    }

    @GetMapping("/essays/{essayId}")
    Essay essay(@RequestAttribute(USER_ID_ATTRIBUTE) UUID studentId, @PathVariable UUID essayId) {
        return service.essay(studentId, essayId);
    }

    @PostMapping("/essays/{essayId}/correction-requests")
    ResponseEntity<Correction> requestCorrection(@RequestAttribute(USER_ID_ATTRIBUTE) UUID studentId,
            @PathVariable UUID essayId) {
        Correction correction = service.requestCorrection(studentId, essayId);
        return ResponseEntity.accepted().location(URI.create(PREFIX + "/correction-requests/" + correction.id()))
                .body(correction);
    }

    @GetMapping("/correction-requests/{requestId}")
    Correction correction(@RequestAttribute(USER_ID_ATTRIBUTE) UUID studentId, @PathVariable UUID requestId) {
        return service.correction(studentId, requestId);
    }

    @GetMapping("/feedbacks/{requestId}")
    Feedback feedback(@RequestAttribute(USER_ID_ATTRIBUTE) UUID studentId, @PathVariable UUID requestId) {
        return service.feedback(studentId, requestId);
    }

    @GetMapping("/plan")
    Plan plan(@RequestAttribute(USER_ID_ATTRIBUTE) UUID studentId) {
        return service.plan(studentId);
    }

    @PatchMapping("/tasks/{taskId}")
    ResponseEntity<Void> completeTask(@RequestAttribute(USER_ID_ATTRIBUTE) UUID studentId, @PathVariable String taskId,
            @RequestBody TaskUpdate update) {
        if (!"DONE".equals(update.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O status aceito é DONE.");
        }
        service.completeTask(studentId, taskId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/quota")
    Quota quota(@RequestAttribute(USER_ID_ATTRIBUTE) UUID studentId) {
        return service.quota(studentId);
    }
}
