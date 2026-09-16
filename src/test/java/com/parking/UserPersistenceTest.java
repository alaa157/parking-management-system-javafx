package com.parking;

import com.parking.enums.NotificationType;
import com.parking.enums.UserRole;
import com.parking.model.Customer;
import com.parking.model.NotificationPreference;
import com.parking.model.User;
import com.parking.persistence.PersistenceStore;
import com.parking.services.NotificationService;
import com.parking.services.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class UserPersistenceTest {
    @TempDir
    Path tempDir;

    private Path database() {
        return tempDir.resolve("parking.db");
    }

    @Test
    void registerUserSurvivesRestart() {
        String id;
        try (PersistenceStore store = new PersistenceStore(database())) {
            UserService service = new UserService(store);
            id = service.registerUser("persisted", "Persist@123", "persisted@example.com", UserRole.CUSTOMER).getUserId();
        }

        try (PersistenceStore store = new PersistenceStore(database())) {
            User user = new UserService(store).getUserByUsername("persisted");
            assertEquals(id, user.getUserId());
            assertEquals("persisted@example.com", user.getEmail());
            assertTrue(user.verifyPassword("Persist@123"));
        }
    }

    @Test
    void profileAndPasswordChangesSurviveRestart() {
        String id;
        try (PersistenceStore store = new PersistenceStore(database())) {
            UserService service = new UserService(store);
            User user = service.registerUser("profile", "Profile@123", "profile@example.com", UserRole.CUSTOMER);
            id = user.getUserId();
            service.updateUserProfile(user, "new-profile@example.com", null);
            service.changePassword(user, "Profile@123", "Changed@123");
        }

        try (PersistenceStore store = new PersistenceStore(database())) {
            User user = new UserService(store).getUserById(id);
            assertEquals("new-profile@example.com", user.getEmail());
            assertTrue(user.verifyPassword("Changed@123"));
            assertFalse(user.verifyPassword("Profile@123"));
        }
    }

    @Test
    void usernameChangeRebuildsIndexAndSurvivesRestart() {
        String id;
        try (PersistenceStore store = new PersistenceStore(database())) {
            UserService service = new UserService(store);
            User user = service.registerUser("old-name", "Name@123", "name@example.com", UserRole.CUSTOMER);
            id = user.getUserId();
            service.adminUpdateUser(user, "Renamed User", " new-name ", "name@example.com", null,
                    UserRole.CUSTOMER, true);
            assertThrows(RuntimeException.class, () -> service.getUserByUsername("old-name"));
            assertEquals(id, service.getUserByUsername("new-name").getUserId());
        }

        try (PersistenceStore store = new PersistenceStore(database())) {
            UserService service = new UserService(store);
            assertThrows(RuntimeException.class, () -> service.getUserByUsername("old-name"));
            assertEquals(id, service.getUserByUsername("new-name").getUserId());
        }
    }

    @Test
    void adminEditPersistsAllUserFields() {
        try (PersistenceStore store = new PersistenceStore(database())) {
            UserService service = new UserService(store);
            User admin = service.registerUser("admin", "Admin@123", "admin@example.com", UserRole.ADMIN);
            User target = service.registerUser("target", "Target@123", "target@example.com", UserRole.CUSTOMER);
            service.adminUpdateUser(admin, target, "Target Updated", "target-2", "target-2@example.com",
                    "TargetNew@123", UserRole.ATTENDANT, false);
        }

        try (PersistenceStore store = new PersistenceStore(database())) {
            User user = new UserService(store).getUserByUsername("target-2");
            assertEquals("Target Updated", user.getFullName());
            assertEquals("target-2@example.com", user.getEmail());
            assertEquals(UserRole.ATTENDANT, user.getRole());
            assertFalse(user.isActive());
            assertTrue(user.verifyPassword("TargetNew@123"));
        }
    }

    @Test
    void deleteUserRemovesDatabaseRowAfterRestart() {
        String id;
        try (PersistenceStore store = new PersistenceStore(database())) {
            UserService service = new UserService(store);
            id = service.registerUser("delete-me", "Delete@123", "delete@example.com", UserRole.CUSTOMER).getUserId();
            assertTrue(service.deleteUser(id));
        }

        try (PersistenceStore store = new PersistenceStore(database())) {
            assertThrows(RuntimeException.class, () -> new UserService(store).getUserById(id));
        }
    }

    @Test
    void failedUpdateLeavesOriginalRecordUnchanged() {
        try (PersistenceStore store = new PersistenceStore(database())) {
            UserService service = new UserService(store);
            User user = service.registerUser("unchanged", "Original@123", "unchanged@example.com", UserRole.CUSTOMER);
            assertThrows(RuntimeException.class, () -> service.updateUserProfile(user, "not-an-email", "Changed@123"));
            assertEquals("unchanged@example.com", user.getEmail());
            assertTrue(user.verifyPassword("Original@123"));
        }

        try (PersistenceStore store = new PersistenceStore(database())) {
            User user = new UserService(store).getUserByUsername("unchanged");
            assertEquals("unchanged@example.com", user.getEmail());
            assertTrue(user.verifyPassword("Original@123"));
        }
    }

    @Test
    void duplicateUsernameAndEmailRemainEnforcedAfterRestart() {
        try (PersistenceStore store = new PersistenceStore(database())) {
            UserService service = new UserService(store);
            service.registerUser("duplicate", "Duplicate@123", "duplicate@example.com", UserRole.CUSTOMER);
        }
        try (PersistenceStore store = new PersistenceStore(database())) {
            UserService service = new UserService(store);
            assertThrows(RuntimeException.class, () -> service.registerUser("duplicate", "Other@123", "other@example.com", UserRole.CUSTOMER));
            assertThrows(RuntimeException.class, () -> service.registerUser("other", "Other@123", "DUPLICATE@example.com", UserRole.CUSTOMER));
        }
    }

    @Test
    void notificationPreferenceSurvivesRestart() {
        String id;
        try (PersistenceStore store = new PersistenceStore(database())) {
            UserService service = new UserService(store);
            NotificationService notifications = new NotificationService(store);
            User user = service.registerUser("notify-user", "Notify@123", "notify@example.com", UserRole.CUSTOMER);
            id = user.getUserId();
            notifications.savePreference(new NotificationPreference(id,
                    Set.of(NotificationType.ENTRY_EXIT, NotificationType.PAYMENT_SUCCESS), "DAILY", false));
        }

        try (PersistenceStore store = new PersistenceStore(database())) {
            NotificationPreference loaded = new NotificationService(store).loadPreference(id);
            assertEquals(id, loaded.userId());
            assertEquals(Set.of(NotificationType.ENTRY_EXIT, NotificationType.PAYMENT_SUCCESS), loaded.enabledTypes());
            assertEquals("DAILY", loaded.frequency());
            assertFalse(loaded.soundEnabled());
        }
    }

    @Test
    void customerWalletBalanceSurvivesRestart() {
        String id;
        try (PersistenceStore store = new PersistenceStore(database())) {
            UserService service = new UserService(store);
            Customer customer = (Customer) service.registerUser(
                    "wallet-user", "Wallet@123", "wallet@example.com", UserRole.CUSTOMER);
            id = customer.getUserId();
            customer.addWalletBalance(125.50);
            store.saveUser(customer);
        }

        try (PersistenceStore store = new PersistenceStore(database())) {
            Customer customer = (Customer) new UserService(store).getUserById(id);
            assertEquals(125.50, customer.getWalletBalance(), 0.001);
        }
    }
}
