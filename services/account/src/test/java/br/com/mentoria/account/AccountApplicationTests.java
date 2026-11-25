package br.com.mentoria.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:account-context;MODE=PostgreSQL",
        "account.jwt.private-key=",
        "account.seed.enabled=false"
})
class AccountApplicationTests {

    @Autowired private AccountTokenIssuer tokens;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void verifiesTokensAfterReloadingTheSigningKeyFromTheDatabase() throws Exception {
        var token = SignedJWT.parse(tokens.issue(UUID.randomUUID(), "STUDENT"));

        var reloaded = new AccountTokenIssuer(jdbc, "");
        var keys = JWKSet.parse(reloaded.publicJwks());
        var key = (RSAKey) keys.getKeyByKeyId(token.getHeader().getKeyID());

        assertThat(reloaded.publicJwks()).isEqualTo(tokens.publicJwks());
        assertThat(token.verify(new RSASSAVerifier(key))).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM account_signing_key", Integer.class)).isEqualTo(1);
    }
}
