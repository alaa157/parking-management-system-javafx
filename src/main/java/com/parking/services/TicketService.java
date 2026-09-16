package com.parking.services;

import com.parking.config.AppConfig;
import com.parking.model.Ticket;
import com.parking.model.Vehicle;
import com.parking.model.ParkingSpot;
import com.parking.enums.TicketStatus;
import com.parking.exceptions.TicketNotFoundException;
import com.parking.exceptions.InvalidTicketStatusException;
import com.parking.exceptions.AuthorizationException;
import com.parking.exceptions.GarageAccessException;
import com.parking.model.User;
import com.parking.model.Customer;
import com.parking.enums.UserRole;
import com.parking.persistence.PersistenceStore;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Service class responsible for ticket creation, management,
 * and tracking of parking sessions.
 */
public class TicketService {

    private List<Ticket> tickets = new ArrayList<>();
    private final PersistenceStore persistence;

    public TicketService() {
        this(new PersistenceStore());
    }

    public TicketService(PersistenceStore persistence) {
        this.persistence = persistence;
        this.tickets.addAll(persistence.loadTickets());
    }

    public PersistenceStore getPersistenceStore() {
        return persistence;
    }

    /**
     * Creates a new parking ticket for a vehicle.
     * Generates a unique ticket ID and records entry time.
     *
     * @param vehicle     the vehicle entering the garage
     * @param parkingSpot the spot allocated to the vehicle
     * @return newly created Ticket object
     */
    public Ticket createTicket(Vehicle vehicle, ParkingSpot parkingSpot) {
        if (parkingSpot == null) throw new IllegalArgumentException("Parking spot is required");
        String garageId;
        try {
            garageId = persistence.garageIdForParkingSpot(parkingSpot.getSpotId());
        } catch (IllegalArgumentException missingSpot) {
            persistence.ensureParkingSpotReference(parkingSpot);
            garageId = "__legacy__";
        }
        return createTicket(vehicle, parkingSpot, garageId);
    }

    public Ticket createTicket(Vehicle vehicle, ParkingSpot parkingSpot, String garageId) {
        if (vehicle == null || parkingSpot == null) throw new IllegalArgumentException("Vehicle and parking spot are required");
        if (garageId == null || garageId.isBlank()) throw new IllegalArgumentException("garageId is required");
        if (!garageId.equals(persistence.garageIdForParkingSpot(parkingSpot.getSpotId()))) {
            throw new IllegalArgumentException("Ticket garage must match parking spot garage");
        }
        persistence.ensureGarageReference(garageId);
        if (!persistence.loadGarage(garageId).map(g -> g.isOpen() && !g.isArchived()).orElse(false)) {
            throw new GarageAccessException("Garage is not available: " + garageId);
        }
        persistence.ensureVehicleReference(vehicle);
        persistence.ensureParkingSpotReference(parkingSpot);
        // Generate a unique ticket ID
        String ticketId = generateTicketId();
        // Create a new ticket
        Ticket ticket = new Ticket(ticketId, vehicle.getVehicleId(), parkingSpot.getSpotId(), vehicle.getUserId(), garageId);
        if (ticket.getUserId() != null && !persistence.hasUser(ticket.getUserId())) ticket.setUserId(null);
        // Set entry time
        ticket.setEntryTime(LocalDateTime.now());
        // Set status to CREATED
        ticket.setStatus(TicketStatus.CREATED);
        // Add the ticket to the list of all tickets
        tickets.add(ticket);
        try {
            persistence.saveTicket(ticket);
            persistence.audit("TICKET_CREATED", ticket.getTicketId(), ticket.getUserId(), true, "status=CREATED");
            // Return the newly created ticket
            return ticket;
        } catch (RuntimeException | Error failure) {
            // The enclosing parking transaction will roll SQLite back. Remove the
            // staged object here as well so a failed write cannot leave an orphan
            // visible to the rest of the application.
            tickets.remove(ticket);
            throw failure;
        }
    }

    public Ticket createTicket(User actor, Vehicle vehicle, ParkingSpot parkingSpot, String garageId) {
        if (actor == null || !actor.isActive()) throw new AuthorizationException("An active authenticated user is required.");
        if (actor.getRole() != UserRole.ADMIN && !persistence.hasGarageAccess(actor.getUserId(), garageId)) {
            throw new GarageAccessException("User does not have access to garage: " + garageId);
        }
        return createTicket(vehicle, parkingSpot, garageId);
    }

    public List<Ticket> findTickets(User actor, String garageId) {
        if (actor == null || !actor.isActive()) throw new AuthorizationException("An active authenticated user is required.");
        if (actor.getRole() == UserRole.ADMIN && garageId == null) return getAllTickets();
        if (garageId == null || !persistence.loadGarage(garageId).map(g -> !g.isArchived()).orElse(false)) {
            throw new GarageAccessException("Garage access is required");
        }
        if (actor.getRole() != UserRole.ADMIN && !persistence.hasGarageAccess(actor.getUserId(), garageId)) {
            throw new GarageAccessException("User does not have access to garage: " + garageId);
        }
        return getAllTickets().stream()
                .filter(ticket -> garageId.equals(ticket.getGarageId()))
                .filter(ticket -> actor.getRole() == UserRole.ADMIN || actor.getRole() == UserRole.ATTENDANT
                        || actor.getUserId().equals(ticket.getUserId()))
                .toList();
    }

    public void removeTicket(Ticket ticket) {
        if (ticket != null) {
            boolean persisted = tickets.remove(ticket);
            if (persisted && ticket.getTicketId() != null) persistence.deleteTicketById(ticket.getTicketId());
        }
    }

    /**
     * Retrieves a ticket by its ID.
     *
     * @param ticketId the ticket ID to search for
     * @return the Ticket object
     * @throws TicketNotFoundException if ticket is not found
     */
    public Ticket getTicketById(String ticketId) throws TicketNotFoundException {
        for (Ticket ticket : tickets) {
            if (ticket.getTicketId().equals(ticketId)) {
                return ticket;
            }
        }
        throw new TicketNotFoundException("Ticket with ID " + ticketId + " not found");
    }

    public Ticket getTicketById(User actor, String ticketId) throws TicketNotFoundException {
        Ticket ticket = getTicketById(ticketId);
        if (!canAccess(actor, ticket)) {
            throw new AuthorizationException("You are not authorized to access this ticket.");
        }
        return ticket;
    }

    /**
     * Retrieves the active ticket for a specific vehicle.
     *
     * @param vehicle the vehicle to search for
     * @return the active Ticket object
     * @throws TicketNotFoundException if no active ticket found
     */
    public Ticket getActiveTicketByVehicle(Vehicle vehicle) throws TicketNotFoundException {
        // Iterate through the tickets list
        for (Ticket ticket : tickets) {
            // Check if the ticket belongs to the vehicle and is active
            if (ticket.getVehicleId().equals(vehicle.getVehicleId()) &&
                    ticket.getStatus() == TicketStatus.ACTIVE) {
                return ticket;
            }
        }
        throw new TicketNotFoundException("No active ticket found for vehicle " + vehicle.getVehicleId());
    }

    /**
     * Returns a snapshot of all tickets for GUI/reporting layers.
     * A defensive copy is returned so callers cannot replace the service's list.
     */
    public List<Ticket> getAllTickets() {
        return new ArrayList<>(tickets);
    }

    public List<Ticket> getTicketsFor(User actor) {
        if (actor == null || !actor.isActive()) {
            throw new AuthorizationException("An active authenticated user is required.");
        }
        if (actor.getRole() == UserRole.ADMIN || actor.getRole() == UserRole.ATTENDANT) {
            return getAllTickets();
        }
        if (actor instanceof Customer) {
            return getTicketsFor((Customer) actor);
        }
        List<Ticket> result = new ArrayList<>();
        for (Ticket ticket : tickets) {
            if (actor.getUserId().equals(ticket.getUserId())) result.add(ticket);
        }
        return result;
    }

    /**
     * Returns the tickets linked to a customer. The customer history is the
     * persisted ticket index; the owner check is retained as a compatibility
     * fallback for tickets created before that index was populated.
     */
    public List<Ticket> getTicketsFor(Customer customer) {
        if (customer == null || !customer.isActive()) {
            throw new AuthorizationException("An active authenticated customer is required.");
        }

        List<Ticket> result = new ArrayList<>();
        List<String> ticketIds = customer.getTicketIds();
        for (Ticket ticket : tickets) {
            boolean linked = ticketIds != null && ticketIds.contains(ticket.getTicketId());
            boolean owned = customer.getUserId().equals(ticket.getUserId());
            if (linked || owned) result.add(ticket);
        }
        return result;
    }

    private boolean canAccess(User actor, Ticket ticket) {
        return actor != null && actor.isActive()
                && (actor.getRole() == UserRole.ADMIN
                || actor.getRole() == UserRole.ATTENDANT
                || actor.getUserId().equals(ticket.getUserId()));
    }

    /**
     * Updates the status of a ticket.
     *
     * @param ticket    the ticket to update
     * @param newStatus the new status to set
     * @throws InvalidTicketStatusException if status transition is invalid
     */
    public void updateTicketStatus(Ticket ticket, TicketStatus newStatus) throws InvalidTicketStatusException {
        // Get the current status of the ticket
        TicketStatus currentStatus = ticket.getStatus();
        // Check if the status transition is valid
        if (!TicketStatus.isValidTransition(currentStatus, newStatus)) {
            throw new InvalidTicketStatusException(
                    "Invalid status transition from " + currentStatus + " to " + newStatus);
        }
        // Update the status of the ticket
        ticket.setStatus(newStatus);
        if (newStatus == TicketStatus.REFUNDED) {
            ticket.setPaid(false);
        }
        // Closing must preserve the exit time frozen during vehicle exit.
        if (newStatus == TicketStatus.CLOSED && ticket.getExitTime() == null) {
            ticket.setExitTime(LocalDateTime.now());
        }
        if (tickets.contains(ticket)) {
            persistence.saveTicket(ticket);
            persistence.audit("TICKET_STATUS_CHANGED", ticket.getTicketId(), null, true,
                    "from=" + currentStatus + ";to=" + newStatus);
        }
    }

    /**
     * Updates the status of several tickets at once, reporting per-ticket
     * outcomes instead of silently ignoring failures. Access is validated
     * first using the existing {@code canAccess} ownership rules and each
     * transition is checked with {@link TicketStatus#isValidTransition};
     * every successful status change is persisted in one transaction so a
     * persistence failure rolls the whole batch back.
     *
     * @param actor     the user performing the update
     * @param ticketIds the tickets to update
     * @param target    the desired status
     * @return typed result with succeeded ids and per-ticket failure reasons
     */
    public synchronized BulkTicketUpdateResult updateStatuses(User actor, Set<String> ticketIds, TicketStatus target) {
        requireBulkActor(actor);
        if (ticketIds == null) throw new IllegalArgumentException("ticketIds cannot be null");
        if (target == null) throw new IllegalArgumentException("target status cannot be null");
        if (ticketIds.isEmpty()) return BulkTicketUpdateResult.empty();

        Map<String, String> failures = new LinkedHashMap<>();
        List<Ticket> candidates = new ArrayList<>();
        for (String ticketId : ticketIds) {
            if (ticketId == null || ticketId.isBlank()) {
                failures.put(ticketId == null ? "<null>" : ticketId, "Ticket id is required");
                continue;
            }
            Ticket ticket;
            try {
                ticket = getTicketById(ticketId);
            } catch (TicketNotFoundException missing) {
                failures.put(ticketId, "Ticket not found: " + ticketId);
                continue;
            }
            if (!canAccess(actor, ticket)) {
                failures.put(ticketId, "You are not authorized to update ticket " + ticketId + ".");
                continue;
            }
            if (!TicketStatus.isValidTransition(ticket.getStatus(), target)) {
                failures.put(ticketId,
                        "Invalid status transition from " + ticket.getStatus() + " to " + target);
                continue;
            }
            candidates.add(ticket);
        }

        if (candidates.isEmpty()) {
            return new BulkTicketUpdateResult(Set.of(), failures);
        }

        Map<String, TicketSnapshot> before = new LinkedHashMap<>();
        for (Ticket ticket : candidates) before.put(ticket.getTicketId(), TicketSnapshot.of(ticket));
        try {
            persistence.inTransaction(() -> {
                for (Ticket ticket : candidates) {
                    TicketStatus from = ticket.getStatus();
                    ticket.setStatus(target);
                    if (target == TicketStatus.REFUNDED) {
                        ticket.setPaid(false);
                    }
                    if (target == TicketStatus.CLOSED && ticket.getExitTime() == null) {
                        ticket.setExitTime(LocalDateTime.now());
                    }
                    persistence.saveTicket(ticket);
                    persistence.audit("TICKET_STATUS_CHANGED", ticket.getTicketId(), actor.getUserId(), true,
                            "from=" + from + ";to=" + target + ";bulk=true");
                }
                return null;
            });
        } catch (RuntimeException | Error failure) {
            for (Ticket ticket : candidates) before.get(ticket.getTicketId()).restore(ticket);
            throw failure;
        } catch (Exception failure) {
            for (Ticket ticket : candidates) before.get(ticket.getTicketId()).restore(ticket);
            throw new IllegalStateException("Could not update ticket statuses", failure);
        }

        Set<String> succeeded = new LinkedHashSet<>();
        for (Ticket ticket : candidates) succeeded.add(ticket.getTicketId());
        return new BulkTicketUpdateResult(succeeded, failures);
    }

    private static void requireBulkActor(User actor) {
        if (actor == null || !actor.isActive()) {
            throw new AuthorizationException("An active authenticated user is required.");
        }
        if (actor.getRole() != UserRole.ADMIN
                && actor.getRole() != UserRole.ATTENDANT
                && actor.getRole() != UserRole.CUSTOMER) {
            throw new AuthorizationException("You are not authorized for ticket operations.");
        }
    }

    private record TicketSnapshot(TicketStatus status, boolean paid, LocalDateTime exitTime) {
        static TicketSnapshot of(Ticket ticket) {
            return new TicketSnapshot(ticket.getStatus(), ticket.isPaid(), ticket.getExitTime());
        }

        void restore(Ticket ticket) {
            ticket.setStatus(status);
            ticket.setPaid(paid);
            ticket.setExitTime(exitTime);
        }
    }

    /**
     * Marks a ticket as awaiting payment.
     *
     * @param ticket the ticket to mark
     * @throws InvalidTicketStatusException if ticket status prevents marking as
     *                                      AWAITING_PAYMENT
     */
    public void markAwaitingPayment(Ticket ticket) throws InvalidTicketStatusException {
        // Get the current status of the ticket
        TicketStatus currentStatus = ticket.getStatus();

        // Check if the ticket can be marked as AWAITING_PAYMENT
        if (currentStatus != TicketStatus.ACTIVE) {
            // Throw an exception if the ticket status is not ACTIVE
            throw new InvalidTicketStatusException(
                    "Cannot mark as AWAITING_PAYMENT: Ticket must be ACTIVE, not " + currentStatus);
        }

        // Update the status of the ticket to AWAITING_PAYMENT
        updateTicketStatus(ticket, TicketStatus.AWAITING_PAYMENT);
    }

    /**
     * Calculates the total parking duration for a ticket.
     *
     * @param ticket the ticket to calculate for
     * @return duration in hours
     * @throws InvalidTicketStatusException if ticket status prevents calculation
     */
    public double calculateParkingDuration(Ticket ticket) throws InvalidTicketStatusException {
        if (ticket == null) {
            throw new InvalidTicketStatusException("Ticket cannot be null");
        }

        TicketStatus status = ticket.getStatus();
        if (status != TicketStatus.ACTIVE &&
                status != TicketStatus.AWAITING_PAYMENT &&
                status != TicketStatus.PAID &&
                status != TicketStatus.CLOSED &&
                status != TicketStatus.REFUNDED) {
            throw new InvalidTicketStatusException(
                    "Cannot calculate duration for ticket with status: " + status);
        }

        LocalDateTime entryTime = ticket.getEntryTime();
        if (entryTime == null) {
            return 0.0;
        }

        LocalDateTime exitTime = ticket.getExitTime() != null
                ? ticket.getExitTime()
                : LocalDateTime.now();

        long durationMinutes = ChronoUnit.MINUTES.between(entryTime, exitTime);
        return durationMinutes / 60.0;
    }

    /**
     * Closes a ticket when the vehicle exits.
     * Sets the exit time and updates status to CLOSED.
     *
     * @param ticket the ticket to close
     * @throws InvalidTicketStatusException if ticket cannot be closed
     */
    public void closeTicket(Ticket ticket) throws InvalidTicketStatusException {
        if (ticket == null) {
            throw new InvalidTicketStatusException("Ticket cannot be null");
        }
        TicketStatus status = ticket.getStatus();
        if (status != TicketStatus.PAID) {
            throw new InvalidTicketStatusException(
                    "Cannot close ticket with status: " + status + ". Ticket must be PAID.");
        }

        updateTicketStatus(ticket, TicketStatus.CLOSED);
    }

    /**
     * Checks if a ticket is expired based on time limits.
     *
     * @param ticket the ticket to check
     * @return true if expired, false otherwise
     */
    public boolean isTicketExpired(Ticket ticket) {
        if (ticket == null || ticket.getStatus() != TicketStatus.ACTIVE) {
            return false;
        }

        try {
            double duration = calculateParkingDuration(ticket);
            return duration > AppConfig.maxParkHours();
        } catch (InvalidTicketStatusException e) {
            return false;
        }
    }

    /**
     * Gets all tickets for a specific vehicle.
     *
     * @param vehicle the vehicle to search for
     * @return list of tickets for the vehicle
     */
    public List<Ticket> getTicketsByVehicle(Vehicle vehicle) {
        List<Ticket> vehicleTickets = new ArrayList<>();
        // Iterate through tickets and collect those matching the vehicle ID
        for (Ticket ticket : tickets) {
            if (ticket.getVehicleId().equals(vehicle.getVehicleId())) {
                vehicleTickets.add(ticket);
            }
        }

        return vehicleTickets;
    }

    /**
     * Displays detailed information about a ticket in a formatted way.
     * Used by viewMyTickets() to show readable ticket details.
     *
     * @param ticket the ticket to display
     * @return formatted string with all ticket details
     */
    public String displayTicketDetails(Ticket ticket) {
        if (ticket == null) {
            return "No ticket information available.";
        }

        StringBuilder details = new StringBuilder();
        details.append("═══════════════════════════════════════\n");
        details.append("  TICKET #").append(ticket.getTicketId()).append("\n");
        details.append("───────────────────────────────────────\n");
        // Basic Info
        details.append("  Vehicle ID:    ").append(ticket.getVehicleId()).append("\n");
        details.append("  Parking Spot:  ").append(ticket.getParkingSpotId()).append("\n");
        details.append("  Status:        ").append(ticket.getStatus()).append("\n");
        // Entry Time
        if (ticket.getEntryTime() != null) {
            details.append("  Entry Time:    ").append(formatDateTime(ticket.getEntryTime())).append("\n");
        } else {
            details.append("  Entry Time:    Not recorded\n");
        }
        // Exit Time (if available)
        if (ticket.getExitTime() != null) {
            details.append("  Exit Time:     ").append(formatDateTime(ticket.getExitTime())).append("\n");
        } else {
            details.append("  Exit Time:     Still parked\n");
        }
        // Parking Duration
        double duration = ticket.getParkingDuration();
        details.append("  Duration:      ").append(formatDuration(duration)).append("\n");
        // Payment Status
        details.append("  Paid:          ").append(ticket.isPaid() ? " YES" : " NO").append("\n");
        // Amount Information
        if (ticket.getAmount() > 0) {
            details.append("  Amount Due:    $").append(String.format("%.2f", ticket.getAmount())).append("\n");
        }
        if (ticket.getFinalAmount() > 0) {
            details.append("  Final Amount:  $").append(String.format("%.2f", ticket.getFinalAmount())).append("\n");
        }
        details.append("═══════════════════════════════════════\n");
        return details.toString();
    }

    /**
     * Formats a duration in hours to a readable string.
     *
     * @param durationHours duration in hours
     * @return formatted duration string (e.g., "2h 30m" or "1.5 hours")
     */
    private String formatDuration(double durationHours) {
        if (durationHours <= 0) {
            return "Less than 1 hour";
        }

        int hours = (int) durationHours;
        int minutes = (int) ((durationHours - hours) * 60);

        if (hours == 0) {
            return minutes + " minutes";
        } else if (minutes == 0) {
            return hours + " hour" + (hours > 1 ? "s" : "");
        } else {
            return hours + "h " + minutes + "m";
        }
    }

    /**
     * Formats LocalDateTime to a readable string.
     *
     * @param dateTime the date/time to format
     * @return formatted string (e.g., "Jan 15, 2024 14:30")
     */
    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "N/A";
        }
        String month = dateTime.getMonth().toString().substring(0, 3);
        String day = String.valueOf(dateTime.getDayOfMonth());
        String year = String.valueOf(dateTime.getYear());
        String hour = String.format("%02d", dateTime.getHour());
        String minute = String.format("%02d", dateTime.getMinute());

        return month + " " + day + ", " + year + " " + hour + ":" + minute;
    }

    /**
     * Cancels a ticket.
     *
     * @param ticket the ticket to cancel
     * @throws InvalidTicketStatusException if ticket cannot be cancelled
     */
    public void cancelTicket(Ticket ticket) throws InvalidTicketStatusException {
        TicketStatus status = ticket.getStatus();
        // Validate ticket status
        if (status != TicketStatus.CREATED && status != TicketStatus.ACTIVE) {
            throw new InvalidTicketStatusException(
                    "Cannot cancel ticket with status: " + status + ". Ticket must be CREATED or ACTIVE.");
        }

        updateTicketStatus(ticket, TicketStatus.CANCELLED);
    }

    /**
     * Generates a unique ticket ID.
     *
     * @return unique ticket ID string
     */
    public String generateTicketId() {
        return "TICKET-" + UUID.randomUUID().toString().substring(0, 8);
    }

}
