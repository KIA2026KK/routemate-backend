package com.routemate.trip.domain;

import java.time.Instant;
import java.util.UUID;

public record Trip(UUID id, UUID userId, Destination destination, Instant startedAt, Instant updatedAt) { }
