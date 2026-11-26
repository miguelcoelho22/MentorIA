package br.com.mentoria.teacher;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import br.com.mentoria.teacher.TeacherData.Average;
import br.com.mentoria.teacher.TeacherData.ClassOverview;
import br.com.mentoria.teacher.TeacherData.ClassSummary;
import br.com.mentoria.teacher.TeacherData.EssaySummary;
import br.com.mentoria.teacher.TeacherData.Feedback;
import br.com.mentoria.teacher.TeacherData.Progress;
import br.com.mentoria.teacher.TeacherData.Student;
import br.com.mentoria.teacher.TeacherData.StudentDetail;
import br.com.mentoria.teacher.TeacherData.StudentNeedingSupport;
import br.com.mentoria.teacher.TeacherData.StudyProgress;
import br.com.mentoria.teacher.TeacherData.Theme;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
class TeacherService {

    private static final Comparator<Feedback> BY_PUBLICATION_DATE = Comparator.comparing(Feedback::publishedAt,
            Comparator.nullsFirst(Comparator.naturalOrder()));

    private final TeacherClients clients;

    TeacherService(TeacherClients clients) {
        this.clients = clients;
    }

    List<ClassSummary> classes(UUID teacherId) {
        return clients.classes(teacherId);
    }

    ClassOverview overview(UUID teacherId, String classId) {
        requireClass(teacherId, classId);
        List<Student> students = clients.classStudents(classId);
        if (students.isEmpty()) {
            return new ClassOverview(classId, 0, 0, null, List.of());
        }

        List<UUID> studentIds = students.stream().map(Student::id).toList();
        Map<UUID, String> names = students.stream().collect(Collectors.toMap(Student::id, Student::name));
        List<StudyProgress> progress = clients.classProgress(studentIds).stream()
                .filter(row -> names.containsKey(row.studentId())).toList();

        double averageScore = progress.stream().mapToInt(StudyProgress::lastScore).average().orElse(0);
        List<StudentNeedingSupport> needSupport = progress.stream()
                .filter(row -> "down".equals(row.trend()) || row.lastScore() < 600 || row.tasksPending() >= 2)
                .map(row -> new StudentNeedingSupport(row.studentId(), names.get(row.studentId()), row.lastScore(),
                        row.trend(), row.tasksPending()))
                .sorted(Comparator.comparingInt(StudentNeedingSupport::lastScore)
                        .thenComparing(Comparator.comparingInt(StudentNeedingSupport::pendingTasks).reversed()))
                .toList();
        return new ClassOverview(classId, students.size(), averageScore, weakestCompetency(progress), needSupport);
    }

    StudentDetail student(UUID teacherId, UUID studentId) {
        Student student = clients.student(studentId);
        requireClass(teacherId, student.classId());

        Progress progress = clients.progress(studentId).view();
        var essays = clients.essays(studentId);
        Map<String, String> titles = clients.themes().stream().collect(Collectors.toMap(Theme::id, Theme::title));
        Map<UUID, Feedback> latestFeedback = clients.feedbacks(studentId).stream()
                .filter(entry -> "PUBLISHED".equals(entry.status()))
                .collect(Collectors.toMap(Feedback::essayId, Function.identity(),
                        (first, second) -> BY_PUBLICATION_DATE.compare(first, second) >= 0 ? first : second));

        List<EssaySummary> history = essays.stream().map(essay -> {
            Feedback feedback = latestFeedback.get(essay.id());
            return new EssaySummary(essay.id(), titles.getOrDefault(essay.themeId(), essay.themeId()), essay.submittedAt(),
                    feedback == null ? null : feedback.totalScore());
        }).toList();
        return new StudentDetail(studentId, student.name(), progress, history);
    }

    private void requireClass(UUID teacherId, String classId) {
        if (classId == null || classes(teacherId).stream().noneMatch(summary -> summary.id().equals(classId))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Turma fora do vínculo do professor.");
        }
    }

    private static Integer weakestCompetency(List<StudyProgress> progress) {
        Map<Integer, Double> averages = progress.stream().flatMap(row -> row.averageByCompetency().stream())
                .collect(Collectors.groupingBy(Average::id,
                        Collectors.averagingDouble(average -> average.average().doubleValue())));
        return averages.entrySet().stream()
                .min(Map.Entry.<Integer, Double>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey).orElse(null);
    }
}
