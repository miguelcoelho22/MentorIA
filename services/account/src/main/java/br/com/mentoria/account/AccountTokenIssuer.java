package br.com.mentoria.account;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class AccountTokenIssuer {

    static final long EXPIRES_IN_SECONDS = 3600;

    private final RSASSASigner signer;

    AccountTokenIssuer(@Value("${account.jwt.private-key:}") String privateKey) {
        try {
            if (privateKey.isBlank()) {
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(2048);
                signer = new RSASSASigner(generator.generateKeyPair().getPrivate());
            } else {
                byte[] encoded = Base64.getDecoder().decode(privateKey);
                var key = (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(encoded));
                signer = new RSASSASigner(key);
            }
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Não foi possível carregar a chave RSA de assinatura do Account.", e);
        }
    }

    String issue(UUID userId, String role) {
        Instant issuedAt = Instant.now();
        var claims = new JWTClaimsSet.Builder()
                .issuer("account")
                .subject(userId.toString())
                .claim("role", role)
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(issuedAt.plusSeconds(EXPIRES_IN_SECONDS)))
                .build();
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        try {
            jwt.sign(signer);
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Não foi possível assinar o token do Account.", e);
        }
    }
}
