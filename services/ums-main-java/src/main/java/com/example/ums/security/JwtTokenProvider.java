package com.example.ums.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

@Component
public class JwtTokenProvider {
    private final SecretKey key;
    private final long ttlMillis;

    public JwtTokenProvider(
            @Value("${ums.security.jwt.secret:dev-only-secret-change-me-in-production-32bytes}") String secret,
            @Value("${ums.security.jwt.ttl-minutes:480}") long ttlMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlMillis = ttlMinutes * 60_000;
    }

    public String generate(UserPrincipal p) {
        Date now = new Date();
        return Jwts.builder()
                .subject(p.userId())
                .claim("username", p.username())
                .claim("displayName", p.displayName())
                .claim("roles", p.roles())
                .claim("provider", p.provider())
                .claim("externalId", p.externalId())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMillis))
                .signWith(key)
                .compact();
    }

    @SuppressWarnings("unchecked")
    public UserPrincipal parseAndValidate(String jwt) throws JwtException {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(jwt).getPayload();
        return new UserPrincipal(
            claims.getSubject(),
            claims.get("username", String.class),
            claims.get("displayName", String.class),
            claims.get("roles", List.class),
            claims.get("provider", String.class),
            claims.get("externalId", String.class)
        );
    }
}
