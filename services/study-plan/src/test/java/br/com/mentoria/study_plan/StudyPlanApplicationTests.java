package br.com.mentoria.study_plan;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:study-plan-smoke;MODE=PostgreSQL",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "study-plan.projection.enabled=false"
})
class StudyPlanApplicationTests {

	@Test
	void contextLoads() {
	}

}
