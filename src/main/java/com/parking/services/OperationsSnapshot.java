package com.parking.services;

import java.util.List;

/**
 * Immutable operational totals for the dashboard.
 *
 * <p>All values are derived from persisted service state visible to the
 * requesting actor. No random or simulated values are used.</p>
 */
public record OperationsSnapshot(
        int available,
        int occupied,
        int reserved,
        int maintenance,
        double occupancyPercent,
        int activeTicketCount,
        double completedRevenue,
        List<Integer> occupancyHistory) {
    public OperationsSnapshot {
        occupancyHistory = occupancyHistory == null ? List.of() : List.copyOf(occupancyHistory);
    }
}
