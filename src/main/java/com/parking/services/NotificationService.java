package com.parking.services;

import com.parking.enums.NotificationType;
import com.parking.model.NotificationPreference;
import com.parking.model.User;
import com.parking.persistence.PersistenceStore;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Persisted event-driven notification backbone. Domain services publish outcomes
 * through {@link #publish}, which filters by the recipient's stored preference
 * and persists one row per delivered event. All writes go through
 * {@link PersistenceStore#inTransaction}, and unread listing plus mark-read are
 * scoped to the requesting user so recipients are isolated at the service layer.
 */
public final class NotificationService {
    public record Notification(String notificationId, String recipientId, NotificationType type,
                               String message, LocalDateTime createdAt, boolean unread) { }

    private final PersistenceStore store;

    public NotificationService(PersistenceStore store) {
        if (store == null) {
            throw new IllegalArgumentException("store cannot be null");
        }
        this.store = store;
    }

    public void publish(String recipientId, NotificationType type, String message) {
        if (recipientId == null || recipientId.isBlank()) {
            throw new IllegalArgumentException("recipientId cannot be null or blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("type cannot be null");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message cannot be null or blank");
        }
        NotificationPreference pref = loadPreference(recipientId);
        if (pref != null && !pref.enabledTypes().contains(type)) {
            return;
        }
        String trimmed = message.trim();
        try {
            store.inTransaction(() -> {
                insertNotification(recipientId, type, trimmed);
                return null;
            });
        } catch (Exception failure) {
            throw new IllegalStateException("Could not publish notification", failure);
        }
    }

    private void insertNotification(String recipientId, NotificationType type, String message) {
        store.insertNotification(UUID.randomUUID().toString(), recipientId, type.name(), message,
                LocalDateTime.now().toString());
    }

    public List<Notification> listUnread(User actor) {
        requireActor(actor);
        List<Notification> result = new ArrayList<>();
        for (PersistenceStore.NotificationRow row : store.loadUnreadNotifications(actor.getUserId())) {
            result.add(new Notification(row.notificationId(), row.recipientId(), NotificationType.valueOf(row.type()),
                    row.message(), LocalDateTime.parse(row.createdAt()), row.unread()));
        }
        return result;
    }

    public void markAllRead(User actor) {
        requireActor(actor);
        try {
            store.inTransaction(() -> {
                store.markNotificationsRead(actor.getUserId());
                return null;
            });
        } catch (Exception failure) {
            throw new IllegalStateException("Could not mark notifications as read", failure);
        }
    }

    public void clear(User actor) {
        requireActor(actor);
        try {
            store.inTransaction(() -> {
                store.deleteNotifications(actor.getUserId());
                return null;
            });
        } catch (Exception failure) {
            throw new IllegalStateException("Could not clear notifications", failure);
        }
    }

    public void savePreference(NotificationPreference preference) {
        if (preference == null) {
            throw new IllegalArgumentException("preference cannot be null");
        }
        String encoded = preference.enabledTypes().stream()
                .map(NotificationType::name)
                .sorted()
                .collect(Collectors.joining(","));
        try {
            store.inTransaction(() -> {
                store.saveNotificationPreference(preference.userId(), encoded, preference.frequency(),
                        preference.soundEnabled());
                return null;
            });
        } catch (Exception failure) {
            throw new IllegalStateException("Could not save notification preference", failure);
        }
    }

    public NotificationPreference loadPreference(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId cannot be null or blank");
        }
        return store.loadNotificationPreference(userId)
                .map(row -> new NotificationPreference(row.userId(), decodeTypes(row.enabledTypes()),
                        row.frequency(), row.soundEnabled()))
                .orElse(null);
    }

    private static Set<NotificationType> decodeTypes(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return EnumSet.noneOf(NotificationType.class);
        }
        return Arrays.stream(encoded.split(","))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .map(NotificationType::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(NotificationType.class)));
    }

    private static void requireActor(User actor) {
        if (actor == null) {
            throw new IllegalArgumentException("actor cannot be null");
        }
        if (actor.getUserId() == null || actor.getUserId().isBlank()) {
            throw new IllegalArgumentException("actor userId cannot be null or blank");
        }
    }
}
