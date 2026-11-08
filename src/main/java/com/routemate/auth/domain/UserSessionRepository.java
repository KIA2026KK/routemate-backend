package com.routemate.auth.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserSessionRepository {
    void create(UUID userId, String tokenHash, Instant createdAt, Instant expiresAt);
    Optional<UUID> findUser(String tokenHash, Instant now);
    void lockUser(UUID userId);
    void issue(UUID userId, String tokenHash, Instant createdAt, Instant expiresAt);
    Instant renew(String tokenHash, Instant now, Instant expiresAt);
    void revokeAll(UUID userId, Instant now);
}
