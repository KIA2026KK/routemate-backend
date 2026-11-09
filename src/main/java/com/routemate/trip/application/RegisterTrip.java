package com.routemate.trip.application;

import com.routemate.trip.domain.*;
import com.routemate.vehicle.application.VehicleAccounts;
import com.routemate.auth.application.AnonymousSessions;
import com.routemate.room.domain.RoomFailure;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegisterTrip {
    private final TripRepository trips;
    private final Clock clock;
    private final VehicleAccounts vehicles;
    private final com.routemate.room.domain.RoomRepository rooms;
    private final AnonymousSessions sessions;
    public RegisterTrip(TripRepository trips, Clock clock, VehicleAccounts vehicles, com.routemate.room.domain.RoomRepository rooms, AnonymousSessions sessions) {
        this.trips = trips; this.clock = clock; this.vehicles = vehicles; this.rooms=rooms; this.sessions=sessions;
    }
    @Transactional
    public Trip execute(String authorization, Destination destination, Precondition precondition) {
        sessions.authenticate(authorization);
        rooms.lock();rooms.expire(clock.instant());
        UUID userId = sessions.authenticateLocked(authorization);
        var vehicle = vehicles.current(userId);
        if(precondition != null) {
            var existing=rooms.trip(userId);
            if(existing.isPresent() ? !existing.get().id().equals(precondition.tripId()) || existing.get().revision()!=precondition.revision()
                : precondition.tripId()!=null || precondition.revision()!=0) throw new RoomFailure("TRIP_CHANGED");
        }
        return trips.register(userId, vehicle.id(), destination, clock.instant());
    }
    public record Precondition(UUID tripId,long revision) {
        public Precondition {
            if(revision<0 || (tripId==null ? revision!=0 : revision==0))throw new IllegalArgumentException("Invalid trip precondition");
        }
    }
}
