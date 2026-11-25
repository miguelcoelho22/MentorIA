package br.com.mentoria.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.UUID;

import br.com.mentoria.account.QuotaReservationService.Reservation;
import br.com.mentoria.account.QuotaReservationService.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class QuotaReservationServiceTests {

    private static final UUID REQUEST_ID = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID STUDENT_ID = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private static final UUID OTHER_STUDENT_ID = UUID.fromString("00000000-0000-4000-8000-000000000003");

    @Mock
    private JdbcTemplate jdbc;

    @InjectMocks
    private QuotaReservationService service;

    @BeforeEach
    void stubRequestLock() {
        doReturn(null).when(jdbc).query(contains("pg_advisory_xact_lock"),
                ArgumentMatchers.<ResultSetExtractor<Void>>any(), eq("account:quota-reservation:" + REQUEST_ID));
    }

    @Test
    void createsFourthActiveReservation() {
        stubActiveCount(3);

        var result = service.reserve(REQUEST_ID, STUDENT_ID);

        assertThat(result.created()).isTrue();
        assertThat(result.reservation().requestId()).isEqualTo(REQUEST_ID);
        assertThat(result.reservation().studentId()).isEqualTo(STUDENT_ID);
        assertThat(result.reservation().status()).isEqualTo(Status.RESERVED);
        assertThat(result.reservation().period()).matches("\\d{4}-\\d{2}");
        verify(jdbc).update(contains("INSERT INTO quota_reservations"), eq(REQUEST_ID), eq(STUDENT_ID),
                any(LocalDate.class));
    }

    @Test
    void repeatsSameRequestWithoutCreatingAnotherReservation() {
        var existing = stubReservation(Status.RESERVED);

        var result = service.reserve(REQUEST_ID, STUDENT_ID);

        assertThat(result.created()).isFalse();
        assertThat(result.reservation()).isEqualTo(existing);
        verifyNoReservationInsert();
    }

    @Test
    void rejectsSameRequestForAnotherStudent() {
        stubReservation(Status.RESERVED);

        var error = assertThrows(ResponseStatusException.class, () -> service.reserve(REQUEST_ID, OTHER_STUDENT_ID));

        assertProblem(error, HttpStatus.CONFLICT, "reservation-student-mismatch");
        verifyNoReservationInsert();
    }

    @Test
    void rejectsFifthActiveReservation() {
        stubActiveCount(4);

        var error = assertThrows(ResponseStatusException.class, () -> service.reserve(REQUEST_ID, STUDENT_ID));

        assertProblem(error, HttpStatus.CONFLICT, "quota-exhausted");
        verifyNoReservationInsert();
    }

    @Test
    void confirmsActiveReservation() {
        stubReservation(Status.RESERVED);

        service.confirm(REQUEST_ID);

        verify(jdbc).update(contains("UPDATE quota_reservations"), eq("CONSUMED"), eq(REQUEST_ID));
    }

    @Test
    void confirmsAlreadyConsumedReservationWithoutUpdatingIt() {
        stubReservation(Status.CONSUMED);

        service.confirm(REQUEST_ID);

        verifyNoStatusUpdate();
    }

    @Test
    void rejectsConfirmationWhenReservationIsMissing() {
        stubReservationLookup(null);

        var error = assertThrows(ResponseStatusException.class, () -> service.confirm(REQUEST_ID));

        assertProblem(error, HttpStatus.NOT_FOUND, "reservation-not-found");
        verifyNoStatusUpdate();
    }

    @Test
    void releasesActiveReservation() {
        stubReservation(Status.RESERVED);

        service.release(REQUEST_ID);

        verify(jdbc).update(contains("UPDATE quota_reservations"), eq("RELEASED"), eq(REQUEST_ID));
    }

    @Test
    void releasesMissingReservationWithoutWriting() {
        stubReservationLookup(null);

        service.release(REQUEST_ID);

        verifyNoStatusUpdate();
    }

    @Test
    void rejectsReleaseWhenReservationWasConsumed() {
        stubReservation(Status.CONSUMED);

        var error = assertThrows(ResponseStatusException.class, () -> service.release(REQUEST_ID));

        assertProblem(error, HttpStatus.CONFLICT, "reservation-already-consumed");
        verifyNoStatusUpdate();
    }

    private void stubActiveCount(int count) {
        stubReservationLookup(null);
        doReturn(STUDENT_ID).when(jdbc).queryForObject(contains("FOR UPDATE"), eq(UUID.class), eq(STUDENT_ID),
                any(LocalDate.class));
        doReturn(count).when(jdbc).queryForObject(contains("SELECT count(*)"), eq(Integer.class), eq(STUDENT_ID),
                any(LocalDate.class));
    }

    private Reservation stubReservation(Status status) {
        var reservation = new Reservation(REQUEST_ID, STUDENT_ID, status, "2026-10");
        stubReservationLookup(reservation);
        return reservation;
    }

    private void stubReservationLookup(Reservation reservation) {
        doReturn(reservation).when(jdbc).query(contains("SELECT student_id, status, period"),
                ArgumentMatchers.<ResultSetExtractor<Reservation>>any(), eq(REQUEST_ID));
    }

    private void verifyNoReservationInsert() {
        verify(jdbc, never()).update(contains("INSERT INTO quota_reservations"), eq(REQUEST_ID), any(UUID.class),
                any(LocalDate.class));
    }

    private void verifyNoStatusUpdate() {
        verify(jdbc, never()).update(contains("UPDATE quota_reservations"), anyString(), eq(REQUEST_ID));
    }

    private static void assertProblem(ResponseStatusException error, HttpStatus status, String type) {
        assertThat(error.getStatusCode()).isEqualTo(status);
        assertThat(error.getBody().getStatus()).isEqualTo(status.value());
        assertThat(error.getBody().getType()).hasToString("https://mentoria.app/errors/" + type);
        assertThat(error.getBody().getTitle()).isNotBlank();
        assertThat(error.getBody().getDetail()).isNotBlank();
    }
}
