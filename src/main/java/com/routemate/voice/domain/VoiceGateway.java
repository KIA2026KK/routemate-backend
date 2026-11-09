package com.routemate.voice.domain;
import java.time.Instant;
import java.util.*;
public interface VoiceGateway {
    Grant issue(UUID room,int capacity,UUID identity,Instant now);
    /** Network enumeration runs outside DB transactions; destructive changes require a fresh guard. */
    void reconcile(PermissionGuard permissions);
    interface PermissionGuard {
        boolean deleteIfEmpty(String room, Runnable deletion);
        void removeIfDenied(String room, String identity, Runnable removal);
    }
    record Grant(String url,String token,Instant expiresAt) {
        @Override public String toString(){return "Grant[token=<redacted>]";}
    }
}
