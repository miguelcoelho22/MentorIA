package br.com.mentoria.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:account-api;MODE=PostgreSQL",
        "account.seed.enabled=true",
        "account.seed.password=account-test-password"
})
@AutoConfigureMockMvc
class AccountApiTests {

    private static final UUID ANA = UUID.fromString("4155387b-4518-4657-bc51-d224aa1a69cb");
    private static final UUID TEACHER = UUID.fromString("145689e0-aefe-45a1-9834-ced2aa148c75");
    private static final KeyPair KEY_PAIR = generateKeyPair();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AccountSeed seed;

    @DynamicPropertySource
    static void signingKey(DynamicPropertyRegistry properties) {
        properties.add("account.jwt.private-key",
                () -> Base64.getEncoder().encodeToString(KEY_PAIR.getPrivate().getEncoded()));
    }

    @Test
    void loginReturnsSignedJwtAndPublicUserFields() throws Exception {
        var response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"ANA@demo.mentoria.test","password":"account-test-password"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.user.id").value(ANA.toString()))
                .andExpect(jsonPath("$.user.name").value("Ana Souza"))
                .andExpect(jsonPath("$.user.role").value("STUDENT"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andReturn().getResponse();

        String token = JsonPath.read(response.getContentAsString(), "$.accessToken");
        var jwt = SignedJWT.parse(token);
        var claims = jwt.getJWTClaimsSet();
        assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
        assertThat(jwt.verify(new RSASSAVerifier((RSAPublicKey) KEY_PAIR.getPublic()))).isTrue();
        assertThat(claims.getSubject()).isEqualTo(ANA.toString());
        assertThat(claims.getStringClaim("role")).isEqualTo("STUDENT");
        assertThat(claims.getExpirationTime().getTime() - claims.getIssueTime().getTime()).isEqualTo(3_600_000);
    }

    @Test
    void distinguishesMalformedLoginFromInvalidCredentials() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"invalid"}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"ana@demo.mentoria.test","password":"wrong"}
                """))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://mentoria.app/errors/invalid-credentials"));
    }

    @Test
    void returnsStudentProfileAndRejectsTeacherId() throws Exception {
        mvc.perform(get("/api/v1/students/{studentId}", ANA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ANA.toString()))
                .andExpect(jsonPath("$.name").value("Ana Souza"))
                .andExpect(jsonPath("$.email").value("ana@demo.mentoria.test"))
                .andExpect(jsonPath("$.classId").value("demo-3A"));
        mvc.perform(get("/api/v1/students/{studentId}", TEACHER)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void quotaUsesCurrentMonthAndIgnoresReleasedAndPreviousReservations() throws Exception {
        YearMonth month = YearMonth.now(ZoneOffset.UTC);
        insertReservation(month, "RESERVED");
        insertReservation(month, "CONSUMED");
        insertReservation(month, "RELEASED");
        insertReservation(month.minusMonths(1), "CONSUMED");

        mvc.perform(get("/api/v1/students/{studentId}/quota", ANA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(ANA.toString()))
                .andExpect(jsonPath("$.period").value(month.toString()))
                .andExpect(jsonPath("$.limit").value(4))
                .andExpect(jsonPath("$.reserved").value(1))
                .andExpect(jsonPath("$.consumed").value(1))
                .andExpect(jsonPath("$.available").value(2))
                .andExpect(jsonPath("$.renewsOn").doesNotExist());
        mvc.perform(get("/api/v1/students/{studentId}/quota", UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void listsTeacherClassesAndReturnsAnEmptyListForUnknownTeacher() throws Exception {
        mvc.perform(get("/api/v1/classes").param("teacherId", TEACHER.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("demo-3A"))
                .andExpect(jsonPath("$[0].name").value("3º ano A"))
                .andExpect(jsonPath("$[0].teacherId").value(TEACHER.toString()))
                .andExpect(jsonPath("$[0].studentCount").value(2));
        mvc.perform(get("/api/v1/classes").param("teacherId", UUID.randomUUID().toString()))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void listsClassStudentsAndReturnsNotFoundForUnknownClass() throws Exception {
        mvc.perform(get("/api/v1/classes/demo-3A/students"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Ana Souza"))
                .andExpect(jsonPath("$[1].name").value("Bruno Silva"));
        mvc.perform(get("/api/v1/classes/unknown/students"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void seedCanRunTwiceWithoutDuplicatingRowsOrChangingThePasswordHash() {
        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, ANA);

        seed.run(null);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM classes", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM class_students", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, ANA))
                .isEqualTo(hash).startsWith("$2");
    }

    private void insertReservation(YearMonth month, String status) {
        jdbc.update("INSERT INTO quotas (student_id, period) VALUES (?, ?) ON CONFLICT DO NOTHING", ANA, month.atDay(1));
        jdbc.update("INSERT INTO quota_reservations (request_id, student_id, period, status) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), ANA, month.atDay(1), status);
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }
}
