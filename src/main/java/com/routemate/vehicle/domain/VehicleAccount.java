package com.routemate.vehicle.domain;

import java.util.UUID;

public record VehicleAccount(UUID id, UUID userId, Vin vin, Source source, VinAccess vinAccessType) {
    public enum Source { PLEOS_SDK, MOCK }
    public enum VinAccess { READ, READ_WRITE }
}
