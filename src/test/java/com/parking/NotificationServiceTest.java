package com.parking;

import com.parking.enums.NotificationType;
import com.parking.enums.UserRole;
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

class NotificationServiceTest {
    @Test
    void disabledTypeProducesNoRow(@TempDir Path dir) {
        var store = new PersistenceStore(dir.resolve("n.db"));
        var users = new UserService(store);
        var svc = new NotificationService(store);
        var u = users.registerUser("n1", "Customer@123!", "n1@x.com", UserRole.CUSTOMER);
        svc.savePreference(new NotificationPreference(u.getUserId(), Set.of(NotificationType.PAYMENT_SUCCESS), "DAILY", true));
        svc.publish(u.getUserId(), NotificationType.MAINTENANCE, "m");
        assertTrue(svc.listUnread(u).isEmpty());
    }

    @Test
    void enabledTypePersistsRow(@TempDir Path dir) {
        var store = new PersistenceStore(dir.resolve("n.db"));
        var users = new UserService(store);
        var svc = new NotificationService(store);
        var u = users.registerUser("n2", "Customer@123!", "n2@x.com", UserRole.CUSTOMER);
        svc.savePreference(new NotificationPreference(u.getUserId(), Set.of(NotificationType.PAYMENT_SUCCESS), "DAILY", true));
        svc.publish(u.getUserId(), NotificationType.PAYMENT_SUCCESS, "paid");
        var unread = svc.listUnread(u);
        assertEquals(1, unread.size());
        assertEquals("paid", unread.get(0).message());
        assertEquals(NotificationType.PAYMENT_SUCCESS, unread.get(0).type());
        assertTrue(unread.get(0).unread());
    }

    @Test
    void publishWithoutPreferenceDelivers(@TempDir Path dir) {
        var store = new PersistenceStore(dir.resolve("n.db"));
        var users = new UserService(store);
        var svc = new NotificationService(store);
        var u = users.registerUser("n3", "Customer@123!", "n3@x.com", UserRole.CUSTOMER);
        svc.publish(u.getUserId(), NotificationType.ENTRY_EXIT, "welcome");
        assertEquals(1, svc.listUnread(u).size());
    }

    @Test
    void markAllReadClearsUnread(@TempDir Path dir) {
        var store = new PersistenceStore(dir.resolve("n.db"));
        var users = new UserService(store);
        var svc = new NotificationService(store);
        var u = users.registerUser("n4", "Customer@123!", "n4@x.com", UserRole.CUSTOMER);
        svc.publish(u.getUserId(), NotificationType.ENTRY_EXIT, "one");
        svc.publish(u.getUserId(), NotificationType.MAINTENANCE, "two");
        assertEquals(2, svc.listUnread(u).size());
        svc.markAllRead(u);
        assertTrue(svc.listUnread(u).isEmpty());
    }

    @Test
    void recipientsAreIsolated(@TempDir Path dir) {
        var store = new PersistenceStore(dir.resolve("n.db"));
        var users = new UserService(store);
        var svc = new NotificationService(store);
        User a = users.registerUser("na", "Customer@123!", "na@x.com", UserRole.CUSTOMER);
        User b = users.registerUser("nb", "Customer@123!", "nb@x.com", UserRole.CUSTOMER);
        svc.publish(a.getUserId(), NotificationType.MAINTENANCE, "for-a");
        assertEquals(1, svc.listUnread(a).size());
        assertTrue(svc.listUnread(b).isEmpty());
        svc.markAllRead(b);
        assertEquals(1, svc.listUnread(a).size());
    }

    @Test
    void notificationsSurviveRestart(@TempDir Path dir) {
        Path db = dir.resolve("n.db");
        String userId;
        try (var store = new PersistenceStore(db)) {
            var users = new UserService(store);
            var svc = new NotificationService(store);
            var u = users.registerUser("n5", "Customer@123!", "n5@x.com", UserRole.CUSTOMER);
            userId = u.getUserId();
            svc.savePreference(new NotificationPreference(userId, Set.of(NotificationType.values()), "IMMEDIATE", false));
            svc.publish(userId, NotificationType.WEEKLY_SUMMARY, "weekly");
        }
        try (var store = new PersistenceStore(db)) {
            var svc = new NotificationService(store);
            User u = new UserService(store).getUserById(userId);
            assertEquals(1, svc.listUnread(u).size());
            assertEquals(Set.of(NotificationType.values()), Set.copyOf(svc.loadPreference(userId).enabledTypes()));
        }
    }
}
