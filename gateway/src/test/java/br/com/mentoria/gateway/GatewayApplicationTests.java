package br.com.mentoria.gateway;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayApplicationTests {

	private static final RSAKey SIGNING_KEY = generateKey();

	private static final DisposableServer BACKEND = HttpServer.create()
			.host("127.0.0.1")
			.port(0)
			.route(routes -> routes
					.get("/api/v1/auth/jwks", (req, res) -> res
							.header("Content-Type", "application/json")
							.sendString(Mono.just(new JWKSet(SIGNING_KEY.toPublicJWK()).toString())))
					.route(req -> true, (req, res) -> res
							.header("Content-Type", "application/json")
							.sendString(Mono.just("""
									{"path":"%s","userId":"%s","role":"%s","correlationId":"%s"}"""
									.formatted(req.uri(),
											req.requestHeaders().get("X-User-Id", ""),
											req.requestHeaders().get("X-User-Role", ""),
											req.requestHeaders().get("X-Correlation-Id", ""))))))
			.bindNow();

	@DynamicPropertySource
	static void backend(DynamicPropertyRegistry registry) {
		String url = "http://127.0.0.1:" + BACKEND.port();
		registry.add("ACCOUNT_SERVICE_URL", () -> url);
		registry.add("BFF_STUDENT_URL", () -> url);
		registry.add("BFF_TEACHER_URL", () -> url);
	}

	@AfterAll
	static void stopBackend() {
		BACKEND.disposeNow();
	}

	@LocalServerPort
	int port;

	WebTestClient client;

	@BeforeEach
	void setUp() {
		client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
	}

	@Test
	void rotaProtegidaSemTokenResponde401() {
		client.get().uri("/api/v1/student/home").exchange()
				.expectStatus().isUnauthorized()
				.expectHeader().exists("X-Correlation-Id");
	}

	@Test
	void tokenExpiradoOuDeOutroEmissorResponde401() {
		String expired = token(UUID.randomUUID(), "STUDENT", "account", Instant.now().minusSeconds(7200));
		String otherIssuer = token(UUID.randomUUID(), "STUDENT", "outro", Instant.now());

		client.get().uri("/api/v1/student/home").headers(h -> h.setBearerAuth(expired)).exchange()
				.expectStatus().isUnauthorized();
		client.get().uri("/api/v1/student/home").headers(h -> h.setBearerAuth(otherIssuer)).exchange()
				.expectStatus().isUnauthorized();
	}

	@Test
	void papelErradoResponde403() {
		String teacher = token(UUID.randomUUID(), "TEACHER");
		String student = token(UUID.randomUUID(), "STUDENT");

		client.get().uri("/api/v1/student/home").headers(h -> h.setBearerAuth(teacher)).exchange()
				.expectStatus().isForbidden();
		client.get().uri("/api/v1/teacher/classes").headers(h -> h.setBearerAuth(student)).exchange()
				.expectStatus().isForbidden();
	}

	@Test
	void encaminhaAoBffComIdentidadeDoTokenEIgnoraCabecalhosForjados() {
		UUID studentId = UUID.randomUUID();
		String jwt = token(studentId, "STUDENT");

		var result = client.get().uri("/api/v1/student/home")
				.headers(h -> {
					h.setBearerAuth(jwt);
					h.set("X-User-Id", "forjado");
					h.set("X-User-Role", "TEACHER");
					h.set("X-Correlation-Id", "forjado");
				})
				.exchange()
				.expectStatus().isOk()
				.expectHeader().exists("X-RateLimit-Remaining")
				.expectBody()
				.jsonPath("$.path").isEqualTo("/api/v1/student/home")
				.jsonPath("$.userId").isEqualTo(studentId.toString())
				.jsonPath("$.role").isEqualTo("STUDENT")
				.returnResult();

		String correlationId = result.getResponseHeaders().getFirst("X-Correlation-Id");
		assertThat(correlationId).isNotEqualTo("forjado");
		assertThat(new String(result.getResponseBodyContent())).contains(correlationId);
	}

	@Test
	void professorChegaAoBffProfessor() {
		UUID teacherId = UUID.randomUUID();
		client.get().uri("/api/v1/teacher/classes")
				.headers(h -> h.setBearerAuth(token(teacherId, "TEACHER")))
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.userId").isEqualTo(teacherId.toString())
				.jsonPath("$.role").isEqualTo("TEACHER");
	}

	@Test
	void loginEPublicoMesmoComTokenInvalidoENaoRepassaIdentidade() {
		client.post().uri("/api/v1/auth/login")
				.headers(h -> {
					h.setBearerAuth("token-velho-e-invalido");
					h.set("X-User-Id", "forjado");
				})
				.bodyValue("{}")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.path").isEqualTo("/api/v1/auth/login")
				.jsonPath("$.userId").isEqualTo("");
	}

	@Test
	void rotasInternasNaoSaoExpostas() {
		String student = token(UUID.randomUUID(), "STUDENT");

		client.put().uri("/api/v1/quota-reservations/" + UUID.randomUUID()).exchange()
				.expectStatus().isUnauthorized();
		client.put().uri("/api/v1/quota-reservations/" + UUID.randomUUID())
				.headers(h -> h.setBearerAuth(student)).exchange()
				.expectStatus().isForbidden();
		client.get().uri("/actuator/env").headers(h -> h.setBearerAuth(student)).exchange()
				.expectStatus().isForbidden();
	}

	@Test
	void pedidoDeCorrecaoLimitadoA2PorMinuto() {
		String jwt = token(UUID.randomUUID(), "STUDENT");
		String uri = "/api/v1/student/essays/" + UUID.randomUUID() + "/correction-requests";

		for (int i = 0; i < 2; i++) {
			client.post().uri(uri).headers(h -> h.setBearerAuth(jwt)).exchange()
					.expectStatus().isOk();
		}
		client.post().uri(uri).headers(h -> h.setBearerAuth(jwt)).exchange()
				.expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
				.expectHeader().exists("Retry-After");

		client.get().uri("/api/v1/student/themes").headers(h -> h.setBearerAuth(jwt)).exchange()
				.expectStatus().isOk();
	}

	@Test
	void limiteGeralDe60PorMinuto() {
		String jwt = token(UUID.randomUUID(), "STUDENT");
		for (int i = 0; i < 60; i++) {
			client.get().uri("/api/v1/student/plan").headers(h -> h.setBearerAuth(jwt)).exchange()
					.expectStatus().isOk();
		}
		client.get().uri("/api/v1/student/plan").headers(h -> h.setBearerAuth(jwt)).exchange()
				.expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
	}

	@Test
	void preflightCorsDeOrigemPermitida() {
		client.options().uri("/api/v1/student/home")
				.header(HttpHeaders.ORIGIN, "http://localhost:3000")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization")
				.exchange()
				.expectStatus().isOk()
				.expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000");

		client.options().uri("/api/v1/student/home")
				.header(HttpHeaders.ORIGIN, "http://malicioso.example")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
				.exchange()
				.expectStatus().isForbidden();
	}

	@Test
	void healthPublico() {
		client.get().uri("/actuator/health/readiness").exchange()
				.expectStatus().isOk();
	}

	private static String token(UUID subject, String role) {
		return token(subject, role, "account", Instant.now());
	}

	private static String token(UUID subject, String role, String issuer, Instant issuedAt) {
		var claims = new JWTClaimsSet.Builder()
				.issuer(issuer)
				.subject(subject.toString())
				.claim("role", role)
				.issueTime(Date.from(issuedAt))
				.expirationTime(Date.from(issuedAt.plusSeconds(3600)))
				.build();
		var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(SIGNING_KEY.getKeyID()).build(), claims);
		try {
			jwt.sign(new RSASSASigner(SIGNING_KEY));
		} catch (JOSEException e) {
			throw new IllegalStateException(e);
		}
		return jwt.serialize();
	}

	private static RSAKey generateKey() {
		try {
			return new RSAKeyGenerator(2048).keyIDFromThumbprint(true).generate();
		} catch (JOSEException e) {
			throw new IllegalStateException(e);
		}
	}
}
