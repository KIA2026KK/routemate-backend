package com.routemate.room.domain;

public class RoomFailure extends RuntimeException {
    public enum Reason { TRIP_REQUIRED, ALREADY_IN_ROOM, ROOM_UNAVAILABLE, ROOM_FULL, NOT_IN_ROOM, VOICE_UNAVAILABLE }
    public final Reason reason;
    public RoomFailure(String code) { super(code); this.reason=Reason.valueOf(code); }
}
