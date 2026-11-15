package com.routemate.room.domain;

import com.routemate.trip.domain.Trip;
import java.time.Instant;
import java.util.*;

public interface RoomRepository {
    void lock();
    void expire(Instant now);
    Optional<Trip> trip(UUID user);
    void endTrip(UUID user, Instant now);
    Optional<Room> room(UUID id);
    Optional<UUID> roomByCode(RoomInviteCode code);
    Optional<Membership> membership(UUID user);
    boolean sameVehicleSource(UUID user, UUID room);
    Room create(UUID user, Trip trip, String name, boolean isPublic, int capacity, Instant now);
    void join(UUID user, UUID room, Instant now);
    void leave(UUID user, Instant now);
    void heartbeat(UUID user, Instant now);
    List<Nearby> nearby(UUID user, int radiusKm);
    record Membership(UUID id, UUID roomId) { }
    record Nearby(Room room, long distanceMeters) { }
}
