package com.routemate.vehicle.infrastructure;

import com.routemate.vehicle.domain.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcVehicleRepository implements VehicleRepository {
    private final JdbcTemplate jdbc;
    JdbcVehicleRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    private VehicleAccount map(ResultSet rs, int row) throws SQLException {
        String access = rs.getString("vin_access_type");
        return new VehicleAccount(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
            new Vin(rs.getString("vin")), VehicleAccount.Source.valueOf(rs.getString("source")),
            access == null ? null : VehicleAccount.VinAccess.valueOf(access));
    }
    @Override public VehicleAccount bind(UUID userId, Vin vin, VehicleAccount.Source source,
        VehicleAccount.VinAccess access, Instant now) {
        try {
            return jdbc.query("""
                insert into vehicles(id, user_id, vin, source, vin_access_type, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?)
                on conflict (user_id) do update
                set vin_access_type = excluded.vin_access_type, updated_at = greatest(vehicles.updated_at, excluded.updated_at)
                where vehicles.vin = excluded.vin and vehicles.source = excluded.source
                returning *
                """, this::map, UUID.randomUUID(), userId, vin.value(), source.name(), access == null ? null : access.name(),
                Timestamp.from(now), Timestamp.from(now)).stream().findFirst().orElseThrow(VehicleRepository.Conflict::new);
        } catch (DuplicateKeyException ignored) { throw new VehicleRepository.Conflict(); }
    }
    @Override public Optional<VehicleAccount> findByUser(UUID userId) {
        return jdbc.query("select * from vehicles where user_id = ?", this::map, userId).stream().findFirst();
    }
}
