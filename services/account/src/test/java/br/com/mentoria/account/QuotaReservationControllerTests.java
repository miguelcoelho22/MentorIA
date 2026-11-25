package br.com.mentoria.account;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.util.UUID;

import br.com.mentoria.account.QuotaReservationService.Reservation;
import br.com.mentoria.account.QuotaReservationService.ReservationResult;
import br.com.mentoria.account.QuotaReservationService.Status;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(QuotaReservationController.class)
class QuotaReservationControllerTests {

    private static final UUID REQUEST_ID = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID STUDENT_ID = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private static final String PATH = "/api/v1/quota-reservations/" + REQUEST_ID;
    private static final String CONFIRMATION_PATH = PATH + "/confirmation";
    private static final String REQUEST_JSON = """
            {"studentId":"%s"}
            """.formatted(STUDENT_ID);
    private static final String RESERVATION_JSON = """
            {"requestId":"%s","studentId":"%s","status":"RESERVED","period":"2026-10"}
            """.formatted(REQUEST_ID, STUDENT_ID);
    private static final Reservation RESERVATION = new Reservation(REQUEST_ID, STUDENT_ID, Status.RESERVED, "2026-10");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private QuotaReservationService service;

    @Test
    void createsReservationWithLocationAndJson() throws Exception {
        when(service.reserve(REQUEST_ID, STUDENT_ID)).thenReturn(new ReservationResult(RESERVATION, true));

        reserve().andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, PATH))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json(RESERVATION_JSON, JsonCompareMode.STRICT));
    }

    @Test
    void returnsOkWhenTheSameReservationIsRetried() throws Exception {
        when(service.reserve(REQUEST_ID, STUDENT_ID)).thenReturn(
                new ReservationResult(RESERVATION, true), new ReservationResult(RESERVATION, false));

        reserve().andExpect(status().isCreated());
        reserve().andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                .andExpect(content().json(RESERVATION_JSON, JsonCompareMode.STRICT));
    }

    @Test
    void returnsConflictWhenRequestIdBelongsToAnotherStudent() throws Exception {
        UUID otherStudent = UUID.fromString("00000000-0000-4000-8000-000000000003");
        String type = "reservation-student-mismatch";
        when(service.reserve(REQUEST_ID, otherStudent)).thenThrow(problem(HttpStatus.CONFLICT, type));

        var response = mvc.perform(put(PATH).contentType(MediaType.APPLICATION_JSON)
                .content("{\"studentId\":\"%s\"}".formatted(otherStudent)));

        expectProblem(response, HttpStatus.CONFLICT, type, PATH);
    }

    @Test
    void returnsConflictWhenMonthlyQuotaIsExhausted() throws Exception {
        String type = "quota-exhausted";
        when(service.reserve(REQUEST_ID, STUDENT_ID)).thenThrow(problem(HttpStatus.CONFLICT, type));

        expectProblem(reserve(), HttpStatus.CONFLICT, type, PATH);
    }

    @Test
    void confirmsReservationWithNoContent() throws Exception {
        mvc.perform(put(CONFIRMATION_PATH)).andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(service).confirm(REQUEST_ID);
    }

    @Test
    void returnsNotFoundWhenConfirmingMissingReservation() throws Exception {
        String type = "reservation-not-found";
        doThrow(problem(HttpStatus.NOT_FOUND, type)).when(service).confirm(REQUEST_ID);

        expectProblem(mvc.perform(put(CONFIRMATION_PATH)), HttpStatus.NOT_FOUND, type, CONFIRMATION_PATH);
    }

    @Test
    void releasesReservationWithNoContent() throws Exception {
        mvc.perform(delete(PATH)).andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(service).release(REQUEST_ID);
    }

    @Test
    void returnsConflictWhenReleasingConsumedReservation() throws Exception {
        String type = "reservation-already-consumed";
        doThrow(problem(HttpStatus.CONFLICT, type)).when(service).release(REQUEST_ID);

        expectProblem(mvc.perform(delete(PATH)), HttpStatus.CONFLICT, type, PATH);
    }

    private ResultActions reserve() throws Exception {
        return mvc.perform(put(PATH).contentType(MediaType.APPLICATION_JSON).content(REQUEST_JSON));
    }

    private static ResponseStatusException problem(HttpStatus status, String type) {
        var error = new ResponseStatusException(status, "Teste de resposta do serviço de reservas");
        error.setType(URI.create("https://mentoria.app/errors/" + type));
        return error;
    }

    private static void expectProblem(ResultActions response, HttpStatus expected, String type, String path)
            throws Exception {
        response.andExpect(status().is(expected.value()))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(expected.value()))
                .andExpect(jsonPath("$.type").value("https://mentoria.app/errors/" + type))
                .andExpect(jsonPath("$.instance").value(path))
                .andExpect(jsonPath("$.title").value(expected.getReasonPhrase()))
                .andExpect(jsonPath("$.detail").value("Teste de resposta do serviço de reservas"));
    }
}
