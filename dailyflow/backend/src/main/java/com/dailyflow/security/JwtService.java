package com.dailyflow.security;

import com.dailyflow.config.AppProperties;
import com.dailyflow.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies short-lived HS256 access tokens.
 * Token strategy: a single access token (default 60 minutes, JWT_EXPIRATION_MINUTES). No refresh tokens.
 * Logout and password change bump users.token_version, which invalidates every earlier token.
 */
@Service
public class JwtService {

    private static final String ISSUER = "dailyflow";
    private static final String TOKEN_VERSION_CLAIM = "tv";

    private final SecretKey key;
    private final Duration lifetime;
    private final Clock clock;

    public JwtService(AppProperties props, Clock clock) {
        String secret = props.jwt().secret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET must be set and at least 32 characters long. Generate one with: openssl rand -base64 48");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.lifetime = Duration.ofMinutes(props.jwt().expirationMinutes());
        this.clock = clock;
    }

    public long getExpirationSeconds() {
        return lifetime.toSeconds();
    }

    public String generateToken(User user) {
        Instant now = Instant.now(clock);
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(user.getId()))
                .claim(TOKEN_VERSION_CLAIM, user.getTokenVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(lifetime)))
                .signWith(key)
                .compact();
    }

    /** Verified token contents, or throws JwtException if the token is invalid or expired. */
    public ParsedToken parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(ISSUER)
                .clock(() -> Date.from(Instant.now(clock)))
                .build()
                .parseSignedClaims(token)
                .getPayload();
        try {
            Long userId = Long.valueOf(claims.getSubject());
            Integer version = claims.get(TOKEN_VERSION_CLAIM, Integer.class);
            return new ParsedToken(userId, version == null ? -1 : version);
        } catch (NumberFormatException ex) {
            throw new JwtException("Invalid subject");
        }
    }

    public record ParsedToken(Long userId, int tokenVersion) {
    }
}
