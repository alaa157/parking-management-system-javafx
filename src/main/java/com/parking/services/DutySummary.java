package com.parking.services;

import java.time.Duration;
import java.util.Objects;

/**
 * Aggregated duty outcome for one attendant over a requested time range:
 * completed payment transactions attributed to open duty sessions, the
 * revenue they produced, and the total on-duty duration.
 */
public record DutySummary(String attendantId, String attendantName, long completedTransactions,
                          double revenue, Duration totalDuration) {
    public DutySummary {
        if (attendantId == null || attendantId.isBlank()) {
            throw new IllegalArgumentException("attendantId cannot be null or blank");
        }
        Objects.requireNonNull(attendantName, "attendantName cannot be null");
        if (completedTransactions < 0) {
            throw new IllegalArgumentException("completedTransactions cannot be negative");
        }
        if (!Double.isFinite(revenue) || revenue < 0) {
            throw new IllegalArgumentException("revenue must be finite and non-negative");
        }
        Objects.requireNonNull(totalDuration, "totalDuration cannot be null");
        if (totalDuration.isNegative()) {
            throw new IllegalArgumentException("totalDuration cannot be negative");
        }
    }
}
