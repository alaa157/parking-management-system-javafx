package com.parking;

import com.parking.enums.SpotType;
import com.parking.enums.UserRole;
import com.parking.enums.VehicleType;
import com.parking.exceptions.GarageAccessException;
import com.parking.model.Garage;
import com.parking.model.Payment;
import com.parking.model.ParkingGarage;
import com.parking.model.ParkingSpot;
import com.parking.model.Ticket;
import com.parking.model.User;
import com.parking.model.Vehicle;
import com.parking.persistence.PersistenceStore;
import com.parking.services.GarageService;
import com.parking.services.TicketService;
import com.parking.services.UserService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MultiGarageTicketPaymentTest {

    @Test
    void ticketsCanBeFilteredByGarageAndAdminsCanSeeAllGarages() {
        try (PersistenceStore store = new PersistenceStore()) {
            UserService users = new UserService(store);
            User admin = users.registerUser("admin", "Admin@123", "admin@example.com", UserRole.ADMIN);
            User attendant = users.registerUser("attendant", "Attendant@123", "attendant@example.com", UserRole.ATTENDANT);
            GarageService garages = new GarageService(store);
            garages.createGarage("G-1", "North Garage", "North", 2, 5.0, "USD", 0, 5, 48);
            garages.createGarage("G-2", "South Garage", "South", 2, 6.0, "USD", 0, 5, 48);
            garages.grantAccess(attendant.getUserId(), "G-1", UserRole.ATTENDANT);
            garages.grantAccess(attendant.getUserId(), "G-2", UserRole.ATTENDANT);

            TicketService tickets = new TicketService(store);
            Ticket north = createTicket(store, tickets, "G-1", "V-1", "S-1");
            Ticket south = createTicket(store, tickets, "G-2", "V-2", "S-2");

            assertEquals("G-1", north.getGarageId());
            assertEquals("G-2", south.getGarageId());
            assertEquals(List.of(north), tickets.findTickets(attendant, "G-1"));
            assertEquals(2, tickets.findTickets(admin, null).size());
        }
    }

    @Test
    void paymentGarageMustMatchItsTicketGarage() {
        try (PersistenceStore store = new PersistenceStore()) {
            UserService users = new UserService(store);
            User owner = users.registerUser("owner", "Owner@123", "owner@example.com", UserRole.CUSTOMER);
            GarageService garages = new GarageService(store);
            garages.createGarage("G-1", "North Garage", "North", 2, 5.0, "USD", 0, 5, 48);
            garages.createGarage("G-2", "South Garage", "South", 2, 6.0, "USD", 0, 5, 48);
            garages.grantAccess(owner.getUserId(), "G-1", UserRole.CUSTOMER);

            TicketService tickets = new TicketService(store);
            Ticket ticket = createTicket(store, tickets, "G-1", "V-3", "S-3");
            Payment payment = new Payment("P-1", ticket.getTicketId(), owner.getUserId(), 5.0, "CASH");
            payment.setGarageId("G-2");

            assertThrows(IllegalArgumentException.class, () -> store.savePayment(payment));
        }
    }

    @Test
    void legacyPaymentDerivesItsGarageFromTheTicket() {
        try (PersistenceStore store = new PersistenceStore()) {
            UserService users = new UserService(store);
            User owner = users.registerUser("owner2", "Owner@123", "owner2@example.com", UserRole.CUSTOMER);
            GarageService garages = new GarageService(store);
            garages.createGarage("G-1", "North Garage", "North", 2, 5.0, "USD", 0, 5, 48);

            TicketService tickets = new TicketService(store);
            Ticket ticket = createTicket(store, tickets, "G-1", "V-4", "S-4");
            Payment payment = new Payment("P-2", ticket.getTicketId(), owner.getUserId(), 5.0, "CASH");

            store.savePayment(payment);

            assertEquals("G-1", payment.getGarageId());
            assertEquals("G-1", store.loadPayments().get(0).getGarageId());
        }
    }

    @Test
    void revokedGarageAccessCannotQueryThatGarage() {
        try (PersistenceStore store = new PersistenceStore()) {
            UserService users = new UserService(store);
            User attendant = users.registerUser("attendant2", "Attendant@123", "attendant2@example.com", UserRole.ATTENDANT);
            GarageService garages = new GarageService(store);
            garages.createGarage("G-1", "North Garage", "North", 2, 5.0, "USD", 0, 5, 48);
            garages.grantAccess(attendant.getUserId(), "G-1", UserRole.ATTENDANT);
            garages.revokeAccess(attendant.getUserId(), "G-1");

            TicketService tickets = new TicketService(store);

            assertThrows(GarageAccessException.class, () -> tickets.findTickets(attendant, "G-1"));
        }
    }

    @Test
    void ticketCreationRejectsAnInaccessibleGarageForAnActor() {
        try (PersistenceStore store = new PersistenceStore()) {
            UserService users = new UserService(store);
            User attendant = users.registerUser("attendant3", "Attendant@123", "attendant3@example.com", UserRole.ATTENDANT);
            GarageService garages = new GarageService(store);
            garages.createGarage("G-1", "North Garage", "North", 2, 5.0, "USD", 0, 5, 48);
            TicketService tickets = new TicketService(store);
            ParkingGarage garage = new ParkingGarage("G-1", "North Garage", "North", 2, 5.0);
            ParkingSpot spot = new ParkingSpot("S-5", SpotType.STANDARD, "Level 1", 5.0);
            garage.addParkingSpot(spot, 0);
            store.saveGarage(garage);
            store.saveParkingSpot(garage, spot);
            Vehicle vehicle = new Vehicle("V-5", "V-5", VehicleType.CAR, "Toyota", "Corolla", "Silver", 2022, null);

            assertThrows(GarageAccessException.class,
                    () -> tickets.createTicket(attendant, vehicle, spot, "G-1"));
        }
    }

    private static Ticket createTicket(PersistenceStore store, TicketService tickets,
                                       String garageId, String vehicleId, String spotId) {
        ParkingGarage garage = new ParkingGarage(garageId, garageId, garageId, 2, 5.0);
        ParkingSpot spot = new ParkingSpot(spotId, SpotType.STANDARD, "Level 1", 5.0);
        garage.addParkingSpot(spot, 0);
        store.saveGarage(garage);
        store.saveParkingSpot(garage, spot);
        Vehicle vehicle = new Vehicle(vehicleId, vehicleId, VehicleType.CAR,
                "Toyota", "Corolla", "Silver", 2022, null);
        return tickets.createTicket(vehicle, spot, garageId);
    }
}
