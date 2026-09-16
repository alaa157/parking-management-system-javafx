package com.parking.interfaces;

/** Domain notification boundary; UI adapters can subscribe without owning business rules. */
public interface NotificationPublisher {
    void publish(String recipientId, String type, String message);
}
