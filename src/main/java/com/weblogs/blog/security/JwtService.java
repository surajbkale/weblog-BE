package com.weblogs.blog.security;

import com.weblogs.blog.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiry}")
    private Duration accessTokenExpiry;

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .claim("displayName", user.getDisplayName())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenExpiry)))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Validates the token and returns its claims.
     * Throws {@link io.jsonwebtoken.JwtException} if invalid or expired.
     */
    public Claims validateAndExtractClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public UUID extractUserId(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }

    public String extractEmail(Claims claims) {
        return claims.get("email", String.class);
    }

    public String extractRole(Claims claims) {
        return claims.get("role", String.class);
    }

    public long getAccessTokenExpirySeconds() {
        return accessTokenExpiry.toSeconds();
    }
}
