package com.routemate.trip.presentation;

import com.routemate.auth.application.AnonymousSessions;
import com.routemate.trip.application.RegisterTrip;
import com.routemate.trip.domain.Destination;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
class TripController {
    private final AnonymousSessions sessions;
    private final RegisterTrip register;
    TripController(AnonymousSessions sessions, RegisterTrip register) { this.sessions = sessions; this.register = register; }

    @PostMapping("/api/v1/trips")
    ResponseEntity<TripResponse> register(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @Valid @RequestBody TripRequest request
    ) {
        UUID userId = sessions.authenticate(authorization);
        var input = request.destination();
        var trip = register.execute(userId, new Destination(input.name(), input.latitude(), input.longitude()));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
            new TripResponse(trip.id(), trip.vehicleId(), trip.destination(), "ACTIVE", trip.startedAt(), trip.updatedAt()));
    }
    record TripRequest(@NotNull @Valid DestinationRequest destination) { }
    record DestinationRequest(@NotBlank @Size(max = 120) String name,
        @NotNull Double latitude, @NotNull Double longitude) { }
    record TripResponse(UUID tripId, UUID vehicleId, Destination destination, String status, Instant startedAt, Instant updatedAt) { }
}
