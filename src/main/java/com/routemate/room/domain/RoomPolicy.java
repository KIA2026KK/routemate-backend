package com.routemate.room.domain;

import java.time.Duration;

/** Lifecycle limits shared by room orchestration and persistence. */
public final class RoomPolicy {
    public static final Duration PARTICIPANT_LEASE = Duration.ofSeconds(90);
    public static final Duration ROOM_IDLE_TIMEOUT = Duration.ofHours(2);
    public static final Duration ROOM_RENEWAL_WINDOW = Duration.ofHours(1);
    private RoomPolicy() { }
    public static void validateRadius(int radiusKm) {
        if (radiusKm != 3 && radiusKm != 5) throw new IllegalArgumentException("Radius must be 3 or 5");
    }
}
