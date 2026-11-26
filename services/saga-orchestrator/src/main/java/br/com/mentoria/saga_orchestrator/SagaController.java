package br.com.mentoria.saga_orchestrator;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import br.com.mentoria.saga_orchestrator.SagaStore.Saga;
import br.com.mentoria.saga_orchestrator.SagaStore.Step;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Consulta das SAGAs e do histórico de passos, para acompanhar a execução e as compensações na demonstração. */
@RestController
@RequestMapping("/api/v1/sagas")
class SagaController {

    record SagaView(long id, UUID requestId, UUID essayId, UUID studentId, String status, String failureReason,
            String failureDetail, OffsetDateTime createdAt, OffsetDateTime updatedAt, List<Step> steps) { }

    private final SagaStore store;

    SagaController(SagaStore store) {
        this.store = store;
    }

    @GetMapping
    List<SagaView> recent() {
        return store.recent(20).stream().map(saga -> view(saga, List.of())).toList();
    }

    @GetMapping("/{requestId}")
    SagaView saga(@PathVariable UUID requestId) {
        Saga saga = store.find(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SAGA não encontrada."));
        return view(saga, store.steps(saga.id()));
    }

    private static SagaView view(Saga saga, List<Step> steps) {
        return new SagaView(saga.id(), saga.requestId(), saga.essayId(), saga.studentId(), saga.status().name(),
                saga.failureReason(), saga.failureDetail(), saga.createdAt(), saga.updatedAt(), steps);
    }
}
