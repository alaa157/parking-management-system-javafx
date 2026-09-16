package com.parking.gui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthWindowLayoutTest {

    @Test
    void logoutPreservesTheWindowStateWhenReturningToLogin() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/parking/gui/ParkingApplication.java"));

        assertTrue(source.contains("stage.isMaximized()"),
                "logout must capture the current maximized state");
        assertTrue(source.contains("stage.setMaximized(maximized)"),
                "logout must restore the maximized state after changing scenes");
        assertTrue(source.contains("stage.setWidth(width)"),
                "logout must preserve the window width when not maximized");
        assertTrue(source.contains("stage.setHeight(height)"),
                "logout must preserve the window height when not maximized");
    }

    @Test
    void authenticationScreensUseCenteredFloatingCards() throws IOException {
        String login = Files.readString(Path.of(
                "src/main/java/com/parking/gui/LoginView.java"));
        String registration = Files.readString(Path.of(
                "src/main/java/com/parking/gui/RegistrationView.java"));
        String css = Files.readString(Path.of(
                "src/main/resources/parkingos.css.template"));

        assertTrue(login.contains("auth-root") && login.contains("auth-card"),
                "login must use the centered authentication surface and floating card");
        assertTrue(registration.contains("auth-root") && registration.contains("auth-card"),
                "registration must use the centered authentication surface and floating card");
        assertTrue(css.contains(".auth-root") && css.contains(".auth-card"),
                "authentication surfaces need dedicated floating-card styles");
    }
}
