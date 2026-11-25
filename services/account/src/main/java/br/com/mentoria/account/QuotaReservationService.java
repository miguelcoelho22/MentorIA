package br.com.mentoria.account;

import java.net.URI;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class QuotaReservationService {

    private static final int MONTHLY_LIMIT = 4;

    private final JdbcTemplate jdbc;

    QuotaReservationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public ReservationResult reserve(UUID requestId, UUID studentId) {
        lockRequest(requestId);
        Reservation existing = findReservation(requestId);
        if (existing != null) {
            if (!existing.studentId().equals(studentId)) {
                throw problem(HttpStatus.CONFLICT, "reservation-student-mismatch", "Reserva vinculada a outro aluno",
                        "O identificador da solicitação já está vinculado a outro aluno.");
            }
            return new ReservationResult(existing, false);
        }

        YearMonth month = YearMonth.now(ZoneOffset.UTC);
        LocalDate period = month.atDay(1);
        // Serialize reservations for the same student and month before counting the quota.
        jdbc.update("""
                INSERT INTO quotas (student_id, period) VALUES (?, ?)
                ON CONFLICT (student_id, period) DO NOTHING
                """, studentId, period);
        jdbc.queryForObject("""
                SELECT student_id FROM quotas WHERE student_id = ? AND period = ? FOR UPDATE
                """, UUID.class, studentId, period);
        int used = jdbc.queryForObject("""
                SELECT count(*) FROM quota_reservations
                WHERE student_id = ? AND period = ? AND status IN ('RESERVED', 'CONSUMED')
                """, Integer.class, studentId, period);
        if (used >= MONTHLY_LIMIT) {
            throw problem(HttpStatus.CONFLICT, "quota-exhausted", "Cota de correções esgotada",
                    "As quatro correções de " + month + " já estão reservadas ou consumidas.");
        }

        jdbc.update("""
                INSERT INTO quota_reservations (request_id, student_id, period, status)
                VALUES (?, ?, ?, 'RESERVED')
                """, requestId, studentId, period);
        return new ReservationResult(new Reservation(requestId, studentId, Status.RESERVED, month.toString()), true);
    }

    @Transactional
    public void confirm(UUID requestId) {
        lockRequest(requestId);
        Reservation reservation = findReservation(requestId);
        if (reservation == null || reservation.status() == Status.RELEASED) {
            throw problem(HttpStatus.NOT_FOUND, "reservation-not-found", "Reserva não encontrada",
                    "Não há reserva ativa para a solicitação informada.");
        }
        if (reservation.status() == Status.RESERVED) {
            updateStatus(requestId, Status.CONSUMED);
        }
    }

    @Transactional
    public void release(UUID requestId) {
        lockRequest(requestId);
        Reservation reservation = findReservation(requestId);
        if (reservation == null || reservation.status() == Status.RELEASED) {
            return;
        }
        if (reservation.status() == Status.CONSUMED) {
            throw problem(HttpStatus.CONFLICT, "reservation-already-consumed", "Reserva já consumida",
                    "Uma reserva consumida não pode ser liberada.");
        }
        updateStatus(requestId, Status.RELEASED);
    }

    private void lockRequest(UUID requestId) {
        // A request can be repeated or compensated before it has a database row to lock.
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> null,
                "account:quota-reservation:" + requestId);
    }

    private Reservation findReservation(UUID requestId) {
        return jdbc.query("""
                SELECT student_id, status, period FROM quota_reservations WHERE request_id = ?
                """, rs -> rs.next() ? new Reservation(
                        requestId, rs.getObject("student_id", UUID.class), Status.valueOf(rs.getString("status")),
                        YearMonth.from(rs.getObject("period", LocalDate.class)).toString()) : null, requestId);
    }

    private void updateStatus(UUID requestId, Status status) {
        jdbc.update("UPDATE quota_reservations SET status = ? WHERE request_id = ?", status.name(), requestId);
    }

    private static ResponseStatusException problem(HttpStatus status, String type, String title, String detail) {
        ResponseStatusException exception = new ResponseStatusException(status, detail);
        exception.setType(URI.create("https://mentoria.app/errors/" + type));
        exception.setTitle(title);
        return exception;
    }

    enum Status { RESERVED, CONSUMED, RELEASED }

    record Reservation(UUID requestId, UUID studentId, Status status, String period) { }

    record ReservationResult(Reservation reservation, boolean created) { }
}
