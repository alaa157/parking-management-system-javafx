package com.parking;

import com.parking.enums.SpotType;
import com.parking.enums.UserRole;
import com.parking.enums.VehicleType;
import com.parking.exceptions.AuthorizationException;
import com.parking.model.DutySession;
import com.parking.model.ParkingGarage;
import com.parking.model.ParkingSpot;
import com.parking.model.Payment;
import com.parking.model.Ticket;
import com.parking.model.User;
import com.parking.model.Vehicle;
import com.parking.persistence.PersistenceStore;
import com.parking.services.DutyService;
import com.parking.services.DutySummary;
import com.parking.services.NotificationService;
import com.parking.services.ParkingService;
import com.parking.services.PaymentService;
import com.parking.services.TicketService;
import com.parking.services.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DutyServiceTest {
    @TempDir Path tempDir;

    private record Fixture(PersistenceStore store, ParkingGarage garage, UserService users,
                           NotificationService notifications, DutyService duty,
                           User admin, User attendant, User owner) { }

    private Fixture fixture(String dbName) {
        Path db = tempDir.resolve(dbName);
        PersistenceStore store = new PersistenceStore(db);
        ParkingGarage garage = new ParkingGarage("G-1", "Test Garage", "Test Address", 1, 5.0);
        garage.addParkingSpot(new ParkingSpot("S-1", SpotType.STANDARD, "Level 1", 5.0), 0);
        store.saveGarage(garage);
        for (ParkingSpot spot : garage.getAllSpots()) store.saveParkingSpot(garage, spot);
        UserService users = new UserService(store);
        User admin = users.registerUser("admin", "Admin@123!", "admin@example.com", UserRole.ADMIN);
        User attendant = users.registerUser("attendant", "Attendant@123!", "attendant@example.com", UserRole.ATTENDANT);
        User owner = users.registerUser("owner", "Owner@123!", "owner@example.com", UserRole.CUSTOMER);
        NotificationService notifications = new NotificationService(store);
        DutyService duty = new DutyService(store, notifications);
        return new Fixture(store, garage, users, notifications, duty, admin, attendant, owner);
    }

    @Test
    void startCreatesOneOpenSessionAndPublishesStartEvent() {
        Fixture f = fixture("start.db");
        LocalDateTime now = LocalDateTime.now().minusHours(2);
        DutySession session = f.duty.start(f.attendant, "Morning", "Zone A", now);

        assertEquals(f.attendant.getUserId(), session.attendantId());
        assertEquals("Morning", session.shift());
        assertEquals("Zone A", session.zone());
        assertEquals("OPEN", session.status());
        assertNull(session.endedAt());
        Optional<DutySession> current = f.duty.current(f.attendant);
        assertTrue(current.isPresent());
        assertEquals(session.sessionId(), current.get().sessionId());
        assertEquals(1, f.notifications.listUnread(f.attendant).size());
    }

    @Test
    void duplicateStartIsRejected() {
        Fixture f = fixture("duplicate.db");
        LocalDateTime now = LocalDateTime.now().minusHours(2);
        DutySession first = f.duty.start(f.attendant, "Morning", "Zone A", now);
        assertThrows(IllegalStateException.class,
                () -> f.duty.start(f.attendant, "Evening", "Zone B", now.plusMinutes(5)));
        assertEquals(first.sessionId(), f.duty.current(f.attendant).orElseThrow().sessionId());
    }

    @Test
    void customerStartThrowsAuthorization() {
        Fixture f = fixture("auth.db");
        assertThrows(AuthorizationException.class,
                () -> f.duty.start(f.owner, "Morning", "Zone A", LocalDateTime.now()));
        assertTrue(f.duty.current(f.owner).isEmpty());
    }

    @Test
    void endWithoutSessionThrows() {
        Fixture f = fixture("end-empty.db");
        assertThrows(IllegalStateException.class,
                () -> f.duty.end(f.attendant, LocalDateTime.now()));
    }

    @Test
    void endClosesSessionAndPublishesEndEvent() {
        Fixture f = fixture("end.db");
        LocalDateTime start = LocalDateTime.now().minusHours(2);
        DutySession opened = f.duty.start(f.attendant, "Morning", "Zone A", start);
        DutySession closed = f.duty.end(f.attendant, start.plusHours(2));

        assertEquals(opened.sessionId(), closed.sessionId());
        assertEquals("CLOSED", closed.status());
        assertEquals(start.plusHours(2), closed.endedAt());
        assertTrue(f.duty.current(f.attendant).isEmpty());
        assertEquals(2, f.notifications.listUnread(f.attendant).size());
    }

    @Test
    void nonAdminSummaryThrowsAuthorization() {
        Fixture f = fixture("summary-auth.db");
        LocalDateTime from = LocalDateTime.now().minusHours(3);
        LocalDateTime to = LocalDateTime.now().plusHours(1);
        assertThrows(AuthorizationException.class, () -> f.duty.summary(f.attendant, from, to));
        assertThrows(AuthorizationException.class, () -> f.duty.summary(f.owner, from, to));
    }

    @Test
    void adminSummaryTotalsAttributePaymentSuccessToOpenSession() throws Exception {
        Fixture f = fixture("totals.db");
        LocalDateTime start = LocalDateTime.now().minusHours(2);
        f.duty.start(f.attendant, "Morning", "Zone A", start);

        Vehicle vehicle = new Vehicle("VH-1", "ABC-123", VehicleType.CAR,
                "Toyota", "Corolla", "Silver", 2022, f.owner.getUserId());
        f.garage.registerVehicleDetails(vehicle);
        f.store.saveVehicle(vehicle);
        TicketService tickets = new TicketService(f.store);
        ParkingService parking = new ParkingService(f.garage, tickets, f.users, f.store);
        Ticket ticket = parking.vehicleEntry(vehicle);
        parking.vehicleExit(ticket);
        PaymentService payments = new PaymentService(f.garage, f.users, tickets, f.store, parking);
        Payment payment = payments.processPayment(ticket,
                new Payment("P-1", ticket.getTicketId(), f.owner.getUserId(), 0, "TEST"));

        f.duty.end(f.attendant, LocalDateTime.now());
        LocalDateTime from = start.minusHours(1);
        LocalDateTime to = LocalDateTime.now().plusHours(1);
        DutySummary summary = f.duty.summary(f.admin, f.attendant.getUserId(), from, to);

        assertEquals(f.attendant.getUserId(), summary.attendantId());
        assertEquals(1, summary.completedTransactions());
        assertEquals(payment.getFinalAmount(), summary.revenue(), 0.0001);
        assertTrue(summary.totalDuration().compareTo(Duration.ofHours(1)) >= 0);
    }

    @Test
    void adminOwnSummaryIsAdminOnlyAndCountsOwnSession() {
        Fixture f = fixture("admin-own.db");
        LocalDateTime start = LocalDateTime.now().minusHours(2);
        f.duty.start(f.admin, "Morning", "Zone A", start);
        f.duty.end(f.admin, start.plusHours(2));

        DutySummary summary = f.duty.summary(f.admin, start.minusHours(1), start.plusHours(3));
        assertEquals(f.admin.getUserId(), summary.attendantId());
        assertEquals(0, summary.completedTransactions());
        assertEquals(Duration.ofHours(2), summary.totalDuration());
    }
}
