package br.com.mentoria.study_plan;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import br.com.mentoria.study_plan.StudyPlanViews.Plan;
import br.com.mentoria.study_plan.StudyPlanViews.Progress;
import br.com.mentoria.study_plan.StudyPlanViews.Task;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/v1")
class StudyPlanController {

    private final StudyPlanCommands commands;
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final ObjectMapper json;

    StudyPlanController(StudyPlanCommands commands, JdbcTemplate jdbc, NamedParameterJdbcTemplate namedJdbc,
            ObjectMapper json) {
        this.commands = commands;
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
        this.json = json;
    }

    @GetMapping("/students/{studentId}/plan")
    Plan plan(@PathVariable UUID studentId) {
        return read("SELECT payload FROM plan_read.student_plan_view WHERE student_id = ?", studentId, Plan.class);
    }

    @GetMapping("/students/{studentId}/progress")
    Progress progress(@PathVariable UUID studentId) {
        return read("SELECT payload FROM plan_read.student_progress_view WHERE student_id = ?", studentId, Progress.class);
    }

    @GetMapping("/progress")
    List<Progress> progressBatch(@RequestParam List<UUID> studentIds) {
        if (studentIds.isEmpty() || studentIds.contains(null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe os UUIDs dos alunos em studentIds.");
        }
        List<UUID> requested = studentIds.stream().distinct().toList();
        Map<UUID, Progress> found = namedJdbc.query("""
                SELECT payload FROM plan_read.student_progress_view WHERE student_id IN (:studentIds)
                """, Map.of("studentIds", requested),
                (row, index) -> json.readValue(row.getString("payload"), Progress.class)).stream()
                .collect(Collectors.toMap(Progress::studentId, Function.identity()));
        return requested.stream().filter(found::containsKey).map(found::get).toList();
    }

    @PatchMapping("/tasks/{taskId}")
    Task updateTask(@PathVariable String taskId, @RequestBody TaskUpdate request) {
        if (!"DONE".equals(request.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O status aceito é DONE.");
        }
        return commands.completeTask(taskId);
    }

    private <T> T read(String query, UUID studentId, Class<T> type) {
        return jdbc.query(query, rows -> {
            if (!rows.next()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Plano ou progresso do aluno não encontrado.");
            }
            return json.readValue(rows.getString("payload"), type);
        }, studentId);
    }

    record TaskUpdate(String status) { }
}
