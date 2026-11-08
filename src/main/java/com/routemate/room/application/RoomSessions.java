package com.routemate.room.application;

import com.routemate.auth.application.AnonymousSessions;
import com.routemate.room.domain.*;
import com.routemate.trip.domain.Trip;
import com.routemate.vehicle.application.VehicleAccounts;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RoomSessions {
    private final RoomRepository rooms;
    private final AnonymousSessions sessions;
    private final VehicleAccounts vehicles;
    private final Clock clock;
    public RoomSessions(RoomRepository rooms,AnonymousSessions sessions,VehicleAccounts vehicles,Clock clock){
        this.rooms=rooms;this.sessions=sessions;this.vehicles=vehicles;this.clock=clock;
    }
    public UUID prepare(String auth){
        sessions.authenticate(auth);rooms.lock();
        UUID user=sessions.authenticate(auth);vehicles.current(user);rooms.expire(clock.instant());return user;
    }
    private Trip requireTrip(UUID user){return rooms.trip(user).orElseThrow(()->new RoomFailure("TRIP_REQUIRED"));}
    public Optional<Trip> currentTrip(String auth){return rooms.trip(prepare(auth));}
    public void endTrip(String auth){rooms.endTrip(prepare(auth),clock.instant());}
    public Optional<Room> current(String auth){return rooms.membership(prepare(auth)).flatMap(m->rooms.room(m.roomId()));}
    public List<RoomRepository.Nearby> nearby(String auth,int radiusKm){
        if(radiusKm!=5 && radiusKm!=10)throw new IllegalArgumentException("Radius must be 5 or 10");
        UUID user=prepare(auth);requireTrip(user);return rooms.nearby(user,radiusKm);
    }
    public Room create(String auth,String name,boolean isPublic,int capacity){
        Room.validate(name,capacity);UUID user=prepare(auth);var trip=requireTrip(user);
        var existing=rooms.membership(user);
        if(existing.isPresent()) {
            var room=rooms.room(existing.get().roomId()).orElseThrow();
            if(room.name().equals(name.trim())&&room.capacity()==capacity&&room.isPublic()==isPublic)return room;
            throw new RoomFailure("ALREADY_IN_ROOM");
        }
        return rooms.create(user,trip,name,isPublic,capacity,clock.instant());
    }
    public Room join(String auth,UUID id){
        UUID user=prepare(auth);requireTrip(user);var existing=rooms.membership(user);
        if(existing.isPresent()) {
            if(existing.get().roomId().equals(id))return rooms.room(id).orElseThrow();
            throw new RoomFailure("ALREADY_IN_ROOM");
        }
        var room=rooms.room(id).orElseThrow(()->new RoomFailure("ROOM_UNAVAILABLE"));
        if(room.participants()>=room.capacity())throw new RoomFailure("ROOM_FULL");
        rooms.join(user,id,clock.instant());return rooms.room(id).orElseThrow();
    }
    public void leave(String auth,UUID id){
        UUID user=prepare(auth);var existing=rooms.membership(user);
        if(existing.isPresent()&&!existing.get().roomId().equals(id))throw new RoomFailure("NOT_IN_ROOM");
        rooms.leave(user,clock.instant());
    }
    public Room heartbeat(String auth,UUID id){
        UUID user=prepare(auth);var member=rooms.membership(user).orElseThrow(()->new RoomFailure("NOT_IN_ROOM"));
        if(!member.roomId().equals(id))throw new RoomFailure("NOT_IN_ROOM");
        rooms.heartbeat(user,clock.instant());return rooms.room(id).orElseThrow();
    }
}
