package com.parking;

import com.parking.enums.SpotType;
import com.parking.enums.UserRole;
import com.parking.enums.VehicleType;
import com.parking.model.Customer;
import com.parking.model.ParkingGarage;
import com.parking.model.ParkingSpot;
import com.parking.model.User;
import com.parking.model.Vehicle;
import com.parking.persistence.PersistenceStore;
import com.parking.services.ParkingService;
import com.parking.services.PaymentService;
import com.parking.services.TicketService;
import com.parking.services.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

abstract class TestFixtures {

    protected ParkingGarage garage;
    protected ParkingService parkingService;
    protected TicketService ticketService;
    protected PaymentService paymentService;
    protected UserService userService;
    protected PersistenceStore persistence;

    protected Customer testCustomer;
    protected Vehicle testVehicle;

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @BeforeEach
    void setUp() {
        garage = newGarage();

        // Services share one persistence boundary so foreign-key validation
        // sees users, tickets, and parking spots in the same database.
        persistence = new PersistenceStore();
        userService = new UserService(persistence);
        ticketService = new TicketService(persistence);
        parkingService = new ParkingService(
                garage,
                ticketService,
                userService,
                persistence
        );
        paymentService = new PaymentService(
                garage,
                userService,
                ticketService,
                persistence
        );

        testCustomer = (Customer) newUser(
                "testuser",
                "Test@123",
                "test@example.com",
                UserRole.CUSTOMER
        );

        testCustomer.setFullName("Test Customer");

        testVehicle = new Vehicle(
                "VEH-TEST",
                "TEST-123",
                VehicleType.CAR,
                "Toyota",
                "Corolla",
                "Silver",
                2022,
                testCustomer.getUserId()
        );

        testCustomer.addVehicle(testVehicle);
    }

    protected User newUser(
            String username,
            String password,
            String email,
            UserRole role
    ) {
        return userService.registerUser(username, password, email, role);
    }

    protected ParkingGarage newGarage() {
        ParkingGarage garage = new ParkingGarage(
                "G-TEST",
                "Test Parking Garage",
                "Test Address",
                3,
                5.0
        );

        garage.addParkingSpot(
                new ParkingSpot("S-1", SpotType.STANDARD, "Level 1", 5.0),
                0
        );

        garage.addParkingSpot(
                new ParkingSpot("S-2", SpotType.STANDARD, "Level 1", 5.0),
                0
        );

        garage.addParkingSpot(
                new ParkingSpot("S-3", SpotType.STANDARD, "Level 1", 5.0),
                0
        );

        garage.addParkingSpot(
                new ParkingSpot("C-1", SpotType.COMPACT, "Level 2", 4.5),
                1
        );

        garage.addParkingSpot(
                new ParkingSpot("L-1", SpotType.LARGE, "Level 2", 6.0),
                1
        );

        garage.addParkingSpot(
                new ParkingSpot("EV-1", SpotType.EV_CHARGING, "Level 3", 7.0),
                2
        );

        return garage;
    }
}
