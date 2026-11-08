package com.routemate.trip.application;

import com.routemate.trip.domain.*;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegisterTrip {
    private final TripRepository trips;
    private final Clock clock;
    public RegisterTrip(TripRepository trips, Clock clock) { this.trips = trips; this.clock = clock; }
    @Transactional
    public Trip execute(UUID userId, Destination destination) { return trips.register(userId, destination, clock.instant()); }
}
