package br.com.mentoria.account;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
class AccountDirectory {

    private final JdbcTemplate jdbc;

    AccountDirectory(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Student student(UUID studentId) {
        return jdbc.query("""
                SELECT u.id, u.name, u.email, enrollment.class_id
                FROM users u
                JOIN class_students enrollment ON enrollment.student_id = u.id
                WHERE u.id = ? AND u.role = 'STUDENT'
                """, rows -> {
                    if (!rows.next()) {
                        throw notFound("Aluno não encontrado.");
                    }
                    return readStudent(rows);
                }, studentId);
    }

    Quota quota(UUID studentId) {
        YearMonth month = YearMonth.now(ZoneOffset.UTC);
        return jdbc.query("""
                SELECT COUNT(CASE WHEN r.status = 'RESERVED' THEN 1 END) AS reserved,
                       COUNT(CASE WHEN r.status = 'CONSUMED' THEN 1 END) AS consumed
                FROM users u
                LEFT JOIN quotas q ON q.student_id = u.id AND q.period = ?
                LEFT JOIN quota_reservations r ON r.student_id = q.student_id AND r.period = q.period
                WHERE u.id = ? AND u.role = 'STUDENT'
                GROUP BY u.id
                """, rows -> {
                    if (!rows.next()) {
                        throw notFound("Aluno não encontrado.");
                    }
                    int reserved = rows.getInt("reserved");
                    int consumed = rows.getInt("consumed");
                    int limit = QuotaReservationService.MONTHLY_LIMIT;
                    return new Quota(studentId, month.toString(), limit, reserved, consumed, limit - reserved - consumed);
                }, month.atDay(1), studentId);
    }

    List<ClassSummary> classes(UUID teacherId) {
        return jdbc.query("""
                SELECT c.id, c.name, c.teacher_id, COUNT(s.id) AS student_count
                FROM classes c
                JOIN users t ON t.id = c.teacher_id AND t.role = 'TEACHER'
                LEFT JOIN class_students enrollment ON enrollment.class_id = c.id
                LEFT JOIN users s ON s.id = enrollment.student_id AND s.role = 'STUDENT'
                WHERE c.teacher_id = ?
                GROUP BY c.id, c.name, c.teacher_id
                ORDER BY c.name, c.id
                """, (row, index) -> new ClassSummary(row.getString("id"), row.getString("name"),
                        row.getObject("teacher_id", UUID.class), row.getInt("student_count")), teacherId);
    }

    List<Student> classStudents(String classId) {
        return jdbc.query("""
                SELECT u.id, u.name, u.email, c.id AS class_id
                FROM classes c
                LEFT JOIN class_students enrollment ON enrollment.class_id = c.id
                LEFT JOIN users u ON u.id = enrollment.student_id AND u.role = 'STUDENT'
                WHERE c.id = ?
                ORDER BY u.name, u.id
                """, rows -> {
                    if (!rows.next()) {
                        throw notFound("Turma não encontrada.");
                    }
                    List<Student> students = new ArrayList<>();
                    do {
                        if (rows.getObject("id") != null) {
                            students.add(readStudent(rows));
                        }
                    } while (rows.next());
                    return students;
                }, classId);
    }

    private static Student readStudent(ResultSet row) throws SQLException {
        return new Student(row.getObject("id", UUID.class), row.getString("name"),
                row.getString("email"), row.getString("class_id"));
    }

    private static ResponseStatusException notFound(String detail) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, detail);
    }

    record Student(UUID id, String name, String email, String classId) { }

    record ClassSummary(String id, String name, UUID teacherId, int studentCount) { }

    record Quota(UUID studentId, String period, int limit, int reserved, int consumed, int available) { }
}
