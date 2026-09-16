package com.parking;

import com.parking.enums.*;
import com.parking.exceptions.*;
import com.parking.model.*;
import com.parking.services.*;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ParkingServiceFlowTest extends TestFixtures {

    @Test
    void testVehicleEntry() throws Exception {
        int availableBefore =
                garage.getAvailableSpots();

        Ticket ticket =
                parkingService.vehicleEntry(testVehicle);

        assertNotNull(ticket);

        assertEquals(
                TicketStatus.ACTIVE,
                ticket.getStatus()
        );

        assertTrue(
                testVehicle.isParked()
        );

        assertNotNull(
                testVehicle.getParkingSpotId()
        );

        ParkingSpot spot =
                garage.getSpotById(
                        testVehicle.getParkingSpotId()
                );

        assertEquals(
                SpotStatus.OCCUPIED,
                spot.getStatus()
        );

        assertEquals(
                availableBefore - 1,
                garage.getAvailableSpots()
        );
    }


    @Test
    void testVehicleAlreadyParked() throws Exception {
        parkingService.vehicleEntry(testVehicle);

        assertThrows(
                VehicleAlreadyParkedException.class,
                () -> parkingService.vehicleEntry(testVehicle)
        );
    }


    @Test
    void testFindAvailableSpot() throws Exception {
        ParkingSpot spot =
                parkingService.findAvailableSpot(testVehicle);

        assertNotNull(spot);
        assertEquals(
                SpotType.STANDARD,
                spot.getSpotType()
        );
    }


    @Test
    void testCarFallsBackToLargeSpot() throws Exception {
        garage.getSpotById("S-1").occupySpot("FULL-1");
        garage.getSpotById("S-2").occupySpot("FULL-2");
        garage.getSpotById("S-3").occupySpot("FULL-3");

        ParkingSpot spot = parkingService.findAvailableSpot(testVehicle);

        assertEquals(SpotType.LARGE, spot.getSpotType());
    }


    @Test
    void testHandicappedVehicleDoesNotUseStandardFallback() throws Exception {
        Vehicle handicapped = new Vehicle(
                "VEH-HC", "HC-123", VehicleType.HANDICAPPED,
                "Toyota", "Corolla", "White", 2022, testCustomer.getUserId());

        assertThrows(
                SpotNotAvailableException.class,
                () -> parkingService.findAvailableSpot(handicapped));
    }


    @Test
    void testReservationExpiryAndAvailability() {
        ParkingSpot spot = garage.getSpotById("S-1");
        parkingService.reserveSpot(spot, testCustomer.getUserId(), 5);

        assertFalse(spot.isAvailable());
        parkingService.releaseExpiredReservations(LocalDateTime.now());
        assertEquals(SpotStatus.RESERVED, spot.getStatus());

        parkingService.releaseExpiredReservations(
                LocalDateTime.now().plusMinutes(6));
        assertTrue(spot.isAvailable());
    }


    @Test
    void testReservationWithUnknownHolderDoesNotMutateSpot() {
        ParkingSpot spot = garage.getSpotById("S-1");

        assertThrows(
                IllegalArgumentException.class,
                () -> parkingService.reserveSpot(spot, "missing-user", 5)
        );

        assertEquals(SpotStatus.AVAILABLE, spot.getStatus());
        assertNull(spot.getReservationHolderUserId());
        assertNull(spot.getReservationExpiry());
    }


    @Test
    void testClosedGarageRejectsVehicleEntry() {
        garage.closeGarage();

        assertThrows(
                IllegalStateException.class,
                () -> parkingService.vehicleEntry(testVehicle));
    }


    @Test
    void testNoAvailableSpot() throws Exception {
        // Occupy all fallback spots allowed for CAR.
        garage.getSpotById("S-1")
                .occupySpot("OTHER-VEHICLE-1");      
        garage.getSpotById("S-2")
                .occupySpot("OTHER-VEHICLE-2");
        garage.getSpotById("S-3")
                .occupySpot("OTHER-VEHICLE-3");
        garage.getSpotById("L-1")
                .occupySpot("OTHER-VEHICLE-4");
        Vehicle anotherCar = new Vehicle(
                "VEH-OTHER",
                "OTHER-123",
                VehicleType.CAR,
                "Honda",
                "Civic",
                "Black",
                2021,
                testCustomer.getUserId()
        );
        assertThrows(
                SpotNotAvailableException.class,
                () -> parkingService.findAvailableSpot(anotherCar)
        );
    }


    @Test
    void testVehicleExit() throws Exception {
        Ticket ticket =
                parkingService.vehicleEntry(testVehicle);

        String spotId =
                testVehicle.getParkingSpotId();

        double amount =
                parkingService.vehicleExit(ticket);

        assertTrue(amount >= 0);

        assertEquals(
                TicketStatus.AWAITING_PAYMENT,
                ticket.getStatus()
        );

        assertTrue(
                testVehicle.isParked()
        );

        ParkingSpot spot =
                garage.getSpotById(spotId);

        assertEquals(
                SpotStatus.OCCUPIED,
                spot.getStatus()
        );

        assertFalse(
                spot.isAvailable()
        );

        CashPayment cash = new CashPayment(
                "PAY-EXIT-RELEASE",
                ticket.getTicketId(),
                testCustomer.getUserId(),
                10.0,
                10.0,
                "Tester");
        paymentService.processPayment(ticket, cash);
        parkingService.releaseAfterPayment(ticket);

        assertTrue(
                spot.isAvailable()
        );
    }


    @Test
    void testOccupancyCountsOnlyOccupiedSpots() {
        ParkingSpot occupied = garage.getSpotById("S-1");
        ParkingSpot maintenance = garage.getSpotById("S-2");

        occupied.occupySpot("VEH-OCCUPIED");
        maintenance.setUnderMaintenance("MAINTENANCE-REASON");
        garage.getSpotById("S-3").reserveSpot(testCustomer.getUserId());
        garage.getSpotById("C-1").setOutOfService();

        assertEquals(
                1.0 / garage.getTotalCapacity(),
                garage.getOccupancyRate(),
                0.001
        );
    }


    @Test
    void testTotalAvailableSpots() throws Exception {
        int before =
                parkingService.getTotalAvailableSpots();

        parkingService.vehicleEntry(testVehicle);

        int after =
                parkingService.getTotalAvailableSpots();

        assertEquals(
                before - 1,
                after
        );
    }


    @Test
    void testFullParkingFlow() throws Exception {
        // =====================================================
        // 1. VEHICLE ENTERS
        // =====================================================
        Ticket ticket =
                parkingService.vehicleEntry(testVehicle);
        assertNotNull(ticket);
        assertEquals(
                TicketStatus.ACTIVE,
                ticket.getStatus()
        );
        assertTrue(
                testVehicle.isParked()
        );
        assertNotNull(
                testVehicle.getParkingSpotId()
        );

        // =====================================================
        // 2. SIMULATE 2 HOURS OF PARKING
        // =====================================================

        ticket.setEntryTime(
                LocalDateTime.now().minusHours(2)
        );

        // =====================================================
        // 3. VEHICLE EXITS
        // =====================================================

        double amount =
                parkingService.vehicleExit(ticket);
        assertTrue(
                amount > 0,
                "Parking amount should be greater than zero"
        );
        assertEquals(
                TicketStatus.AWAITING_PAYMENT,
                ticket.getStatus()
        );
        // =====================================================
        // 4. CREATE CASH PAYMENT
        // =====================================================

        CashPayment cash =
                new CashPayment(
                        "PAY-FULL",
                        ticket.getTicketId(),
                        testCustomer.getUserId(),
                        amount,
                        amount + 100.0,
                        "Tester"
                );          
           
        // =====================================================
        // 5. PROCESS PAYMENT
        // =====================================================
            
        Payment payment =
                paymentService.processPayment(
                        ticket,
                        cash
                );
        assertNotNull(payment);
        assertEquals(
                PaymentStatus.COMPLETED,
                payment.getStatus()
        );
        assertTrue(
                payment.getFinalAmount() > 0
        );

        // =====================================================
        // 6. VERIFY TICKET WAS PAID
        // =====================================================
        assertEquals(
                TicketStatus.CLOSED,
                ticket.getStatus()
        );

        parkingService.releaseAfterPayment(ticket);

        assertFalse(
                testVehicle.isParked()
        );
        assertEquals(
                TicketStatus.CLOSED,
                ticket.getStatus()
        );
        assertNotNull(
                ticket.getExitTime()
        );
    }


    @Test
    void testMultipleVehicles() throws Exception {

        Vehicle car2 = new Vehicle(
                "VEH-002",
                "XYZ-789",
                VehicleType.CAR,
                "Honda",
                "Civic",
                "Black",
                2021,
                testCustomer.getUserId()
        );

        Vehicle car3 = new Vehicle(
                "VEH-003",
                "XYZ-456",
                VehicleType.CAR,
                "BMW",
                "320i",
                "White",
                2023,
                testCustomer.getUserId()
        );

        Ticket ticket1 =
                parkingService.vehicleEntry(testVehicle);

        Ticket ticket2 =
                parkingService.vehicleEntry(car2);

        assertNotNull(ticket1);
        assertNotNull(ticket2);

        assertNotEquals(
                ticket1.getParkingSpotId(),
                ticket2.getParkingSpotId()
        );

        assertTrue(testVehicle.isParked());
        assertTrue(car2.isParked());

        parkingService.vehicleExit(ticket1);

        // Exit only moves the ticket to payment-pending; the vehicle remains
        // attached to its spot until payment succeeds.
        assertTrue(testVehicle.isParked());
        assertTrue(car2.isParked());

        Ticket ticket3 =
                parkingService.vehicleEntry(car3);

        assertNotNull(ticket3);

        assertTrue(car3.isParked());
    }


}
