package com.parking.gui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class NavigationStyleTest {

    @Test
    void idleNavigationButtonsDoNotRenderAnOutline() throws IOException {
        String css = new String(
                NavigationStyleTest.class.getResourceAsStream("/parkingos.css.template").readAllBytes(),
                StandardCharsets.UTF_8);

        assertTrue(css.contains(".nav-button {\n    -fx-background-color: transparent;\n"
                        + "    -fx-text-fill: @MUTED@;\n    -fx-border-color: transparent;\n"
                        + "    -fx-border-width: 0;\n}"),
                        "idle navigation buttons must explicitly remove the JavaFX default border");
    }

    @Test
    void loginDoesNotExposePasswordRecovery() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/parking/gui/ParkingApplication.java"));

        assertFalse(source.contains("Forgot password?"));
        assertFalse(source.contains("Password recovery is not connected yet."));
    }

    @Test
    void demoUsersAreOptInAndFreshDatabasesHaveAdminSetup() throws IOException {
        String servicesSource = Files.readString(Path.of(
                "src/main/java/com/parking/gui/shell/AppServices.java"));
        String applicationSource = Files.readString(Path.of(
                "src/main/java/com/parking/gui/ParkingApplication.java"));

        assertTrue(servicesSource.contains("Boolean.getBoolean(\"parkingos.demo\")"),
                "demo data must require an explicit development property");
        assertTrue(applicationSource.contains("showFirstRunSetup()"),
                "fresh databases must provide first-run administrator setup");
    }
}
