package com.routemate.auth.application;

import com.routemate.auth.domain.UserSessionRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnonymousSessions {
    private final UserSessionRepository sessions;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    public AnonymousSessions(UserSessionRepository sessions, Clock clock) {
        this.sessions = sessions; this.clock = clock;
    }

    @Transactional
    public IssuedSession create() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        Instant expiresAt = now.plus(Duration.ofDays(30));
        UUID userId = UUID.randomUUID();
        sessions.create(userId, hash(token), now, expiresAt);
        return new IssuedSession(userId, token, expiresAt);
    }

    @Transactional(readOnly = true)
    public UUID authenticate(String authorization) {
        if (authorization == null || !authorization.matches("(?i)Bearer [A-Za-z0-9_-]{43}")) {
            throw new Unauthorized();
        }
        return sessions.findUser(hash(authorization.substring(7)), clock.instant()).orElseThrow(Unauthorized::new);
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }

    public record IssuedSession(UUID userId, String accessToken, Instant expiresAt) {
        @Override public String toString() { return "IssuedSession[accessToken=<redacted>]"; }
    }
    public static class Unauthorized extends RuntimeException { }
}
