package com.parking;

import com.parking.enums.UserRole;
import com.parking.exceptions.GarageAccessException;
import com.parking.model.Garage;
import com.parking.model.User;
import com.parking.persistence.PersistenceStore;
import com.parking.services.GarageService;
import com.parking.services.UserService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GarageServiceTest {

    @Test
    void garageRejectsInvalidAttributes() {
        assertThrows(IllegalArgumentException.class,
                () -> new Garage("G-1", "", "Address", 2, 5.0, "USD",
                        0, 5, 48));
        assertThrows(IllegalArgumentException.class,
                () -> new Garage("G-1", "Garage", "Address", 0, 5.0, "USD",
                        0, 5, 48));
        assertThrows(IllegalArgumentException.class,
                () -> new Garage("G-1", "Garage", "Address", 2, -1.0, "USD",
                        0, 5, 48));
    }

    @Test
    void garageLifecyclePersistsValidatedChanges() {
        try (PersistenceStore store = new PersistenceStore()) {
            GarageService garages = new GarageService(store);
            Garage created = garages.createGarage(
                    "G-1", "Main Garage", "Downtown", 3, 5.0,
                    "USD", 10, 5, 48);

            assertEquals("Main Garage", created.getName());
            assertTrue(created.isOpen());
            assertFalse(created.isArchived());

            created.setName("Main Garage Updated");
            garages.updateGarage(created);
            assertEquals("Main Garage Updated", garages.getGarage("G-1").getName());

            garages.archiveGarage("G-1");
            assertTrue(garages.getGarage("G-1").isArchived());
            garages.reopenGarage("G-1");
            assertFalse(garages.getGarage("G-1").isArchived());
        }
    }

    @Test
    void accessRulesSupportAssignmentsRevocationAndGlobalAdmins() {
        try (PersistenceStore store = new PersistenceStore()) {
            UserService users = new UserService(store);
            User admin = users.registerUser("admin", "Admin@123", "admin@example.com", UserRole.ADMIN);
            User attendant = users.registerUser("attendant", "Attendant@123", "attendant@example.com", UserRole.ATTENDANT);
            GarageService garages = new GarageService(store);
            garages.createGarage("G-1", "Main Garage", "Downtown", 3, 5.0,
                    "USD", 0, 5, 48);

            assertFalse(garages.canAccess(attendant, "G-1"));
            garages.grantAccess(attendant.getUserId(), "G-1", UserRole.ATTENDANT);
            assertTrue(garages.canAccess(attendant, "G-1"));
            assertTrue(garages.canAccess(admin, "G-1"));

            garages.revokeAccess(attendant.getUserId(), "G-1");
            assertFalse(garages.canAccess(attendant, "G-1"));
            assertThrows(GarageAccessException.class,
                    () -> garages.requireAccess(attendant, "G-1"));

            garages.archiveGarage("G-1");
            assertFalse(garages.canAccess(admin, "G-1"),
                    "archived garages cannot receive operational access");
        }
    }
}
