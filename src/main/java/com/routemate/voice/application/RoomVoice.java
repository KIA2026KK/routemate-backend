package com.routemate.voice.application;
import com.routemate.room.application.RoomSessions;
import com.routemate.room.domain.*;
import com.routemate.voice.domain.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class RoomVoice {
    private final RoomSessions sessions;
    private final RoomRepository rooms;
    private final VoiceLeaseRepository leases;
    private final VoiceGateway gateway;
    private final Clock clock;
    public RoomVoice(RoomSessions sessions,RoomRepository rooms,VoiceLeaseRepository leases,VoiceGateway gateway,Clock clock){
        this.sessions=sessions;this.rooms=rooms;this.leases=leases;this.gateway=gateway;this.clock=clock;
    }
    @Transactional public VoiceGateway.Grant issue(String auth,UUID roomId){
        UUID user=sessions.prepare(auth);
        var membership=rooms.membership(user).filter(m->m.roomId().equals(roomId)).orElseThrow(()->new RoomFailure("NOT_IN_ROOM"));
        var room=rooms.room(roomId).orElseThrow(()->new RoomFailure("ROOM_UNAVAILABLE"));
        try{
            var hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(auth.substring(7).getBytes(StandardCharsets.UTF_8)));
            var identity=leases.identity(membership.id(),hash);rooms.heartbeat(user,clock.instant());
            return gateway.issue(roomId,room.capacity(),identity,clock.instant());
        }catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    @Transactional public void maintain(){
        rooms.lock();rooms.expire(clock.instant());
        try { gateway.reconcile(leases.allowed(clock.instant())); }
        catch(RoomFailure ignored){org.slf4j.LoggerFactory.getLogger(RoomVoice.class).warn("Voice cleanup deferred; database expiry retained");}
    }
}
