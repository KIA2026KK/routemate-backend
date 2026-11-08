package com.routemate.vehicle.domain;

import java.util.Locale;

public record Vin(String value) {
    public Vin {
        if (value == null) throw new IllegalArgumentException("VIN required");
        value = value.strip().toUpperCase(Locale.ROOT);
        if (!value.matches("[A-HJ-NPR-Z0-9]{17}") || value.equals("00000000000000000")) {
            throw new IllegalArgumentException("Invalid VIN");
        }
    }
    public String masked() { return "*************" + value.substring(13); }
    @Override public String toString() { return "Vin[" + masked() + "]"; }
}
