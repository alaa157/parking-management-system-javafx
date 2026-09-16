package com.parking;

import com.parking.enums.SpotType;
import com.parking.enums.UserRole;
import com.parking.enums.VehicleType;
import com.parking.exceptions.VehicleAlreadyParkedException;
import com.parking.model.ParkingGarage;
import com.parking.model.ParkingSpot;
import com.parking.model.Ticket;
import com.parking.model.User;
import com.parking.model.Vehicle;
import com.parking.persistence.PersistenceStore;
import com.parking.services.ParkingService;
import com.parking.services.TicketService;
import com.parking.services.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class GlobalVehicleParkingTest {

    @TempDir
    Path tempDir;

    @Test
    void vehicleCannotBeParkedInTwoGaragesAtOnce() throws Exception {
        try (PersistenceStore store = new PersistenceStore()) {
            User admin = new UserService(store).registerUser("admin-global", "Admin@123", "admin-global@example.com", UserRole.ADMIN);
            TicketService tickets = new TicketService(store);
            ParkingGarage north = garage(store, "G-A", "S-A");
            ParkingGarage south = garage(store, "G-B", "S-B");
            ParkingService northParking = new ParkingService(north, tickets, null, store);
            ParkingService southParking = new ParkingService(south, tickets, null, store);
            Vehicle vehicle = vehicle("V-GLOBAL");

            Ticket original = northParking.vehicleEntry(vehicle, "G-A", admin);

            Vehicle sameVehicle = vehicle("V-GLOBAL");
            VehicleAlreadyParkedException conflict = assertThrows(VehicleAlreadyParkedException.class,
                    () -> southParking.vehicleEntry(sameVehicle, "G-B", admin));

            assertTrue(conflict.getMessage().contains(original.getTicketId()));
            assertTrue(conflict.getMessage().contains("G-A"));
            assertTrue(conflict.getMessage().contains("S-A"));
        }
    }

    @Test
    void vehicleCanParkInAnotherGarageAfterTheActiveTicketIsClosed() throws Exception {
        try (PersistenceStore store = new PersistenceStore()) {
            User admin = new UserService(store).registerUser("admin-release", "Admin@123", "admin-release@example.com", UserRole.ADMIN);
            TicketService tickets = new TicketService(store);
            ParkingGarage north = garage(store, "G-A", "S-A");
            ParkingGarage south = garage(store, "G-B", "S-B");
            ParkingService northParking = new ParkingService(north, tickets, null, store);
            ParkingService southParking = new ParkingService(south, tickets, null, store);
            Vehicle vehicle = vehicle("V-RELEASE");

            Ticket original = northParking.vehicleEntry(vehicle, "G-A", admin);
            northParking.vehicleExit(original);
            original.setStatus(com.parking.enums.TicketStatus.CLOSED);
            store.saveTicket(original);
            northParking.releaseAfterPayment(original);

            Ticket moved = southParking.vehicleEntry(vehicle("V-RELEASE"), "G-B", admin);

            assertEquals("G-B", moved.getGarageId());
        }
    }

    @Test
    void startupRejectsPreexistingDuplicateActiveTickets() throws Exception {
        Path database = tempDir.resolve("duplicate-active.db");
        try (PersistenceStore store = new PersistenceStore(database)) {
            ParkingGarage garage = garage(store, "G-DUP", "S-DUP");
            store.saveParkingSpot(garage, garage.getSpotById("S-DUP"));
            Vehicle vehicle = vehicle("V-DUP");
            store.saveVehicle(vehicle);
            Ticket ticket = new Ticket("T-DUP-1", vehicle.getVehicleId(), "S-DUP", null, "G-DUP");
            ticket.setStatus(com.parking.enums.TicketStatus.ACTIVE);
            store.saveTicket(ticket);
        }
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("DROP INDEX idx_tickets_active_vehicle");
            statement.executeUpdate("INSERT INTO tickets(ticket_id,vehicle_id,owner_id,spot_id,garage_id,status,entry_time,exit_time,amount,final_amount,payment_id) "
                    + "VALUES ('T-DUP-2','V-DUP',NULL,'S-DUP','G-DUP','ACTIVE','2026-01-01T10:00:00',NULL,5.0,5.0,NULL)");
        }

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> new PersistenceStore(database));
        assertTrue(failure.getMessage().contains("Duplicate active parking sessions"));
    }

    private static ParkingGarage garage(PersistenceStore store, String garageId, String spotId) {
        ParkingGarage garage = new ParkingGarage(garageId, garageId, "Address", 1, 5.0);
        garage.addParkingSpot(new ParkingSpot(spotId, SpotType.STANDARD, "Level 1", 5.0), 0);
        store.saveGarage(garage);
        return garage;
    }

    private static Vehicle vehicle(String id) {
        return new Vehicle(id, id, VehicleType.CAR, "Toyota", "Corolla", "Blue", 2022, null);
    }
}
