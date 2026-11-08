package com.routemate.trip.application;

import com.routemate.trip.domain.*;
import com.routemate.vehicle.application.VehicleAccounts;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegisterTrip {
    private final TripRepository trips;
    private final Clock clock;
    private final VehicleAccounts vehicles;
    public RegisterTrip(TripRepository trips, Clock clock, VehicleAccounts vehicles) {
        this.trips = trips; this.clock = clock; this.vehicles = vehicles;
    }
    @Transactional
    public Trip execute(UUID userId, Destination destination) {
        var vehicle = vehicles.current(userId);
        return trips.register(userId, vehicle.id(), destination, clock.instant());
    }
}
