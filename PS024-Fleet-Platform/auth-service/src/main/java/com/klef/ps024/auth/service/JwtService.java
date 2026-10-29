package com.klef.ps024.auth.service;

import com.klef.ps024.auth.config.JwtProperties;
import com.klef.ps024.auth.entity.User;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final JwtProperties properties;

    public JwtService(SecretKey signingKey, JwtProperties properties) {
        this.signingKey = signingKey;
        this.properties = properties;
    }

    public String generateAccessToken(User user) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusMillis(properties.expirationMs());

        return Jwts.builder()
                .subject(user.getEmail())
                .claim("roles", List.of(user.getRole().name()))
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }
}
