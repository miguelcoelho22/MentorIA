package br.com.mentoria.account;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.text.ParseException;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.stereotype.Component;

@Component
class AccountTokenIssuer {

    static final long EXPIRES_IN_SECONDS = 3600;
    private static final String ISSUER = "account";

    private final RSASSASigner signer;
    private final RSASSAVerifier verifier;
    private final RSAKey publicKey;

    AccountTokenIssuer(@Value("${account.jwt.private-key:${ACCOUNT_JWT_PRIVATE_KEY:}}") String privateKey) {
        try {
            KeyFactory rsa = KeyFactory.getInstance("RSA");
            RSAPrivateCrtKey signingKey;
            if (privateKey.isBlank()) {
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(2048);
                signingKey = (RSAPrivateCrtKey) generator.generateKeyPair().getPrivate();
            } else {
                byte[] encoded = Base64.getDecoder().decode(privateKey.strip());
                if (!(rsa.generatePrivate(new PKCS8EncodedKeySpec(encoded)) instanceof RSAPrivateCrtKey key)) {
                    throw new IllegalArgumentException("A chave RSA precisa conter o expoente público.");
                }
                signingKey = key;
            }

            var verificationKey = (RSAPublicKey) rsa.generatePublic(
                    new RSAPublicKeySpec(signingKey.getModulus(), signingKey.getPublicExponent()));
            publicKey = new RSAKey.Builder(verificationKey)
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .keyIDFromThumbprint()
                    .build();
            signer = new RSASSASigner(signingKey);
            verifier = new RSASSAVerifier(verificationKey);
        } catch (GeneralSecurityException | JOSEException | IllegalArgumentException e) {
            throw new IllegalStateException("Não foi possível carregar a chave RSA de assinatura do Account.", e);
        }
    }

    JWKSet publicKeys() {
        return new JWKSet(publicKey);
    }

    String issue(UUID userId, String role) {
        Instant issuedAt = Instant.now();
        var claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject(userId.toString())
                .claim("role", role)
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(issuedAt.plusSeconds(EXPIRES_IN_SECONDS)))
                .build();
        var header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(publicKey.getKeyID()).build();
        var jwt = new SignedJWT(header, claims);
        try {
            jwt.sign(signer);
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Não foi possível assinar o token do Account.", e);
        }
    }

    UUID verifiedSubject(String token) {
        try {
            var jwt = SignedJWT.parse(token);
            var header = jwt.getHeader();
            if (!JWSAlgorithm.RS256.equals(header.getAlgorithm())
                    || (header.getKeyID() != null && !publicKey.getKeyID().equals(header.getKeyID()))
                    || !jwt.verify(verifier)) {
                throw new BadJwtException("Assinatura do token inválida.");
            }

            var claims = jwt.getJWTClaimsSet();
            var now = new Date();
            if (!ISSUER.equals(claims.getIssuer()) || claims.getSubject() == null
                    || claims.getExpirationTime() == null || !claims.getExpirationTime().after(now)
                    || (claims.getNotBeforeTime() != null && claims.getNotBeforeTime().after(now))) {
                throw new BadJwtException("Claims do token inválidas.");
            }
            return UUID.fromString(claims.getSubject());
        } catch (ParseException | JOSEException | IllegalArgumentException e) {
            throw new BadJwtException("Token inválido.", e);
        }
    }
}
