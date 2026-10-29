package com.klef.ps024.gateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Configuration
public class JwtConfiguration {

    @Bean
    public SecretKey jwtSigningKey(@Value("${security.jwt.secret}") String secret) {
        final byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("JWT_SECRET must be a valid Base64-encoded key.", exception);
        }

        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must decode to at least 32 bytes for HS256.");
        }

        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    @Bean
    public ReactiveJwtDecoder jwtDecoder(SecretKey jwtSigningKey) {
        return NimbusReactiveJwtDecoder.withSecretKey(jwtSigningKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }
}
