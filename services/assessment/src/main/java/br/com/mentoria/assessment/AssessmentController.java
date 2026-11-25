package br.com.mentoria.assessment;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import br.com.mentoria.assessment.AssessmentData.CorrectionRequest;
import br.com.mentoria.assessment.AssessmentData.Essay;
import br.com.mentoria.assessment.AssessmentData.EssayCreate;
import br.com.mentoria.assessment.AssessmentData.Theme;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
class AssessmentController {

    private final AssessmentService service;

    AssessmentController(AssessmentService service) {
        this.service = service;
    }

    @GetMapping("/themes")
    List<Theme> themes() {
        return service.themes();
    }

    @PostMapping("/essays")
    ResponseEntity<Essay> createEssay(@RequestHeader("X-User-Id") UUID studentId, @Valid @RequestBody EssayCreate draft) {
        Essay essay = service.createEssay(studentId, draft);
        return ResponseEntity.created(URI.create("/api/v1/essays/" + essay.id())).body(essay);
    }

    @GetMapping("/essays")
    List<Essay> essays(@RequestParam(required = false) UUID studentId, @RequestParam(required = false) String classId) {
        return service.listEssays(studentId, classId);
    }

    @GetMapping("/essays/{essayId}")
    Essay essay(@PathVariable UUID essayId) {
        return service.essay(essayId);
    }

    @PostMapping("/essays/{essayId}/correction-requests")
    ResponseEntity<CorrectionRequest> requestCorrection(@RequestHeader("X-User-Id") UUID studentId, @PathVariable UUID essayId) {
        var request = service.requestCorrection(essayId, studentId);
        return ResponseEntity.accepted().location(URI.create(request.links().get("self").href())).body(request);
    }

    @GetMapping("/correction-requests/{requestId}")
    CorrectionRequest correction(@PathVariable UUID requestId) {
        return service.correction(requestId);
    }
}
