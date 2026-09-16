package com.parking.services;

import java.util.Map;
import java.util.Set;

/**
 * Typed outcome of a bulk ticket-status update. Successful ticket ids are
 * reported alongside per-ticket failure reasons so callers never silently
 * ignore invalid transitions, missing tickets, or authorization problems.
 * This result is rendered as a toast/dialog only; it owns no persistence.
 */
public record BulkTicketUpdateResult(Set<String> succeeded, Map<String, String> failures) {
    public BulkTicketUpdateResult {
        succeeded = succeeded == null ? Set.of() : Set.copyOf(succeeded);
        failures = failures == null ? Map.of() : Map.copyOf(failures);
    }

    public static BulkTicketUpdateResult empty() {
        return new BulkTicketUpdateResult(Set.of(), Map.of());
    }
}
