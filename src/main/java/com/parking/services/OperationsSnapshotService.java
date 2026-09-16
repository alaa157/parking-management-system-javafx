package com.parking.services;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.parking.enums.PaymentStatus;
import com.parking.enums.SpotStatus;
import com.parking.enums.TicketStatus;
import com.parking.exceptions.AuthorizationException;
import com.parking.model.ParkingSpot;
import com.parking.model.Payment;
import com.parking.model.Ticket;
import com.parking.model.User;

/**
 * Builds live operational snapshots from persisted garage, ticket, and
 * payment state visible to the requesting actor.
 */
public final class OperationsSnapshotService {

    static final int HISTORY_POINTS = 10;

    private final ParkingService parkingService;
    private final TicketService ticketService;
    private final PaymentService paymentService;

    public OperationsSnapshotService(ParkingService parkingService, TicketService ticketService,
            PaymentService paymentService) {
        if (parkingService == null || ticketService == null || paymentService == null) {
            throw new IllegalArgumentException("parking, ticket, and payment services are required");
        }
        this.parkingService = parkingService;
        this.ticketService = ticketService;
        this.paymentService = paymentService;
    }

    /**
     * Returns an immutable snapshot of live operational totals.
     *
     * @param actor the requesting user; ticket and payment totals are scoped
     *              through the existing actor access rules
     * @param now the day used for completed-revenue filtering
     */
    public OperationsSnapshot snapshot(User actor, LocalDateTime now) {
        requireOperationalActor(actor);
        if (now == null) {
            throw new IllegalArgumentException("Reference time is required");
        }

        int available = 0;
        int occupied = 0;
        int reserved = 0;
        int maintenance = 0;
        List<ParkingSpot> spots = parkingService.getGarage().getAllSpots();
        for (ParkingSpot spot : spots) {
            SpotStatus status = spot == null ? null : spot.getStatus();
            if (status == SpotStatus.AVAILABLE) {
                available++;
            } else if (status == SpotStatus.OCCUPIED) {
                occupied++;
            } else if (status == SpotStatus.RESERVED) {
                reserved++;
            } else if (status == SpotStatus.UNDER_MAINTENANCE || status == SpotStatus.OUT_OF_SERVICE) {
                maintenance++;
            }
        }
        int total = spots.size();
        double occupancyPercent = total == 0 ? 0.0 : (occupied * 100.0) / total;

        int activeTicketCount = 0;
        for (Ticket ticket : ticketService.getTicketsFor(actor)) {
            if (ticket != null && ticket.getStatus() == TicketStatus.ACTIVE) {
                activeTicketCount++;
            }
        }

        double completedRevenue = 0.0;
        for (Payment payment : paymentService.getPaymentsFor(actor)) {
            if (payment == null || payment.getStatus() != PaymentStatus.COMPLETED) {
                continue;
            }
            LocalDateTime paidAt = payment.getPaymentTime();
            if (paidAt == null || !paidAt.toLocalDate().equals(now.toLocalDate())) {
                continue;
            }
            completedRevenue += payment.getFinalAmount();
        }

        int current = (int) Math.round(occupancyPercent);
        List<Integer> history = new ArrayList<>(HISTORY_POINTS);
        for (int i = 0; i < HISTORY_POINTS; i++) {
            history.add(current);
        }

        return new OperationsSnapshot(available, occupied, reserved, maintenance, occupancyPercent,
                activeTicketCount, completedRevenue, List.copyOf(history));
    }

    private void requireOperationalActor(User actor) {
        if (actor == null || !actor.isActive()) {
            throw new AuthorizationException("An active authenticated user is required.");
        }
        switch (actor.getRole()) {
            case ADMIN, ATTENDANT, CUSTOMER -> {
            }
            default -> throw new AuthorizationException("You are not authorized for parking operations.");
        }
    }
}
