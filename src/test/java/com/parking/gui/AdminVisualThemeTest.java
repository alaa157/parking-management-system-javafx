package com.parking.gui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminVisualThemeTest {

    @Test
    void adminNavigationKeepsOneGarageRoute() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/parking/gui/ParkingApplication.java"));
        int adminStart = source.indexOf("case ADMIN:");
        int defaultStart = source.indexOf("default:", adminStart);
        String adminNavigation = source.substring(adminStart, defaultStart);

        assertTrue(!adminNavigation.contains("\"Garage\", false"),
                "admin navigation must remove the duplicate Garage tab");
        assertTrue(adminNavigation.contains("\"Parking Spots\", false"),
                "admin navigation must retain the parking spots route");
    }

    @Test
    void spotCardsHaveLightThemeSurfacesAndReadableText() throws IOException {
        String css = new String(
                AdminVisualThemeTest.class.getResourceAsStream("/parkingos.css.template").readAllBytes(),
                StandardCharsets.UTF_8);

        assertTrue(css.contains(".theme-light .spot-available"), "available spot cards need a light override");
        assertTrue(css.contains(".theme-light .spot-occupied"), "occupied spot cards need a light override");
        assertTrue(css.contains(".theme-light .spot-maintenance"), "maintenance spot cards need a light override");
        assertTrue(css.contains(".theme-light .spot-out-of-service"), "out-of-service cards need a light override");
        assertTrue(css.contains("-fx-text-fill: @LIGHT_TEXT@;"), "light spot cards need readable text");
    }

    @Test
    void analyticsUsesTheGlobalThemeStateAndRefreshesOnToggle() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/parking/gui/AnalyticsReportingView.java"));

        assertTrue(source.contains("ThemeManager.get().isLight()"),
                "analytics must initialize from the global theme");
        assertTrue(source.contains("ThemeManager.get().setLight(lightTheme)"),
                "analytics theme toggle must update the global theme");
        assertTrue(source.contains("getRoot().applyCss();"),
                "analytics theme toggle must refresh the live scene");
        assertTrue(source.contains("if (source != null) showToast(\"Analytics data refreshed successfully\", GREEN);"),
                "opening analytics must not show a manual-refresh notification");
    }

    @Test
    void paymentMethodOptionsHaveLightThemeStates() throws IOException {
        String css = new String(
                AdminVisualThemeTest.class.getResourceAsStream("/parkingos.css.template").readAllBytes(),
                StandardCharsets.UTF_8);

        assertTrue(css.contains(".theme-light .method-option"),
                "payment method options need a light surface and readable text");
        assertTrue(css.contains(".theme-light .method-option:selected"),
                "selected payment method options need a light-theme state");
        assertTrue(css.contains(".theme-light .method-option:hover"),
                "payment method options need a light-theme hover state");
    }

    @Test
    void topRightGhostButtonsHaveLightHoverAndPressedStates() throws IOException {
        String css = new String(
                AdminVisualThemeTest.class.getResourceAsStream("/parkingos.css.template").readAllBytes(),
                StandardCharsets.UTF_8);

        assertTrue(css.contains(".theme-light .ghost-button:hover"),
                "light mode must preserve readable Dashboard and Logout hover states");
        assertTrue(css.contains(".theme-light .ghost-button:pressed"),
                "light mode must preserve readable Dashboard and Logout pressed states");
    }
}
