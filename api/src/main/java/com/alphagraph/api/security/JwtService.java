package com.alphagraph.api.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;

/** Issues and validates the HS256 bearer tokens described in docs/004_API_Architecture.md §4. */
@Component
public class JwtService {

    public record AuthenticatedPrincipal(String email, String role) {
    }

    private final SecretKey key;
    private final long expiryMinutes;

    public JwtService(
        @Value("${alphagraph.security.jwt-secret}") String secret,
        @Value("${alphagraph.security.jwt-expiry-minutes:60}") long expiryMinutes
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiryMinutes = expiryMinutes;
    }

    public String issueToken(String subjectEmail, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(subjectEmail)
            .claim("role", role)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(expiryMinutes, ChronoUnit.MINUTES)))
            .signWith(key)
            .compact();
    }

    /** Never throws — an invalid/expired/tampered token just fails to authenticate. */
    public Optional<AuthenticatedPrincipal> validate(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return Optional.of(new AuthenticatedPrincipal(claims.getSubject(), claims.get("role", String.class)));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
