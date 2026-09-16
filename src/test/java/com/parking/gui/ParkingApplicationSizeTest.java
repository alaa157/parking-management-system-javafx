package com.parking.gui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ParkingApplicationSizeTest {

    @Test
    void parkingApplicationIsNotUnlimited() throws Exception {
        long lines = Files.lines(Path.of("src/main/java/com/parking/gui/ParkingApplication.java")).count();
        assertTrue(lines < 800, "ParkingApplication is " + lines + " lines");
    }
}
