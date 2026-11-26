package br.com.mentoria.teacher;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

@RestControllerAdvice
class DownstreamErrors {

    @ExceptionHandler(RestClientResponseException.class)
    ResponseEntity<byte[]> downstreamResponse(RestClientResponseException error) {
        var response = ResponseEntity.status(error.getStatusCode());
        HttpHeaders headers = error.getResponseHeaders();
        if (headers != null) {
            for (String name : new String[]{HttpHeaders.CONTENT_TYPE, HttpHeaders.RETRY_AFTER, HttpHeaders.WWW_AUTHENTICATE}) {
                if (headers.containsHeader(name)) {
                    response.header(name, headers.getValuesAsList(name).toArray(String[]::new));
                }
            }
        }
        return response.body(error.getResponseBodyAsByteArray());
    }

    @ExceptionHandler(ResourceAccessException.class)
    ProblemDetail downstreamUnavailable() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Serviço interno indisponível.");
    }
}
