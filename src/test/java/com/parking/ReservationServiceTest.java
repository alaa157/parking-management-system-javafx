package com.parking;

import com.parking.config.AppConfig;
import com.parking.enums.ReservationStatus;
import com.parking.enums.SpotStatus;
import com.parking.enums.SpotType;
import com.parking.enums.TicketStatus;
import com.parking.enums.UserRole;
import com.parking.enums.VehicleType;
import com.parking.exceptions.AuthorizationException;
import com.parking.model.ParkingGarage;
import com.parking.model.ParkingSpot;
import com.parking.model.Reservation;
import com.parking.model.User;
import com.parking.model.Vehicle;
import com.parking.persistence.PersistenceStore;
import com.parking.services.NotificationService;
import com.parking.services.ParkingService;
import com.parking.services.ReservationService;
import com.parking.services.TicketService;
import com.parking.services.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReservationServiceTest {
    @TempDir Path tempDir;

    private record Fixture(PersistenceStore store, ParkingGarage garage, UserService users,
                           NotificationService notifications, ReservationService svc,
                           User owner, User other, User admin) { }

    private Fixture fixture(String dbName) {
        Path db = tempDir.resolve(dbName);
        PersistenceStore store = new PersistenceStore(db);
        ParkingGarage garage = new ParkingGarage("G-1", "Test Garage", "Test Address", 1, 5.0);
        garage.addParkingSpot(new ParkingSpot("S-1", SpotType.STANDARD, "Level 1", 5.0), 0);
        garage.addParkingSpot(new ParkingSpot("S-2", SpotType.STANDARD, "Level 1", 5.0), 0);
        store.saveGarage(garage);
        for (ParkingSpot spot : garage.getAllSpots()) store.saveParkingSpot(garage, spot);
        UserService users = new UserService(store);
        User owner = users.registerUser("owner", "Owner@123!", "owner@example.com", UserRole.CUSTOMER);
        User other = users.registerUser("other", "Other@123!", "other@example.com", UserRole.CUSTOMER);
        User admin = users.registerUser("admin", "Admin@123!", "admin@example.com", UserRole.ADMIN);
        NotificationService notifications = new NotificationService(store);
        ReservationService svc = new ReservationService(garage, store, notifications);
        return new Fixture(store, garage, users, notifications, svc, owner, other, admin);
    }

    @Test
    void reserveCreatesActiveHoldWithDefaultHold() {
        Fixture f = fixture("reserve.db");
        LocalDateTime now = LocalDateTime.of(2026, 9, 15, 10, 0);
        Reservation reservation = f.svc.reserve(f.owner, "S-1", now);
        assertEquals(f.owner.getUserId(), reservation.userId());
        assertEquals("S-1", reservation.spotId());
        assertEquals(now, reservation.createdAt());
        assertEquals(now.plusMinutes(AppConfig.reservationHoldMinutes()), reservation.expiresAt());
        assertEquals(ReservationStatus.ACTIVE, reservation.status());
        assertEquals(SpotStatus.RESERVED, f.garage.getSpotById("S-1").getStatus());
        assertEquals(1, f.notifications.listUnread(f.owner).size());
    }

    @Test
    void duplicateReservationThrows() {
        Fixture f = fixture("duplicate.db");
        LocalDateTime now = LocalDateTime.of(2026, 9, 15, 10, 0);
        f.svc.reserve(f.owner, "S-1", now);
        assertThrows(IllegalStateException.class, () -> f.svc.reserve(f.owner, "S-1", now));
        assertThrows(IllegalStateException.class, () -> f.svc.reserve(f.other, "S-1", now));
    }

    @Test
    void unauthorizedCancelThrows() {
        Fixture f = fixture("unauthorized-cancel.db");
        LocalDateTime now = LocalDateTime.of(2026, 9, 15, 10, 0);
        String reservationId = f.svc.reserve(f.owner, "S-1", now).reservationId();
        assertThrows(AuthorizationException.class, () -> f.svc.cancel(f.other, reservationId, now));
    }

    @Test
    void ownerCancelFreesSpotAndNotifies() {
        Fixture f = fixture("cancel.db");
        LocalDateTime now = LocalDateTime.of(2026, 9, 15, 10, 0);
        Reservation created = f.svc.reserve(f.owner, "S-1", now);
        Reservation cancelled = f.svc.cancel(f.owner, created.reservationId(), now.plusMinutes(1));
        assertEquals(ReservationStatus.CANCELLED, cancelled.status());
        assertEquals(SpotStatus.AVAILABLE, f.garage.getSpotById("S-1").getStatus());
        assertEquals(2, f.notifications.listUnread(f.owner).size());
    }

    @Test
    void adminCanCancelOtherUsersReservation() {
        Fixture f = fixture("admin-cancel.db");
        LocalDateTime now = LocalDateTime.of(2026, 9, 15, 10, 0);
        String reservationId = f.svc.reserve(f.owner, "S-1", now).reservationId();
        Reservation cancelled = f.svc.cancel(f.admin, reservationId, now.plusMinutes(1));
        assertEquals(ReservationStatus.CANCELLED, cancelled.status());
        assertEquals(SpotStatus.AVAILABLE, f.garage.getSpotById("S-1").getStatus());
    }

    @Test
    void expiryMarksExpiredFreesSpotAndNotifiesOnce() {
        Fixture f = fixture("expiry.db");
        LocalDateTime now = LocalDateTime.of(2026, 9, 15, 10, 0);
        String reservationId = f.svc.reserve(f.owner, "S-1", now).reservationId();
        LocalDateTime afterHold = now.plusMinutes(AppConfig.reservationHoldMinutes() + 1);
        List<String> expired = f.svc.expire(afterHold);
        assertEquals(List.of(reservationId), expired);
        assertEquals(SpotStatus.AVAILABLE, f.garage.getSpotById("S-1").getStatus());
        assertTrue(f.svc.expire(afterHold.plusMinutes(1)).isEmpty());
        assertEquals(2, f.notifications.listUnread(f.owner).size());
    }

    @Test
    void claimForEntryViaParkingEntryParksOnReservedSpot() throws Exception {
        Fixture f = fixture("claim.db");
        LocalDateTime now = LocalDateTime.now();
        String reservationId = f.svc.reserve(f.owner, "S-1", now).reservationId();
        TicketService tickets = new TicketService(f.store);
        ParkingService parking = new ParkingService(f.garage, tickets, f.users, f.store);
        parking.setReservationService(f.svc);
        Vehicle vehicle = new Vehicle("VH-1", "ABC-123", VehicleType.CAR, "Toyota", "Corolla", "Silver", 2022, f.owner.getUserId());
        var ticket = parking.vehicleEntry(f.owner, vehicle, reservationId);
        assertEquals("S-1", ticket.getParkingSpotId());
        assertEquals(TicketStatus.ACTIVE, ticket.getStatus());
        assertEquals("CLAIMED", f.store.loadReservation(reservationId).orElseThrow().status());
    }

    @Test
    void claimByOtherUserThrows() {
        Fixture f = fixture("claim-other.db");
        LocalDateTime now = LocalDateTime.of(2026, 9, 15, 10, 0);
        String reservationId = f.svc.reserve(f.owner, "S-1", now).reservationId();
        Vehicle vehicle = new Vehicle("VH-9", "XYZ-999", VehicleType.CAR, "Ford", "Focus", "Blue", 2021, f.other.getUserId());
        assertThrows(AuthorizationException.class, () -> f.svc.claimForEntry(f.other, vehicle, reservationId));
    }

    @Test
    void reserveUnknownSpotThrows() {
        Fixture f = fixture("unknown-spot.db");
        LocalDateTime now = LocalDateTime.of(2026, 9, 15, 10, 0);
        assertThrows(IllegalArgumentException.class, () -> f.svc.reserve(f.owner, "S-404", now));
    }
}
