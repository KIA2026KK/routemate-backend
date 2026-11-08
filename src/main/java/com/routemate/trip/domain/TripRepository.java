package com.routemate.trip.domain;

import java.time.Instant;
import java.util.UUID;

public interface TripRepository {
    /** Atomically creates or updates the user's single active trip. */
    Trip register(UUID userId, UUID vehicleId, Destination destination, Instant now);
}
