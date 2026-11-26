package br.com.mentoria.teacher;

import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.web.client.RestClient;

@SpringBootTest(properties = "FEEDBACK_SERVICE_URL=http://feedback-test:9080")
@AutoConfigureMockMvc
class TeacherApiTests {

    private static final String API = "/api/v1/teacher";
    private static final String ACCOUNT = "http://account:8080";
    private static final String ASSESSMENT = "http://assessment:8080";
    private static final String FEEDBACK = "http://feedback-test:9080";
    private static final String STUDY_PLAN = "http://study-plan:8080";
    private static final String USER = "X-User-Id";
    private static final String TRACE = "X-Correlation-Id";
    private static final String TEACHER = "65f9ed05-c970-4082-8ff0-8de636eee187";
    private static final String ANA = "4155387b-4518-4657-bc51-d224aa1a69cb";
    private static final String BRUNO = "e96b75e0-42ea-4f46-84db-5fbfb7ff4fe7";
    private static final String CARLA = "3ca7aeb2-7db5-4b09-8d09-92fe1a5d8abe";
    private static final String DIEGO = "dc455151-5c56-4987-a55e-c044352b033d";
    private static final String EVA = "ed529169-dd35-4a20-9132-84f3d79933f5";
    private static final String ESSAY = "9085f1fe-cac4-4813-bf06-203a68270d82";
    private static final String UNGRADED_ESSAY = "004aecc7-310a-4a63-aafd-4e927c784e2d";
    private static final String TEACHER_CLASSES = "/api/v1/classes?teacherId=" + TEACHER;
    private static final String ANA_ENDPOINT = "/api/v1/students/" + ANA;
    private static final String CLASSES = """
            [{"id":"3A","name":"Turma 3A","teacherId":"%s","studentCount":5}]
            """.formatted(TEACHER);

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
    void requiresValidTeacherIdentity() throws Exception {
        mvc.perform(get(API + "/classes")).andExpect(status().isUnauthorized());
        mvc.perform(get(API + "/classes").header(USER, "invalid")).andExpect(status().isUnauthorized());
    }

    @Test
    void listsOnlyClassesForTheAuthenticatedTeacher() throws Exception {
        http.expect(ACCOUNT, TEACHER_CLASSES).andExpect(method(GET)).andExpect(header(TRACE, "trace-classes"))
                .andRespond(withSuccess(CLASSES, APPLICATION_JSON));

        mvc.perform(get(API + "/classes").queryParam("teacherId", ANA).header(USER, TEACHER).header(TRACE, "trace-classes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("3A"))
                .andExpect(jsonPath("$[0].name").value("Turma 3A"))
                .andExpect(jsonPath("$[0].studentCount").value(5))
                .andExpect(jsonPath("$[0].teacherId").doesNotHaveJsonPath());
    }

    @Test
    void deniesOverviewForAnotherTeachersClassBeforeReadingItsStudents() throws Exception {
        http.expect(ACCOUNT, TEACHER_CLASSES).andRespond(withSuccess(CLASSES, APPLICATION_JSON));

        mvc.perform(get(API + "/classes/3B/overview").header(USER, TEACHER)).andExpect(status().isForbidden());
    }

    @Test
    void aggregatesProjectedStudentsAndAppliesEachSupportCriterion() throws Exception {
        http.expect(ACCOUNT, TEACHER_CLASSES).andRespond(withSuccess(CLASSES, APPLICATION_JSON));
        http.expect(ACCOUNT, "/api/v1/classes/3A/students").andRespond(withSuccess(array(
                student(ANA, "Ana", "3A"), student(BRUNO, "Bruno", "3A"), student(CARLA, "Carla", "3A"),
                student(DIEGO, "Diego", "3A"), student(EVA, "Eva", "3A")), APPLICATION_JSON));
        http.expect(STUDY_PLAN, "/api/v1/progress?studentIds=" + String.join(",", ANA, BRUNO, CARLA, DIEGO, EVA))
                .andRespond(withSuccess(array(progress(ANA, 590, "stable", 0, 100, 160),
                        progress(BRUNO, 710, "down", 0, 190, 100), progress(CARLA, 700, "up", 2, 150, 100),
                        progress(DIEGO, 600, "stable", 1, 160, 100)), APPLICATION_JSON));

        mvc.perform(get(API + "/classes/3A/overview").header(USER, TEACHER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classId").value("3A"))
                .andExpect(jsonPath("$.students").value(5))
                .andExpect(jsonPath("$.averageScore").value(650))
                .andExpect(jsonPath("$.weakestCompetency").value(2))
                .andExpect(jsonPath("$.needSupport.length()").value(3))
                .andExpect(jsonPath("$.needSupport[0].studentId").value(ANA))
                .andExpect(jsonPath("$.needSupport[1].name").value("Carla"))
                .andExpect(jsonPath("$.needSupport[1].pendingTasks").value(2))
                .andExpect(jsonPath("$.needSupport[2].trend").value("down"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void returnsAnEmptyOverviewForClassesWithoutStudentsOrProjections(boolean hasStudents) throws Exception {
        http.expect(ACCOUNT, TEACHER_CLASSES).andRespond(withSuccess(CLASSES, APPLICATION_JSON));
        String roster = hasStudents ? array(student(ANA, "Ana", "3A")) : "[]";
        http.expect(ACCOUNT, "/api/v1/classes/3A/students").andRespond(withSuccess(roster, APPLICATION_JSON));
        if (hasStudents) {
            http.expect(STUDY_PLAN, "/api/v1/progress?studentIds=" + ANA).andRespond(withSuccess("[]", APPLICATION_JSON));
        }

        mvc.perform(get(API + "/classes/3A/overview").header(USER, TEACHER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.students").value(hasStudents ? 1 : 0))
                .andExpect(jsonPath("$.averageScore").value(0))
                .andExpect(jsonPath("$.weakestCompetency").hasJsonPath())
                .andExpect(jsonPath("$.weakestCompetency").isEmpty())
                .andExpect(jsonPath("$.needSupport").isEmpty());
    }

    @Test
    void deniesDetailsOfStudentsFromAnotherClassOrWithoutAClass() throws Exception {
        http.expect(ACCOUNT, ANA_ENDPOINT).andRespond(withSuccess(student(ANA, "Ana", "3B"), APPLICATION_JSON));
        http.expect(ACCOUNT, TEACHER_CLASSES).andRespond(withSuccess(CLASSES, APPLICATION_JSON));
        http.expect(ACCOUNT, ANA_ENDPOINT).andRespond(withSuccess("""
                {"id":"%s","name":"Ana"}
                """.formatted(ANA), APPLICATION_JSON));

        mvc.perform(get(API + "/students/" + ANA).header(USER, TEACHER)).andExpect(status().isForbidden());
        mvc.perform(get(API + "/students/" + ANA).header(USER, TEACHER)).andExpect(status().isForbidden());
    }

    @Test
    void combinesStudentProgressWithThemeTitlesAndMostRecentPublishedFeedback() throws Exception {
        http.expect(ACCOUNT, ANA_ENDPOINT).andExpect(header(TRACE, "trace-details"))
                .andRespond(withSuccess(student(ANA, "Ana", "3A"), APPLICATION_JSON));
        http.expect(ACCOUNT, TEACHER_CLASSES).andExpect(header(TRACE, "trace-details"))
                .andRespond(withSuccess(CLASSES, APPLICATION_JSON));
        http.expect(STUDY_PLAN, ANA_ENDPOINT + "/progress").andExpect(header(TRACE, "trace-details"))
                .andRespond(withSuccess(progress(ANA, 720, "up", 1, 120, 160), APPLICATION_JSON));
        http.expect(ASSESSMENT, "/api/v1/essays?studentId=" + ANA).andExpect(header(TRACE, "trace-details"))
                .andRespond(withSuccess(array(essay(ESSAY, "t07"), essay(UNGRADED_ESSAY, "t08")), APPLICATION_JSON));
        http.expect(ASSESSMENT, "/api/v1/themes").andExpect(header(TRACE, "trace-details"))
                .andRespond(withSuccess("""
                        [{"id":"t07","title":"Mobilidade","description":"Descrição"},{"id":"t08","title":"Inclusão"}]
                        """, APPLICATION_JSON));
        http.expect(FEEDBACK, "/api/v1/feedbacks?studentId=" + ANA).andExpect(header(TRACE, "trace-details"))
                .andRespond(withSuccess(array(feedback(ESSAY, "PUBLISHED", 670, "2026-11-20T12:00:00Z"),
                        feedback(ESSAY, "PUBLISHED", 720, "2026-11-24T12:00:00Z"),
                        feedback(ESSAY, "DRAFT", 900, "2026-11-26T12:00:00Z")), APPLICATION_JSON));

        mvc.perform(get(API + "/students/" + ANA).header(USER, TEACHER).header(TRACE, "trace-details"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(ANA))
                .andExpect(jsonPath("$.name").value("Ana"))
                .andExpect(jsonPath("$.progress.lastScore").value(720))
                .andExpect(jsonPath("$.progress.averageByCompetency[0].average").value(120))
                .andExpect(jsonPath("$.progress.tasksDone").value(1))
                .andExpect(jsonPath("$.essays[0].theme").value("Mobilidade"))
                .andExpect(jsonPath("$.essays[0].totalScore").value(720))
                .andExpect(jsonPath("$.essays[1].theme").value("Inclusão"))
                .andExpect(jsonPath("$.essays[1].totalScore").hasJsonPath())
                .andExpect(jsonPath("$.essays[1].totalScore").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 409, 503})
    void forwardsDownstreamProblemDetailsUnmodified(int code) throws Exception {
        String problem = "{\"status\":" + code + ",\"detail\":\"Falha ao consultar o aluno\"}";
        http.expect(ACCOUNT, ANA_ENDPOINT)
                .andRespond(withStatus(HttpStatusCode.valueOf(code)).contentType(APPLICATION_PROBLEM_JSON).body(problem));

        mvc.perform(get(API + "/students/" + ANA).header(USER, TEACHER))
                .andExpect(status().is(code))
                .andExpect(content().contentType(APPLICATION_PROBLEM_JSON))
                .andExpect(content().bytes(problem.getBytes(StandardCharsets.UTF_8)));
    }

    private static String student(String id, String name, String classId) {
        return """
                {"id":"%s","name":"%s","email":"aluno@example.test","classId":"%s"}
                """.formatted(id, name, classId);
    }

    private static String progress(String id, int score, String trend, int pending, int competencyOne, int competencyTwo) {
        return """
                {"studentId":"%s","lastScore":%d,"trend":"%s","weakestCompetency":1,"tasksDone":1,"tasksPending":%d,
                 "averageByCompetency":[{"id":1,"average":%d},{"id":2,"average":%d}],"updatedAt":"2026-11-26T12:00:00Z"}
                """.formatted(id, score, trend, pending, competencyOne, competencyTwo);
    }

    private static String essay(String id, String themeId) {
        return """
                {"id":"%s","studentId":"%s","themeId":"%s","submittedAt":"2026-11-19T12:00:00Z"}
                """.formatted(id, ANA, themeId);
    }

    private static String feedback(String essayId, String status, int score, String publishedAt) {
        return """
                {"essayId":"%s","status":"%s","totalScore":%d,"publishedAt":"%s"}
                """.formatted(essayId, status, score, publishedAt);
    }

    private static String array(String... items) {
        return "[" + String.join(",", items) + "]";
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
