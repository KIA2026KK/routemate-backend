package com.routemate.auth.infrastructure;

import com.routemate.auth.domain.UserSessionRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcUserSessionRepository implements UserSessionRepository {
    private final JdbcTemplate jdbc;
    JdbcUserSessionRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public void create(UUID userId, String tokenHash, Instant createdAt, Instant expiresAt) {
        jdbc.update("insert into users(id, created_at) values (?, ?)", userId, Timestamp.from(createdAt));
        issue(userId, tokenHash, createdAt, expiresAt);
    }
    @Override public void issue(UUID userId, String tokenHash, Instant createdAt, Instant expiresAt) {
        jdbc.update("insert into auth_sessions(token_hash, user_id, created_at, expires_at) values (?, ?, ?, ?)",
            tokenHash, userId, Timestamp.from(createdAt), Timestamp.from(expiresAt));
    }

    @Override public Optional<UUID> findUser(String tokenHash, Instant now) {
        return jdbc.query("select user_id from auth_sessions where token_hash = ? and expires_at > ? and revoked_at is null",
            (rs, row) -> rs.getObject("user_id", UUID.class), tokenHash, Timestamp.from(now)).stream().findFirst();
    }
    @Override public void lockUser(UUID userId) {
        jdbc.queryForObject("select id from users where id = ? for update", UUID.class, userId);
    }
    @Override public Instant renew(String tokenHash, Instant now, Instant expiresAt) {
        return jdbc.queryForObject("""
            update auth_sessions set expires_at = greatest(expires_at, ?)
            where token_hash = ? and revoked_at is null and expires_at > ? returning expires_at
            """, (rs,row)->rs.getTimestamp(1).toInstant(), Timestamp.from(expiresAt), tokenHash, Timestamp.from(now));
    }
    @Override public void revokeAll(UUID userId, Instant now) {
        jdbc.update("update auth_sessions set revoked_at = ? where user_id = ? and revoked_at is null", Timestamp.from(now), userId);
    }
}
