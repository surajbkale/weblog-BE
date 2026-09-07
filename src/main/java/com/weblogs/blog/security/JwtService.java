package com.weblogs.blog.security;

import com.weblogs.blog.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
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

    /**
     * Cached signing key. Decoded once at startup from the Base64 {@code jwt.secret}
     * property and reused for every token generation and validation call.
     * Re-creating it per-call (decode + HMAC key derivation) is wasteful under load.
     */
    private SecretKey signingKey;

    @PostConstruct
    private void initSigningKey() {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .claim("displayName", user.getDisplayName())
                .claim("active", user.isActive())
                .claim("emailVerified", user.isEmailVerified())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenExpiry)))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Validates the token and returns its claims.
     * Throws {@link io.jsonwebtoken.JwtException} if invalid or expired.
     */
    public Claims validateAndExtractClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
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

    /** Returns {@code false} when the account has been suspended by an admin. */
    public boolean extractActive(Claims claims) {
        Boolean active = claims.get("active", Boolean.class);
        return active == null || active; // treat missing claim as active (backward-compat)
    }

    /** Returns true if the user's email was verified at token issuance. */
    public boolean extractEmailVerified(Claims claims) {
        Boolean verified = claims.get("emailVerified", Boolean.class);
        return verified != null && verified; // treat missing claim as unverified (safe default)
    }

    public long getAccessTokenExpirySeconds() {
        return accessTokenExpiry.toSeconds();
    }
}
