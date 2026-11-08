package com.routemate.auth.presentation;

import com.routemate.auth.application.VehicleSessionLifecycle;
import com.routemate.vehicle.domain.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
class VehicleSessionController {
    private final VehicleSessionLifecycle lifecycle;
    VehicleSessionController(VehicleSessionLifecycle lifecycle){this.lifecycle=lifecycle;}
    @PostMapping("/api/v1/auth/device-sessions/renew")
    ResponseEntity<VehicleSessionLifecycle.Lease> renew(@RequestHeader(value="Authorization",required=false) String authorization){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(lifecycle.renew(authorization));
    }
    @PostMapping("/api/v1/auth/recovery-keys")
    ResponseEntity<VehicleSessionLifecycle.IssuedRecoveryKey> createKey(@RequestHeader(value="Authorization",required=false) String authorization){
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(lifecycle.createKey(authorization));
    }
    @PostMapping("/api/v1/auth/device-sessions/recover")
    ResponseEntity<VehicleSessionLifecycle.RestoredSession> recover(@Valid @RequestBody RecoveryRequest request){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(lifecycle.recover(new Vin(request.vin()),request.source(),request.recoveryKey()));
    }
    record RecoveryRequest(@NotBlank @Size(max=64) String vin,@NotNull VehicleAccount.Source source,
        @Size(max=256) String recoveryKey){
        @Override public String toString(){return "RecoveryRequest[vin=<redacted>, recoveryKey=<redacted>]";}
    }
}
