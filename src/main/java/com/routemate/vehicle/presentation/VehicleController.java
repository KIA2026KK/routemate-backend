package com.routemate.vehicle.presentation;

import com.routemate.auth.application.AnonymousSessions;
import com.routemate.vehicle.application.VehicleAccounts;
import com.routemate.vehicle.domain.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
class VehicleController {
    private final AnonymousSessions sessions;
    private final VehicleAccounts vehicles;
    VehicleController(AnonymousSessions sessions, VehicleAccounts vehicles) { this.sessions = sessions; this.vehicles = vehicles; }
    @PostMapping("/api/v1/vehicles/me")
    ResponseEntity<VehicleResponse> bind(@RequestHeader(value = "Authorization", required = false) String authorization,
        @Valid @RequestBody VehicleRequest request) {
        UUID userId = sessions.authenticate(authorization);
        var account = vehicles.bind(userId, new Vin(request.vin()), request.source(), request.vinAccessType());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new VehicleResponse(account.id(),
            account.vin().masked(), account.source(), account.vinAccessType(), false));
    }
    record VehicleRequest(@NotBlank @Size(max = 64) String vin, @NotNull VehicleAccount.Source source,
        VehicleAccount.VinAccess vinAccessType) {
        @Override public String toString() { return "VehicleRequest[vin=<redacted>, source=" + source + "]"; }
    }
    // Plain SDK values do not constitute signed ownership evidence.
    record VehicleResponse(UUID vehicleId, String maskedVin, VehicleAccount.Source source,
        VehicleAccount.VinAccess vinAccessType, boolean ownershipVerified) { }
}
