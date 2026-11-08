package com.routemate.trip.infrastructure;

import com.routemate.trip.domain.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcTripRepository implements TripRepository {
    private final JdbcTemplate jdbc;
    JdbcTripRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public Trip register(UUID userId, Destination destination, Instant now) {
        return jdbc.queryForObject("""
            insert into trip_sessions(id, user_id, destination_name, destination, started_at, updated_at)
            values (?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?, ?)
            on conflict (user_id) where ended_at is null do update
            set destination_name = excluded.destination_name, destination = excluded.destination,
                updated_at = greatest(trip_sessions.updated_at, excluded.updated_at)
            returning id, user_id, destination_name, ST_Y(destination::geometry) as latitude,
                ST_X(destination::geometry) as longitude, started_at, updated_at
            """, (rs, row) -> new Trip(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                new Destination(rs.getString("destination_name"), rs.getDouble("latitude"), rs.getDouble("longitude")),
                rs.getTimestamp("started_at").toInstant(), rs.getTimestamp("updated_at").toInstant()),
            UUID.randomUUID(), userId, destination.name(), destination.longitude(), destination.latitude(),
            Timestamp.from(now), Timestamp.from(now));
    }
}
