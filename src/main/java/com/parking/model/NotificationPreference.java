package com.parking.model;

import com.parking.enums.NotificationType;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Per-user notification preference persisted in the notification backbone.
 */
public record NotificationPreference(String userId, Set<NotificationType> enabledTypes, String frequency, boolean soundEnabled) {
    public NotificationPreference {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId cannot be null or blank");
        }
        if (enabledTypes == null) {
            throw new IllegalArgumentException("enabledTypes cannot be null");
        }
        if (frequency == null || frequency.isBlank()) {
            throw new IllegalArgumentException("frequency cannot be null or blank");
        }
        enabledTypes = Collections.unmodifiableSet(EnumSet.copyOf(enabledTypes.isEmpty() ? EnumSet.noneOf(NotificationType.class) : EnumSet.copyOf(enabledTypes)));
    }
}
