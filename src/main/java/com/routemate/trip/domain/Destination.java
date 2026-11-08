package com.routemate.trip.domain;

public record Destination(String name, double latitude, double longitude) {
    public Destination {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Destination name required");
        name = name.strip();
        if (name.length() > 120 || !Double.isFinite(latitude) || latitude < -90 || latitude > 90 ||
            !Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Invalid destination");
        }
    }
}
