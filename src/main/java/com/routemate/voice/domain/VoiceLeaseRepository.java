package com.routemate.voice.domain;
import java.time.Instant;
import java.util.*;
public interface VoiceLeaseRepository {
    UUID identity(UUID membership,String credentialHash);
    Map<String,Set<String>> allowed(Instant now);
}
