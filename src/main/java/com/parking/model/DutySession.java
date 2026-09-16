package com.parking.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Durable record of one attendant duty session owned by exactly one user,
 * from shift start through closeout. Status is {@code OPEN} while the
 * attendant is on duty and {@code CLOSED} once ended.
 */
public record DutySession(String sessionId, String attendantId, String shift, String zone,
                          LocalDateTime startedAt, LocalDateTime endedAt, String status) {
    public DutySession {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId cannot be null or blank");
        }
        if (attendantId == null || attendantId.isBlank()) {
            throw new IllegalArgumentException("attendantId cannot be null or blank");
        }
        if (shift == null || shift.isBlank()) {
            throw new IllegalArgumentException("shift cannot be null or blank");
        }
        if (zone == null || zone.isBlank()) {
            throw new IllegalArgumentException("zone cannot be null or blank");
        }
        Objects.requireNonNull(startedAt, "startedAt cannot be null");
        if (status == null || !(status.equals("OPEN") || status.equals("CLOSED"))) {
            throw new IllegalArgumentException("status must be OPEN or CLOSED");
        }
        if (status.equals("OPEN") && endedAt != null) {
            throw new IllegalArgumentException("an open duty session cannot have endedAt");
        }
        if (status.equals("CLOSED")) {
            Objects.requireNonNull(endedAt, "a closed duty session requires endedAt");
            if (endedAt.isBefore(startedAt)) {
                throw new IllegalArgumentException("endedAt cannot be before startedAt");
            }
        }
    }
}
