package br.com.mentoria.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.UUID;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;

class AccountTokenIssuerTests {

    @Test
    void generatedKeySignsTokensAndPublishesOnlyThePublicKey() throws Exception {
        var issuer = new AccountTokenIssuer("");
        var jwt = SignedJWT.parse(issuer.issue(UUID.randomUUID(), "STUDENT"));
        var key = (RSAKey) issuer.publicKeys().getKeyByKeyId(jwt.getHeader().getKeyID());

        assertThat(key).isNotNull();
        assertThat(key.isPrivate()).isFalse();
        assertThat(jwt.verify(new RSASSAVerifier(key.toRSAPublicKey()))).isTrue();
    }

    @Test
    void invalidConfiguredKeyFailsRatherThanGeneratingAnotherKey() {
        assertThatIllegalStateException().isThrownBy(() -> new AccountTokenIssuer("invalid-base64!"));
    }
}
