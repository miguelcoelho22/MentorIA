package br.com.mentoria.account;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:account-context",
        "account.seed.enabled=false"
})
class AccountApplicationTests {

	@Test
	void contextLoads() {
	}

}
