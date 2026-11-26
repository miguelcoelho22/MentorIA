package br.com.mentoria.student;

import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.web.client.RestClient;

@SpringBootTest
@AutoConfigureMockMvc
class StudentApiTests {

    private static final String API = "/api/v1/student";
    private static final String ACCOUNT = "http://account:8080";
    private static final String ASSESSMENT = "http://assessment:8080";
    private static final String FEEDBACK = "http://feedback:8080";
    private static final String STUDY_PLAN = "http://study-plan:8080";
    private static final String USER = "X-User-Id";
    private static final String TRACE = "X-Correlation-Id";
    private static final String STUDENT = "4155387b-4518-4657-bc51-d224aa1a69cb";
    private static final String OTHER = "e96b75e0-42ea-4f46-84db-5fbfb7ff4fe7";
    private static final String ESSAY = "9085f1fe-cac4-4813-bf06-203a68270d82";
    private static final String REQUEST = "b6f39305-960b-42bc-9d2c-bcb5ee94b02f";
    private static final String INTERNAL_STUDENT = "/api/v1/students/" + STUDENT;

    private static final String QUOTA = """
            {"studentId":"%s","period":"2026-11","limit":4,"reserved":1,"consumed":1,"available":2}
            """.formatted(STUDENT);
    private static final String PLAN = """
            {"studentId":"%s","updatedAt":"2026-11-26T12:00:00Z","tasks":[
              {"id":"done","title":"Finalizada","competency":1,"status":"DONE"},
              {"id":"pending","title":"Pendente","competency":5,"status":"PENDING"}]}
            """.formatted(STUDENT);
    private static final String PROGRESS = """
            {"studentId":"%s","lastScore":640,"trend":"up","tasksPending":1,"tasksDone":1}
            """.formatted(STUDENT);

    @Autowired MockMvc mvc;
    @Autowired StubHttp http;

    @AfterEach
    void verifyRequests() {
        try {
            http.servers.values().forEach(MockRestServiceServer::verify);
        } finally {
            http.servers.values().forEach(MockRestServiceServer::reset);
        }
    }

    @Test
    void requiresValidIdentityEvenOnThemes() throws Exception {
        mvc.perform(get(API + "/themes")).andExpect(status().isUnauthorized());
        mvc.perform(get(API + "/themes").header(USER, "not-a-uuid")).andExpect(status().isUnauthorized());
        mvc.perform(get(API + "/themes").header(USER, "1-2-3-4-5")).andExpect(status().isUnauthorized());
    }

    @Test
    void combinesHomeDataAndForwardsCorrelationToEveryService() throws Exception {
        http.expect(ACCOUNT, INTERNAL_STUDENT + "/quota").andExpect(header(TRACE, "trace-home"))
                .andRespond(withSuccess(QUOTA, APPLICATION_JSON));
        http.expect(STUDY_PLAN, INTERNAL_STUDENT + "/plan").andExpect(header(TRACE, "trace-home"))
                .andRespond(withSuccess(PLAN, APPLICATION_JSON));
        http.expect(STUDY_PLAN, INTERNAL_STUDENT + "/progress").andExpect(header(TRACE, "trace-home"))
                .andRespond(withSuccess(PROGRESS, APPLICATION_JSON));

        mvc.perform(get(API + "/home").header(USER, STUDENT).header(TRACE, "trace-home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quota.available").value(2))
                .andExpect(jsonPath("$.quota.renewsOn").value("2026-12-01T12:00:00Z"))
                .andExpect(jsonPath("$.nextTasks[0].id").value("pending"))
                .andExpect(jsonPath("$.nextTasks[0].done").value(false))
                .andExpect(jsonPath("$.nextTasks[1].done").value(true))
                .andExpect(jsonPath("$.progress.lastScore").value(640))
                .andExpect(jsonPath("$.progress.trend").value("up"))
                .andExpect(jsonPath("$._links.newEssay.href").value(API + "/essays"));
    }

    @Test
    void returnsHomeWithNullQuotaWhenAccountCannotBeReached() throws Exception {
        http.expect(ACCOUNT, INTERNAL_STUDENT + "/quota").andRespond(request -> {
            throw new IOException("Connection refused");
        });
        http.expect(STUDY_PLAN, INTERNAL_STUDENT + "/plan").andRespond(withSuccess(PLAN, APPLICATION_JSON));
        http.expect(STUDY_PLAN, INTERNAL_STUDENT + "/progress").andRespond(withSuccess(PROGRESS, APPLICATION_JSON));

        mvc.perform(get(API + "/home").header(USER, STUDENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quota").isEmpty())
                .andExpect(jsonPath("$.quota").hasJsonPath());
    }

    @Test
    void listsThemesAndScopesEssayListToTheCurrentStudent() throws Exception {
        http.expect(ASSESSMENT, "/api/v1/themes").andExpect(method(GET))
                .andRespond(withSuccess("""
                        [{"id":"t07","title":"Mobilidade","description":"Discuta o tema."}]
                        """, APPLICATION_JSON));
        http.expect(ASSESSMENT, "/api/v1/essays?studentId=" + STUDENT).andExpect(method(GET))
                .andRespond(withSuccess("[" + essay(STUDENT) + "]", APPLICATION_JSON));

        mvc.perform(get(API + "/themes").header(USER, STUDENT))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].description").value("Discuta o tema."));
        mvc.perform(get(API + "/essays").queryParam("studentId", OTHER).header(USER, STUDENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(ESSAY))
                .andExpect(jsonPath("$[0].studentId").doesNotHaveJsonPath());
    }

    @Test
    void createsEssayWithIdentityAndPublicLocation() throws Exception {
        String draft = """
                {"themeId":"t07","text":"Minha redação"}
                """;
        http.expect(ASSESSMENT, "/api/v1/essays").andExpect(method(POST)).andExpect(header(USER, STUDENT))
                .andExpect(content().json(draft))
                .andRespond(withStatus(HttpStatusCode.valueOf(201)).contentType(APPLICATION_JSON).body(essay(STUDENT)));

        mvc.perform(post(API + "/essays").header(USER, STUDENT).contentType(APPLICATION_JSON).content(draft))
                .andExpect(status().isCreated())
                .andExpect(MockMvcResultMatchers.header().string("Location", API + "/essays/" + ESSAY))
                .andExpect(jsonPath("$.id").value(ESSAY))
                .andExpect(jsonPath("$.text").doesNotHaveJsonPath());
    }

    @Test
    void readsOwnedEssayDetailWithText() throws Exception {
        http.expect(ASSESSMENT, "/api/v1/essays/" + ESSAY).andRespond(withSuccess("""
                {"id":"%s","studentId":"%s","themeId":"t07","text":"Texto integral","submittedAt":"2026-11-26T12:00:00Z"}
                """.formatted(ESSAY, STUDENT), APPLICATION_JSON));

        mvc.perform(get(API + "/essays/" + ESSAY).header(USER, STUDENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Texto integral"))
                .andExpect(jsonPath("$.studentId").doesNotHaveJsonPath());
    }

    @Test
    void requestsCorrectionWithoutBodyAndRewritesLocationAndLinks() throws Exception {
        http.expect(ASSESSMENT, "/api/v1/essays/" + ESSAY + "/correction-requests")
                .andExpect(method(POST)).andExpect(header(USER, STUDENT)).andExpect(content().bytes(new byte[0]))
                .andRespond(withStatus(HttpStatusCode.valueOf(202)).contentType(APPLICATION_JSON)
                        .body(correction("PROCESSING", """
                                {"self":{"href":"/api/v1/correction-requests/%s"},"essay":{"href":"/api/v1/essays/%s"}}
                                """.formatted(REQUEST, ESSAY))));

        mvc.perform(post(API + "/essays/" + ESSAY + "/correction-requests").header(USER, STUDENT))
                .andExpect(status().isAccepted())
                .andExpect(MockMvcResultMatchers.header().string("Location", API + "/correction-requests/" + REQUEST))
                .andExpect(jsonPath("$._links.self.href").value(API + "/correction-requests/" + REQUEST))
                .andExpect(jsonPath("$._links.essay.href").value(API + "/essays/" + ESSAY));
    }

    @Test
    void rewritesFeedbackPlanQuotaAndRetryLinksAfterOwnershipCheck() throws Exception {
        http.expect(ASSESSMENT, "/api/v1/correction-requests/" + REQUEST)
                .andRespond(withSuccess(correction("COMPLETED", """
                        {"feedback":{"href":"/api/v1/feedbacks/%s"},"study-plan":{"href":"%s/plan"},
                         "quota":{"href":"%s/quota"},"retry":{"href":"/api/v1/essays/%s/correction-requests"}}
                        """.formatted(REQUEST, INTERNAL_STUDENT, INTERNAL_STUDENT, ESSAY)), APPLICATION_JSON));
        http.expect(ASSESSMENT, "/api/v1/essays/" + ESSAY).andRespond(withSuccess(essay(STUDENT), APPLICATION_JSON));

        mvc.perform(get(API + "/correction-requests/" + REQUEST).header(USER, STUDENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links.feedback.href").value(API + "/feedbacks/" + REQUEST))
                .andExpect(jsonPath("$._links['study-plan'].href").value(API + "/plan"))
                .andExpect(jsonPath("$._links.quota.href").value(API + "/quota"))
                .andExpect(jsonPath("$._links.retry.href").value(API + "/essays/" + ESSAY + "/correction-requests"));
    }

    @Test
    void hidesEssaysCorrectionsFeedbackAndTasksOwnedBySomeoneElse() throws Exception {
        http.expect(ASSESSMENT, "/api/v1/essays/" + ESSAY).andRespond(withSuccess(essay(OTHER), APPLICATION_JSON));
        http.expect(ASSESSMENT, "/api/v1/correction-requests/" + REQUEST)
                .andRespond(withSuccess(correction("COMPLETED", "{}"), APPLICATION_JSON));
        http.expect(ASSESSMENT, "/api/v1/essays/" + ESSAY).andRespond(withSuccess(essay(OTHER), APPLICATION_JSON));
        http.expect(FEEDBACK, "/api/v1/feedbacks/" + REQUEST).andRespond(withSuccess(feedback(OTHER), APPLICATION_JSON));
        http.expect(STUDY_PLAN, INTERNAL_STUDENT + "/plan").andRespond(withSuccess(PLAN, APPLICATION_JSON));

        mvc.perform(get(API + "/essays/" + ESSAY).header(USER, STUDENT)).andExpect(status().isNotFound());
        mvc.perform(get(API + "/correction-requests/" + REQUEST).header(USER, STUDENT)).andExpect(status().isNotFound());
        mvc.perform(get(API + "/feedbacks/" + REQUEST).header(USER, STUDENT)).andExpect(status().isNotFound());
        mvc.perform(patch(API + "/tasks/other").header(USER, STUDENT).contentType(APPLICATION_JSON)
                .content("{\"status\":\"DONE\"}")).andExpect(status().isNotFound());
    }

    @Test
    void returnsOwnedFeedbackPlanAndQuotaAndCompletesTaskWithNoContent() throws Exception {
        http.expect(FEEDBACK, "/api/v1/feedbacks/" + REQUEST).andRespond(withSuccess(feedback(STUDENT), APPLICATION_JSON));
        http.expect(STUDY_PLAN, INTERNAL_STUDENT + "/plan").andRespond(withSuccess(PLAN, APPLICATION_JSON));
        http.expect(STUDY_PLAN, INTERNAL_STUDENT + "/plan").andRespond(withSuccess(PLAN, APPLICATION_JSON));
        http.expect(STUDY_PLAN, "/api/v1/tasks/pending").andExpect(method(PATCH))
                .andExpect(content().json("{\"status\":\"DONE\"}"))
                .andRespond(withSuccess("""
                        {"id":"pending","title":"Pendente","competency":5,"status":"DONE"}
                        """, APPLICATION_JSON));
        http.expect(ACCOUNT, INTERNAL_STUDENT + "/quota").andRespond(withSuccess(QUOTA, APPLICATION_JSON));

        mvc.perform(get(API + "/feedbacks/" + REQUEST).header(USER, STUDENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalScore").value(640))
                .andExpect(jsonPath("$.competencies[0].comment").value("Comentário"))
                .andExpect(jsonPath("$.status").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.studentId").doesNotHaveJsonPath());
        mvc.perform(get(API + "/plan").header(USER, STUDENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks[1].status").value("PENDING"))
                .andExpect(jsonPath("$.studentId").doesNotHaveJsonPath());
        mvc.perform(patch(API + "/tasks/pending").header(USER, STUDENT).contentType(APPLICATION_JSON)
                .content("{\"status\":\"DONE\"}")).andExpect(status().isNoContent())
                .andExpect(MockMvcResultMatchers.content().string(""));
        mvc.perform(get(API + "/quota").header(USER, STUDENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("2026-11"))
                .andExpect(jsonPath("$.studentId").doesNotHaveJsonPath());
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 409, 503})
    void preservesDownstreamErrorStatusMediaTypeAndBody(int code) throws Exception {
        String problem = "{\"status\":" + code + ",\"detail\":\"Falha na correção\",\"requestId\":\"" + REQUEST + "\"}";
        http.expect(ASSESSMENT, "/api/v1/essays/" + ESSAY + "/correction-requests")
                .andRespond(withStatus(HttpStatusCode.valueOf(code)).contentType(APPLICATION_PROBLEM_JSON).body(problem));

        mvc.perform(post(API + "/essays/" + ESSAY + "/correction-requests").header(USER, STUDENT))
                .andExpect(status().is(code))
                .andExpect(MockMvcResultMatchers.content().contentType(APPLICATION_PROBLEM_JSON))
                .andExpect(MockMvcResultMatchers.content().bytes(problem.getBytes(StandardCharsets.UTF_8)));
    }

    private static String essay(String studentId) {
        return """
                {"id":"%s","studentId":"%s","themeId":"t07","submittedAt":"2026-11-26T12:00:00Z"}
                """.formatted(ESSAY, studentId);
    }

    private static String correction(String status, String links) {
        return """
                {"id":"%s","essayId":"%s","status":"%s","requestedAt":"2026-11-26T12:00:00Z","_links":%s}
                """.formatted(REQUEST, ESSAY, status, links);
    }

    private static String feedback(String studentId) {
        return """
                {"requestId":"%s","studentId":"%s","essayId":"%s","status":"PUBLISHED","totalScore":640,
                 "publishedAt":"2026-11-26T12:00:00Z","competencies":[{"id":1,"score":120,"comment":"Comentário"}]}
                """.formatted(REQUEST, studentId, ESSAY);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class HttpTestConfiguration {
        @Bean
        @Primary
        StubHttp stubHttp() {
            return new StubHttp();
        }
    }

    static class StubHttp extends DownstreamHttp {
        private final Map<String, MockRestServiceServer> servers = new HashMap<>();

        @Override
        RestClient.Builder builder(String baseUrl) {
            RestClient.Builder builder = super.builder(baseUrl);
            servers.put(baseUrl, MockRestServiceServer.bindTo(builder).build());
            return builder;
        }

        ResponseActions expect(String baseUrl, String path) {
            return servers.get(baseUrl).expect(requestTo(baseUrl + path));
        }
    }
}
