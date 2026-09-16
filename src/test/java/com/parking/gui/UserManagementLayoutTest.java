package com.parking.gui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class UserManagementLayoutTest {

    @Test
    void userManagementHeaderUsesResponsiveWrapping() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/parking/gui/UserManagementView.java"));

        assertTrue(source.contains("FlowPane actions"),
                "user-management actions must wrap instead of forcing a narrow horizontal row");
        assertTrue(source.contains("VBox header"),
                "user-management header must keep title and actions on separate responsive rows");
    }

    @Test
    void userManagementFiltersUseResponsiveWrapping() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/parking/gui/UserManagementView.java"));

        assertTrue(source.contains("FlowPane filters"),
                "user-management filters must wrap at narrow widths");
    }
}
