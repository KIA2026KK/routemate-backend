package com.routemate.room.domain;

import java.time.Instant;
import java.util.UUID;

/** Public room representation deliberately excludes destination coordinates and VIN. */
public record Room(UUID id, String name, boolean isPublic, int capacity, int participants, Instant expiresAt, String inviteCode) {
    public static void validate(String name, int capacity) {
        if(name == null || name.isBlank() || name.length()>60 || capacity<2 || capacity>8)
            throw new IllegalArgumentException("Invalid room settings");
    }
}
