package com.routemate.auth.application;

import com.routemate.auth.domain.*;
import com.routemate.vehicle.application.VehicleAccounts;
import com.routemate.vehicle.domain.*;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VehicleSessionLifecycle {
    private final AnonymousSessions sessions;
    private final UserSessionRepository credentials;
    private final RecoveryKeyRepository keys;
    private final VehicleAccounts vehicles;
    private final Clock clock;
    private final SecureRandom random=new SecureRandom();
    public VehicleSessionLifecycle(AnonymousSessions sessions, UserSessionRepository credentials, RecoveryKeyRepository keys,
        VehicleAccounts vehicles, Clock clock) {
        this.sessions=sessions; this.credentials=credentials; this.keys=keys; this.vehicles=vehicles; this.clock=clock;
    }
    private String secret() {
        byte[] bytes=new byte[32]; random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    private UUID authorizedUser(String authorization) {
        UUID user=sessions.authenticate(authorization);
        credentials.lockUser(user);
        // Recovery may have revoked this credential while the request waited for the lock.
        if(!sessions.authenticate(authorization).equals(user)) throw new AnonymousSessions.Unauthorized();
        return user;
    }
    @Transactional
    public Lease renew(String authorization) {
        var user=authorizedUser(authorization);
        var vehicle=vehicles.current(user);
        var now=clock.instant();
        var expiry=credentials.renew(AnonymousSessions.hash(authorization.substring(7)),now,now.plus(Duration.ofDays(30)));
        return new Lease(vehicle.id(),vehicle.vin().masked(),expiry);
    }
    @Transactional
    public IssuedRecoveryKey createKey(String authorization) {
        var user=authorizedUser(authorization);
        var vehicle=vehicles.current(user);
        var key=secret(); var now=clock.instant(); var expiry=now.plus(Duration.ofDays(365));
        keys.replace(user,AnonymousSessions.hash(key),now,expiry);
        return new IssuedRecoveryKey(vehicle.id(),vehicle.vin().masked(),key,expiry);
    }
    @Transactional
    public RestoredSession recover(Vin vin, VehicleAccount.Source source, String recoveryKey) {
        if(recoveryKey==null || !recoveryKey.matches("[A-Za-z0-9_-]{43}")) throw new AnonymousSessions.Unauthorized();
        var hash=AnonymousSessions.hash(recoveryKey);
        UUID user=keys.findUser(hash,vin,source,clock.instant()).orElseThrow(AnonymousSessions.Unauthorized::new);
        credentials.lockUser(user);
        if(!keys.findUser(hash,vin,source,clock.instant()).filter(user::equals).isPresent()) throw new AnonymousSessions.Unauthorized();
        var vehicle=vehicles.current(user);
        var token=secret(); var now=clock.instant(); var expiry=now.plus(Duration.ofDays(30));
        credentials.revokeAll(user,now);
        credentials.issue(user,AnonymousSessions.hash(token),now,expiry);
        return new RestoredSession(user,vehicle.id(),vehicle.vin().masked(),token,expiry);
    }
    public record Lease(UUID vehicleId,String maskedVin,Instant expiresAt) { }
    public record IssuedRecoveryKey(UUID vehicleId,String maskedVin,String recoveryKey,Instant expiresAt) {
        @Override public String toString(){return "IssuedRecoveryKey[recoveryKey=<redacted>]";}
    }
    public record RestoredSession(UUID userId,UUID vehicleId,String maskedVin,String accessToken,Instant expiresAt) {
        @Override public String toString(){return "RestoredSession[accessToken=<redacted>]";}
    }
}
