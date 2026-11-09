package com.routemate.trip.domain;

import java.time.Instant;
import java.util.UUID;

public record Trip(UUID id, UUID userId, UUID vehicleId, Destination destination, Instant startedAt, Instant updatedAt, long revision) { }
