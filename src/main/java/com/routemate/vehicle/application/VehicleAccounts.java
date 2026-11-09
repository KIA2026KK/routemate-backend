package com.routemate.vehicle.application;

import com.routemate.vehicle.domain.*;
import java.time.Clock;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VehicleAccounts {
    private final VehicleRepository vehicles;
    private final Clock clock;
    private final boolean allowMock;
    private final com.routemate.auth.application.AnonymousSessions sessions;
    public VehicleAccounts(VehicleRepository vehicles, Clock clock,
        @Value("${routemate.vehicle.allow-mock:false}") boolean allowMock,
        com.routemate.auth.application.AnonymousSessions sessions) {
        this.vehicles = vehicles; this.clock = clock; this.allowMock = allowMock; this.sessions=sessions;
    }
    private void checkSource(VehicleAccount.Source source) {
        if (source == VehicleAccount.Source.MOCK && !allowMock) throw new VehicleRepository.MockDisabled();
    }
    @Transactional
    public VehicleAccount bindAuthenticated(String authorization,Vin vin,VehicleAccount.Source source,VehicleAccount.VinAccess access) {
        return bind(sessions.authenticateLocked(authorization),vin,source,access);
    }
    @Transactional
    public VehicleAccount bind(UUID userId, Vin vin, VehicleAccount.Source source, VehicleAccount.VinAccess access) {
        checkSource(source);
        if ((source == VehicleAccount.Source.PLEOS_SDK && access == null) ||
            (source == VehicleAccount.Source.MOCK && access != null)) throw new IllegalArgumentException("Invalid VIN capability");
        return vehicles.bind(userId, vin, source, access, clock.instant());
    }
    @Transactional(readOnly = true)
    public VehicleAccount current(UUID userId) {
        var vehicle = vehicles.findByUser(userId).orElseThrow(VehicleRepository.Required::new);
        checkSource(vehicle.source());
        return vehicle;
    }
}
