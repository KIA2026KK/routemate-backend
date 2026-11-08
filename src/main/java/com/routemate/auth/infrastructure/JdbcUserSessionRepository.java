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
        jdbc.update("insert into auth_sessions(token_hash, user_id, created_at, expires_at) values (?, ?, ?, ?)",
            tokenHash, userId, Timestamp.from(createdAt), Timestamp.from(expiresAt));
    }

    @Override public Optional<UUID> findUser(String tokenHash, Instant now) {
        return jdbc.query("select user_id from auth_sessions where token_hash = ? and expires_at > ?",
            (rs, row) -> rs.getObject("user_id", UUID.class), tokenHash, Timestamp.from(now)).stream().findFirst();
    }
}
