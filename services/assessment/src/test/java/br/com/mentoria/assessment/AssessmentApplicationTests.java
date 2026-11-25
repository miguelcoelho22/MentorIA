package br.com.mentoria.assessment;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:assessment-smoke;MODE=PostgreSQL",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "assessment.outbox.enabled=false"
})
class AssessmentApplicationTests {

	@Test
	void contextLoads() {
	}

}
