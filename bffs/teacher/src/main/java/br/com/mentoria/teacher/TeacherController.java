package br.com.mentoria.teacher;

import static br.com.mentoria.teacher.BffWebConfiguration.USER_ID_ATTRIBUTE;

import java.util.List;
import java.util.UUID;

import br.com.mentoria.teacher.TeacherData.ClassOverview;
import br.com.mentoria.teacher.TeacherData.ClassSummary;
import br.com.mentoria.teacher.TeacherData.StudentDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(TeacherController.PREFIX)
class TeacherController {

    static final String PREFIX = "/api/v1/teacher";
    private final TeacherService service;

    TeacherController(TeacherService service) {
        this.service = service;
    }

    @GetMapping("/classes")
    List<ClassSummary> classes(@RequestAttribute(USER_ID_ATTRIBUTE) UUID teacherId) {
        return service.classes(teacherId);
    }

    @GetMapping("/classes/{classId}/overview")
    ClassOverview overview(@RequestAttribute(USER_ID_ATTRIBUTE) UUID teacherId, @PathVariable String classId) {
        return service.overview(teacherId, classId);
    }

    @GetMapping("/students/{studentId}")
    StudentDetail student(@RequestAttribute(USER_ID_ATTRIBUTE) UUID teacherId, @PathVariable UUID studentId) {
        return service.student(teacherId, studentId);
    }
}
