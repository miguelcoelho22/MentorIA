package br.com.mentoria.account;

import java.util.List;
import java.util.UUID;

import br.com.mentoria.account.AccountDirectory.ClassSummary;
import br.com.mentoria.account.AccountDirectory.Quota;
import br.com.mentoria.account.AccountDirectory.Student;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
class AccountDirectoryController {

    private final AccountDirectory directory;

    AccountDirectoryController(AccountDirectory directory) {
        this.directory = directory;
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
}
