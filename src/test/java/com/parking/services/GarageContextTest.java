package com.parking.services;

import com.parking.enums.UserRole;
import com.parking.exceptions.GarageAccessException;
import com.parking.model.User;
import com.parking.persistence.PersistenceStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GarageContextTest {

    @Test
    void userCanSelectOnlyAnAccessibleActiveGarage() {
        try (PersistenceStore store = new PersistenceStore()) {
            User user = new UserService(store).registerUser("context-user", "Context@123", "context@example.com", UserRole.ATTENDANT);
            GarageService garages = new GarageService(store);
            garages.createGarage("G-1", "North", "North", 2, 5.0, "USD", 0, 5, 48);
            garages.createGarage("G-2", "South", "South", 2, 5.0, "USD", 0, 5, 48);
            garages.grantAccess(user.getUserId(), "G-1", UserRole.ATTENDANT);
            GarageContext context = new GarageContext(garages, user);

            context.select("G-1");

            assertEquals("G-1", context.getSelectedGarageId());
            assertFalse(context.isAllGarages());
            assertThrows(GarageAccessException.class, () -> context.select("G-2"));
            assertThrows(GarageAccessException.class, context::selectAll);
        }
    }

    @Test
    void globalAdminCanSelectAllGaragesButArchivedSelectionIsCleared() {
        try (PersistenceStore store = new PersistenceStore()) {
            User admin = new UserService(store).registerUser("context-admin", "Context@123", "context-admin@example.com", UserRole.ADMIN);
            GarageService garages = new GarageService(store);
            garages.createGarage("G-1", "North", "North", 2, 5.0, "USD", 0, 5, 48);
            GarageContext context = new GarageContext(garages, admin);

            context.select("G-1");
            garages.archiveGarage("G-1");
            context.refresh();

            assertNull(context.getSelectedGarageId());
            assertFalse(context.isAllGarages());
            context.selectAll();
            assertTrue(context.isAllGarages());
        }
    }
}
