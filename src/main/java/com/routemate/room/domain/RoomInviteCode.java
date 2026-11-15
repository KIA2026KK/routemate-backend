package com.routemate.room.domain;

import java.util.Locale;

public record RoomInviteCode(String value) {
    public RoomInviteCode {
        if (value == null) throw new IllegalArgumentException("Invitation required");
        value = value.trim().toUpperCase(Locale.ROOT);
        if (!value.matches("[A-Z0-9]{8,32}")) throw new IllegalArgumentException("Invalid invitation");
    }
}
