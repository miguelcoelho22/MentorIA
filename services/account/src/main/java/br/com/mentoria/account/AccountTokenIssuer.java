package br.com.mentoria.account;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@DependsOnDatabaseInitialization
class AccountTokenIssuer {

    static final long EXPIRES_IN_SECONDS = 3600;

    private final RSASSASigner signer;
    private final JWSHeader header;
    private final Map<String, Object> publicJwks;

    AccountTokenIssuer(JdbcTemplate jdbc, @Value("${account.jwt.private-key:}") String privateKey) {
        try {
            String encodedKey = privateKey.isBlank() ? loadOrCreateKey(jdbc) : privateKey.strip();
            byte[] encoded = Base64.getDecoder().decode(encodedKey);
            var key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(encoded));
            if (!(key instanceof RSAPrivateCrtKey rsa)) {
                throw new IllegalArgumentException("A chave RSA PKCS#8 deve incluir o expoente público.");
            }
            signer = new RSASSASigner(rsa);
            var publicKey = new RSAKey.Builder(Base64URL.encode(rsa.getModulus()), Base64URL.encode(rsa.getPublicExponent()))
                    .algorithm(JWSAlgorithm.RS256)
                    .keyUse(KeyUse.SIGNATURE)
                    .keyIDFromThumbprint()
                    .build();
            header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(publicKey.getKeyID()).build();
            publicJwks = new JWKSet(publicKey).toJSONObject();
        } catch (GeneralSecurityException | IllegalArgumentException | JOSEException e) {
            throw new IllegalStateException("Não foi possível carregar a chave RSA de assinatura do Account.", e);
        }
    }

    Map<String, Object> publicJwks() {
        return publicJwks;
    }

    private static String loadOrCreateKey(JdbcTemplate jdbc) throws GeneralSecurityException {
        String selectKey = "SELECT private_key_pkcs8 FROM account_signing_key WHERE id = 1";
        String storedKey = jdbc.query(selectKey, rows -> rows.next() ? rows.getString(1) : null);
        if (storedKey != null) {
            return storedKey;
        }

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        String generatedKey = Base64.getEncoder().encodeToString(generator.generateKeyPair().getPrivate().getEncoded());
        jdbc.update("""
                INSERT INTO account_signing_key (id, private_key_pkcs8) VALUES (1, ?)
                ON CONFLICT DO NOTHING
                """, generatedKey);
        return jdbc.queryForObject(selectKey, String.class);
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
        var jwt = new SignedJWT(header, claims);
        try {
            jwt.sign(signer);
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Não foi possível assinar o token do Account.", e);
        }
    }
}
