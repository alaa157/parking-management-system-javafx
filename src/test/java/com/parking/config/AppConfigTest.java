package com.parking.config;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppConfigTest {
    @TempDir Path tempDir;

    @BeforeEach
    void configureTemporarySettingsFile() {
        System.setProperty("parkingos.settings.file", tempDir.resolve("settings.properties").toString());
        AppConfig.load();
    }

    @AfterEach
    void clearConfigurationOverride() {
        System.clearProperty("parkingos.settings.file");
        AppConfig.load();
    }

    @Test
    void normalSaveAndReloadPreservesAllSettings() {
        AppConfig.update(" Garage A ", "Address A", 7.25, 12.5, 15, 36, "EUR");
        AppConfig.load();

        assertEquals(AppConfig.LoadStatus.LOADED, AppConfig.lastLoadStatus());
        assertEquals("Garage A", AppConfig.garageName());
        assertEquals("Address A", AppConfig.garageAddress());
        assertEquals(7.25, AppConfig.baseRate());
        assertEquals(0.125, AppConfig.taxRate());
        assertEquals(15, AppConfig.freeParkingMinutes());
        assertEquals(36, AppConfig.maxParkHours());
        assertEquals(5, AppConfig.reservationHoldMinutes());
        assertEquals("EUR", AppConfig.currency());
    }

    @Test
    void malformedSettingsResetDefaultsWithoutPartialApplication() throws Exception {
        Path file = tempDir.resolve("settings.properties");
        Files.writeString(file, "garage.name=Partially Applied\nbase.rate=not-a-number\n", StandardCharsets.UTF_8);

        AppConfig.load();

        assertEquals("Main Parking Garage", AppConfig.garageName());
        assertEquals(AppConfig.DEFAULT_BASE_RATE, AppConfig.baseRate());
        assertEquals(AppConfig.DEFAULT_TAX_RATE, AppConfig.taxRate());
        assertEquals(AppConfig.DEFAULT_RESERVATION_HOLD_MINUTES, AppConfig.reservationHoldMinutes());
        assertEquals(AppConfig.LoadStatus.MALFORMED, AppConfig.lastLoadStatus());
    }

    @Test
    void failedReplacementPreservesPreviousFileAndCleansTemporaryFile() throws Exception {
        AppConfig.update("Before Failure", "Stable Address", 5.0, 10.0, 5, 48, "USD");
        Path file = tempDir.resolve("settings.properties");
        byte[] previous = Files.readAllBytes(file);

        AppConfig.failBeforeReplacementForTests();
        assertThrows(IllegalStateException.class,
                () -> AppConfig.update("After Failure", "Should Not Replace", 9.0, 20.0, 10, 72, "GBP"));

        assertArrayEquals(previous, Files.readAllBytes(file));
        try (Stream<Path> files = Files.list(tempDir)) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().startsWith("settings.properties")
                    && path.getFileName().toString().endsWith(".tmp")));
        }

        AppConfig.load();
        assertEquals("Before Failure", AppConfig.garageName());
        assertEquals("Stable Address", AppConfig.garageAddress());
        assertEquals("USD", AppConfig.currency());
    }

    @Test
    void incompleteSettingsUseDefaultsForMissingProperties() throws Exception {
        Path file = tempDir.resolve("settings.properties");
        Files.writeString(file, "garage.name=Small Garage\n", StandardCharsets.UTF_8);

        AppConfig.load();

        assertEquals("Small Garage", AppConfig.garageName());
        assertEquals("Behind the University", AppConfig.garageAddress());
        assertEquals(AppConfig.DEFAULT_BASE_RATE, AppConfig.baseRate());
        assertEquals(AppConfig.DEFAULT_MAX_PARK_HOURS, AppConfig.maxParkHours());
    }

    @Test
    void missingSettingsFileResetsDefaultsAndReportsMissingStatus() {
        assertEquals(AppConfig.LoadStatus.MISSING, AppConfig.lastLoadStatus());
        assertEquals("Main Parking Garage", AppConfig.garageName());
    }

    @Test
    void legacySettingsAreLoadedAndMigratedWithSchemaVersion() throws Exception {
        Path file = tempDir.resolve("settings.properties");
        Files.writeString(file, "garage.name=Legacy Garage\nbase.rate=8.5\ntax.rate=0.15\n");

        AppConfig.load();

        assertEquals(AppConfig.LoadStatus.MIGRATED, AppConfig.lastLoadStatus());
        assertEquals("Legacy Garage", AppConfig.garageName());
        Properties persisted = new Properties();
        try (var in = Files.newInputStream(file)) {
            persisted.load(in);
        }
        assertEquals(Integer.toString(AppConfig.CURRENT_SCHEMA_VERSION),
                persisted.getProperty("config.schema.version"));
    }

    @Test
    void malformedSettingsDoNotPartiallyOverwriteExistingValidValues() throws Exception {
        AppConfig.update("Stable Garage", "Stable Address", 9.0, 11.0, 10, 48, "GBP");
        Files.writeString(tempDir.resolve("settings.properties"),
                "garage.name=Partial\nbase.rate=not-a-number\n");

        AppConfig.load();

        assertEquals(AppConfig.LoadStatus.MALFORMED, AppConfig.lastLoadStatus());
        assertEquals("Stable Garage", AppConfig.garageName());
        assertEquals(9.0, AppConfig.baseRate());
        assertEquals("GBP", AppConfig.currency());
    }

    @Test
    void unsupportedSchemaDoesNotOverwriteExistingValidValues() throws Exception {
        AppConfig.update("Stable Garage", "Stable Address", 9.0, 11.0, 10, 48, "GBP");
        Files.writeString(tempDir.resolve("settings.properties"),
                "config.schema.version=999\ngarage.name=Future Garage\nbase.rate=99\n");

        AppConfig.load();

        assertEquals(AppConfig.LoadStatus.UNSUPPORTED_VERSION, AppConfig.lastLoadStatus());
        assertEquals("Stable Garage", AppConfig.garageName());
        assertEquals(9.0, AppConfig.baseRate());
    }

    @Test
    void roundTripPersistsCurrentSchemaVersion() throws Exception {
        AppConfig.update("Round Trip", "Address", 6.75, 12.0, 20, 72, "EUR");
        AppConfig.load();

        assertEquals(AppConfig.LoadStatus.LOADED, AppConfig.lastLoadStatus());
        assertEquals("Round Trip", AppConfig.garageName());
        assertEquals(6.75, AppConfig.baseRate());
        assertEquals(0.12, AppConfig.taxRate());
    }

    @Test
    void defaultReportsDirectoryIsAbsoluteAndScopedToApplicationData() {
        Path reports = AppConfig.reportsDirectory();

        assertTrue(reports.isAbsolute());
        assertEquals(tempDir.resolve("reports").toAbsolutePath().normalize(), reports);
    }

    @Test
    void configuredReportsDirectoryPersistsAndReloads() throws Exception {
        Path configured = tempDir.resolve("exports").resolve("scheduled");

        AppConfig.setReportsDirectory(configured);
        assertEquals(configured.toAbsolutePath().normalize(), AppConfig.reportsDirectory());

        AppConfig.load();

        assertEquals(configured.toAbsolutePath().normalize(), AppConfig.reportsDirectory());
    }

    @Test
    void invalidReportsDirectoryOverrideIsRejected() {
        System.setProperty("parkingos.reports.directory", "bad\u0000path");

        assertThrows(IllegalStateException.class, AppConfig::reportsDirectory);

        System.clearProperty("parkingos.reports.directory");
    }
}
