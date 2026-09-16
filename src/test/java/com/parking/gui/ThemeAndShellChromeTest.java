package com.parking.gui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ThemeAndShellChromeTest {

    @Test
    void adminThemeChangesRefreshTheActivePageAndScene() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/parking/gui/ParkingApplication.java"));

        assertTrue(source.contains("ThemeManager.get().apply(dashboardRoot);"),
                "theme changes must refresh the active dashboard page");
        assertTrue(source.contains("scene.getRoot().applyCss();"),
                "theme changes must reapply CSS to the live scene");
        String settingsSource = Files.readString(Path.of(
                "src/main/java/com/parking/gui/SettingsView.java"));
        assertTrue(settingsSource.contains("ThemeManager.get().apply(host);"),
                "settings theme changes must refresh the active settings page");
    }

    @Test
    void sidebarFooterKeepsUserBadgeAndLogoutAtTheBottom() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/parking/gui/shell/AppShell.java"));

        assertTrue(source.contains("VBox footer"),
                "sidebar user badge and logout need a dedicated footer container");
        assertTrue(source.contains("side.getChildren().addAll(collapse, brand, nav, push, footer);"),
                "sidebar footer must remain after the growing navigation spacer");
    }
}
