package com.parking;

import com.parking.enums.SpotType;
import com.parking.enums.UserRole;
import com.parking.enums.VehicleType;
import com.parking.model.Payment;
import com.parking.model.ParkingGarage;
import com.parking.model.ParkingSpot;
import com.parking.model.Customer;
import com.parking.model.Ticket;
import com.parking.model.User;
import com.parking.model.Vehicle;
import com.parking.persistence.PersistenceStore;
import com.parking.services.ParkingService;
import com.parking.services.PaymentService;
import com.parking.services.TicketService;
import com.parking.services.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class GaragePersistenceTest {
    @TempDir Path tempDir;

    private Path db() { return tempDir.resolve("garage.db"); }

    @Test
    void vehicleAndSpotSurviveRestart() {
        try (PersistenceStore store = new PersistenceStore(db())) {
            ParkingGarage garage = garage();
            User owner = new UserService(store).registerUser("owner", "Owner@123", "owner@example.com", UserRole.CUSTOMER);
            Vehicle vehicle = vehicle(owner.getUserId());
            store.saveGarage(garage);
            store.saveParkingSpot(garage, garage.getSpotById("S-1"));
            store.saveVehicle(vehicle);
        }

        try (PersistenceStore store = new PersistenceStore(db())) {
            assertEquals(1, store.loadVehicles().size());
            assertEquals("VH-1", store.loadVehicles().get(0).getVehicleId());
            assertEquals(1, store.loadParkingSpots("G-1").size());
            assertEquals("S-1", store.loadParkingSpots("G-1").get(0).spot().getSpotId());
        }
    }

    @Test
    void parkingEntrySurvivesRestart() throws Exception {
        String ticketId;
        try (PersistenceStore store = new PersistenceStore(db())) {
            ParkingGarage garage = garage();
            UserService users = new UserService(store);
            User owner = users.registerUser("owner", "Owner@123", "owner@example.com", UserRole.CUSTOMER);
            Vehicle vehicle = vehicle(owner.getUserId());
            garage.registerVehicleDetails(vehicle);
            store.saveVehicle(vehicle);
            persistSpots(store, garage);
            TicketService tickets = new TicketService(store);
            Ticket ticket = new ParkingService(garage, tickets, users, store).vehicleEntry(vehicle);
            ticketId = ticket.getTicketId();
        }

        try (PersistenceStore store = new PersistenceStore(db())) {
            ParkingGarage garage = restoreGarage(store);
            Vehicle vehicle = store.loadVehicles().get(0);
            garage.registerVehicleDetails(vehicle);
            garage.registerVehicle(vehicle);
            Ticket ticket = new TicketService(store).getTicketById(ticketId);
            assertTrue(vehicle.isParked());
            assertEquals("S-1", vehicle.getParkingSpotId());
            assertEquals("VH-1", garage.getSpotById("S-1").getVehicleId());
            assertEquals(com.parking.enums.TicketStatus.ACTIVE, ticket.getStatus());
        }
    }

    @Test
    void exitAndPaymentReleaseStateAcrossRestart() throws Exception {
        try (PersistenceStore store = new PersistenceStore(db())) {
            ParkingGarage garage = garage();
            UserService users = new UserService(store);
            User owner = users.registerUser("owner", "Owner@123", "owner@example.com", UserRole.CUSTOMER);
            Vehicle vehicle = vehicle(owner.getUserId());
            garage.registerVehicleDetails(vehicle);
            store.saveVehicle(vehicle);
            persistSpots(store, garage);
            TicketService tickets = new TicketService(store);
            ParkingService parking = new ParkingService(garage, tickets, users, store);
            Ticket ticket = parking.vehicleEntry(vehicle);
            parking.vehicleExit(ticket);
            PaymentService payments = new PaymentService(garage, users, tickets, store, parking);
            payments.processPayment(ticket, new Payment("P-1", ticket.getTicketId(), owner.getUserId(), 0, "TEST"));
        }

        try (PersistenceStore store = new PersistenceStore(db())) {
            ParkingGarage garage = restoreGarage(store);
            Vehicle vehicle = store.loadVehicles().get(0);
            assertFalse(vehicle.isParked());
            assertNull(vehicle.getParkingSpotId());
            assertNull(garage.getSpotById("S-1").getVehicleId());
            assertTrue(garage.getSpotById("S-1").isAvailable());
            assertEquals(com.parking.enums.TicketStatus.CLOSED, store.loadTickets().get(0).getStatus());
            assertEquals(1, store.loadPayments().size());
        }
    }

    @Test
    void paymentFailureRollsBackPaymentTicketAndSpotState() throws Exception {
        try (PersistenceStore store = new PersistenceStore(db())) {
            ParkingGarage garage = garage();
            UserService users = new UserService(store);
            User owner = users.registerUser("rollback", "Rollback@123", "rollback@example.com", UserRole.CUSTOMER);
            Vehicle vehicle = vehicle(owner.getUserId());
            garage.registerVehicleDetails(vehicle);
            store.saveVehicle(vehicle);
            persistSpots(store, garage);

            TicketService tickets = new TicketService(store);
            ParkingService parking = new ParkingService(garage, tickets, users, store);
            Ticket ticket = parking.vehicleEntry(vehicle);
            parking.vehicleExit(ticket);

            PaymentService payments = new PaymentService(garage, users, tickets, store, parking);
            store.failAfterWritesForTests(1);

            assertThrows(com.parking.exceptions.PaymentFailedException.class,
                    () -> payments.processPayment(ticket,
                            new Payment("P-rollback", ticket.getTicketId(), owner.getUserId(), 0, "TEST")));
            assertEquals(0, store.loadPayments().size());
            assertEquals(com.parking.enums.TicketStatus.AWAITING_PAYMENT, ticket.getStatus());
            assertTrue(vehicle.isParked());
            assertEquals("VH-1", garage.getSpotById("S-1").getVehicleId());
        }
    }

    @Test
    void parkingEntryFailureRestoresMemoryAndDatabaseState() throws Exception {
        try (PersistenceStore store = new PersistenceStore(db())) {
            ParkingGarage garage = garage();
            UserService users = new UserService(store);
            User owner = users.registerUser("entry-rollback", "EntryRollback@123", "entry-rollback@example.com", UserRole.CUSTOMER);
            Vehicle vehicle = vehicle(owner.getUserId());
            garage.registerVehicleDetails(vehicle);
            store.saveVehicle(vehicle);
            persistSpots(store, garage);

            TicketService tickets = new TicketService(store);
            ParkingService parking = new ParkingService(garage, tickets, users, store);

            // Entry writes the vehicle and spot before creating the ticket. Fail
            // at the ticket write so both persisted and staged state are tested.
            store.failAfterWritesForTests(2);
            assertThrows(RuntimeException.class, () -> parking.vehicleEntry(vehicle));

            assertTrue(tickets.getAllTickets().isEmpty());
            assertFalse(vehicle.isParked());
            assertNull(vehicle.getParkingSpotId());
            assertNull(garage.getSpotById("S-1").getVehicleId());
            assertTrue(garage.getSpotById("S-1").isAvailable());
            assertNull(garage.getVehicle(vehicle.getVehicleId()));
            assertTrue(((Customer) owner).getTicketIds().isEmpty());

            assertTrue(store.loadTickets().isEmpty());
            Vehicle persistedVehicle = store.loadVehicles().stream()
                    .filter(saved -> vehicle.getVehicleId().equals(saved.getVehicleId()))
                    .findFirst().orElseThrow();
            assertFalse(persistedVehicle.isParked());
            assertNull(persistedVehicle.getParkingSpotId());
            assertNull(store.loadParkingSpots("G-1").get(0).spot().getVehicleId());
        }
    }

    @Test
    void invalidFinancialValuesStatusesAndTimestampsAreRejected() {
        try (PersistenceStore store = new PersistenceStore(db())) {
            Ticket negativeTicket = new Ticket("T-negative", "V-missing", "S-missing", null);
            negativeTicket.setAmount(-1.0);
            assertThrows(IllegalArgumentException.class, () -> store.saveTicket(negativeTicket));

            Payment negativePayment = new Payment("P-negative", "T-missing", null, -1.0, "CASH");
            assertThrows(IllegalArgumentException.class, () -> store.savePayment(negativePayment));

            Payment invalidStatus = new Payment("P-status", "T-missing", null, 1.0, "CASH");
            invalidStatus.setStatus(null);
            assertThrows(IllegalArgumentException.class, () -> store.savePayment(invalidStatus));

            Ticket invalidTimestamp = new Ticket("T-time", "V-missing", "S-missing", null);
            invalidTimestamp.setEntryTime(null);
            assertThrows(IllegalArgumentException.class, () -> store.saveTicket(invalidTimestamp));
        }
    }

    @Test
    void reloadingDoesNotDuplicateRecords() {
        try (PersistenceStore store = new PersistenceStore(db())) {
            ParkingGarage garage = garage();
            persistSpots(store, garage);
            assertEquals(1, store.loadParkingSpots("G-1").size());
            assertEquals(1, store.loadParkingSpots("G-1").size());
        }
    }

    @Test
    void conflictingOccupancyAndInvalidReferencesAreRejected() {
        try (PersistenceStore store = new PersistenceStore(db())) {
            ParkingGarage garage = garage();
            ParkingSpot spot = garage.getSpotById("S-1");
            spot.setStatus(com.parking.enums.SpotStatus.OCCUPIED);
            assertThrows(IllegalArgumentException.class, () -> store.saveParkingSpot(garage, spot));

            Vehicle first = vehicle(null);
            Vehicle duplicatePlate = new Vehicle("VH-2", "ABC-123", VehicleType.CAR, "Ford", "Focus", "Blue", 2021, null);
            store.saveVehicle(first);
            assertThrows(RuntimeException.class, () -> store.saveVehicle(duplicatePlate));
        }
    }

    @Test
    void foreignKeysAreEnabledAndInvalidRelationshipsFailBeforePersisting() {
        try (PersistenceStore store = new PersistenceStore(db())) {
            assertTrue(store.foreignKeysEnabled());

            Ticket ticket = new Ticket("T-invalid", "missing-vehicle", "missing-spot", null);
            assertThrows(IllegalArgumentException.class, () -> store.saveTicket(ticket));

            Payment payment = new Payment("P-invalid", "missing-ticket", null, 1.0, "CASH");
            assertThrows(IllegalArgumentException.class, () -> store.savePayment(payment));
        }
    }

    @Test
    void freshInitializationRecordsSchemaVersionAndClosesConnection() throws Exception {
        PersistenceStore store = new PersistenceStore(db());
        assertTrue(store.foreignKeysEnabled());
        store.close();
        store.close();
        assertTrue(store.isClosed());

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db().toAbsolutePath());
             Statement statement = connection.createStatement();
             var version = statement.executeQuery("SELECT MAX(version) FROM schema_version")) {
            assertTrue(version.next());
            assertEquals(11, version.getInt(1));
        }
    }

    @Test
    void historicalRecordsSurviveUserRemovalWithOwnerReferencesDetached() {
        try (PersistenceStore store = new PersistenceStore(db())) {
            User owner = new UserService(store).registerUser("history", "History@123", "history@example.com", UserRole.CUSTOMER);
            ParkingGarage garage = garage();
            store.saveGarage(garage);
            store.saveParkingSpot(garage, garage.getSpotById("S-1"));
            Vehicle vehicle = vehicle(owner.getUserId());
            store.saveVehicle(vehicle);

            Ticket ticket = new Ticket("T-history", vehicle.getVehicleId(), "S-1", owner.getUserId());
            store.saveTicket(ticket);
            Payment payment = new Payment("P-history", ticket.getTicketId(), owner.getUserId(), 5.0, "CASH");
            store.savePayment(payment);
            ticket.setPaymentId(payment.getPaymentId());
            store.saveTicket(ticket);

            assertTrue(new UserService(store).deleteUser(owner.getUserId()));
            assertEquals(1, store.loadVehicles().size());
            assertEquals(1, store.loadTickets().size());
            assertEquals(1, store.loadPayments().size());
            assertNull(store.loadVehicles().get(0).getUserId());
            assertNull(store.loadTickets().get(0).getUserId());
            assertNull(store.loadPayments().get(0).getCustomerId());
        }
    }

    @Test
    void populatedLegacyDatabaseMigratesIdempotently() throws Exception {
        createLegacyDatabase(db());

        try (PersistenceStore store = new PersistenceStore(db())) {
            assertTrue(store.foreignKeysEnabled());
            assertEquals(1, store.loadVehicles().size());
            assertEquals(1, store.loadTickets().size());
            assertEquals(1, store.loadPayments().size());
            assertEquals(5.0, store.loadPayments().get(0).getAmount());
            assertEquals(5.0, store.loadPayments().get(0).getFinalAmount());
        }
        try (PersistenceStore store = new PersistenceStore(db())) {
            assertEquals(1, store.loadVehicles().size());
            assertEquals(1, store.loadTickets().size());
            assertEquals(1, store.loadPayments().size());
        }
    }

    @Test
    void invalidLegacyReferencesAbortMigrationWithoutCleanup() throws Exception {
        createLegacyDatabase(db());
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db().toAbsolutePath());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO payments VALUES ('P-orphan','missing-ticket',NULL,'FAILED',0.0,0.0,0.0,NULL,'CASH')");
        }

        assertThrows(IllegalStateException.class, () -> new PersistenceStore(db()));
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db().toAbsolutePath());
             Statement statement = connection.createStatement();
             var result = statement.executeQuery("SELECT COUNT(*) FROM payments WHERE payment_id='P-orphan'")) {
            assertTrue(result.next());
            assertEquals(1, result.getInt(1));
        }
    }

    private void createLegacyDatabase(Path path) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + path.toAbsolutePath());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE users (user_id TEXT PRIMARY KEY, username TEXT NOT NULL UNIQUE, email TEXT NOT NULL UNIQUE, password_hash TEXT NOT NULL, role TEXT NOT NULL, active INTEGER NOT NULL, updated_at TEXT NOT NULL)");
            statement.executeUpdate("CREATE TABLE vehicles (vehicle_id TEXT PRIMARY KEY, license_plate TEXT NOT NULL UNIQUE COLLATE NOCASE, vehicle_type TEXT NOT NULL, make TEXT, model TEXT, color TEXT, year INTEGER NOT NULL, owner_id TEXT, entry_time TEXT, parking_spot_id TEXT, is_parked INTEGER NOT NULL DEFAULT 0)");
            statement.executeUpdate("CREATE TABLE parking_spots (spot_id TEXT PRIMARY KEY, garage_id TEXT NOT NULL, level INTEGER NOT NULL, spot_type TEXT NOT NULL, location TEXT, hourly_rate REAL NOT NULL, status TEXT NOT NULL, vehicle_id TEXT, reservation_holder TEXT, reservation_expiry TEXT, under_maintenance INTEGER NOT NULL DEFAULT 0, maintenance_reason TEXT)");
            statement.executeUpdate("CREATE TABLE tickets (ticket_id TEXT PRIMARY KEY, vehicle_id TEXT NOT NULL, owner_id TEXT NOT NULL, spot_id TEXT NOT NULL, status TEXT NOT NULL, entry_time TEXT, exit_time TEXT, amount REAL NOT NULL, final_amount REAL NOT NULL, payment_id TEXT)");
            statement.executeUpdate("CREATE TABLE payments (payment_id TEXT PRIMARY KEY, ticket_id TEXT NOT NULL, customer_id TEXT, status TEXT NOT NULL, amount REAL NOT NULL, tax_amount REAL NOT NULL, final_amount REAL NOT NULL, payment_time TEXT, payment_method TEXT)");
            statement.executeUpdate("INSERT INTO users VALUES ('U-1','legacy','legacy@example.com','hash','CUSTOMER',1,'2025-01-01')");
            statement.executeUpdate("INSERT INTO vehicles VALUES ('V-1','LEG-123','CAR','Toyota','Corolla','Blue',2020,'U-1',NULL,NULL,0)");
            statement.executeUpdate("INSERT INTO parking_spots VALUES ('S-1','G-1',0,'STANDARD','Level 1',5.0,'AVAILABLE',NULL,NULL,NULL,0,NULL)");
            statement.executeUpdate("INSERT INTO tickets VALUES ('T-1','V-1','U-1','S-1','CLOSED','2025-01-01T10:00:00','2025-01-01T11:00:00',5.0,5.0,NULL)");
            statement.executeUpdate("INSERT INTO payments VALUES ('P-1','T-1','U-1','COMPLETED',5.0,0.0,5.0,'2025-01-01T11:00:00','CASH')");
        }
    }

    @Test
    void reservationHoldSurvivesRestart() {
        Path db = db();
        String reservationId;
        try (PersistenceStore store = new PersistenceStore(db)) {
            ParkingGarage garage = garage();
            UserService users = new UserService(store);
            User owner = users.registerUser("holder", "Holder@123", "holder@example.com", UserRole.CUSTOMER);
            persistSpots(store, garage);
            reservationId = new com.parking.services.ReservationService(garage, store,
                    new com.parking.services.NotificationService(store))
                    .reserve(owner, "S-1", java.time.LocalDateTime.of(2026, 9, 15, 10, 0))
                    .reservationId();
        }

        try (PersistenceStore store = new PersistenceStore(db)) {
            var row = store.loadReservation(reservationId).orElseThrow();
            assertEquals("ACTIVE", row.status());
            assertEquals("S-1", row.spotId());
            ParkingGarage garage = restoreGarage(store);
            assertEquals(com.parking.enums.SpotStatus.RESERVED, garage.getSpotById("S-1").getStatus());
        }
    }

    @Test
    void dutySessionSurvivesRestart() {
        Path db = db();
        String sessionId;
        try (PersistenceStore store = new PersistenceStore(db)) {
            User attendant = new UserService(store).registerUser(
                    "attendant", "Attendant@123", "attendant@example.com", UserRole.ATTENDANT);
            sessionId = new com.parking.services.DutyService(store,
                    new com.parking.services.NotificationService(store))
                    .start(attendant, "Morning", "Zone A",
                            java.time.LocalDateTime.of(2026, 9, 15, 8, 0))
                    .sessionId();
        }

        try (PersistenceStore store = new PersistenceStore(db)) {
            var row = store.loadOpenDutySession(
                    store.loadUsers().stream().filter(user -> user.getUsername().equals("attendant"))
                            .findFirst().orElseThrow().getUserId()).orElseThrow();
            assertEquals(sessionId, row.sessionId());
            assertEquals("OPEN", row.status());
            assertEquals("Morning", row.shift());
            assertEquals("Zone A", row.zone());
        }
    }

    @Test
    void notificationsPreferencesReservationsAndDutySurviveRestart() {
        Path db = db();
        String reservationId;
        String customerId;
        String attendantId;
        try (PersistenceStore store = new PersistenceStore(db)) {
            ParkingGarage garage = garage();
            UserService users = new UserService(store);
            User customer = users.registerUser("restart-customer", "Customer@123!", "restart-customer@example.com", UserRole.CUSTOMER);
            User attendant = users.registerUser("restart-attendant", "Attendant@123!", "restart-attendant@example.com", UserRole.ATTENDANT);
            customerId = customer.getUserId();
            attendantId = attendant.getUserId();
            persistSpots(store, garage);
            com.parking.services.NotificationService notifications =
                    new com.parking.services.NotificationService(store);
            notifications.savePreference(new com.parking.model.NotificationPreference(customerId,
                    java.util.Set.of(com.parking.enums.NotificationType.values()), "IMMEDIATE", false));
            reservationId = new com.parking.services.ReservationService(garage, store, notifications)
                    .reserve(customer, "S-1", java.time.LocalDateTime.of(2026, 9, 15, 10, 0))
                    .reservationId();
            notifications.publish(customerId, com.parking.enums.NotificationType.PAYMENT_SUCCESS, "receipt ready");
            new com.parking.services.DutyService(store, notifications)
                    .start(attendant, "Morning", "Zone A",
                            java.time.LocalDateTime.of(2026, 9, 15, 8, 0));
        }

        try (PersistenceStore store = new PersistenceStore(db)) {
            UserService users = new UserService(store);
            com.parking.services.NotificationService notifications =
                    new com.parking.services.NotificationService(store);
            User customer = users.getUserById(customerId);
            User attendant = users.getUserById(attendantId);

            assertEquals("ACTIVE", store.loadReservation(reservationId).orElseThrow().status());
            assertEquals("S-1", store.loadReservation(reservationId).orElseThrow().spotId());
            assertEquals("OPEN", store.loadOpenDutySession(attendantId).orElseThrow().status());
            assertEquals(java.util.Set.of(com.parking.enums.NotificationType.values()),
                    java.util.Set.copyOf(notifications.loadPreference(customerId).enabledTypes()));
            // One reservation confirmation plus one explicit payment notice.
            assertEquals(2, notifications.listUnread(customer).size());
            // Duty start notice for the attendant.
            assertEquals(1, notifications.listUnread(attendant).size());

            ParkingGarage garage = restoreGarage(store);
            assertEquals(com.parking.enums.SpotStatus.RESERVED, garage.getSpotById("S-1").getStatus());
        }
    }

    private ParkingGarage garage() {
        ParkingGarage garage = new ParkingGarage("G-1", "Test Garage", "Test Address", 1, 5.0);
        garage.addParkingSpot(new ParkingSpot("S-1", SpotType.STANDARD, "Level 1", 5.0), 0);
        return garage;
    }

    private Vehicle vehicle(String ownerId) {
        return new Vehicle("VH-1", "ABC-123", VehicleType.CAR, "Toyota", "Corolla", "Silver", 2022, ownerId);
    }

    private void persistSpots(PersistenceStore store, ParkingGarage garage) {
        store.saveGarage(garage);
        for (ParkingSpot spot : garage.getAllSpots()) store.saveParkingSpot(garage, spot);
    }

    private ParkingGarage restoreGarage(PersistenceStore store) {
        ParkingGarage garage = new ParkingGarage("G-1", "Test Garage", "Test Address", 1, 5.0);
        for (PersistenceStore.SpotSnapshot snapshot : store.loadParkingSpots("G-1")) {
            garage.addParkingSpot(snapshot.spot(), snapshot.level());
        }
        return garage;
    }
}
