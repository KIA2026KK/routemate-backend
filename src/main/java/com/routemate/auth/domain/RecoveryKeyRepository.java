package com.routemate.auth.domain;

import com.routemate.vehicle.domain.*;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RecoveryKeyRepository {
    void replace(UUID userId, String keyHash, Instant now, Instant expiresAt);
    Optional<UUID> findUser(String keyHash, Vin vin, VehicleAccount.Source source, Instant now);
}
