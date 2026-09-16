package com.parking.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Properties;

/** Runtime configuration with safe defaults and a small, secret-free store. */
public final class AppConfig {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    private static final String SCHEMA_VERSION_KEY = "config.schema.version";
    public static final double DEFAULT_TAX_RATE = 0.10;
    public static final double DEFAULT_BASE_RATE = 5.0;
    public static final int DEFAULT_RESERVATION_HOLD_MINUTES = 5;
    public static final int DEFAULT_MAX_PARK_HOURS = 48;
    public static final int DEFAULT_FREE_PARKING_MINUTES = 0;
    public static final String DEFAULT_CURRENCY = "USD";
    private static volatile double taxRate = DEFAULT_TAX_RATE;
    private static volatile double baseRate = DEFAULT_BASE_RATE;
    private static volatile int reservationHoldMinutes = DEFAULT_RESERVATION_HOLD_MINUTES;
    private static volatile int maxParkHours = DEFAULT_MAX_PARK_HOURS;
    private static volatile int freeParkingMinutes = DEFAULT_FREE_PARKING_MINUTES;
    private static volatile String garageName = "Main Parking Garage";
    private static volatile String garageAddress = "Behind the University";
    private static volatile String currency = DEFAULT_CURRENCY;
    private static volatile String reportsDirectory = "";
    private static volatile LoadStatus lastLoadStatus = LoadStatus.MISSING;
    private static volatile boolean failBeforeReplacementForTests;

    public enum LoadStatus { MISSING, LOADED, MIGRATED, MALFORMED, UNSUPPORTED_VERSION, IO_FAILURE }

    private AppConfig() { }

    public static synchronized void load() {
        Path file = settingsFile();
        if (!Files.isRegularFile(file)) {
            resetDefaults();
            lastLoadStatus = LoadStatus.MISSING;
            return;
        }
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
        } catch (IOException e) {
            lastLoadStatus = LoadStatus.IO_FAILURE;
            return;
        }

        try {
            int version = readSchemaVersion(p);
            if (version < 0 || version > CURRENT_SCHEMA_VERSION) {
                lastLoadStatus = LoadStatus.UNSUPPORTED_VERSION;
                return;
            }
            boolean migrate = version < CURRENT_SCHEMA_VERSION;
            Properties migrated = migrateProperties(p, version);
            SettingsValues values = parse(migrated);
            applyValues(values);
            if (migrate) {
                try {
                    save();
                    lastLoadStatus = LoadStatus.MIGRATED;
                } catch (IllegalStateException migrationWriteFailure) {
                    lastLoadStatus = LoadStatus.IO_FAILURE;
                }
            } else {
                lastLoadStatus = LoadStatus.LOADED;
            }
        } catch (IllegalArgumentException malformed) {
            lastLoadStatus = LoadStatus.MALFORMED;
        }
    }

    public static synchronized void save() {
        Path file = settingsFile().toAbsolutePath();
        Path temporary = null;
        try {
            Path parent = file.getParent();
            if (parent == null) throw new IOException("Settings file has no parent directory");
            Files.createDirectories(parent);
            Properties p = new Properties();
            p.setProperty(SCHEMA_VERSION_KEY, Integer.toString(CURRENT_SCHEMA_VERSION));
            p.setProperty("garage.name", garageName);
            p.setProperty("garage.address", garageAddress);
            p.setProperty("base.rate", Double.toString(baseRate));
            p.setProperty("tax.rate", Double.toString(taxRate));
            p.setProperty("free.minutes", Integer.toString(freeParkingMinutes));
            p.setProperty("max.hours", Integer.toString(maxParkHours));
            p.setProperty("reservation.minutes", Integer.toString(reservationHoldMinutes));
            p.setProperty("currency", currency);
            if (!reportsDirectory.isBlank()) p.setProperty("reports.directory", reportsDirectory);
            temporary = Files.createTempFile(parent, file.getFileName().toString(), ".tmp");
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE);
                 OutputStream out = Channels.newOutputStream(channel)) {
                p.store(out, "ParkingOS runtime settings");
                out.flush();
                channel.force(true);
            }
            if (failBeforeReplacementForTests) {
                failBeforeReplacementForTests = false;
                throw new IOException("Injected settings replacement failure");
            }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            temporary = null;
        } catch (IOException e) {
            throw new IllegalStateException("Could not persist application settings", e);
        } finally {
            failBeforeReplacementForTests = false;
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException ignored) { }
            }
        }
    }

    public static synchronized void update(String name, String address, double rate, double taxPercent,
                                            int freeMinutes, int maxHours, String currencyCode) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Garage name is required");
        if (address == null || address.isBlank()) throw new IllegalArgumentException("Garage address is required");
        if (!Double.isFinite(rate) || rate < 0) throw new IllegalArgumentException("Base rate must be non-negative");
        if (!Double.isFinite(taxPercent) || taxPercent < 0 || taxPercent > 100) throw new IllegalArgumentException("Tax rate must be between 0 and 100");
        if (freeMinutes < 0 || freeMinutes > 1440) throw new IllegalArgumentException("Free parking must be between 0 and 1440 minutes");
        if (maxHours <= 0 || maxHours > 720) throw new IllegalArgumentException("Maximum duration must be between 1 and 720 hours");
        if (currencyCode == null || !currencyCode.matches("[A-Z]{3}")) throw new IllegalArgumentException("Currency must be a three-letter code");
        updateInMemory(name.trim(), address.trim(), rate, taxPercent, freeMinutes,
                maxHours, reservationHoldMinutes, currencyCode, reportsDirectory);
        save();
    }

    private static int readSchemaVersion(Properties p) {
        String raw = p.getProperty(SCHEMA_VERSION_KEY);
        if (raw == null || raw.isBlank()) return 0;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException invalidVersion) {
            throw new IllegalArgumentException("Invalid configuration schema version", invalidVersion);
        }
    }

    /** Applies explicit migrations without mutating the live configuration. */
    private static Properties migrateProperties(Properties source, int version) {
        Properties migrated = new Properties();
        migrated.putAll(source);
        switch (version) {
            case 0 -> migrated.setProperty(SCHEMA_VERSION_KEY, Integer.toString(CURRENT_SCHEMA_VERSION));
            case CURRENT_SCHEMA_VERSION -> { }
            default -> throw new IllegalArgumentException("Unsupported configuration schema version: " + version);
        }
        return migrated;
    }

    private static SettingsValues parse(Properties p) {
        String name = p.getProperty("garage.name", "Main Parking Garage");
        String address = p.getProperty("garage.address", "Behind the University");
        double rate = Double.parseDouble(p.getProperty("base.rate", Double.toString(DEFAULT_BASE_RATE)));
        double taxPercent = Double.parseDouble(p.getProperty("tax.rate", Double.toString(DEFAULT_TAX_RATE))) * 100;
        int freeMinutes = Integer.parseInt(p.getProperty("free.minutes", Integer.toString(DEFAULT_FREE_PARKING_MINUTES)));
        int maxHours = Integer.parseInt(p.getProperty("max.hours", Integer.toString(DEFAULT_MAX_PARK_HOURS)));
        int reservationMinutes = Integer.parseInt(p.getProperty("reservation.minutes", Integer.toString(DEFAULT_RESERVATION_HOLD_MINUTES)));
        String currencyCode = p.getProperty("currency", DEFAULT_CURRENCY);
        String reportDirectory = p.getProperty("reports.directory", "");
        return validateSettings(name, address, rate, taxPercent, freeMinutes, maxHours,
                reservationMinutes, currencyCode, reportDirectory);
    }

    private static void updateInMemory(String name, String address, double rate, double taxPercent,
                                       int freeMinutes, int maxHours, int reservationMinutes,
                                       String currencyCode, String reportDirectory) {
        applyValues(validateSettings(name, address, rate, taxPercent, freeMinutes, maxHours,
                reservationMinutes, currencyCode, reportDirectory));
    }

    private static SettingsValues validateSettings(String name, String address, double rate, double taxPercent,
                                                    int freeMinutes, int maxHours, int reservationMinutes,
                                                    String currencyCode, String reportDirectory) {
        if (name == null || address == null || name.isBlank() || address.isBlank()
                || !Double.isFinite(rate) || rate < 0
                || !Double.isFinite(taxPercent) || taxPercent < 0 || taxPercent > 100
                || freeMinutes < 0 || freeMinutes > 1440
                || maxHours <= 0 || maxHours > 720
                || reservationMinutes <= 0
                || currencyCode == null || !currencyCode.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Invalid persisted settings");
        }
        if (reportDirectory == null || reportDirectory.isBlank()) {
            reportDirectory = "";
        } else {
            try {
                reportDirectory = Path.of(reportDirectory).toAbsolutePath().normalize().toString();
            } catch (InvalidPathException invalidPath) {
                throw new IllegalArgumentException("Invalid reports directory", invalidPath);
            }
        }
        return new SettingsValues(name, address, rate, taxPercent, freeMinutes, maxHours,
                reservationMinutes, currencyCode.toUpperCase(Locale.ROOT), reportDirectory);
    }

    private static void applyValues(SettingsValues values) {
        garageName = values.name();
        garageAddress = values.address();
        baseRate = values.rate();
        taxRate = values.taxPercent() / 100.0;
        freeParkingMinutes = values.freeMinutes();
        reservationHoldMinutes = values.reservationMinutes();
        maxParkHours = values.maxHours();
        currency = values.currency();
        reportsDirectory = values.reportDirectory();
    }

    private record SettingsValues(String name, String address, double rate, double taxPercent,
                                   int freeMinutes, int maxHours, int reservationMinutes,
                                   String currency, String reportDirectory) { }

    private static void resetDefaults() {
        taxRate = DEFAULT_TAX_RATE;
        baseRate = DEFAULT_BASE_RATE;
        reservationHoldMinutes = DEFAULT_RESERVATION_HOLD_MINUTES;
        maxParkHours = DEFAULT_MAX_PARK_HOURS;
        freeParkingMinutes = DEFAULT_FREE_PARKING_MINUTES;
        garageName = "Main Parking Garage";
        garageAddress = "Behind the University";
        currency = DEFAULT_CURRENCY;
        reportsDirectory = "";
    }

    private static Path settingsFile() {
        String override = System.getProperty("parkingos.settings.file");
        return override == null || override.isBlank() ? Path.of(System.getProperty("user.home"), ".parkingos", "settings.properties") : Path.of(override);
    }

    static void failBeforeReplacementForTests() {
        failBeforeReplacementForTests = true;
    }

    /** Returns the normalized directory used by scheduled reports and delivery audit output. */
    public static synchronized Path reportsDirectory() {
        String configured = System.getProperty("parkingos.reports.directory");
        if (configured == null || configured.isBlank()) configured = reportsDirectory;
        if (configured == null || configured.isBlank()) return defaultReportsDirectory();
        try {
            return Path.of(configured).toAbsolutePath().normalize();
        } catch (InvalidPathException invalidPath) {
            throw new IllegalStateException("Configured reports directory is invalid", invalidPath);
        }
    }

    /** Persists an explicit reports directory; null restores the application default. */
    public static synchronized void setReportsDirectory(Path directory) {
        if (directory == null) {
            reportsDirectory = "";
        } else {
            try {
                reportsDirectory = directory.toAbsolutePath().normalize().toString();
            } catch (InvalidPathException invalidPath) {
                throw new IllegalArgumentException("Invalid reports directory", invalidPath);
            }
        }
        save();
    }

    private static Path defaultReportsDirectory() {
        Path settings = settingsFile().toAbsolutePath().normalize();
        Path parent = settings.getParent();
        if (parent == null) parent = Path.of(System.getProperty("user.home"), ".parkingos");
        return parent.resolve("reports").toAbsolutePath().normalize();
    }

    public static double taxRate() { return taxRate; }
    public static double baseRate() { return baseRate; }
    public static int reservationHoldMinutes() { return reservationHoldMinutes; }
    public static int maxParkHours() { return maxParkHours; }
    public static int freeParkingMinutes() { return freeParkingMinutes; }
    public static String garageName() { return garageName; }
    public static String garageAddress() { return garageAddress; }
    public static String currency() { return currency; }
    public static LoadStatus lastLoadStatus() { return lastLoadStatus; }
    public static final String GARAGE_ID = "G-001";
    public static final String GARAGE_NAME = "Main Parking Garage";
}
