package com.parking.model;

import com.parking.enums.ReservationStatus;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Durable record of one parking-spot reservation hold owned by exactly one user.
 */
public record Reservation(String reservationId, String userId, String spotId,
                          LocalDateTime createdAt, LocalDateTime expiresAt, ReservationStatus status) {
    public Reservation {
        if (reservationId == null || reservationId.isBlank()) {
            throw new IllegalArgumentException("reservationId cannot be null or blank");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId cannot be null or blank");
        }
        if (spotId == null || spotId.isBlank()) {
            throw new IllegalArgumentException("spotId cannot be null or blank");
        }
        Objects.requireNonNull(createdAt, "createdAt cannot be null");
        Objects.requireNonNull(expiresAt, "expiresAt cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt must be after createdAt");
        }
    }
}
