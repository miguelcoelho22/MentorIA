package br.com.mentoria.feedback;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:feedback-smoke;MODE=PostgreSQL",
        "feedback.outbox.enabled=false"
})
class FeedbackApplicationTests {

	@Test
	void contextLoads() {
	}

}
