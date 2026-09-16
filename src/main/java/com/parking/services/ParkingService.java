package com.parking.services;

import com.parking.config.AppConfig;
import com.parking.model.Customer;
import com.parking.model.ParkingGarage;
import com.parking.model.ParkingSpot;
import com.parking.model.Reservation;
import com.parking.model.Vehicle;
import com.parking.model.Ticket;
import com.parking.model.User;
import com.parking.enums.SpotType;
import com.parking.enums.SpotStatus;
import com.parking.enums.ReservationStatus;
import com.parking.enums.TicketStatus;
import com.parking.enums.VehicleType;
import com.parking.exceptions.InvalidTicketStatusException;
import com.parking.exceptions.SpotNotAvailableException;
import com.parking.exceptions.TicketNotFoundException;
import com.parking.exceptions.VehicleAlreadyParkedException;
import com.parking.exceptions.AuthorizationException;
import com.parking.persistence.PersistenceStore;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Service class responsible for managing parking operations including
 * vehicle entry, exit, and spot allocation.
 */
public class ParkingService {

    private ParkingGarage garage;
    private TicketService ticketService;
    private UserService userService;
    private final PersistenceStore persistence;
    private ReservationService reservationService;

    /**
     * Constructs a ParkingService with the specified garage and ticket service.
     *
     * @param garage the parking garage instance
     * @param ticketService the ticket service for managing tickets
     */
    public ParkingService(ParkingGarage garage, TicketService ticketService, UserService userService) {
        this(garage, ticketService, userService, ticketService.getPersistenceStore());
    }

    public ParkingService(ParkingGarage garage, TicketService ticketService, UserService userService,
                           PersistenceStore persistence) {
        this.garage = garage;
        this.ticketService = ticketService;
        this.userService = userService;
        if (persistence == null) throw new IllegalArgumentException("persistence cannot be null");
        this.persistence = persistence;
    }

    public ParkingService(ParkingGarage garage, TicketService ticketService, UserService userService,
                           PersistenceStore persistence, ReservationService reservationService) {
        this(garage, ticketService, userService, persistence);
        this.reservationService = reservationService;
    }

    public void setReservationService(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    public ReservationService getReservationService() {
        return reservationService;
    }

    /**
     * Handles vehicle entry into the parking garage.
     * Finds an available parking spot, assigns it to the vehicle,
     * and creates a new ticket.
     *
     * @param vehicle the vehicle entering the garage
     * @return Ticket object for the parked vehicle
     * @throws SpotNotAvailableException if no suitable spot is available
     * @throws VehicleAlreadyParkedException if the vehicle is already parked
     * @throws InvalidTicketStatusException if the ticket status is invalid
     */
    public synchronized Ticket vehicleEntry(Vehicle vehicle) throws SpotNotAvailableException, VehicleAlreadyParkedException, InvalidTicketStatusException {
        if (!garage.isOpen()) {
            throw new IllegalStateException("Parking garage is closed");
        }
        if (vehicle == null) {
            throw new IllegalArgumentException("Vehicle cannot be null");
        }
        ensureVehicleNotParked(vehicle.getVehicleId());
        // Find an available spot for the vehicle
        ParkingSpot spot = findAvailableSpot(vehicle);
        return parkVehicle(vehicle, spot);
    }

    /**
     * Parks a vehicle in an explicitly allocated spot inside one persistence
     * transaction, rolling both memory and database state back on failure.
     */
    private synchronized Ticket parkVehicle(Vehicle vehicle, ParkingSpot spot)
            throws VehicleAlreadyParkedException, InvalidTicketStatusException {
        Ticket ticket = null;
        Ticket[] createdHolder = new Ticket[1];
        try {
            ticket = persistence.inTransaction(() -> {
                allocateSpot(spot, vehicle);
                Ticket created = ticketService.createTicket(vehicle, spot, garage.getGarageId());
                createdHolder[0] = created;
                ticketService.updateTicketStatus(created, TicketStatus.ACTIVE);
                linkTicketToCustomer(created, vehicle.getUserId());
                return created;
            });
            return ticket;
        } catch (Exception ex) {
            if (ticket == null) ticket = createdHolder[0];
            if (ticket != null) {
                ticketService.removeTicket(ticket);
                removeTicketFromCustomer(ticket);
            }
            if (spot.getVehicleId() != null) spot.freeSpot();
            if (vehicle.isParked()) vehicle.unpark();
            garage.unregisterVehicle(vehicle.getVehicleId());
            garage.updateAvailability();
            if (ex instanceof InvalidTicketStatusException invalid) throw invalid;
            if (ex instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("Atomic parking entry failed", ex);
        }
    }

    public synchronized Ticket vehicleEntry(Vehicle vehicle, String garageId, User actor)
            throws SpotNotAvailableException, VehicleAlreadyParkedException, InvalidTicketStatusException {
        requireOperationalActor(actor);
        if (garageId == null || !garageId.equals(garage.getGarageId())) {
            throw new AuthorizationException("This parking service is not bound to garage: " + garageId);
        }
        if (actor.getRole() != com.parking.enums.UserRole.ADMIN
                && !persistence.hasGarageAccess(actor.getUserId(), garageId)) {
            throw new AuthorizationException("User does not have access to garage: " + garageId);
        }
        if (actor.getRole() == com.parking.enums.UserRole.CUSTOMER
                && !actor.getUserId().equals(vehicle == null ? null : vehicle.getUserId())) {
            throw new AuthorizationException("Customers may only park their own vehicles.");
        }
        return vehicleEntry(vehicle);
    }

    public void ensureVehicleNotParked(String vehicleId) throws VehicleAlreadyParkedException {
        var active = persistence.findActiveTicketForVehicle(vehicleId);
        if (active.isPresent()) {
            Ticket ticket = active.get().ticket();
            throw new VehicleAlreadyParkedException(ticket.getTicketId(), ticket.getGarageId(),
                    ticket.getParkingSpotId(), ticket.getEntryTime());
        }
    }

    public Ticket vehicleEntry(User actor, Vehicle vehicle)
            throws SpotNotAvailableException, VehicleAlreadyParkedException, InvalidTicketStatusException {
        return vehicleEntry(actor, vehicle, LocalDateTime.now());
    }

    public synchronized Ticket vehicleEntry(User actor, Vehicle vehicle, LocalDateTime now)
            throws SpotNotAvailableException, VehicleAlreadyParkedException, InvalidTicketStatusException {
        requireOperationalActor(actor);
        if (now == null) throw new IllegalArgumentException("now cannot be null");
        if (actor.getRole() == com.parking.enums.UserRole.CUSTOMER
                && !actor.getUserId().equals(vehicle == null ? null : vehicle.getUserId())) {
            throw new AuthorizationException("Customers may only park their own vehicles.");
        }
        if (reservationService != null) {
            List<Reservation> live = new ArrayList<>();
            for (Reservation reservation : reservationService.activeForUser(actor)) {
                if (now.isBefore(reservation.expiresAt())) live.add(reservation);
            }
            if (live.size() == 1) return vehicleEntry(actor, vehicle, live.get(0).reservationId(), now);
        }
        return vehicleEntry(vehicle);
    }

    public synchronized Ticket vehicleEntry(User actor, Vehicle vehicle, String reservationId)
            throws SpotNotAvailableException, VehicleAlreadyParkedException, InvalidTicketStatusException {
        return vehicleEntry(actor, vehicle, reservationId, LocalDateTime.now());
    }

    /**
     * Enters a vehicle by claiming one of the actor's active reservations.
     * Claiming another user's reservation is rejected with
     * {@link AuthorizationException}. Claim and park run in one persistence
     * transaction so a park failure after CLAIMED rolls both back together.
     */
    public synchronized Ticket vehicleEntry(User actor, Vehicle vehicle, String reservationId, LocalDateTime now)
            throws SpotNotAvailableException, VehicleAlreadyParkedException, InvalidTicketStatusException {
        requireOperationalActor(actor);
        if (reservationId == null || reservationId.isBlank()) return vehicleEntry(actor, vehicle, now);
        if (now == null) throw new IllegalArgumentException("now cannot be null");
        if (reservationService == null) throw new IllegalStateException("Reservations are not available");
        if (vehicle == null) throw new IllegalArgumentException("Vehicle cannot be null");
        if (actor.getRole() == com.parking.enums.UserRole.CUSTOMER
                && !actor.getUserId().equals(vehicle.getUserId())) {
            throw new AuthorizationException("Customers may only park their own vehicles.");
        }
        ensureVehicleNotParked(vehicle.getVehicleId());
        Vehicle priorRegistration = garage.getVehicle(vehicle.getVehicleId());
        boolean[] registeredThisCall = new boolean[1];
        Ticket[] createdHolder = new Ticket[1];
        try {
            return persistence.inTransaction(() -> {
                Reservation claimed = reservationService.claimForEntry(actor, vehicle, reservationId, now);
                ParkingSpot spot = garage.getSpotById(claimed.spotId());
                if (spot == null) throw new IllegalStateException("Reserved parking spot is missing: " + claimed.spotId());
                allocateSpot(spot, vehicle);
                registeredThisCall[0] = true;
                Ticket created = ticketService.createTicket(vehicle, spot, garage.getGarageId());
                createdHolder[0] = created;
                ticketService.updateTicketStatus(created, TicketStatus.ACTIVE);
                linkTicketToCustomer(created, vehicle.getUserId());
                return created;
            });
        } catch (Exception ex) {
            Ticket ticket = createdHolder[0];
            if (ticket != null) {
                ticketService.removeTicket(ticket);
                removeTicketFromCustomer(ticket);
            }
            restoreHoldMemoryAfterFailedClaimPark(reservationId, vehicle,
                    registeredThisCall[0], priorRegistration);
            if (ex instanceof InvalidTicketStatusException invalid) throw invalid;
            if (ex instanceof SpotNotAvailableException notAvailable) throw notAvailable;
            if (ex instanceof VehicleAlreadyParkedException alreadyParked) throw alreadyParked;
            if (ex instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("Atomic parking entry failed", ex);
        }
    }

    private void restoreHoldMemoryAfterFailedClaimPark(String reservationId, Vehicle vehicle,
            boolean registeredThisCall, Vehicle priorRegistration) {
        try {
            var row = persistence.loadReservation(reservationId);
            if (row.isPresent() && ReservationStatus.ACTIVE.name().equals(row.get().status())) {
                ParkingSpot spot = garage.getSpotById(row.get().spotId());
                if (spot != null) {
                    if (registeredThisCall && vehicle != null) {
                        if (vehicle.isParked()) vehicle.unpark();
                        rollbackVehicleRegistration(priorRegistration, vehicle);
                    }
                    spot.setVehicleId(null);
                    spot.setStatus(SpotStatus.RESERVED);
                    spot.setReservationHolderUserId(row.get().userId());
                    spot.setReservationExpiry(LocalDateTime.parse(row.get().expiresAt()));
                    garage.updateAvailability();
                    return;
                }
            }
        } catch (RuntimeException ignored) {
            // Fall through to generic cleanup below.
        }
        try {
            if (registeredThisCall && vehicle != null && vehicle.isParked()) {
                ParkingSpot spot = vehicle.getParkingSpotId() == null
                        ? null : garage.getSpotById(vehicle.getParkingSpotId());
                if (spot != null && vehicle.getVehicleId().equals(spot.getVehicleId())) spot.freeSpot();
                vehicle.unpark();
            }
            if (registeredThisCall && vehicle != null) rollbackVehicleRegistration(priorRegistration, vehicle);
            garage.updateAvailability();
        } catch (RuntimeException ignored) {
            // Rollback best-effort only; the database transaction already rolled back.
        }
    }

    private void rollbackVehicleRegistration(Vehicle priorRegistration, Vehicle vehicle) {
        if (priorRegistration == null) {
            garage.unregisterVehicle(vehicle.getVehicleId());
        } else if (priorRegistration != vehicle) {
            garage.registerVehicle(priorRegistration);
        }
    }

    /**
     * Links a ticket to the customer who owns the vehicle.
     * Retrieves the customer from the user service and adds the ticket to their history.
     *
     * @param ticket the ticket to link
     * @param userId the ID of the customer who owns the vehicle
     */
    private void linkTicketToCustomer(Ticket ticket, String userId) {
        if (userId == null || userId.isEmpty() || userService == null) {
            // User ID is invalid or user service is not available
            return;
        }
        try {
            User user = userService.getUserById(userId);
            if (user instanceof Customer) {
                Customer customer = (Customer) user;
                // Add the ticket to the customer's history
                customer.addTicket(ticket.getTicketId());
            }
        } catch (RuntimeException e) {
            // User not found - guest parking
        }
    }

    private void removeTicketFromCustomer(Ticket ticket) {
        if (ticket == null || userService == null || ticket.getUserId() == null) return;
        try {
            User user = userService.getUserById(ticket.getUserId());
            if (user instanceof Customer) ((Customer) user).removeTicket(ticket.getTicketId());
        } catch (RuntimeException ignored) {
            // A missing owner does not prevent state rollback.
        }
    }

    /**
     * Handles vehicle exit from the parking garage.
     * Frees the parking spot and updates ticket status.
     *
     * @param ticket the ticket associated with the exiting vehicle
     * @return the total amount to be paid
     * @throws TicketNotFoundException if the ticket is not found
     */
    public synchronized double vehicleExit(Ticket ticket) throws TicketNotFoundException, InvalidTicketStatusException {
        if (ticket == null) {
            throw new TicketNotFoundException("Ticket not found");
        }
        ParkingSpot spot = garage.getSpotById(ticket.getParkingSpotId());
        if (spot == null) {
            throw new TicketNotFoundException("Parking spot not found for ticket");
        }
        if (ticket.getStatus() == TicketStatus.AWAITING_PAYMENT) {
            return ticket.getAmount();
        }
        if (ticket.getStatus() == TicketStatus.PAID || ticket.getStatus() == TicketStatus.CLOSED) {
            return ticket.getFinalAmount() > 0 ? ticket.getFinalAmount() : ticket.getAmount();
        }
        if (ticket.getStatus() != TicketStatus.ACTIVE) {
            throw new InvalidTicketStatusException(
                    "Vehicle exit requires an ACTIVE ticket, not " + ticket.getStatus());
        }

        LocalDateTime oldExitTime = ticket.getExitTime();
        double oldAmount = ticket.getAmount();
        TicketStatus oldStatus = ticket.getStatus();
        boolean oldPaid = ticket.isPaid();
        try {
            return persistence.inTransaction(() -> {
                ticket.setExitTime(LocalDateTime.now());
                double amount = ticket.calculateAmount(spot);
                ticketService.markAwaitingPayment(ticket);
                return amount;
            });
        } catch (Exception ex) {
            ticket.setExitTime(oldExitTime);
            ticket.setAmount(oldAmount);
            ticket.setStatus(oldStatus);
            ticket.setPaid(oldPaid);
            if (ex instanceof InvalidTicketStatusException invalid) throw invalid;
            if (ex instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("Atomic vehicle exit failed", ex);
        }
    }

    public double vehicleExit(User actor, Ticket ticket)
            throws TicketNotFoundException, InvalidTicketStatusException {
        requireOperationalActor(actor);
        if (ticket == null) throw new TicketNotFoundException("Ticket not found");
        if (actor.getRole() == com.parking.enums.UserRole.CUSTOMER
                && !actor.getUserId().equals(ticket.getUserId())) {
            throw new AuthorizationException("Customers may only exit their own tickets.");
        }
        return vehicleExit(ticket);
    }

    private void requireOperationalActor(User actor) {
        if (actor == null || !actor.isActive()) {
            throw new AuthorizationException("An active authenticated user is required.");
        }
        if (actor.getRole() != com.parking.enums.UserRole.ADMIN
                && actor.getRole() != com.parking.enums.UserRole.ATTENDANT
                && actor.getRole() != com.parking.enums.UserRole.CUSTOMER) {
            throw new AuthorizationException("You are not authorized for parking operations.");
        }
    }

    /**
     * Releases the ticket's parking spot after the ticket has been paid and closed.
     *
     * @param ticket the paid/closed ticket whose spot should be released
     * @throws InvalidTicketStatusException if the ticket is not CLOSED
     * @throws TicketNotFoundException if the parking spot cannot be found
     */
    public synchronized void releaseAfterPayment(Ticket ticket)
            throws TicketNotFoundException, InvalidTicketStatusException {
        if (ticket == null) {
            throw new TicketNotFoundException("Ticket not found");
        }
        if (ticket.getStatus() != TicketStatus.CLOSED) {
            throw new InvalidTicketStatusException(
                    "Parking spot can only be released after the ticket is CLOSED");
        }

        ParkingSpot spot = garage.getSpotById(ticket.getParkingSpotId());
        if (spot == null) {
            throw new TicketNotFoundException("Parking spot not found for ticket");
        }

        if (spot.getVehicleId() == null) {
            garage.updateAvailability();
            return;
        }
        if (ticket.getVehicleId() != null && !ticket.getVehicleId().equals(spot.getVehicleId())) {
            throw new InvalidTicketStatusException("Ticket does not own the occupied parking spot");
        }
        freeSpot(spot);
    }

    /**
     * Maps VehicleType to appropriate SpotType.
     *
     * @param vehicleType the vehicle type to map
     * @return the corresponding SpotType
     */
    private List<SpotType> getAllowedSpotTypes(VehicleType vehicleType) {
        List<SpotType> allowed = new ArrayList<>();
        if (vehicleType == null) {
            return allowed;
        }
        switch (vehicleType) {
            case MOTORCYCLE:
                allowed.add(SpotType.MOTORCYCLE);
                allowed.add(SpotType.COMPACT);
                allowed.add(SpotType.STANDARD);
                break;
            case COMPACT_CAR:
                allowed.add(SpotType.COMPACT);
                allowed.add(SpotType.STANDARD);
                break;
            case CAR:
                allowed.add(SpotType.STANDARD);
                allowed.add(SpotType.LARGE);
                break;
            case SUV:
            case TRUCK:
                allowed.add(SpotType.LARGE);
                allowed.add(SpotType.STANDARD);
                break;
            case ELECTRIC_VEHICLE:
                allowed.add(SpotType.EV_CHARGING);
                allowed.add(SpotType.STANDARD);
                break;
            case HANDICAPPED:
                allowed.add(SpotType.HANDICAPPED);
                break;
            default:
                break;
        }
        return allowed;
    }

    /**
     * Finds an available parking spot for a vehicle based on its type.
     *
     * @param vehicle the vehicle needing a spot
     * @return an available ParkingSpot
     * @throws SpotNotAvailableException if no spots are available
     */
    public ParkingSpot findAvailableSpot(Vehicle vehicle) throws SpotNotAvailableException {
        if (vehicle == null || vehicle.getVehicleType() == null) {
            throw new SpotNotAvailableException("Vehicle type is required");
        }
        cleanupExpiredReservations();
        for (SpotType type : getAllowedSpotTypes(vehicle.getVehicleType())) {
            ParkingSpot spot = garage.findAvailableSpot(type);
            if (spot != null) {
                return spot;
            }
        }
        throw new SpotNotAvailableException("No available parking spot for " + vehicle.getVehicleType());
    }

    private void cleanupExpiredReservations() {
        releaseExpiredReservations(LocalDateTime.now());
    }

    public void releaseExpiredReservations(LocalDateTime now) {
        if (now == null) {
            return;
        }
        for (ParkingSpot spot : garage.getAllSpots()) {
            if (spot.clearReservationIfExpired(now)) persistence.saveParkingSpot(garage, spot);
        }
        garage.updateAvailability();
    }

    public void reserveSpot(ParkingSpot spot, String userId, int holdMinutes) {
        if (spot == null) {
            throw new IllegalArgumentException("Parking spot cannot be null");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("Reservation holder is required");
        }
        if (holdMinutes <= 0) {
            throw new IllegalArgumentException("Hold time must be greater than 0");
        }
        if (!persistence.hasUser(userId)) {
            throw new IllegalArgumentException("Reservation holder does not exist: " + userId);
        }
        if (!spot.isAvailable()) {
            throw new IllegalStateException("Parking spot is not available for reservation");
        }
        if (!spot.reserveSpot(userId)) {
            throw new IllegalStateException("Parking spot could not be reserved");
        }
        spot.setReservationExpiry(LocalDateTime.now().plusMinutes(holdMinutes));
        garage.updateAvailability();
        persistence.saveParkingSpot(garage, spot);
    }

    public void reserveSpot(ParkingSpot spot, String userId) {
        reserveSpot(spot, userId, AppConfig.reservationHoldMinutes());
    }

    public Vehicle findVehicleByPlate(String plate) {
        if (plate == null || plate.isBlank()) {
            return null;
        }
        String normalized = plate.trim();
        for (ParkingSpot spot : garage.getAllSpots()) {
            String vehicleId = spot.getVehicleId();
            if (vehicleId == null || vehicleId.isBlank()) {
                continue;
            }
            Vehicle vehicle = garage.getVehicle(vehicleId);
            if (vehicle != null
                    && vehicle.getLicensePlate() != null
                    && vehicle.getLicensePlate().equalsIgnoreCase(normalized)) {
                return vehicle;
            }
        }
        return null;
    }

    public static int getMaxParkHours() {
        return AppConfig.maxParkHours();
    }

    /**
     * Allocates a specific parking spot to a vehicle.
     *
     * @param spot the parking spot to allocate
     * @param vehicle the vehicle to park
     * @throws SpotNotAvailableException if the spot is not available
     */
    public void allocateSpot(ParkingSpot spot, Vehicle vehicle) throws SpotNotAvailableException {
        // Check if spot is available
        if (!spot.isAvailable()) {
            throw new SpotNotAvailableException("The parking spot is not available");
        }
        // Occupy the spot with the vehicle
        if (!spot.occupySpot(vehicle.getVehicleId())) {
            throw new SpotNotAvailableException("The parking spot could not be occupied");
        }
        // Mark the vehicle as parked
        vehicle.park(spot.getSpotId());
        // Register the vehicle
        garage.registerVehicle(vehicle);
        // Update garage availability
        garage.updateAvailability();
        if (vehicle.getUserId() != null && userService != null) {
            User owner = null;
            try { owner = userService.getUserById(vehicle.getUserId()); }
            catch (RuntimeException ignored) { /* guest parking has no owner row */ }
            if (owner != null) persistence.saveUser(owner);
        }
        persistence.saveVehicle(vehicle);
        persistence.saveParkingSpot(garage, spot);
    }

    /**
     * Frees a parking spot when a vehicle exits.
     *
     * @param spot the parking spot to free
     * @param vehicle the vehicle to mark as unparked (optional)
     * @throws NullPointerException if either spot or vehicle is null
     */
    public synchronized void freeSpot(ParkingSpot spot) {
        if (spot == null) return;
        // Get the vehicle ID from the spot
        String vehicleId = spot.getVehicleId();
        // Find the vehicle and mark it as unparked
        if (vehicleId != null && !vehicleId.isEmpty()) {
            Vehicle vehicle = garage.getVehicle(vehicleId);
            if (vehicle != null) {
                vehicle.unpark();
                persistence.saveVehicle(vehicle);
            }
            garage.unregisterVehicle(vehicleId);
        }
        // Free the parking spot
        spot.freeSpot();
        // Update garage availability
        garage.updateAvailability();
        persistence.saveParkingSpot(garage, spot);
    }

    /**
     * Checks if the garage has available spots for a specific vehicle type.
     *
     * @param vehicleType the type of vehicle to check for
     * @return true if at least one spot is available, false otherwise
     */
    public boolean hasAvailableSpots(VehicleType vehicleType) {
        if (vehicleType == null) {
            return false;
        }
        cleanupExpiredReservations();
        for (SpotType type : getAllowedSpotTypes(vehicleType)) {
            if (!garage.getAvailableSpotsByType(type).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets the total number of available spots in the garage.
     *
     * @return total available spots count
     */
    public int getTotalAvailableSpots() {
        // Return the count of available spots from the garage
        return garage.getAvailableSpots();
    }

    public ParkingGarage getGarage() {
        return garage;
    }

}
