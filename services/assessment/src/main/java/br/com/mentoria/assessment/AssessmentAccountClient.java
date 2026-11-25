package br.com.mentoria.assessment;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Component
class AssessmentAccountClient {

    private final RestClient http;

    AssessmentAccountClient(@Value("${assessment.account.base-url}") String baseUrl,
            @Value("${assessment.account.timeout}") Duration timeout) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        http = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    List<UUID> studentsOf(String classId) {
        try {
            Student[] students = http.get().uri("/api/v1/classes/{classId}/students", classId).retrieve().body(Student[].class);
            if (students == null || Arrays.stream(students).anyMatch(student -> student == null || student.id() == null)) {
                throw new RestClientException("Resposta de turma inválida.");
            }
            return Arrays.stream(students).map(Student::id).distinct().toList();
        } catch (HttpClientErrorException.NotFound e) {
            return List.of();
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Não foi possível consultar a turma no Account.");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Student(UUID id) { }
}
