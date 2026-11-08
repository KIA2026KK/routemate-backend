package com.routemate.auth.infrastructure;

import com.routemate.auth.domain.RecoveryKeyRepository;
import com.routemate.vehicle.domain.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcRecoveryKeyRepository implements RecoveryKeyRepository {
    private final JdbcTemplate jdbc;
    JdbcRecoveryKeyRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    @Override public void replace(UUID userId, String keyHash, Instant now, Instant expiresAt) {
        jdbc.update("""
            insert into vehicle_recovery_keys(user_id,key_hash,created_at,expires_at) values (?,?,?,?)
            on conflict(user_id) do update set key_hash=excluded.key_hash,created_at=excluded.created_at,expires_at=excluded.expires_at
            """,userId,keyHash,Timestamp.from(now),Timestamp.from(expiresAt));
    }
    @Override public Optional<UUID> findUser(String keyHash, Vin vin, VehicleAccount.Source source, Instant now) {
        return jdbc.query("""
            select k.user_id from vehicle_recovery_keys k join vehicles v on v.user_id=k.user_id
            where k.key_hash=? and v.vin=? and v.source=? and k.expires_at>?
            """,(rs,row)->rs.getObject(1,UUID.class),keyHash,vin.value(),source.name(),Timestamp.from(now)).stream().findFirst();
    }
}
