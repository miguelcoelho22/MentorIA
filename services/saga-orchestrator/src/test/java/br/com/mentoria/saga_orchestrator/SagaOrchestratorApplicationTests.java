package br.com.mentoria.saga_orchestrator;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Precisa de PostgreSQL e RabbitMQ; o fluxo completo é testado com ./mentoria saga e ./mentoria saga-falha.")
class SagaOrchestratorApplicationTests {

	@Test
	void contextLoads() {
	}

}
