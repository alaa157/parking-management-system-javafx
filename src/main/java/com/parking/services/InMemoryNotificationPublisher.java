package com.parking.services;

import com.parking.interfaces.NotificationPublisher;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Deterministic notification implementation for desktop runtime and tests. */
public final class InMemoryNotificationPublisher implements NotificationPublisher {
    public record Notification(String recipientId, String type, String message, LocalDateTime createdAt) { }
    private final List<Notification> notifications = new ArrayList<>();

    @Override public synchronized void publish(String recipientId, String type, String message) {
        if (message == null || message.isBlank()) return;
        notifications.add(new Notification(recipientId, type, message, LocalDateTime.now()));
    }

    public synchronized List<Notification> getNotifications() { return List.copyOf(notifications); }
}
