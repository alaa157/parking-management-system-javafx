package com.parking;

import com.parking.enums.ReservationStatus;
import com.parking.enums.SpotType;
import com.parking.enums.TicketStatus;
import com.parking.enums.UserRole;
import com.parking.enums.VehicleType;
import com.parking.model.CashPayment;
import com.parking.model.DutySession;
import com.parking.model.ParkingGarage;
import com.parking.model.ParkingSpot;
import com.parking.model.Payment;
import com.parking.model.Reservation;
import com.parking.model.Ticket;
import com.parking.model.User;
import com.parking.model.Vehicle;
import com.parking.persistence.PersistenceStore;
import com.parking.services.DutyService;
import com.parking.services.DutySummary;
import com.parking.services.NotificationService;
import com.parking.services.OperationsSnapshot;
import com.parking.services.OperationsSnapshotService;
import com.parking.services.ParkingService;
import com.parking.services.PaymentService;
import com.parking.services.ReservationService;
import com.parking.services.TicketService;
import com.parking.services.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Task 5 integration verification: one persisted backbone flow from
 * reservation through notification, claim-for-entry, payment, duty totals,
 * and the operations snapshot. All timestamps derive from fixed offsets
 * around the wall clock so the duty window always contains the payment time
 * and the snapshot date always matches the payment date.
 */
class NotificationBackboneEndToEndTest {
    @TempDir Path tempDir;

    @Test
    void reserveNotifyClaimPayDutySnapshotEndToEnd() throws Exception {
        Path db = tempDir.resolve("backbone-e2e.db");
        LocalDateTime dutyStart = LocalDateTime.now().minusHours(2);
        LocalDateTime reserveAt = dutyStart.plusMinutes(30);
        LocalDateTime claimAt = reserveAt.plusMinutes(10);

        try (PersistenceStore store = new PersistenceStore(db)) {
            ParkingGarage garage = new ParkingGarage("G-1", "Test Garage", "Test Address", 1, 5.0);
            garage.addParkingSpot(new ParkingSpot("S-1", SpotType.STANDARD, "Level 1", 5.0), 0);
            garage.addParkingSpot(new ParkingSpot("S-2", SpotType.STANDARD, "Level 1", 5.0), 0);
            store.saveGarage(garage);
            for (ParkingSpot spot : garage.getAllSpots()) store.saveParkingSpot(garage, spot);

            UserService users = new UserService(store);
            User admin = users.registerUser("e2e-admin", "Admin@123!", "e2e-admin@example.com", UserRole.ADMIN);
            User attendant = users.registerUser("e2e-attendant", "Attendant@123!", "e2e-attendant@example.com", UserRole.ATTENDANT);
            User customer = users.registerUser("e2e-customer", "Customer@123!", "e2e-customer@example.com", UserRole.CUSTOMER);

            NotificationService notifications = new NotificationService(store);
            ReservationService reservations = new ReservationService(garage, store, notifications);
            DutyService duty = new DutyService(store, notifications);
            TicketService tickets = new TicketService(store);
            ParkingService parking = new ParkingService(garage, tickets, users, store);
            parking.setReservationService(reservations);
            PaymentService payments = new PaymentService(garage, users, tickets, store, parking);
            OperationsSnapshotService snapshots = new OperationsSnapshotService(parking, tickets, payments);

            // Duty start publishes one backbone notification for the attendant.
            DutySession opened = duty.start(attendant, "Morning", "Zone A", dutyStart);
            assertEquals("OPEN", opened.status());
            assertEquals(1, notifications.listUnread(attendant).size());

            // Reserve publishes one backbone notification for the holder.
            // Explicit 120-minute hold keeps the fixed claim time inside the window.
            Reservation reservation = reservations.reserve(customer, "S-1", reserveAt, 120);
            assertEquals(ReservationStatus.ACTIVE, reservation.status());
            assertEquals("S-1", reservation.spotId());
            assertEquals(1, notifications.listUnread(customer).size());

            // Claim-for-entry parks on the held spot and publishes again.
            Vehicle vehicle = new Vehicle("VH-E2E", "E2E-123", VehicleType.CAR,
                    "Toyota", "Corolla", "Silver", 2022, customer.getUserId());
            Ticket ticket = parking.vehicleEntry(customer, vehicle, reservation.reservationId(), claimAt);
            assertEquals("S-1", ticket.getParkingSpotId());
            assertEquals(TicketStatus.ACTIVE, ticket.getStatus());
            assertEquals("CLAIMED", store.loadReservation(reservation.reservationId()).orElseThrow().status());
            assertEquals(2, notifications.listUnread(customer).size());

            // Exit then pay closes the ticket and frees the held spot.
            parking.vehicleExit(ticket);
            assertEquals(TicketStatus.AWAITING_PAYMENT, ticket.getStatus());
            Payment payment = payments.processPayment(ticket,
                    new CashPayment("PAY-E2E", ticket.getTicketId(), customer.getUserId(), 0, 100.0, "E2E-Test"));
            assertEquals(TicketStatus.CLOSED, ticket.getStatus());
            assertEquals(com.parking.enums.PaymentStatus.COMPLETED, payment.getStatus());

            // Duty end publishes for the attendant; the payment falls inside
            // the open session window so the admin summary attributes it.
            LocalDateTime dutyEnd = payment.getPaymentTime().plusMinutes(30);
            DutySession closed = duty.end(attendant, dutyEnd);
            assertEquals("CLOSED", closed.status());
            assertEquals(2, notifications.listUnread(attendant).size());

            DutySummary summary = duty.summary(admin, attendant.getUserId(),
                    dutyStart.minusHours(1), dutyEnd.plusHours(1));
            assertEquals(attendant.getUserId(), summary.attendantId());
            assertEquals(1, summary.completedTransactions());
            assertEquals(payment.getFinalAmount(), summary.revenue(), 0.0001);

            // The snapshot sees the freed spot, no active tickets, and the
            // completed revenue on the payment's own date.
            OperationsSnapshot snapshot = snapshots.snapshot(admin, payment.getPaymentTime());
            assertEquals(2, snapshot.available());
            assertEquals(0, snapshot.activeTicketCount());
            assertEquals(payment.getFinalAmount(), snapshot.completedRevenue(), 0.0001);
        }
    }
}
