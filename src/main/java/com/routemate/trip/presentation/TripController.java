package com.routemate.trip.presentation;

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
    private final RegisterTrip register;
    TripController(RegisterTrip register) { this.register = register; }

    @PostMapping("/api/v1/trips")
    ResponseEntity<TripResponse> register(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @Valid @RequestBody TripRequest request
    ) {
        var input = request.destination();
        var expected=request.precondition();
        var trip = register.execute(authorization, new Destination(input.name(), input.latitude(), input.longitude()),
            expected==null?null:new RegisterTrip.Precondition(expected.tripId(),expected.revision()));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
            new TripResponse(trip.id(), trip.vehicleId(), trip.destination(), "ACTIVE", trip.startedAt(), trip.updatedAt(),trip.revision()));
    }
    record TripRequest(@NotNull @Valid DestinationRequest destination,@Valid PreconditionRequest precondition) { }
    record PreconditionRequest(UUID tripId,@NotNull @Min(0) Long revision) { }
    record DestinationRequest(@NotBlank @Size(max = 120) String name,
        @NotNull Double latitude, @NotNull Double longitude) { }
    record TripResponse(UUID tripId, UUID vehicleId, Destination destination, String status, Instant startedAt, Instant updatedAt,long revision) { }
}
