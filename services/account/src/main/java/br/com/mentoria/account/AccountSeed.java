package br.com.mentoria.account;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "account.seed.enabled", havingValue = "true", matchIfMissing = true)
class AccountSeed implements ApplicationRunner {

    private static final UUID TEACHER = UUID.fromString("145689e0-aefe-45a1-9834-ced2aa148c75");
    private static final UUID ANA = UUID.fromString("4155387b-4518-4657-bc51-d224aa1a69cb");
    private static final UUID BRUNO = UUID.fromString("e96b75e0-42ea-4f46-84db-5fbfb7ff4fe7");
    private static final String CLASS_ID = "demo-3A";

    private final JdbcTemplate jdbc;
    private final String password;

    AccountSeed(JdbcTemplate jdbc, @Value("${account.seed.password:MentorIA-demo-2026!}") String password) {
        this.jdbc = jdbc;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM users WHERE NOT demo)", Boolean.class))) {
            return;
        }
        if (password.isBlank() || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("account.seed.password deve conter de 1 a 72 bytes.");
        }

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        insertUser(TEACHER, "professor@demo.mentoria.test", "Marcos Lima", "TEACHER", encoder.encode(password));
        insertUser(ANA, "ana@demo.mentoria.test", "Ana Souza", "STUDENT", encoder.encode(password));
        insertUser(BRUNO, "bruno@demo.mentoria.test", "Bruno Silva", "STUDENT", encoder.encode(password));
        jdbc.update("""
                INSERT INTO classes (id, name, teacher_id) VALUES (?, ?, ?)
                ON CONFLICT DO NOTHING
                """, CLASS_ID, "3º ano A", TEACHER);
        enroll(ANA);
        enroll(BRUNO);
    }

    private void insertUser(UUID id, String email, String name, String role, String passwordHash) {
        jdbc.update("""
                INSERT INTO users (id, email, name, role, password_hash, demo) VALUES (?, ?, ?, ?, ?, TRUE)
                ON CONFLICT DO NOTHING
                """, id, email, name, role, passwordHash);
    }

    private void enroll(UUID studentId) {
        jdbc.update("""
                INSERT INTO class_students (student_id, class_id) VALUES (?, ?)
                ON CONFLICT DO NOTHING
                """, studentId, CLASS_ID);
    }
}
