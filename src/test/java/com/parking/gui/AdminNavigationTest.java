package com.parking.gui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminNavigationTest {

    @Test
    void adminParkingSpotsRouteOpensGarageManagement() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/parking/gui/ParkingApplication.java"));
        int adminStart = source.indexOf("case ADMIN:");
        int defaultStart = source.indexOf("default:", adminStart);
        String adminNavigation = source.substring(adminStart, defaultStart);

        assertTrue(adminNavigation.contains("\"Overview\", true,\n"
                        + "                        () -> navigateTo(buildDashboardContent(root)))"),
                "admin Overview must remain connected to the dashboard");
        assertTrue(adminNavigation.contains("\"Parking Spots\", false,\n"
                        + "                        () -> navigateTo(buildParkingManagement(root)))"),
                "admin Parking Spots must open garage management");
    }

    @Test
    void adminNavigationCombinesReportingAndIncludesSettings() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/parking/gui/ParkingApplication.java"));
        int adminStart = source.indexOf("case ADMIN:");
        int defaultStart = source.indexOf("default:", adminStart);
        String adminNavigation = source.substring(adminStart, defaultStart);

        assertTrue(adminNavigation.contains("\"Revenue & Analytics\", false,\n"
                        + "                        () -> navigateTo(buildAnalyticsReports()))"),
                "admin navigation must expose one combined revenue and analytics tab");
        assertTrue(adminNavigation.contains("\"Settings\", false,\n"
                        + "                        () -> navigateTo(buildSettings()))"),
                "admin navigation must expose Settings");
        assertTrue(!adminNavigation.contains("\"Revenue\", false")
                        && !adminNavigation.contains("\"Analytics\", false"),
                "admin navigation must not expose duplicate reporting tabs");
    }
}
