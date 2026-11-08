package com.routemate.vehicle.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface VehicleRepository {
    VehicleAccount bind(UUID userId, Vin vin, VehicleAccount.Source source, VehicleAccount.VinAccess access, Instant now);
    Optional<VehicleAccount> findByUser(UUID userId);
    class Conflict extends RuntimeException { }
    class Required extends RuntimeException { }
    class MockDisabled extends RuntimeException { }
}
