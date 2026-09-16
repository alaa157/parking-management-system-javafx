package com.parking.gui.shell;

import com.parking.config.AppConfig;
import com.parking.enums.SpotType;
import com.parking.enums.SpotStatus;
import com.parking.enums.TicketStatus;
import com.parking.enums.UserRole;
import com.parking.gui.BackgroundTaskRunner;
import com.parking.gui.ToastManager;
import com.parking.model.ParkingGarage;
import com.parking.model.ParkingSpot;
import com.parking.model.Ticket;
import com.parking.model.User;
import com.parking.model.Vehicle;
import com.parking.model.Customer;
import com.parking.persistence.PersistenceStore;
import com.parking.services.GarageContext;
import com.parking.services.GarageService;
import com.parking.services.DutyService;
import com.parking.services.NotificationService;
import com.parking.services.OperationsSnapshotService;
import com.parking.services.ParkingService;
import com.parking.services.PaymentService;
import com.parking.services.ReservationService;
import com.parking.services.TicketService;
import com.parking.services.UserService;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Application-owned service graph and persistence bootstrap. */
public final class AppServices {
    public final PersistenceStore persistence;
    public final BackgroundTaskRunner backgroundTasks;
    public final UserService users;
    public final GarageService garages;
    public final ToastManager toasts;
    public ParkingGarage garage;
    public ParkingService parking;
    public TicketService tickets;
    public PaymentService payments;
    public OperationsSnapshotService snapshots;
    public NotificationService notifications;
    public ReservationService reservations;
    public DutyService duty;
    public GarageContext garageContext;

    private AppServices() {
        persistence = new PersistenceStore();
        backgroundTasks = new BackgroundTaskRunner();
        users = new UserService(persistence);
        garages = new GarageService(persistence);
        toasts = new ToastManager();
    }

    public static AppServices boot() {
        AppServices services = new AppServices();
        services.initialize();
        return services;
    }

    public void shutdown() {
        backgroundTasks.close();
        toasts.clear();
        persistence.close();
    }

    private void initialize() {
        AppConfig.load();
        garage = new ParkingGarage(
                AppConfig.GARAGE_ID,
                AppConfig.garageName(),
                AppConfig.garageAddress(),
                3,
                AppConfig.baseRate());

        restoreGarage();
        boolean demoMode = Boolean.getBoolean("parkingos.demo");
        if (demoMode) seedUsers();

        restorePersistedVehicles();
        tickets = new TicketService(persistence);
        restorePersistedTicketReferences();
        parking = new ParkingService(garage, tickets, users, persistence);
        payments = new PaymentService(garage, users, tickets, persistence, parking);
        notifications = new NotificationService(persistence);
        reservations = new ReservationService(garage, persistence, notifications);
        duty = new DutyService(persistence, notifications);
        parking.setReservationService(reservations);
        payments.setNotificationService(notifications);
        snapshots = new OperationsSnapshotService(parking, tickets, payments);
        users.setTicketService(tickets);
        if (demoMode) seedPhase4Users();
        garage.updateAvailability();
    }

    private void restoreGarage() {
        var savedSpots = persistence.loadParkingSpots(garage.getGarageId());
        if (savedSpots.isEmpty()) {
            buildPhase2Spots();
            persistence.saveGarage(garage);
            garage.getAllSpots().forEach(spot -> persistence.saveParkingSpot(garage, spot));
            return;
        }
        for (PersistenceStore.SpotSnapshot saved : savedSpots) {
            if (!garage.addParkingSpot(saved.spot(), saved.level())) {
                throw new IllegalStateException("Duplicate persisted parking spot: " + saved.spot().getSpotId());
            }
        }
    }

    private void restorePersistedVehicles() {
        Map<String, Vehicle> vehicles = new HashMap<>();
        for (Vehicle vehicle : persistence.loadVehicles()) {
            if (vehicles.put(vehicle.getVehicleId(), vehicle) != null) {
                throw new IllegalStateException("Duplicate persisted vehicle: " + vehicle.getVehicleId());
            }
            garage.registerVehicleDetails(vehicle);
            if (vehicle.getUserId() != null) {
                try {
                    User owner = users.getUserById(vehicle.getUserId());
                    if (owner instanceof Customer customer) customer.addVehicle(vehicle);
                } catch (RuntimeException ignored) {
                    // Guest vehicles may not have an owner.
                }
            }
        }
        Set<String> occupiedVehicles = new HashSet<>();
        for (ParkingSpot spot : garage.getAllSpots()) {
            if (spot.getStatus() != SpotStatus.OCCUPIED) {
                if (spot.getVehicleId() != null) {
                    throw new IllegalStateException("Available spot retains a vehicle: " + spot.getSpotId());
                }
                continue;
            }
            Vehicle vehicle = vehicles.get(spot.getVehicleId());
            if (vehicle == null || !vehicle.isParked() || !spot.getSpotId().equals(vehicle.getParkingSpotId())) {
                throw new IllegalStateException("Occupied spot has an invalid vehicle reference: " + spot.getSpotId());
            }
            if (!occupiedVehicles.add(vehicle.getVehicleId())) {
                throw new IllegalStateException("Vehicle occupies multiple spots: " + vehicle.getVehicleId());
            }
            garage.registerVehicle(vehicle);
        }
        for (Vehicle vehicle : vehicles.values()) {
            if (vehicle.isParked() && !occupiedVehicles.contains(vehicle.getVehicleId())) {
                throw new IllegalStateException("Parked vehicle has no occupied spot: " + vehicle.getVehicleId());
            }
        }
    }

    private void restorePersistedTicketReferences() {
        for (Ticket ticket : tickets.getAllTickets()) {
            Vehicle vehicle = garage.getRegisteredVehicle(ticket.getVehicleId());
            ParkingSpot spot = garage.getSpotById(ticket.getParkingSpotId());
            if (vehicle == null || spot == null) {
                throw new IllegalStateException("Persisted ticket has an unresolved vehicle or spot: " + ticket.getTicketId());
            }
            if (ticket.getStatus() == TicketStatus.ACTIVE || ticket.getStatus() == TicketStatus.AWAITING_PAYMENT) {
                if (spot.getVehicleId() == null) {
                    if (!spot.occupySpot(vehicle.getVehicleId())) {
                        throw new IllegalStateException("Could not recover occupancy for ticket: " + ticket.getTicketId());
                    }
                    vehicle.park(spot.getSpotId());
                    garage.registerVehicle(vehicle);
                    persistence.saveVehicle(vehicle);
                    persistence.saveParkingSpot(garage, spot);
                } else if (!vehicle.getVehicleId().equals(spot.getVehicleId())
                        || !vehicle.isParked() || !spot.getSpotId().equals(vehicle.getParkingSpotId())) {
                    throw new IllegalStateException("Persisted ticket conflicts with occupancy: " + ticket.getTicketId());
                }
            }
            if (ticket.getUserId() != null) {
                try {
                    User owner = users.getUserById(ticket.getUserId());
                    if (owner instanceof Customer customer) customer.addTicket(ticket.getTicketId());
                } catch (RuntimeException ignored) {
                    // Guest tickets do not need customer history.
                }
            }
        }
        garage.updateAvailability();
    }

    private void buildPhase2Spots() {
        for (int level = 0; level < 3; level++) {
            for (int i = 1; i <= 20; i++) {
                SpotType type;
                if (i == 5 || i == 15) type = SpotType.HANDICAPPED;
                else if (level == 2 && (i == 4 || i == 8 || i == 12 || i == 16)) type = SpotType.EV_CHARGING;
                else if (i % 5 == 0) type = SpotType.LARGE;
                else if (i % 3 == 0) type = SpotType.COMPACT;
                else type = SpotType.STANDARD;

                double rate = switch (type) {
                    case COMPACT -> 4.50;
                    case LARGE -> 6.00;
                    case HANDICAPPED -> 3.00;
                    case EV_CHARGING -> 7.00;
                    default -> 5.00;
                };
                garage.addParkingSpot(new ParkingSpot(
                        "L" + (level + 1) + "-" + String.format("%02d", i),
                        type, "Level " + (level + 1), rate), level);
            }
        }
    }

    private void seedUsers() {
        seedSafely("admin", "Admin@123!", "admin@parking.com", UserRole.ADMIN, "Alaa");
        seedSafely("attendant", "Attendant@123!", "attendant@parking.com", UserRole.ATTENDANT, "Adel");
        seedSafely("customer", "Customer@123!", "customer@parking.com", UserRole.CUSTOMER, "Ahmed");
    }

    private void seedSafely(String username, String password, String email, UserRole role, String fullName) {
        try {
            User existing = users.getUserByUsername(username);
            if (existing != null) {
                existing.setFullName(fullName);
                return;
            }
        } catch (RuntimeException ignored) {
            // User does not exist yet.
        }
        User user = users.registerUser(username, password, email, role);
        user.setFullName(fullName);
    }

    private void seedPhase4Users() {
        seedManagedUser("john.doe", "John Doe", "john.doe@parking.com", UserRole.CUSTOMER, true);
        seedManagedUser("sara.miller", "Sara Miller", "sara.miller@parking.com", UserRole.CUSTOMER, true);
        seedManagedUser("mike.wilson", "Mike Wilson", "mike.wilson@parking.com", UserRole.CUSTOMER, false);
        seedManagedUser("lina.hassan", "Lina Hassan", "lina.hassan@parking.com", UserRole.CUSTOMER, true);
        seedManagedUser("omar.ali", "Omar Ali", "omar.ali@parking.com", UserRole.CUSTOMER, true);
        seedManagedUser("nora.salem", "Nora Salem", "nora.salem@parking.com", UserRole.CUSTOMER, true);
        seedManagedUser("karim.fathy", "Karim Fathy", "karim.fathy@parking.com", UserRole.CUSTOMER, false);
        seedManagedUser("youssef.adel", "Youssef Adel", "youssef.adel@parking.com", UserRole.CUSTOMER, true);
        seedManagedUser("mariam.ahmed", "Mariam Ahmed", "mariam.ahmed@parking.com", UserRole.ATTENDANT, true);
        seedManagedUser("hassan.mohamed", "Hassan Mohamed", "hassan.mohamed@parking.com", UserRole.ATTENDANT, true);
        seedManagedUser("dina.ali", "Dina Ali", "dina.ali@parking.com", UserRole.ATTENDANT, false);
        seedManagedUser("mostafa.samir", "Mostafa Samir", "mostafa.samir@parking.com", UserRole.ATTENDANT, true);
        seedManagedUser("salma.khaled", "Salma Khaled", "salma.khaled@parking.com", UserRole.ATTENDANT, true);
        seedManagedUser("ramy.tarek", "Ramy Tarek", "ramy.tarek@parking.com", UserRole.ATTENDANT, false);
        seedManagedUser("admin.ops", "Operations Admin", "admin.ops@parking.com", UserRole.ADMIN, true);
        seedManagedUser("admin.finance", "Finance Admin", "admin.finance@parking.com", UserRole.ADMIN, true);
    }

    private void seedManagedUser(String username, String fullName, String email, UserRole role, boolean active) {
        try {
            users.getUserByUsername(username);
            return;
        } catch (RuntimeException ignored) {
        }
        try {
            User user = users.registerUser(username, "Parking@123!", email, role);
            user.setFullName(fullName);
            user.setActive(active);
            user.setLastLogin(active ? LocalDateTime.now().minusHours((username.hashCode() & 7) + 1) : null);
        } catch (RuntimeException ignored) {
            // Demo seed data must not prevent startup.
        }
    }
}
