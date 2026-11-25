package br.com.mentoria.account;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import br.com.mentoria.account.AccountDirectory.ClassSummary;
import br.com.mentoria.account.AccountDirectory.Profile;
import br.com.mentoria.account.AccountDirectory.Quota;
import br.com.mentoria.account.AccountDirectory.Student;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
class AccountDirectoryController {

    private final AccountDirectory directory;
    private final AccountRegistration registration;

    AccountDirectoryController(AccountDirectory directory, AccountRegistration registration) {
        this.directory = directory;
        this.registration = registration;
    }

    @PostMapping("/teachers")
    ResponseEntity<Profile> registerTeacher(@Valid @RequestBody TeacherRequest request) {
        Profile teacher = registration.registerTeacher(request.name(), request.email(), request.password());
        return ResponseEntity.created(URI.create("/api/v1/teachers/" + teacher.id())).body(teacher);
    }

    @PostMapping("/classes")
    ResponseEntity<ClassSummary> registerClass(@Valid @RequestBody ClassRequest request) {
        ClassSummary schoolClass = registration.registerClass(request.name(), request.teacherId());
        return ResponseEntity.created(URI.create("/api/v1/classes/" + schoolClass.id())).body(schoolClass);
    }

    @PostMapping("/students")
    ResponseEntity<Student> registerStudent(@Valid @RequestBody StudentRequest request) {
        Student student = registration.registerStudent(request.name(), request.email(), request.password(), request.classId());
        return ResponseEntity.created(URI.create("/api/v1/students/" + student.id())).body(student);
    }

    @GetMapping("/students/{studentId}")
    Student student(@PathVariable UUID studentId) {
        return directory.student(studentId);
    }

    @GetMapping("/students/{studentId}/quota")
    Quota quota(@PathVariable UUID studentId) {
        return directory.quota(studentId);
    }

    @GetMapping("/classes")
    List<ClassSummary> classes(@RequestParam UUID teacherId) {
        return directory.classes(teacherId);
    }

    @GetMapping("/classes/{classId}/students")
    List<Student> classStudents(@PathVariable String classId) {
        return directory.classStudents(classId);
    }

    record TeacherRequest(@NotBlank String name, @NotBlank @Email String email, @NotBlank String password) { }

    record ClassRequest(@NotBlank String name, @NotNull UUID teacherId) { }

    record StudentRequest(@NotBlank String name, @NotBlank @Email String email, @NotBlank String password,
            @NotBlank String classId) { }
}
