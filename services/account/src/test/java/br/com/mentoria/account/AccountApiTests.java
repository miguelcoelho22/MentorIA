package br.com.mentoria.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.json.JsonCompareMode;
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
        properties.add("ACCOUNT_JWT_PRIVATE_KEY",
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

        String jwks = mvc.perform(get("/api/v1/auth/jwks"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.keys.length()").value(1))
                .andExpect(jsonPath("$.keys[0].use").value("sig"))
                .andReturn().getResponse().getContentAsString();
        var publicKey = (RSAKey) JWKSet.parse(jwks).getKeys().getFirst();
        assertThat(publicKey.toJSONObject()).containsOnlyKeys("kty", "kid", "alg", "use", "n", "e");
        assertThat(publicKey.toRSAPublicKey()).isEqualTo(KEY_PAIR.getPublic());
        assertThat(publicKey.getKeyID()).isEqualTo(publicKey.computeThumbprint().toString())
                .isEqualTo(jwt.getHeader().getKeyID());
        assertThat(jwt.verify(new RSASSAVerifier(publicKey.toRSAPublicKey()))).isTrue();
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

    @Test
    @Transactional
    void registersTeacherClassAndStudentWithInitialMonthlyQuota() throws Exception {
        String teacherId = JsonPath.read(mvc.perform(post("/api/v1/teachers")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"Nova Professora","email":"NOVA.PROF@demo.mentoria.test","password":"teacher-test-password"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Nova Professora"))
                .andExpect(jsonPath("$.email").value("nova.prof@demo.mentoria.test"))
                .andExpect(jsonPath("$.role").value("TEACHER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn().getResponse().getContentAsString(), "$.id");

        String classId = JsonPath.read(mvc.perform(post("/api/v1/classes")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"Turma P2","teacherId":"%s"}
                                """.formatted(teacherId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.teacherId").value(teacherId))
                .andExpect(jsonPath("$.studentCount").value(0))
                .andReturn().getResponse().getContentAsString(), "$.id");

        var student = mvc.perform(post("/api/v1/students").contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"Nova Aluna","email":"nova.aluna@demo.mentoria.test","password":"student-test-password","classId":"%s"}
                """.formatted(classId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.classId").value(classId))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn().getResponse();
        String studentId = JsonPath.read(student.getContentAsString(), "$.id");
        assertThat(student.getHeader(HttpHeaders.LOCATION)).isEqualTo("/api/v1/students/" + studentId);

        YearMonth month = YearMonth.now(ZoneOffset.UTC);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM quotas WHERE student_id = ? AND period = ?",
                Integer.class, UUID.fromString(studentId), month.atDay(1))).isEqualTo(1);
        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, UUID.fromString(studentId));
        assertThat(new BCryptPasswordEncoder().matches("student-test-password", hash)).isTrue();
        mvc.perform(get("/api/v1/students/{studentId}/quota", studentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value(month.toString()))
                .andExpect(jsonPath("$.reserved").value(0))
                .andExpect(jsonPath("$.consumed").value(0))
                .andExpect(jsonPath("$.available").value(4));
        mvc.perform(get("/api/v1/classes/{classId}/students", classId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(studentId));
        String token = login("nova.aluna@demo.mentoria.test", "student-test-password");
        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"id":"%s","name":"Nova Aluna","email":"nova.aluna@demo.mentoria.test","role":"STUDENT"}
                        """.formatted(studentId), JsonCompareMode.STRICT));
    }

    @Test
    void rejectsClassCreationForMissingTeacherOrStudentId() throws Exception {
        for (UUID teacherId : new UUID[] {UUID.randomUUID(), ANA}) {
            mvc.perform(post("/api/v1/classes").contentType(MediaType.APPLICATION_JSON).content("""
                    {"name":"Turma inválida","teacherId":"%s"}
                    """.formatted(teacherId)))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
    }

    @Test
    void rejectsEnrollmentInMissingClassWithoutCreatingUser() throws Exception {
        String email = "sem.turma@demo.mentoria.test";
        mvc.perform(post("/api/v1/students").contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"Sem Turma","email":"%s","password":"student-test-password","classId":"unknown"}
                """.formatted(email)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, email)).isZero();
    }

    @Test
    void rejectsDuplicateEmailAcrossRolesAndMalformedRegistration() throws Exception {
        mvc.perform(post("/api/v1/teachers").contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"Outro Usuário","email":"ANA@demo.mentoria.test","password":"teacher-test-password"}
                """))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/students").contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"Aluno","email":"invalid","classId":"demo-3A"}
                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMissingMalformedExpiredOrIncorrectlySignedBearerToken() throws Exception {
        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
        var valid = SignedJWT.parse(login("ana@demo.mentoria.test", "account-test-password"));
        var expired = new SignedJWT(valid.getHeader(), new JWTClaimsSet.Builder(valid.getJWTClaimsSet())
                .expirationTime(Date.from(Instant.now().minusSeconds(10))).build());
        expired.sign(new RSASSASigner(KEY_PAIR.getPrivate()));
        var forged = new SignedJWT(valid.getHeader(), valid.getJWTClaimsSet());
        forged.sign(new RSASSASigner(generateKeyPair().getPrivate()));
        for (String token : new String[] {"not-a-jwt", expired.serialize(), forged.serialize()}) {
            mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
    }

    private String login(String email, String password) throws Exception {
        var response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"%s"}
                """.formatted(email, password)))
                .andExpect(status().isOk()).andReturn().getResponse();
        return JsonPath.read(response.getContentAsString(), "$.accessToken");
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
