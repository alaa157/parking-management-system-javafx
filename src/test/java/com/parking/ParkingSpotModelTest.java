package com.parking;

import com.parking.enums.*;
import com.parking.exceptions.*;
import com.parking.model.*;
import com.parking.services.*;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ParkingSpotModelTest extends TestFixtures {

    @Test
    void testCreateParkingSpot() {
        ParkingSpot spot = new ParkingSpot(
                "TEST-SPOT",
                SpotType.STANDARD,
                "Level 1",
                5.0
        );

        assertNotNull(spot);
        assertEquals("TEST-SPOT", spot.getSpotId());
        assertEquals(SpotType.STANDARD, spot.getSpotType());
        assertEquals("Level 1", spot.getLocation());
        assertEquals(5.0, spot.getHourlyRate(), 0.001);
        assertTrue(spot.isAvailable());
    }


    @Test
    void testOccupyAndFreeSpot() {
        ParkingSpot spot = garage.getSpotById("S-1");

        assertTrue(spot.isAvailable());

        assertTrue(
                spot.occupySpot(testVehicle.getVehicleId())
        );

        assertFalse(spot.isAvailable());
        assertEquals(
                testVehicle.getVehicleId(),
                spot.getVehicleId()
        );
        assertEquals(
                SpotStatus.OCCUPIED,
                spot.getStatus()
        );

        spot.freeSpot();

        assertTrue(spot.isAvailable());
        assertNull(spot.getVehicleId());
    }


    @Test
    void testOccupyingAlreadyOccupiedSpot() {
        ParkingSpot spot = garage.getSpotById("S-1");

        assertTrue(
                spot.occupySpot("VEH-001")
        );

        assertFalse(
                spot.occupySpot("VEH-002")
        );
    }


    @Test
    void testParkingSpotMaintenance() {
        ParkingSpot spot = garage.getSpotById("S-1");

        assertFalse(spot.isUnderMaintenance());

        spot.setUnderMaintenance("Maintenance test");

        assertTrue(spot.isUnderMaintenance());
        assertEquals(
                "Maintenance test",
                spot.getMaintenanceReason()
        );

        spot.removeFromMaintenance();

        assertFalse(spot.isUnderMaintenance());
    }


    @Test
    void testAddParkingSpot() {
        ParkingSpot spot = new ParkingSpot(
                "NEW-1",
                SpotType.STANDARD,
                "Level 1",
                5.0
        );

        assertTrue(
                garage.addParkingSpot(spot, 0)
        );

        assertNotNull(
                garage.getSpotById("NEW-1")
        );
    }


    @Test
    void testDuplicateParkingSpot() {
        ParkingSpot duplicate = new ParkingSpot(
                "S-1",
                SpotType.STANDARD,
                "Level 1",
                5.0
        );

        assertFalse(
                garage.addParkingSpot(duplicate, 0)
        );
    }


    @Test
    void testInvalidGarageLevel() {
        ParkingSpot spot = new ParkingSpot(
                "INVALID",
                SpotType.STANDARD,
                "Invalid",
                5.0
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> garage.addParkingSpot(spot, 99)
        );
    }


    @Test
    void testGarageAvailability() {
        garage.updateAvailability();

        assertEquals(
                6,
                garage.getAvailableSpots()
        );

        assertEquals(
                0.0,
                garage.getOccupancyRate(),
                0.001
        );
    }


    @Test
    void testGarageOpenClose() {
        garage.openGarage();

        assertTrue(garage.isOpen());

        garage.closeGarage();

        assertFalse(garage.isOpen());
    }


    @Test
    void testCannotCloseOccupiedGarage() {
        ParkingSpot spot = garage.getSpotById("S-1");

        spot.occupySpot("VEH-001");

        assertThrows(
                IllegalStateException.class,
                () -> garage.closeGarage()
        );
    }


    @Test
    void testCreateVehicle() {
        assertNotNull(testVehicle);

        assertEquals(
                "VEH-TEST",
                testVehicle.getVehicleId()
        );

        assertEquals(
                "TEST-123",
                testVehicle.getLicensePlate()
        );

        assertEquals(
                VehicleType.CAR,
                testVehicle.getVehicleType()
        );
    }


    @Test
    void testVehicleParkAndUnpark() {
        assertFalse(testVehicle.isParked());

        testVehicle.park("S-1");

        assertTrue(testVehicle.isParked());
        assertEquals(
                "S-1",
                testVehicle.getParkingSpotId()
        );
        assertNotNull(testVehicle.getEntryTime());

        testVehicle.unpark();

        assertFalse(testVehicle.isParked());
        assertNull(testVehicle.getParkingSpotId());
    }


    @Test
    void testVehicleInvalidParkingSpot() {
        assertThrows(
                IllegalArgumentException.class,
                () -> testVehicle.park("")
        );
    }


    @Test
    void testNullVehicleIdForParkingSpot() {
        ParkingSpot spot =
                garage.getSpotById("S-1");

        assertFalse(
                spot.occupySpot(null)
        );
    }


    @Test
    void testEmptyVehicleIdForParkingSpot() {
        ParkingSpot spot =
                garage.getSpotById("S-1");

        assertFalse(
                spot.occupySpot("")
        );
    }


}
