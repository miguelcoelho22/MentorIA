package br.com.mentoria.study_plan;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "study-plan.seed.enabled", havingValue = "true", matchIfMissing = true)
class StudyPlanSeed implements ApplicationRunner {

    private static final UUID ANA = UUID.fromString("4155387b-4518-4657-bc51-d224aa1a69cb");
    private static final UUID BRUNO = UUID.fromString("e96b75e0-42ea-4f46-84db-5fbfb7ff4fe7");

    private final JdbcTemplate jdbc;

    StudyPlanSeed(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (createPlan(ANA)) {
            insertTask("demo-ana-intervencao", ANA, "Proposta de intervenção: 2 exercícios", 5, "PENDING");
            insertTask("demo-ana-regencia", ANA, "Revisar regência verbal", 1, "DONE");
            insertScores(ANA, "demo-ana-1", now.minusDays(8), 140, 120, 120, 120, 80);
            insertScores(ANA, "demo-ana-2", now.minusDays(1), 160, 140, 140, 120, 80);
            enqueue(ANA);
        }
        if (createPlan(BRUNO)) {
            insertTask("demo-bruno-argumentacao", BRUNO, "Organizar tese e argumentos em um esquema", 3, "PENDING");
            insertTask("demo-bruno-coesao", BRUNO, "Reescrever um parágrafo usando conectivos variados", 4, "PENDING");
            insertScores(BRUNO, "demo-bruno-1", now.minusDays(8), 100, 100, 80, 100, 100);
            insertScores(BRUNO, "demo-bruno-2", now.minusDays(1), 100, 100, 40, 100, 80);
            enqueue(BRUNO);
        }
    }

    private boolean createPlan(UUID studentId) {
        return jdbc.update("INSERT INTO plan_write.plan (student_id) VALUES (?) ON CONFLICT DO NOTHING", studentId) == 1;
    }

    private void insertTask(String id, UUID studentId, String title, int competency, String status) {
        jdbc.update("INSERT INTO plan_write.task (id, student_id, title, competency, status) VALUES (?, ?, ?, ?, ?)",
                id, studentId, title, competency, status);
    }

    private void insertScores(UUID studentId, String requestId, OffsetDateTime publishedAt, int... scores) {
        for (int index = 0; index < scores.length; index++) {
            jdbc.update("""
                    INSERT INTO plan_write.competency_score (student_id, request_id, competency, score, published_at)
                    VALUES (?, ?, ?, ?, ?)
                    """, studentId, requestId, index + 1, scores[index], publishedAt);
        }
    }

    private void enqueue(UUID studentId) {
        jdbc.update("INSERT INTO plan_write.domain_event (student_id, event_type) VALUES (?, 'TasksRecommended')", studentId);
    }
}
