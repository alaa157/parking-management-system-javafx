package com.parking;

import com.parking.enums.UserRole;
import com.parking.model.Garage;
import com.parking.model.ParkingGarage;
import com.parking.model.User;
import com.parking.persistence.PersistenceStore;
import com.parking.services.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GarageMigrationTest {
    @TempDir Path tempDir;

    @Test
    void existingGarageConfigAndUsersBackfillDefaultGarageAccess() throws Exception {
        Path database = tempDir.resolve("legacy-garage.db");
        String userId;
        try (PersistenceStore store = new PersistenceStore(database)) {
            User owner = new UserService(store).registerUser(
                    "owner", "Owner@123", "owner@example.com", UserRole.CUSTOMER);
            userId = owner.getUserId();
            store.saveGarage(new ParkingGarage("G-LEGACY", "Legacy Garage", "Old Address", 2, 4.5));
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                 Statement statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM garages");
                statement.executeUpdate("DELETE FROM user_garages");
            }
        }

        try (PersistenceStore store = new PersistenceStore(database)) {
            List<Garage> garages = store.loadGarages();
            assertEquals(1, garages.size());
            assertEquals("G-LEGACY", garages.get(0).getGarageId());
            assertTrue(store.hasGarageAccess(userId, "G-LEGACY"));
            assertEquals(11, store.schemaVersionForTests());
        }
    }
}
