package br.com.mentoria.account;

import java.net.URI;
import java.util.UUID;

import br.com.mentoria.account.QuotaReservationService.Reservation;
import br.com.mentoria.account.QuotaReservationService.ReservationResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping(QuotaReservationController.RESERVATIONS_PATH)
class QuotaReservationController {

    static final String RESERVATIONS_PATH = "/api/v1/quota-reservations";

    private final QuotaReservationService reservations;

    QuotaReservationController(QuotaReservationService reservations) {
        this.reservations = reservations;
    }

    @PutMapping("/{requestId}")
    ResponseEntity<Reservation> reserve(@PathVariable UUID requestId, @RequestBody ReserveRequest request) {
        if (request.studentId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O campo studentId é obrigatório.");
        }
        ReservationResult result = reservations.reserve(requestId, request.studentId());
        if (result.created()) {
            return ResponseEntity.created(URI.create(RESERVATIONS_PATH + "/" + requestId)).body(result.reservation());
        }
        return ResponseEntity.ok(result.reservation());
    }

    @PutMapping("/{requestId}/confirmation")
    ResponseEntity<Void> confirm(@PathVariable UUID requestId) {
        reservations.confirm(requestId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{requestId}")
    ResponseEntity<Void> release(@PathVariable UUID requestId) {
        reservations.release(requestId);
        return ResponseEntity.noContent().build();
    }

    record ReserveRequest(UUID studentId) { }
}
