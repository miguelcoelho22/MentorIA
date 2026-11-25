package br.com.mentoria.account;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class AccountLoginController {

    private final JdbcTemplate jdbc;
    private final AccountTokenIssuer tokens;
    private final boolean demoEnabled;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final String missingUserHash = encoder.encode(UUID.randomUUID().toString());

    AccountLoginController(JdbcTemplate jdbc, AccountTokenIssuer tokens,
            @Value("${account.seed.enabled:true}") boolean demoEnabled) {
        this.jdbc = jdbc;
        this.tokens = tokens;
        this.demoEnabled = demoEnabled;
    }

    @PostMapping("/api/v1/auth/login")
    LoginResponse login(@Valid @RequestBody LoginRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw invalidCredentials();
        }
        Credential credential = jdbc.query("""
                SELECT id, name, role, password_hash FROM users WHERE email = ? AND (NOT demo OR ?)
                """, rows -> rows.next() ? new Credential(
                        new User(rows.getObject("id", UUID.class), rows.getString("name"), rows.getString("role")),
                        rows.getString("password_hash")) : null,
                request.email().toLowerCase(Locale.ROOT), demoEnabled);

        String hash = credential == null ? missingUserHash : credential.passwordHash();
        if (!encoder.matches(request.password(), hash) || credential == null) {
            throw invalidCredentials();
        }
        User user = credential.user();
        return new LoginResponse(tokens.issue(user.id(), user.role()), "Bearer", AccountTokenIssuer.EXPIRES_IN_SECONDS, user);
    }

    private static ResponseStatusException invalidCredentials() {
        var error = new ResponseStatusException(HttpStatus.UNAUTHORIZED, "E-mail ou senha incorretos.");
        error.setType(URI.create("https://mentoria.app/errors/invalid-credentials"));
        error.setTitle("Credenciais inválidas");
        return error;
    }

    record LoginRequest(@NotBlank @Email String email, @NotBlank String password) { }

    record LoginResponse(String accessToken, String tokenType, long expiresIn, User user) { }

    record User(UUID id, String name, String role) { }

    private record Credential(User user, String passwordHash) { }
}
