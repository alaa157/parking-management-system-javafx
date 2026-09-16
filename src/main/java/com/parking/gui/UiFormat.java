package com.parking.gui;

import com.parking.enums.SpotStatus;
import com.parking.enums.SpotType;

import java.util.Locale;

public final class UiFormat {
    private UiFormat() {
    }

    public static String money(double amount) {
        return String.format(Locale.US, "$%.2f", amount);
    }

    public static String initials(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "U";
        }
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, 1).toUpperCase(Locale.ROOT);
        }
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1))
                .toUpperCase(Locale.ROOT);
    }

    public static String prettyType(SpotType type) {
        return switch (type) {
            case EV_CHARGING -> "EV Charging";
            case HANDICAPPED -> "Handicapped";
            case COMPACT -> "Compact";
            case LARGE -> "Large";
            case MOTORCYCLE -> "Motorcycle";
            case RESERVED -> "Reserved";
            default -> "Standard";
        };
    }

    public static String statusText(SpotStatus status) {
        return switch (status) {
            case UNDER_MAINTENANCE -> "Maintenance";
            case OUT_OF_SERVICE -> "Out of Service";
            case RESERVED -> "Reserved";
            default -> status.name().substring(0, 1)
                    + status.name().substring(1).toLowerCase(Locale.ROOT);
        };
    }
}
