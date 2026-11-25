package br.com.mentoria.account;

import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;

import br.com.mentoria.account.AccountDirectory.ClassSummary;
import br.com.mentoria.account.AccountDirectory.Profile;
import br.com.mentoria.account.AccountDirectory.Student;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class AccountRegistration {

    private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    AccountRegistration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Profile registerTeacher(String name, String email, String password) {
        return createUser(name, email, password, "TEACHER");
    }

    ClassSummary registerClass(String name, UUID teacherId) {
        String classId = UUID.randomUUID().toString();
        String className = name.strip();
        int created = jdbc.update("""
                INSERT INTO classes (id, name, teacher_id)
                SELECT ?, ?, id FROM users WHERE id = ? AND role = 'TEACHER'
                """, classId, className, teacherId);
        if (created == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Professor não encontrado.");
        }
        return new ClassSummary(classId, className, teacherId, 0);
    }

    @Transactional
    public Student registerStudent(String name, String email, String password, String classId) {
        if (!Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM classes WHERE id = ?)", Boolean.class, classId))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Turma não encontrada.");
        }

        Profile user = createUser(name, email, password, "STUDENT");
        jdbc.update("INSERT INTO class_students (student_id, class_id) VALUES (?, ?)", user.id(), classId);
        jdbc.update("INSERT INTO quotas (student_id, period) VALUES (?, ?)", user.id(),
                YearMonth.now(ZoneOffset.UTC).atDay(1));
        return new Student(user.id(), user.name(), user.email(), classId);
    }

    private Profile createUser(String name, String email, String password, String role) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A senha deve ter até 72 bytes.");
        }
        var user = new Profile(UUID.randomUUID(), name.strip(), email.toLowerCase(Locale.ROOT), role);
        int created = jdbc.update("""
                INSERT INTO users (id, name, email, password_hash, role) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT DO NOTHING
                """, user.id(), user.name(), user.email(), encoder.encode(password), user.role());
        if (created == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "E-mail já cadastrado.");
        }
        return user;
    }
}
