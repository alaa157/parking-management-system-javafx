package com.parking.gui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsRoleTabsTest {

    @Test
    void settingsTabsAreRoleAwareAndCustomerNavigationSkipsParkingConfig() throws IOException {
        String source = Files.readString(Path.of("src/main/java/com/parking/gui/SettingsView.java"));

        assertTrue(source.contains("boolean admin = currentUser.getRole() == UserRole.ADMIN;"),
                "settings navigation must determine whether the current user is an admin");
        assertTrue(source.contains("new String[]{\"Profile\", \"Security\", \"Notifications\", \"Parking Config\", \"Appearance\", \"About\"}"),
                "admins must retain the Parking Config tab");
        assertTrue(source.contains("new String[]{\"Profile\", \"Security\", \"Notifications\", \"Appearance\", \"About\"}"),
                "customers must not receive the Parking Config tab");
        assertTrue(source.contains("activeTab = (activeTab + 1) % settingsNav.getChildren().size();"),
                "customer keyboard navigation must use the visible tab count");
    }
}
