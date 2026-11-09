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

/** Short authorization transactions are separate from LiveKit enumeration and room creation IO. */
@Service
public class VoiceAuthorizations implements VoiceGateway.PermissionGuard {
    private final RoomSessions sessions;
    private final RoomRepository rooms;
    private final VoiceLeaseRepository leases;
    private final Clock clock;
    public VoiceAuthorizations(RoomSessions sessions,RoomRepository rooms,VoiceLeaseRepository leases,Clock clock){
        this.sessions=sessions;this.rooms=rooms;this.leases=leases;this.clock=clock;
    }
    @Transactional
    public Plan prepare(String auth,UUID roomId){
        UUID user=sessions.prepare(auth);
        var membership=rooms.membership(user).filter(m->m.roomId().equals(roomId)).orElseThrow(()->new RoomFailure("NOT_IN_ROOM"));
        var room=rooms.room(roomId).orElseThrow(()->new RoomFailure("ROOM_UNAVAILABLE"));
        try{
            var hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(auth.substring(7).getBytes(StandardCharsets.UTF_8)));
            var identity=leases.identity(membership.id(),hash);
            rooms.heartbeat(user,clock.instant());
            return new Plan(roomId,room.capacity(),identity);
        }catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    @Transactional
    public void confirm(String auth,Plan plan){
        UUID user=sessions.prepare(auth);
        if(rooms.membership(user).filter(m->m.roomId().equals(plan.roomId())).isEmpty() ||
            !allowed(plan.roomName()).contains(plan.identity().toString()))throw new RoomFailure("NOT_IN_ROOM");
    }
    @Transactional
    public void expire(){rooms.lock();rooms.expire(clock.instant());}
    private Set<String> allowed(String room){return leases.allowed(clock.instant()).getOrDefault(room,Set.of());}
    @Override @Transactional
    public boolean deleteIfEmpty(String room,Runnable deletion){
        rooms.lock();rooms.expire(clock.instant());
        if(!allowed(room).isEmpty())return false;
        // Lock only this destructive HTTP request, not list/enumeration IO. A token cannot
        // be authorized between this check and deletion, then immediately lose its room.
        deletion.run();return true;
    }
    @Override @Transactional
    public void removeIfDenied(String room,String identity,Runnable removal){
        rooms.lock();rooms.expire(clock.instant());
        if(!allowed(room).contains(identity))removal.run();
    }
    public record Plan(UUID roomId,int capacity,UUID identity){public String roomName(){return "rmv-"+roomId;}}
}
