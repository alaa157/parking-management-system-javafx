package com.parking.gui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ShellRefactorCharacterizationTest {

    @Test
    void shellFlowsHaveFocusedViewSeams() throws Exception {
        assertTrue(Files.exists(Path.of("src/main/java/com/parking/gui/LoginView.java")));
        assertTrue(Files.exists(Path.of("src/main/java/com/parking/gui/RegistrationView.java")));
        assertTrue(Files.exists(Path.of("src/main/java/com/parking/gui/FirstRunSetupView.java")));
        assertTrue(Files.readString(Path.of("src/main/java/com/parking/gui/RegistrationView.java"))
                .contains("public Parent build()"));
        assertTrue(Files.readString(Path.of("src/main/java/com/parking/gui/FirstRunSetupView.java"))
                .contains("public Parent build()"));
        assertTrue(Files.exists(Path.of("src/main/java/com/parking/gui/GarageSelectorView.java")));
        assertTrue(Files.exists(Path.of("src/main/java/com/parking/gui/OperationsViewFactory.java")));
    }

    @Test
    void existingShellStillOwnsSessionNavigationAndLogoutCallbacks() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/parking/gui/ParkingApplication.java"));
        assertTrue(source.contains("private User currentUser;"));
        assertTrue(source.contains("private void navigateTo(Node page)"));
        assertTrue(source.contains("private void logout()"));
        assertTrue(!source.contains("showLoginLegacy"));
        assertTrue(!source.contains("private void handleLogin("));
        String login = Files.readString(Path.of("src/main/java/com/parking/gui/LoginView.java"));
        assertTrue(login.contains("new Button()"));
        assertTrue(!login.contains("new Button(\"Sign In\")"));
        assertTrue(login.contains("password-toggle"));
        assertTrue(login.contains("EYE_OFF"));
    }

    @Test
    void occupancyAndAuthFlowsStillExistSomewhere() throws Exception {
        String app = Files.readString(Path.of("src/main/java/com/parking/gui/ParkingApplication.java"));
        assertTrue(Files.exists(Path.of("src/main/java/com/parking/gui/shell/AppShell.java")));
        assertTrue(Files.exists(Path.of("src/main/java/com/parking/gui/DashboardView.java")));
        assertTrue(Files.exists(Path.of("src/main/java/com/parking/gui/OccupancyMapView.java")));
        assertTrue(Files.exists(Path.of("src/main/java/com/parking/gui/CustomerVehiclesView.java")));
        assertTrue(Files.exists(Path.of("src/main/java/com/parking/gui/WalletView.java")));
        assertTrue(Files.exists(Path.of("src/main/java/com/parking/gui/MaintenanceView.java")));
        assertTrue(Files.readString(Path.of("src/main/java/com/parking/gui/OccupancyMapView.java"))
                .contains("interface OccupancyHost"));
        assertTrue(app.contains("new DashboardView"));
        assertTrue(app.contains("new OccupancyMapView"));
        assertTrue(app.contains("new WalletView"));
        assertTrue(app.contains("new MaintenanceView"));
        assertTrue(app.contains("occupancyHost()"));
        assertTrue(!app.contains("private void performPark("));
    }
}
