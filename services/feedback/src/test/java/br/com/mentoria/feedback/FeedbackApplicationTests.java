package br.com.mentoria.feedback;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:feedback-smoke;MODE=PostgreSQL",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "feedback.outbox.enabled=false"
})
class FeedbackApplicationTests {

	@Autowired
	private RabbitTemplate rabbit;

	@Test
	void configuresJsonConversionForRabbitTemplate() {
		assertThat(rabbit.getMessageConverter()).isInstanceOf(Jackson2JsonMessageConverter.class);
	}

}
