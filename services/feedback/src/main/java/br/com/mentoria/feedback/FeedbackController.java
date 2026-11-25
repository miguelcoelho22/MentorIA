package br.com.mentoria.feedback;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import br.com.mentoria.feedback.FeedbackData.DraftRequest;
import br.com.mentoria.feedback.FeedbackData.Feedback;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(FeedbackController.PATH)
class FeedbackController {

    static final String PATH = "/api/v1/feedbacks";
    private final FeedbackService service;

    FeedbackController(FeedbackService service) {
        this.service = service;
    }

    @PutMapping("/{requestId}")
    ResponseEntity<Feedback> createDraft(@PathVariable UUID requestId, @Valid @RequestBody DraftRequest request) {
        var result = service.createDraft(requestId, request);
        if (result.created()) {
            return ResponseEntity.created(URI.create(PATH + "/" + requestId)).body(result.feedback());
        }
        return ResponseEntity.ok(result.feedback());
    }

    @GetMapping("/{requestId}")
    Feedback getPublished(@PathVariable UUID requestId) {
        return service.getPublished(requestId);
    }

    @DeleteMapping("/{requestId}")
    ResponseEntity<Void> cancel(@PathVariable UUID requestId) {
        service.cancel(requestId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{requestId}/publication")
    ResponseEntity<Void> publish(@PathVariable UUID requestId) {
        service.publish(requestId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    List<Feedback> listPublished(@RequestParam UUID studentId) {
        return service.listPublished(studentId);
    }
}
