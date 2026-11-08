package com.routemate.voice.domain;
import java.time.Instant;
import java.util.*;
public interface VoiceGateway {
    Grant issue(UUID room,int capacity,UUID identity,Instant now);
    void reconcile(Map<String,Set<String>> allowed);
    record Grant(String url,String token,Instant expiresAt) {
        @Override public String toString(){return "Grant[token=<redacted>]";}
    }
}
