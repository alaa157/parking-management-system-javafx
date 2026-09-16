package com.parking.persistence;

import com.parking.model.Payment;
import com.parking.model.Ticket;
import com.parking.model.User;
import com.parking.model.Admin;
import com.parking.model.Attendant;
import com.parking.model.Customer;
import com.parking.model.ParkingGarage;
import com.parking.model.ParkingSpot;
import com.parking.model.Vehicle;
import com.parking.model.Garage;
import com.parking.model.GarageAccess;
import com.parking.enums.UserRole;
import com.parking.enums.PaymentStatus;
import com.parking.enums.TicketStatus;
import com.parking.enums.SpotStatus;
import com.parking.enums.SpotType;
import com.parking.enums.VehicleType;
import com.parking.util.Money;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/** Small SQLite persistence boundary for durable business-event snapshots. */
public final class PersistenceStore implements AutoCloseable {
    private static final int CURRENT_SCHEMA_VERSION = 11;
    private Connection connection;
    private int transactionDepth;
    private int failAfterWritesForTests = -1;
    private boolean closed;

    @FunctionalInterface
    public interface TransactionWork<T> {
        T run() throws Exception;
    }

    public record ActiveTicketSnapshot(Ticket ticket, Garage garage) { }

    public PersistenceStore() {
        this(databasePath());
    }

    public PersistenceStore(Path path) {
        try {
            boolean memory = "__memory__".equals(path.toString());
            Path parent = path.toAbsolutePath().getParent();
            if (!memory && parent != null) Files.createDirectories(parent);
            connection = DriverManager.getConnection(memory ? "jdbc:sqlite::memory:" : "jdbc:sqlite:" + path.toAbsolutePath());
            configureConnection(memory);
            initializeSchema();
            // Schema migration runs before the application transaction boundary is opened.
            connection.setAutoCommit(false);
            connection.commit();
        } catch (IOException | SQLException e) {
            closeAfterInitializationFailure();
            throw new IllegalStateException("Could not initialize parking database", e);
        } catch (RuntimeException e) {
            closeAfterInitializationFailure();
            throw e;
        }
    }

    private void closeAfterInitializationFailure() {
        if (connection != null) {
            try { connection.close(); } catch (SQLException ignored) { }
        }
    }

    private void configureConnection(boolean memory) throws SQLException {
        try (Statement s = connection.createStatement()) {
            // A Swing app can have service work and refreshes close together.
            // Wait briefly for SQLite instead of failing a legitimate write
            // with SQLITE_BUSY. WAL also keeps reads from unnecessarily
            // blocking a committed write in file-backed databases.
            s.execute("PRAGMA busy_timeout = 5000");
            s.execute("PRAGMA foreign_keys = ON");
            if (!memory) {
                s.execute("PRAGMA journal_mode = WAL");
                s.execute("PRAGMA synchronous = NORMAL");
            }
        }
    }

    private void initializeSchema() throws SQLException {
        try (Statement s = connection.createStatement()) {
            s.executeUpdate("PRAGMA foreign_keys = ON");
            ensureSchemaVersionTable(s);
            s.executeUpdate("CREATE TABLE IF NOT EXISTS users (user_id TEXT PRIMARY KEY, username TEXT NOT NULL UNIQUE, email TEXT NOT NULL UNIQUE, password_hash TEXT NOT NULL, role TEXT NOT NULL, active INTEGER NOT NULL, updated_at TEXT NOT NULL)");
            // Keep the original table compatible while extending it for the complete user record.
            addUserColumnIfMissing(s, "full_name", "TEXT NOT NULL DEFAULT ''");
            addUserColumnIfMissing(s, "phone_number", "TEXT");
            addUserColumnIfMissing(s, "created_at", "TEXT");
            addUserColumnIfMissing(s, "last_login", "TEXT");
            addUserColumnIfMissing(s, "failed_login_attempts", "INTEGER NOT NULL DEFAULT 0");
            addUserColumnIfMissing(s, "locked_until", "TEXT");
            addUserColumnIfMissing(s, "wallet_balance", "REAL NOT NULL DEFAULT 0");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS tickets (ticket_id TEXT PRIMARY KEY, vehicle_id TEXT NOT NULL, owner_id TEXT NOT NULL, spot_id TEXT NOT NULL, garage_id TEXT, status TEXT NOT NULL, entry_time TEXT, exit_time TEXT, amount REAL NOT NULL, final_amount REAL NOT NULL, payment_id TEXT)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS payments (payment_id TEXT PRIMARY KEY, ticket_id TEXT NOT NULL, customer_id TEXT, garage_id TEXT, status TEXT NOT NULL, amount REAL NOT NULL, tax_amount REAL NOT NULL, final_amount REAL NOT NULL, payment_time TEXT, payment_method TEXT)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS notification_prefs (user_id TEXT PRIMARY KEY, enabled_types TEXT NOT NULL, frequency TEXT NOT NULL, sound_enabled INTEGER NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS notifications (notification_id TEXT PRIMARY KEY, recipient_id TEXT NOT NULL, type TEXT NOT NULL, message TEXT NOT NULL, created_at TEXT NOT NULL, unread INTEGER NOT NULL)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_notifications_recipient ON notifications(recipient_id, unread)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS audit_events (event_id INTEGER PRIMARY KEY AUTOINCREMENT, event_time TEXT NOT NULL, action TEXT NOT NULL, target_id TEXT NOT NULL, actor_id TEXT, success INTEGER NOT NULL, metadata TEXT)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS garage_config (garage_id TEXT PRIMARY KEY, name TEXT NOT NULL, address TEXT NOT NULL, total_levels INTEGER NOT NULL, base_hourly_rate REAL NOT NULL, is_open INTEGER NOT NULL)");
            migrateGarageSchema(s);
            s.executeUpdate("CREATE TABLE IF NOT EXISTS vehicles (vehicle_id TEXT PRIMARY KEY, license_plate TEXT NOT NULL UNIQUE COLLATE NOCASE, vehicle_type TEXT NOT NULL, make TEXT, model TEXT, color TEXT, year INTEGER NOT NULL, owner_id TEXT, entry_time TEXT, parking_spot_id TEXT, is_parked INTEGER NOT NULL DEFAULT 0)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS parking_spots (spot_id TEXT PRIMARY KEY, garage_id TEXT NOT NULL, level INTEGER NOT NULL, spot_type TEXT NOT NULL, location TEXT, hourly_rate REAL NOT NULL, status TEXT NOT NULL, vehicle_id TEXT, reservation_holder TEXT, reservation_expiry TEXT, under_maintenance INTEGER NOT NULL DEFAULT 0, maintenance_reason TEXT)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS reservations (reservation_id TEXT PRIMARY KEY, user_id TEXT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE, spot_id TEXT NOT NULL REFERENCES parking_spots(spot_id) ON DELETE CASCADE, created_at TEXT NOT NULL, expires_at TEXT NOT NULL, status TEXT NOT NULL CHECK(status IN ('ACTIVE','CLAIMED','CANCELLED','EXPIRED')))");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_reservations_user ON reservations(user_id, status, expires_at)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS duty_sessions (session_id TEXT PRIMARY KEY, attendant_id TEXT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE, shift TEXT NOT NULL, zone TEXT NOT NULL, started_at TEXT NOT NULL, ended_at TEXT, status TEXT NOT NULL CHECK(status IN ('OPEN','CLOSED')))");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_duty_sessions_attendant ON duty_sessions(attendant_id, status)");
            // DB-level guard: at most one OPEN session per attendant. Repair legacy
            // duplicates (keep earliest row) before enforcing the partial unique index.
            s.executeUpdate("UPDATE duty_sessions SET status='CLOSED', ended_at=started_at WHERE status='OPEN' AND rowid NOT IN (SELECT MIN(rowid) FROM duty_sessions WHERE status='OPEN' GROUP BY attendant_id)");
            s.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS idx_duty_sessions_open_attendant ON duty_sessions(attendant_id) WHERE status='OPEN'");
            ensureGaragesFromParkingSpots(s);
            migrateTicketPaymentGarageColumns(s);
            migrateReferentialIntegrity(s);
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_vehicles_owner ON vehicles(owner_id)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_tickets_owner ON tickets(owner_id)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_tickets_vehicle ON tickets(vehicle_id)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_tickets_spot ON tickets(spot_id)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_tickets_status ON tickets(status)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_tickets_garage ON tickets(garage_id)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_payments_ticket ON payments(ticket_id)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_payments_customer ON payments(customer_id)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_payments_time ON payments(payment_time)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_payments_garage ON payments(garage_id)");
            s.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS idx_parking_spots_vehicle ON parking_spots(vehicle_id) WHERE vehicle_id IS NOT NULL");
            ensureNoDuplicateActiveVehicles(s);
            s.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS idx_tickets_active_vehicle ON tickets(vehicle_id) WHERE status IN ('ACTIVE','AWAITING_PAYMENT')");
            validateSchema(s);
            setSchemaVersion(s, CURRENT_SCHEMA_VERSION);
        }
    }

    /** Adds the multi-garage model and backfills the legacy single-garage setup. */
    private void migrateGarageSchema(Statement statement) throws SQLException {
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS garages (garage_id TEXT PRIMARY KEY, name TEXT NOT NULL, address TEXT NOT NULL, total_levels INTEGER NOT NULL CHECK(total_levels > 0), base_hourly_rate REAL NOT NULL CHECK(base_hourly_rate >= 0), currency TEXT NOT NULL, free_parking_minutes INTEGER NOT NULL CHECK(free_parking_minutes >= 0), reservation_hold_minutes INTEGER NOT NULL CHECK(reservation_hold_minutes > 0), max_park_hours INTEGER NOT NULL CHECK(max_park_hours > 0), is_open INTEGER NOT NULL, archived INTEGER NOT NULL DEFAULT 0, created_at TEXT NOT NULL, updated_at TEXT NOT NULL)");
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS user_garages (access_id TEXT PRIMARY KEY, user_id TEXT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE, garage_id TEXT NOT NULL REFERENCES garages(garage_id) ON DELETE CASCADE, access_role TEXT NOT NULL, active INTEGER NOT NULL, created_at TEXT NOT NULL, updated_at TEXT NOT NULL, UNIQUE(user_id, garage_id))");
        statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_user_garages_user ON user_garages(user_id)");
        statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_user_garages_garage ON user_garages(garage_id)");
        statement.executeUpdate("INSERT INTO garages(garage_id,name,address,total_levels,base_hourly_rate,currency,free_parking_minutes,reservation_hold_minutes,max_park_hours,is_open,archived,created_at,updated_at) SELECT legacy.garage_id,legacy.name,legacy.address,legacy.total_levels,legacy.base_hourly_rate,'USD',0,5,48,legacy.is_open,0,datetime('now'),datetime('now') FROM garage_config legacy WHERE NOT EXISTS (SELECT 1 FROM garages current WHERE current.garage_id=legacy.garage_id)");
        statement.executeUpdate("INSERT OR IGNORE INTO user_garages(access_id,user_id,garage_id,access_role,active,created_at,updated_at) SELECT u.user_id || ':' || g.garage_id, u.user_id, g.garage_id, u.role, u.active, datetime('now'), datetime('now') FROM users u CROSS JOIN garages g WHERE g.garage_id=(SELECT garage_id FROM garages ORDER BY created_at, garage_id LIMIT 1)");
    }

    /** Adds garage ownership to legacy tickets/payments before the FK rebuild. */
    private void migrateTicketPaymentGarageColumns(Statement statement) throws SQLException {
        addColumnIfMissing(statement, "tickets", "garage_id", "TEXT");
        addColumnIfMissing(statement, "payments", "garage_id", "TEXT");
        statement.executeUpdate("UPDATE tickets SET garage_id=(SELECT garage_id FROM garages ORDER BY created_at, garage_id LIMIT 1) WHERE garage_id IS NULL");
        statement.executeUpdate("UPDATE payments SET garage_id=(SELECT garage_id FROM tickets WHERE tickets.ticket_id=payments.ticket_id) WHERE garage_id IS NULL");
    }

    private void ensureGaragesFromParkingSpots(Statement statement) throws SQLException {
        statement.executeUpdate("INSERT INTO garages(garage_id,name,address,total_levels,base_hourly_rate,currency,free_parking_minutes,reservation_hold_minutes,max_park_hours,is_open,archived,created_at,updated_at) "
                + "SELECT s.garage_id, s.garage_id, '', COALESCE(MAX(s.level) + 1, 1), COALESCE(MIN(s.hourly_rate), 0), 'USD', 0, 5, 48, 1, 0, datetime('now'), datetime('now') "
                + "FROM parking_spots s LEFT JOIN garages g ON g.garage_id=s.garage_id WHERE g.garage_id IS NULL GROUP BY s.garage_id");
    }

    private void ensureNoDuplicateActiveVehicles(Statement statement) throws SQLException {
        try (ResultSet result = statement.executeQuery("SELECT vehicle_id FROM tickets WHERE status IN ('ACTIVE','AWAITING_PAYMENT') GROUP BY vehicle_id HAVING COUNT(*) > 1")) {
            if (result.next()) {
                throw new IllegalStateException("Duplicate active parking sessions found for vehicle " + result.getString("vehicle_id"));
            }
        }
    }

    /**
     * Upgrades the phase-2 tables to real foreign-key tables without deleting
     * valid rows. SQLite cannot add these constraints with ALTER TABLE, so the
     * migration copies validated rows into temporary tables and swaps them.
     */
    private void migrateReferentialIntegrity(Statement statement) throws SQLException {
        int version = schemaVersion(statement);
        if (version > CURRENT_SCHEMA_VERSION) {
            throw new IllegalStateException("Database schema version " + version + " is newer than this application supports");
        }
        boolean complete = hasRequiredForeignKeys("vehicles") && hasRequiredForeignKeys("parking_spots")
                && hasRequiredForeignKeys("tickets") && hasRequiredForeignKeys("payments")
                && hasRequiredDataConstraints();
        if (complete && version == CURRENT_SCHEMA_VERSION) {
            statement.execute("PRAGMA foreign_keys = ON");
            return;
        }
        validateLegacyReferences(statement);
        validateLegacyData(statement);
        if (complete) return;

        boolean autoCommit = connection.getAutoCommit();
        if (!autoCommit) connection.setAutoCommit(true);
        try {
            statement.execute("PRAGMA foreign_keys = OFF");
            statement.execute("DROP TABLE IF EXISTS vehicles_new");
            statement.execute("DROP TABLE IF EXISTS parking_spots_new");
            statement.execute("DROP TABLE IF EXISTS tickets_new");
            statement.execute("DROP TABLE IF EXISTS payments_new");
            statement.execute("DROP TABLE IF EXISTS garage_config_new");

            statement.execute("CREATE TABLE vehicles_new (vehicle_id TEXT PRIMARY KEY, license_plate TEXT NOT NULL UNIQUE COLLATE NOCASE, vehicle_type TEXT NOT NULL, make TEXT, model TEXT, color TEXT, year INTEGER NOT NULL, owner_id TEXT REFERENCES users(user_id) ON DELETE SET NULL, entry_time TEXT, parking_spot_id TEXT, is_parked INTEGER NOT NULL DEFAULT 0)");
            statement.execute("CREATE TABLE parking_spots_new (spot_id TEXT PRIMARY KEY, garage_id TEXT NOT NULL, level INTEGER NOT NULL, spot_type TEXT NOT NULL, location TEXT, hourly_rate REAL NOT NULL CHECK(hourly_rate >= 0), status TEXT NOT NULL CHECK(status IN ('AVAILABLE','OCCUPIED','RESERVED','UNDER_MAINTENANCE','OUT_OF_SERVICE')), vehicle_id TEXT REFERENCES vehicles(vehicle_id) ON DELETE RESTRICT, reservation_holder TEXT REFERENCES users(user_id) ON DELETE SET NULL, reservation_expiry TEXT, under_maintenance INTEGER NOT NULL DEFAULT 0, maintenance_reason TEXT)");
            statement.execute("CREATE TABLE tickets_new (ticket_id TEXT PRIMARY KEY CHECK(length(trim(ticket_id)) > 0), vehicle_id TEXT NOT NULL REFERENCES vehicles(vehicle_id) ON DELETE RESTRICT, owner_id TEXT REFERENCES users(user_id) ON DELETE SET NULL, spot_id TEXT NOT NULL REFERENCES parking_spots(spot_id) ON DELETE RESTRICT, garage_id TEXT NOT NULL REFERENCES garages(garage_id) ON DELETE RESTRICT, status TEXT NOT NULL CHECK(status IN ('CREATED','ACTIVE','CLOSED','CANCELLED','AWAITING_PAYMENT','PAID','REFUNDED')), entry_time TEXT NOT NULL CHECK(length(trim(entry_time)) > 0), exit_time TEXT, amount REAL NOT NULL CHECK(amount >= 0), final_amount REAL NOT NULL CHECK(final_amount >= 0), payment_id TEXT REFERENCES payments(payment_id) ON DELETE RESTRICT)");
            statement.execute("CREATE TABLE payments_new (payment_id TEXT PRIMARY KEY CHECK(length(trim(payment_id)) > 0), ticket_id TEXT NOT NULL REFERENCES tickets(ticket_id) ON DELETE RESTRICT, customer_id TEXT REFERENCES users(user_id) ON DELETE SET NULL, garage_id TEXT NOT NULL REFERENCES garages(garage_id) ON DELETE RESTRICT, status TEXT NOT NULL CHECK(status IN ('PENDING','COMPLETED','FAILED','REFUNDED','CANCELLED')), amount REAL NOT NULL CHECK(amount >= 0), tax_amount REAL NOT NULL CHECK(tax_amount >= 0), final_amount REAL NOT NULL CHECK(final_amount >= 0 AND abs(final_amount - round(amount + tax_amount, 2)) < 0.005), payment_time TEXT NOT NULL CHECK(length(trim(payment_time)) > 0), payment_method TEXT)");
            statement.execute("CREATE TABLE garage_config_new (garage_id TEXT PRIMARY KEY, name TEXT NOT NULL, address TEXT NOT NULL, total_levels INTEGER NOT NULL, base_hourly_rate REAL NOT NULL CHECK(base_hourly_rate >= 0), is_open INTEGER NOT NULL)");

            statement.execute("INSERT INTO vehicles_new SELECT vehicle_id,license_plate,vehicle_type,make,model,color,year,owner_id,entry_time,parking_spot_id,is_parked FROM vehicles");
            statement.execute("INSERT INTO parking_spots_new SELECT spot_id,garage_id,level,spot_type,location,hourly_rate,status,vehicle_id,reservation_holder,reservation_expiry,under_maintenance,maintenance_reason FROM parking_spots");
            statement.execute("INSERT INTO tickets_new SELECT ticket_id,vehicle_id,owner_id,spot_id,garage_id,status,entry_time,exit_time,amount,final_amount,payment_id FROM tickets");
            statement.execute("INSERT INTO payments_new SELECT payment_id,ticket_id,customer_id,garage_id,status,amount,tax_amount,final_amount,payment_time,payment_method FROM payments");
            statement.execute("INSERT INTO garage_config_new SELECT garage_id,name,address,total_levels,base_hourly_rate,is_open FROM garage_config");

            statement.execute("BEGIN");
            statement.execute("DROP INDEX IF EXISTS idx_parking_spots_vehicle");
            statement.execute("DROP TABLE payments");
            statement.execute("DROP TABLE tickets");
            statement.execute("DROP TABLE parking_spots");
            statement.execute("DROP TABLE vehicles");
            statement.execute("DROP TABLE garage_config");
            statement.execute("ALTER TABLE vehicles_new RENAME TO vehicles");
            statement.execute("ALTER TABLE parking_spots_new RENAME TO parking_spots");
            statement.execute("ALTER TABLE tickets_new RENAME TO tickets");
            statement.execute("ALTER TABLE payments_new RENAME TO payments");
            statement.execute("ALTER TABLE garage_config_new RENAME TO garage_config");
            statement.execute("COMMIT");
        } catch (SQLException e) {
            try { statement.execute("ROLLBACK"); } catch (SQLException ignored) { }
            throw new IllegalStateException("Could not migrate SQLite referential integrity", e);
        } finally {
            statement.execute("PRAGMA foreign_keys = ON");
            if (!autoCommit) connection.setAutoCommit(false);
        }
    }

    private void ensureSchemaVersionTable(Statement statement) throws SQLException {
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER PRIMARY KEY, applied_at TEXT NOT NULL)");
        try (ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM schema_version")) {
            if (result.next() && result.getInt(1) == 0) {
                statement.executeUpdate("INSERT INTO schema_version(version, applied_at) VALUES(0, datetime('now'))");
            }
        }
    }

    private void validateSchema(Statement statement) throws SQLException {
        String[] tables = {"schema_version", "users", "vehicles", "parking_spots", "tickets", "payments", "audit_events", "garage_config", "garages", "user_garages", "notification_prefs", "notifications", "reservations", "duty_sessions"};
        for (String table : tables) {
            try (PreparedStatement check = connection.prepareStatement("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?")) {
                check.setString(1, table);
                try (ResultSet result = check.executeQuery()) {
                    if (!result.next()) throw new IllegalStateException("Required SQLite table is missing: " + table);
                }
            }
        }

        requireColumns("schema_version", new String[]{"version", "applied_at"});
        requireColumns("users", new String[]{"user_id", "username", "email", "active"});
        requireColumns("vehicles", new String[]{"vehicle_id", "license_plate", "owner_id"});
        requireColumns("parking_spots", new String[]{"spot_id", "garage_id", "hourly_rate"});
            requireColumns("tickets", new String[]{"ticket_id", "vehicle_id", "spot_id", "garage_id", "status", "entry_time"});
            requireColumns("payments", new String[]{"payment_id", "ticket_id", "garage_id", "status", "amount", "tax_amount", "final_amount", "payment_time"});
        requireColumns("audit_events", new String[]{"event_id", "event_time", "action"});
        requireColumns("garage_config", new String[]{"garage_id", "base_hourly_rate"});
        requireColumns("garages", new String[]{"garage_id", "name", "address", "total_levels", "base_hourly_rate", "currency", "archived"});
        requireColumns("user_garages", new String[]{"access_id", "user_id", "garage_id", "access_role", "active"});
        requireColumns("notification_prefs", new String[]{"user_id", "enabled_types", "frequency", "sound_enabled"});
        requireColumns("notifications", new String[]{"notification_id", "recipient_id", "type", "message", "created_at", "unread"});
        requireColumns("reservations", new String[]{"reservation_id", "user_id", "spot_id", "created_at", "expires_at", "status"});
        requireColumns("duty_sessions", new String[]{"session_id", "attendant_id", "shift", "zone", "started_at", "ended_at", "status"});

        requirePrimaryKey("schema_version", "version");
        requirePrimaryKey("users", "user_id");
        requirePrimaryKey("vehicles", "vehicle_id");
        requirePrimaryKey("parking_spots", "spot_id");
        requirePrimaryKey("tickets", "ticket_id");
        requirePrimaryKey("payments", "payment_id");
        requirePrimaryKey("garage_config", "garage_id");
        requirePrimaryKey("garages", "garage_id");
        requirePrimaryKey("user_garages", "access_id");
        requirePrimaryKey("notification_prefs", "user_id");
        requirePrimaryKey("notifications", "notification_id");
        requirePrimaryKey("reservations", "reservation_id");
        requirePrimaryKey("duty_sessions", "session_id");
        requireUniqueIndex("users", "username");
        requireUniqueIndex("users", "email");
        requireUniqueIndex("vehicles", "license_plate");

        if (!foreignKeysEnabled()) throw new IllegalStateException("SQLite foreign-key enforcement is disabled");
        try (ResultSet result = statement.executeQuery("PRAGMA integrity_check")) {
            if (!result.next() || !"ok".equalsIgnoreCase(result.getString(1))) {
                throw new IllegalStateException("SQLite integrity check failed");
            }
        }
        for (String table : tables) {
            try (ResultSet ignored = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
                if (!ignored.next()) throw new IllegalStateException("Could not read migrated table: " + table);
            }
        }
    }

    private void requireColumns(String table, String[] columns) throws SQLException {
        java.util.Set<String> found = new java.util.HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement("PRAGMA table_info(" + table + ")")) {
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) found.add(result.getString("name"));
            }
        }
        for (String column : columns) {
            if (!found.contains(column)) throw new IllegalStateException("Required SQLite column is missing: " + table + "." + column);
        }
    }

    private void requirePrimaryKey(String table, String column) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("PRAGMA table_info(" + table + ")")) {
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    if (column.equals(result.getString("name")) && result.getInt("pk") > 0) return;
                }
            }
        }
        throw new IllegalStateException("Required SQLite primary key is missing: " + table + "." + column);
    }

    private void requireUniqueIndex(String table, String column) throws SQLException {
        try (PreparedStatement indexes = connection.prepareStatement("PRAGMA index_list(" + table + ")")) {
            try (ResultSet indexRows = indexes.executeQuery()) {
                while (indexRows.next()) {
                    if (indexRows.getInt("unique") == 0) continue;
                    String indexName = indexRows.getString("name");
                    try (PreparedStatement info = connection.prepareStatement("PRAGMA index_info('" + indexName.replace("'", "''") + "')")) {
                        try (ResultSet columns = info.executeQuery()) {
                            while (columns.next()) if (column.equals(columns.getString("name"))) return;
                        }
                    }
                }
            }
        }
        throw new IllegalStateException("Required SQLite unique constraint is missing: " + table + "." + column);
    }

    private int schemaVersion(Statement statement) throws SQLException {
        try (ResultSet result = statement.executeQuery("SELECT MAX(version) FROM schema_version")) {
            if (!result.next() || result.getObject(1) == null) return 0;
            return result.getInt(1);
        }
    }

    private void setSchemaVersion(Statement statement, int version) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        if (!autoCommit) connection.setAutoCommit(true);
        try {
            statement.execute("BEGIN");
            statement.executeUpdate("DELETE FROM schema_version");
            try (PreparedStatement update = connection.prepareStatement("INSERT INTO schema_version(version, applied_at) VALUES(?, datetime('now'))")) {
                update.setInt(1, version);
                update.executeUpdate();
            }
            statement.execute("COMMIT");
        } catch (SQLException failure) {
            try { statement.execute("ROLLBACK"); } catch (SQLException ignored) { }
            throw new IllegalStateException("Could not record schema migration version", failure);
        } finally {
            if (!autoCommit) connection.setAutoCommit(false);
        }
    }

    private void validateLegacyReferences(Statement statement) throws SQLException {
        String[] checks = {
                "SELECT COUNT(*) FROM vehicles v LEFT JOIN users u ON u.user_id=v.owner_id WHERE v.owner_id IS NOT NULL AND u.user_id IS NULL",
                "SELECT COUNT(*) FROM parking_spots s LEFT JOIN vehicles v ON v.vehicle_id=s.vehicle_id WHERE s.vehicle_id IS NOT NULL AND v.vehicle_id IS NULL",
                "SELECT COUNT(*) FROM tickets t LEFT JOIN vehicles v ON v.vehicle_id=t.vehicle_id WHERE v.vehicle_id IS NULL",
                "SELECT COUNT(*) FROM tickets t LEFT JOIN parking_spots s ON s.spot_id=t.spot_id WHERE s.spot_id IS NULL",
                "SELECT COUNT(*) FROM tickets t LEFT JOIN users u ON u.user_id=t.owner_id WHERE t.owner_id IS NOT NULL AND u.user_id IS NULL",
                "SELECT COUNT(*) FROM payments p LEFT JOIN tickets t ON t.ticket_id=p.ticket_id WHERE t.ticket_id IS NULL",
                "SELECT COUNT(*) FROM payments p LEFT JOIN users u ON u.user_id=p.customer_id WHERE p.customer_id IS NOT NULL AND u.user_id IS NULL",
                "SELECT COUNT(*) FROM payments p LEFT JOIN tickets t ON t.ticket_id=p.ticket_id WHERE p.garage_id IS NULL OR t.garage_id IS NULL OR p.garage_id<>t.garage_id",
                "SELECT COUNT(*) FROM parking_spots s LEFT JOIN users u ON u.user_id=s.reservation_holder WHERE s.reservation_holder IS NOT NULL AND u.user_id IS NULL",
                "SELECT COUNT(*) FROM tickets t LEFT JOIN payments p ON p.payment_id=t.payment_id WHERE t.payment_id IS NOT NULL AND p.payment_id IS NULL"
        };
        for (String check : checks) {
            try (ResultSet result = statement.executeQuery(check)) {
                if (result.next() && result.getInt(1) > 0) {
                    throw new IllegalStateException("SQLite migration found invalid relationship: " + check);
                }
            }
        }
    }

    private void validateLegacyData(Statement statement) throws SQLException {
        String[] checks = {
                "SELECT COUNT(*) FROM payments WHERE amount < 0 OR tax_amount < 0 OR final_amount < 0",
                "SELECT COUNT(*) FROM tickets WHERE amount < 0 OR final_amount < 0",
                "SELECT COUNT(*) FROM payments WHERE status NOT IN ('PENDING','COMPLETED','FAILED','REFUNDED','CANCELLED')",
                "SELECT COUNT(*) FROM tickets WHERE status NOT IN ('CREATED','ACTIVE','CLOSED','CANCELLED','AWAITING_PAYMENT','PAID','REFUNDED')",
                "SELECT COUNT(*) FROM payments WHERE payment_id IS NULL OR trim(payment_id) = '' OR ticket_id IS NULL OR trim(ticket_id) = '' OR payment_time IS NULL OR trim(payment_time) = ''",
                "SELECT COUNT(*) FROM tickets WHERE ticket_id IS NULL OR trim(ticket_id) = '' OR vehicle_id IS NULL OR trim(vehicle_id) = '' OR spot_id IS NULL OR trim(spot_id) = '' OR entry_time IS NULL OR trim(entry_time) = ''",
                "SELECT COUNT(*) FROM payments WHERE abs(final_amount - round(amount + tax_amount, 2)) >= 0.005",
                "SELECT COUNT(*) FROM parking_spots WHERE hourly_rate < 0",
                "SELECT COUNT(*) FROM parking_spots WHERE status NOT IN ('AVAILABLE','OCCUPIED','RESERVED','UNDER_MAINTENANCE','OUT_OF_SERVICE')",
                "SELECT COUNT(*) FROM garage_config WHERE base_hourly_rate < 0"
        };
        for (String check : checks) {
            try (ResultSet result = statement.executeQuery(check)) {
                if (result.next() && result.getInt(1) > 0) {
                    throw new IllegalStateException("SQLite migration found invalid financial or lifecycle data: " + check);
                }
            }
        }
        validateTimestamps(statement, "tickets", "entry_time");
        validateTimestamps(statement, "tickets", "exit_time");
        validateTimestamps(statement, "payments", "payment_time");
    }

    private void validateTimestamps(Statement statement, String table, String column) throws SQLException {
        try (ResultSet result = statement.executeQuery("SELECT " + column + " FROM " + table + " WHERE " + column + " IS NOT NULL")) {
            while (result.next()) {
                String value = result.getString(1);
                if (value == null || value.isBlank() || "null".equalsIgnoreCase(value)) {
                    throw new IllegalStateException("SQLite migration found invalid timestamp in " + table + "." + column);
                }
                try { LocalDateTime.parse(value); }
                catch (RuntimeException e) {
                    throw new IllegalStateException("SQLite migration found malformed timestamp in " + table + "." + column, e);
                }
            }
        }
    }

    private boolean hasRequiredDataConstraints() throws SQLException {
        String[] required = {"tickets", "payments", "parking_spots", "garage_config"};
        try (PreparedStatement statement = connection.prepareStatement("SELECT lower(sql) FROM sqlite_master WHERE type='table' AND name=?")) {
            for (String table : required) {
                statement.setString(1, table);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) return false;
                    String sql = result.getString(1);
                    if (sql == null) return false;
                    if (table.equals("payments") && !(sql.contains("check(status in") && sql.contains("check(amount >= 0") && sql.contains("payment_time text not null") && sql.contains("abs(final_amount - round(amount + tax_amount, 2))"))) return false;
                    if (table.equals("tickets") && !(sql.contains("check(status in") && sql.contains("check(amount >= 0") && sql.contains("entry_time text not null"))) return false;
                    if (table.equals("parking_spots") && !sql.contains("check(hourly_rate >= 0)")) return false;
                    if (table.equals("garage_config") && !sql.contains("check(base_hourly_rate >= 0)")) return false;
                }
            }
            return true;
        }
    }

    private boolean hasRequiredForeignKeys(String table) throws SQLException {
        String[][] required = switch (table) {
            case "vehicles" -> new String[][]{{"users", "owner_id", "SET NULL"}};
            case "parking_spots" -> new String[][]{{"vehicles", "vehicle_id", "RESTRICT"}, {"users", "reservation_holder", "SET NULL"}};
            case "tickets" -> new String[][]{{"vehicles", "vehicle_id", "RESTRICT"}, {"users", "owner_id", "SET NULL"}, {"parking_spots", "spot_id", "RESTRICT"}, {"payments", "payment_id", "RESTRICT"}};
            case "payments" -> new String[][]{{"tickets", "ticket_id", "RESTRICT"}, {"users", "customer_id", "SET NULL"}};
            default -> new String[0][0];
        };
        try (PreparedStatement statement = connection.prepareStatement("PRAGMA foreign_key_list(" + table + ")")) {
            try (ResultSet result = statement.executeQuery()) {
                boolean[] found = new boolean[required.length];
                while (result.next()) {
                    String target = result.getString("table");
                    String from = result.getString("from");
                    String action = result.getString("on_delete");
                    for (int i = 0; i < required.length; i++) {
                        if (required[i][0].equals(target) && required[i][1].equals(from) && required[i][2].equalsIgnoreCase(action)) found[i] = true;
                    }
                }
                for (boolean value : found) if (!value) return false;
                return true;
            }
        }
    }

    public synchronized void saveTicket(Ticket ticket) {
        require(ticket, "ticket");
        if (ticket.getGarageId() == null) ticket.setGarageId(garageIdForSpot(ticket.getParkingSpotId()));
        validateTicketValues(ticket);
        requireReference("vehicles", "vehicle_id", ticket.getVehicleId(), "ticket vehicle");
        requireReference("parking_spots", "spot_id", ticket.getParkingSpotId(), "ticket parking spot");
        requireReference("garages", "garage_id", ticket.getGarageId(), "ticket garage");
        String spotGarage = garageIdForSpot(ticket.getParkingSpotId());
        if (!ticket.getGarageId().equals(spotGarage)) throw new IllegalArgumentException("Ticket garage must match parking spot garage");
        requireOptionalReference("users", "user_id", ticket.getUserId(), "ticket owner");
        requireOptionalReference("payments", "payment_id", ticket.getPaymentId(), "ticket payment");
        execute("INSERT INTO tickets(ticket_id,vehicle_id,owner_id,spot_id,garage_id,status,entry_time,exit_time,amount,final_amount,payment_id) VALUES(?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(ticket_id) DO UPDATE SET status=excluded.status,exit_time=excluded.exit_time,amount=excluded.amount,final_amount=excluded.final_amount,payment_id=excluded.payment_id",
                ticket.getTicketId(), ticket.getVehicleId(), ticket.getUserId(), ticket.getParkingSpotId(), ticket.getGarageId(), ticket.getStatus().name(), timeValue(ticket.getEntryTime()), timeValue(ticket.getExitTime()), ticket.getAmount(), ticket.getFinalAmount(), ticket.getPaymentId());
    }

    public synchronized void savePayment(Payment payment) {
        require(payment, "payment");
        requireReference("tickets", "ticket_id", payment.getTicketId(), "payment ticket");
        String ticketGarage = garageIdForTicket(payment.getTicketId());
        if (payment.getGarageId() == null) payment.setGarageId(ticketGarage);
        if (!ticketGarage.equals(payment.getGarageId())) throw new IllegalArgumentException("Payment garage must match ticket garage");
        requireReference("garages", "garage_id", payment.getGarageId(), "payment garage");
        validatePaymentValues(payment);
        requireOptionalReference("users", "user_id", payment.getCustomerId(), "payment customer");
        execute("INSERT INTO payments(payment_id,ticket_id,customer_id,garage_id,status,amount,tax_amount,final_amount,payment_time,payment_method) VALUES(?,?,?,?,?,?,?,?,?,?) ON CONFLICT(payment_id) DO UPDATE SET status=excluded.status,amount=excluded.amount,tax_amount=excluded.tax_amount,final_amount=excluded.final_amount,payment_time=excluded.payment_time",
                payment.getPaymentId(), payment.getTicketId(), payment.getCustomerId(), payment.getGarageId(), payment.getStatus().name(), payment.getAmount(), payment.getTaxAmount(), payment.getFinalAmount(), timeValue(payment.getPaymentTime()), payment.getPaymentType());
    }

    public synchronized void saveGarage(ParkingGarage garage) {
        require(garage, "garage");
        requireMoney(garage.getBaseHourlyRate(), "garage baseHourlyRate");
        execute("INSERT INTO garage_config(garage_id,name,address,total_levels,base_hourly_rate,is_open) VALUES(?,?,?,?,?,?) ON CONFLICT(garage_id) DO UPDATE SET name=excluded.name,address=excluded.address,total_levels=excluded.total_levels,base_hourly_rate=excluded.base_hourly_rate,is_open=excluded.is_open",
                garage.getGarageId(), garage.getName(), garage.getAddress(), garage.getTotalLevels(), garage.getBaseHourlyRate(), garage.isOpen() ? 1 : 0);
        if (!hasGarage(garage.getGarageId())) {
            saveGarage(new Garage(garage.getGarageId(), garage.getName(), garage.getAddress(),
                    garage.getTotalLevels(), garage.getBaseHourlyRate(), "USD", 0, 5, 48,
                    garage.isOpen(), false, LocalDateTime.now(), LocalDateTime.now()));
        }
    }

    public synchronized void saveGarage(Garage garage) {
        require(garage, "garage");
        requireMoney(garage.getBaseHourlyRate(), "garage baseHourlyRate");
        execute("INSERT INTO garages(garage_id,name,address,total_levels,base_hourly_rate,currency,free_parking_minutes,reservation_hold_minutes,max_park_hours,is_open,archived,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(garage_id) DO UPDATE SET name=excluded.name,address=excluded.address,total_levels=excluded.total_levels,base_hourly_rate=excluded.base_hourly_rate,currency=excluded.currency,free_parking_minutes=excluded.free_parking_minutes,reservation_hold_minutes=excluded.reservation_hold_minutes,max_park_hours=excluded.max_park_hours,is_open=excluded.is_open,archived=excluded.archived,updated_at=excluded.updated_at",
                garage.getGarageId(), garage.getName(), garage.getAddress(), garage.getTotalLevels(),
                garage.getBaseHourlyRate(), garage.getCurrency(), garage.getFreeParkingMinutes(),
                garage.getReservationHoldMinutes(), garage.getMaxParkHours(), garage.isOpen() ? 1 : 0,
                garage.isArchived() ? 1 : 0, timeValue(garage.getCreatedAt()), timeValue(garage.getUpdatedAt()));
    }

    public synchronized List<Garage> loadGarages() {
        List<Garage> result = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT * FROM garages ORDER BY name, garage_id")) {
            while (rows.next()) result.add(garageFromRow(rows));
            return result;
        } catch (SQLException | IllegalArgumentException failure) {
            throw new IllegalStateException("Could not load garages", failure);
        }
    }

    public synchronized java.util.Optional<Garage> loadGarage(String garageId) {
        if (garageId == null || garageId.isBlank()) return java.util.Optional.empty();
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM garages WHERE garage_id=?")) {
            statement.setString(1, garageId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? java.util.Optional.of(garageFromRow(rows)) : java.util.Optional.empty();
            }
        } catch (SQLException | IllegalArgumentException failure) {
            throw new IllegalStateException("Could not load garage", failure);
        }
    }

    public synchronized void saveGarageAccess(GarageAccess access) {
        require(access, "access");
        requireReference("users", "user_id", access.getUserId(), "garage access user");
        requireReference("garages", "garage_id", access.getGarageId(), "garage access garage");
        execute("INSERT INTO user_garages(access_id,user_id,garage_id,access_role,active,created_at,updated_at) VALUES(?,?,?,?,?,?,?) ON CONFLICT(access_id) DO UPDATE SET access_role=excluded.access_role,active=excluded.active,updated_at=excluded.updated_at",
                access.getUserId() + ":" + access.getGarageId(), access.getUserId(), access.getGarageId(),
                access.getRole().name(), access.isActive() ? 1 : 0,
                timeValue(access.getCreatedAt()), timeValue(access.getUpdatedAt()));
    }

    public synchronized java.util.Optional<GarageAccess> loadGarageAccess(String userId, String garageId) {
        if (userId == null || userId.isBlank() || garageId == null || garageId.isBlank()) {
            return java.util.Optional.empty();
        }
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM user_garages WHERE user_id=? AND garage_id=?")) {
            statement.setString(1, userId);
            statement.setString(2, garageId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? java.util.Optional.of(accessFromRow(rows)) : java.util.Optional.empty();
            }
        } catch (SQLException | IllegalArgumentException failure) {
            throw new IllegalStateException("Could not load garage access", failure);
        }
    }

    public synchronized boolean hasGarage(String garageId) {
        return garageId != null && exists("garages", "garage_id", garageId);
    }

    /** Compatibility bridge for in-memory garage fixtures that persist spots first. */
    public synchronized void ensureGarageReference(String garageId) {
        if (hasGarage(garageId)) return;
        saveGarage(new Garage(garageId, garageId, "Legacy", 1, 0, "USD", 0, 5, 48));
    }

    public synchronized boolean hasGarageAccess(String userId, String garageId) {
        if (userId == null || garageId == null) return false;
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM user_garages WHERE user_id=? AND garage_id=? AND active=1 LIMIT 1")) {
            statement.setString(1, userId);
            statement.setString(2, garageId);
            try (ResultSet rows = statement.executeQuery()) { return rows.next(); }
        } catch (SQLException failure) {
            throw new IllegalStateException("Could not inspect garage access", failure);
        }
    }

    public synchronized int schemaVersionForTests() {
        try (Statement statement = connection.createStatement()) {
            return schemaVersion(statement);
        } catch (SQLException failure) {
            throw new IllegalStateException("Could not inspect schema version", failure);
        }
    }

    public synchronized void saveVehicle(Vehicle vehicle) {
        require(vehicle, "vehicle");
        if (vehicle.getVehicleId() == null || vehicle.getLicensePlate() == null || vehicle.getVehicleType() == null) {
            throw new IllegalArgumentException("Vehicle ID, license plate and type are required");
        }
        if (vehicle.isParked() && (vehicle.getParkingSpotId() == null || vehicle.getParkingSpotId().isBlank())) {
            throw new IllegalArgumentException("A parked vehicle must reference a parking spot");
        }
        requireOptionalReference("users", "user_id", vehicle.getUserId(), "vehicle owner");
        execute("INSERT INTO vehicles(vehicle_id,license_plate,vehicle_type,make,model,color,year,owner_id,entry_time,parking_spot_id,is_parked) VALUES(?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(vehicle_id) DO UPDATE SET license_plate=excluded.license_plate,vehicle_type=excluded.vehicle_type,make=excluded.make,model=excluded.model,color=excluded.color,year=excluded.year,owner_id=excluded.owner_id,entry_time=excluded.entry_time,parking_spot_id=excluded.parking_spot_id,is_parked=excluded.is_parked",
                vehicle.getVehicleId(), vehicle.getLicensePlate().trim(), vehicle.getVehicleType().name(), vehicle.getMake(), vehicle.getModel(), vehicle.getColor(), vehicle.getYear(), vehicle.getUserId(), String.valueOf(vehicle.getEntryTime()), vehicle.getParkingSpotId(), vehicle.isParked() ? 1 : 0);
    }

    public synchronized void saveParkingSpot(ParkingGarage garage, ParkingSpot spot) {
        require(garage, "garage");
        require(spot, "spot");
        requireMoney(spot.getHourlyRate(), "parking spot hourlyRate");
        if (spot.getStatus() == SpotStatus.OCCUPIED && (spot.getVehicleId() == null || spot.getVehicleId().isBlank())) {
            throw new IllegalArgumentException("An occupied spot must reference a vehicle");
        }
        if (spot.getStatus() != SpotStatus.OCCUPIED && spot.getVehicleId() != null) {
            throw new IllegalArgumentException("An available spot cannot retain a vehicle");
        }
        requireOptionalReference("vehicles", "vehicle_id", spot.getVehicleId(), "parking spot vehicle");
        requireOptionalReference("users", "user_id", spot.getReservationHolderUserId(), "parking spot reservation holder");
        execute("INSERT INTO parking_spots(spot_id,garage_id,level,spot_type,location,hourly_rate,status,vehicle_id,reservation_holder,reservation_expiry,under_maintenance,maintenance_reason) VALUES(?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(spot_id) DO UPDATE SET garage_id=excluded.garage_id,level=excluded.level,spot_type=excluded.spot_type,location=excluded.location,hourly_rate=excluded.hourly_rate,status=excluded.status,vehicle_id=excluded.vehicle_id,reservation_holder=excluded.reservation_holder,reservation_expiry=excluded.reservation_expiry,under_maintenance=excluded.under_maintenance,maintenance_reason=excluded.maintenance_reason",
                spot.getSpotId(), garage.getGarageId(), levelFor(garage, spot), spot.getSpotType().name(), spot.getLocation(), spot.getHourlyRate(), spot.getStatus().name(), spot.getVehicleId(), spot.getReservationHolderUserId(), String.valueOf(spot.getReservationExpiry()), spot.isUnderMaintenance() ? 1 : 0, spot.getMaintenanceReason());
    }

    /** Compatibility bridge for standalone TicketService callers; normal parking uses saveVehicle. */
    public synchronized void ensureVehicleReference(Vehicle vehicle) {
        require(vehicle, "vehicle");
        if (exists("vehicles", "vehicle_id", vehicle.getVehicleId())) return;
        String ownerId = vehicle.getUserId() != null && hasUser(vehicle.getUserId()) ? vehicle.getUserId() : null;
        execute("INSERT INTO vehicles(vehicle_id,license_plate,vehicle_type,make,model,color,year,owner_id,entry_time,parking_spot_id,is_parked) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                vehicle.getVehicleId(), vehicle.getLicensePlate(), vehicle.getVehicleType().name(), vehicle.getMake(), vehicle.getModel(), vehicle.getColor(), vehicle.getYear(), ownerId, String.valueOf(vehicle.getEntryTime()), vehicle.getParkingSpotId(), vehicle.isParked() ? 1 : 0);
    }

    /** Compatibility bridge for standalone TicketService callers; the ticket still enforces the FK. */
    public synchronized void ensureParkingSpotReference(ParkingSpot spot) {
        require(spot, "spot");
        if (exists("parking_spots", "spot_id", spot.getSpotId())) return;
        if (spot.getStatus() == SpotStatus.OCCUPIED && spot.getVehicleId() == null) {
            throw new IllegalArgumentException("An occupied spot must reference a vehicle");
        }
        requireOptionalReference("vehicles", "vehicle_id", spot.getVehicleId(), "parking spot vehicle");
        requireOptionalReference("users", "user_id", spot.getReservationHolderUserId(), "parking spot reservation holder");
        execute("INSERT INTO parking_spots(spot_id,garage_id,level,spot_type,location,hourly_rate,status,vehicle_id,reservation_holder,reservation_expiry,under_maintenance,maintenance_reason) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                spot.getSpotId(), "__legacy__", 0, spot.getSpotType().name(), spot.getLocation(), spot.getHourlyRate(), spot.getStatus().name(), spot.getVehicleId(), spot.getReservationHolderUserId(), String.valueOf(spot.getReservationExpiry()), spot.isUnderMaintenance() ? 1 : 0, spot.getMaintenanceReason());
    }

    public synchronized boolean hasUser(String userId) {
        return userId != null && exists("users", "user_id", userId);
    }

    public synchronized List<Vehicle> loadVehicles() {
        List<Vehicle> result = new ArrayList<>();
        try (Statement s = connection.createStatement(); ResultSet rs = s.executeQuery("SELECT * FROM vehicles ORDER BY vehicle_id")) {
            while (rs.next()) {
                Vehicle vehicle = new Vehicle(rs.getString("vehicle_id"), rs.getString("license_plate"),
                        VehicleType.valueOf(rs.getString("vehicle_type")), rs.getString("make"), rs.getString("model"),
                        rs.getString("color"), rs.getInt("year"), rs.getString("owner_id"));
                vehicle.setEntryTime(parseTimeOrNull(rs.getString("entry_time")));
                vehicle.setParkingSpotId(rs.getString("parking_spot_id"));
                vehicle.setParked(rs.getInt("is_parked") == 1);
                result.add(vehicle);
            }
            return result;
        } catch (SQLException | IllegalArgumentException e) {
            throw new IllegalStateException("Could not load vehicles", e);
        }
    }

    public synchronized List<SpotSnapshot> loadParkingSpots(String garageId) {
        List<SpotSnapshot> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM parking_spots WHERE garage_id=? ORDER BY level,spot_id")) {
            statement.setString(1, garageId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    ParkingSpot spot = new ParkingSpot(rs.getString("spot_id"), SpotType.valueOf(rs.getString("spot_type")),
                            rs.getString("location"), rs.getDouble("hourly_rate"));
                    spot.setStatus(SpotStatus.valueOf(rs.getString("status")));
                    spot.setVehicleId(rs.getString("vehicle_id"));
                    spot.setReservationHolderUserId(rs.getString("reservation_holder"));
                    spot.setReservationExpiry(parseTimeOrNull(rs.getString("reservation_expiry")));
                    spot.setMaintenanceReason(rs.getString("maintenance_reason"));
                    if (rs.getInt("under_maintenance") == 1) spot.setUnderMaintenance(spot.getMaintenanceReason());
                    result.add(new SpotSnapshot(rs.getInt("level"), spot));
                }
            }
            return result;
        } catch (SQLException | IllegalArgumentException e) {
            throw new IllegalStateException("Could not load parking spots", e);
        }
    }

    public synchronized void deleteTicketById(String ticketId) {
        require(ticketId, "ticketId");
        execute("DELETE FROM tickets WHERE ticket_id=?", ticketId);
    }

    public synchronized void deleteVehicleById(String vehicleId) {
        require(vehicleId, "vehicleId");
        execute("DELETE FROM vehicles WHERE vehicle_id=?", vehicleId);
    }

    public synchronized void deleteParkingSpotById(String spotId) {
        require(spotId, "spotId");
        execute("DELETE FROM parking_spots WHERE spot_id=?", spotId);
    }

    public record SpotSnapshot(int level, ParkingSpot spot) { }

    public synchronized void saveUser(User user) {
        require(user, "user");
        execute("INSERT INTO users(user_id,username,email,password_hash,role,active,updated_at,full_name,phone_number,created_at,last_login,failed_login_attempts,locked_until,wallet_balance) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(user_id) DO UPDATE SET username=excluded.username,email=excluded.email,password_hash=excluded.password_hash,role=excluded.role,active=excluded.active,updated_at=excluded.updated_at,full_name=excluded.full_name,phone_number=excluded.phone_number,created_at=excluded.created_at,last_login=excluded.last_login,failed_login_attempts=excluded.failed_login_attempts,locked_until=excluded.locked_until,wallet_balance=excluded.wallet_balance",
                user.getUserId(), user.getUsername(), user.getEmail(), user.getStoredPasswordHash(), user.getRole().name(), user.isActive() ? 1 : 0,
                String.valueOf(user.getLastLogin()), user.getFullName(), user.getPhoneNumber(), String.valueOf(user.getCreatedAt()),
                String.valueOf(user.getLastLogin()), user.getFailedLoginAttempts(), String.valueOf(user.getLockedUntil()),
                user instanceof Customer customer ? customer.getWalletBalance() : 0.0);
    }

    /** Deletes exactly one user row and commits it through the store transaction. */
    public synchronized void deleteUserById(String userId) {
        require(userId, "userId");
        execute("DELETE FROM users WHERE user_id = ?", userId);
    }

    public synchronized List<User> loadUsers() {
        List<User> result = new ArrayList<>();
        try (Statement s = connection.createStatement(); ResultSet rs = s.executeQuery("SELECT * FROM users")) {
            while (rs.next()) {
                UserRole role = UserRole.valueOf(rs.getString("role"));
                String id = rs.getString("user_id"), username = rs.getString("username"), hash = rs.getString("password_hash");
                String email = rs.getString("email");
                String fullName = safeFullName(rs.getString("full_name"), username);
                User user = role == UserRole.ADMIN ? new Admin(id, username, hash, email, fullName, "", true)
                        : role == UserRole.ATTENDANT ? new Attendant(id, username, hash, email, fullName, "", true)
                        : new Customer(id, username, hash, email, fullName, true);
                user.setActive(rs.getInt("active") == 1);
                user.setPhoneNumber(rs.getString("phone_number"));
                user.setCreatedAt(parseTimeOrNull(rs.getString("created_at")) == null ? user.getCreatedAt() : parseTimeOrNull(rs.getString("created_at")));
                user.setLastLogin(parseTimeOrNull(rs.getString("last_login")));
                user.restoreLoginState(rs.getInt("failed_login_attempts"), parseTimeOrNull(rs.getString("locked_until")));
                if (user instanceof Customer customer) customer.setWalletBalance(rs.getDouble("wallet_balance"));
                result.add(user);
            }
            return result;
        } catch (SQLException | IllegalArgumentException e) { throw new IllegalStateException("Could not load users", e); }
    }

    public synchronized List<Ticket> loadTickets() {
        List<Ticket> result = new ArrayList<>();
        try (Statement s = connection.createStatement(); ResultSet rs = s.executeQuery("SELECT * FROM tickets")) {
            while (rs.next()) {
                Ticket t = new Ticket();
                t.setTicketId(rs.getString("ticket_id")); t.setVehicleId(rs.getString("vehicle_id"));
                t.setUserId(rs.getString("owner_id")); t.setParkingSpotId(rs.getString("spot_id"));
                t.setGarageId(rs.getString("garage_id"));
                t.setStatus(TicketStatus.valueOf(rs.getString("status")));
                t.setEntryTime(parseTime(rs.getString("entry_time"))); t.setExitTime(parseTime(rs.getString("exit_time")));
                t.setAmount(rs.getDouble("amount")); t.setFinalAmount(rs.getDouble("final_amount")); t.setPaymentId(rs.getString("payment_id"));
                t.setPaid(t.getStatus() == TicketStatus.PAID || t.getStatus() == TicketStatus.CLOSED);
                result.add(t);
            }
            return result;
        } catch (SQLException | IllegalArgumentException e) { throw new IllegalStateException("Could not load tickets", e); }
    }

    public synchronized Optional<ActiveTicketSnapshot> findActiveTicketForVehicle(String vehicleId) {
        if (vehicleId == null || vehicleId.isBlank()) return Optional.empty();
        String sql = "SELECT t.ticket_id,t.vehicle_id,t.owner_id,t.spot_id,t.garage_id,t.status,t.entry_time,t.exit_time,t.amount,t.final_amount,t.payment_id, "
                + "g.name,g.address,g.total_levels,g.base_hourly_rate,g.currency,g.free_parking_minutes,g.reservation_hold_minutes,g.max_park_hours,g.is_open,g.archived,g.created_at,g.updated_at "
                + "FROM tickets t JOIN garages g ON g.garage_id=t.garage_id "
                + "WHERE t.vehicle_id=? AND t.status IN ('ACTIVE','AWAITING_PAYMENT') ORDER BY t.entry_time LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, vehicleId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                Ticket ticket = new Ticket(rs.getString("ticket_id"), rs.getString("vehicle_id"),
                        rs.getString("spot_id"), rs.getString("owner_id"), rs.getString("garage_id"));
                ticket.setStatus(TicketStatus.valueOf(rs.getString("status")));
                ticket.setEntryTime(parseTime(rs.getString("entry_time")));
                ticket.setExitTime(parseTimeOrNull(rs.getString("exit_time")));
                ticket.setAmount(rs.getDouble("amount"));
                ticket.setFinalAmount(rs.getDouble("final_amount"));
                ticket.setPaymentId(rs.getString("payment_id"));
                ticket.setPaid(ticket.getStatus() == TicketStatus.PAID || ticket.getStatus() == TicketStatus.CLOSED);
                Garage garage = new Garage(rs.getString("garage_id"), rs.getString("name"), rs.getString("address"),
                        rs.getInt("total_levels"), rs.getDouble("base_hourly_rate"), rs.getString("currency"),
                        rs.getInt("free_parking_minutes"), rs.getInt("reservation_hold_minutes"),
                        rs.getInt("max_park_hours"), rs.getInt("is_open") == 1, rs.getInt("archived") == 1,
                        parseFlexibleTime(rs.getString("created_at")), parseFlexibleTime(rs.getString("updated_at")));
                return Optional.of(new ActiveTicketSnapshot(ticket, garage));
            }
        } catch (SQLException | IllegalArgumentException e) {
            throw new IllegalStateException("Could not find active ticket for vehicle", e);
        }
    }

    public synchronized List<Payment> loadPayments() {
        List<Payment> result = new ArrayList<>();
        try (Statement s = connection.createStatement(); ResultSet rs = s.executeQuery("SELECT * FROM payments")) {
            while (rs.next()) {
                Payment p = new Payment(); p.setPaymentId(rs.getString("payment_id")); p.setTicketId(rs.getString("ticket_id"));
                p.setCustomerId(rs.getString("customer_id")); p.setStatus(PaymentStatus.valueOf(rs.getString("status")));
                p.setGarageId(rs.getString("garage_id"));
                p.setAmount(rs.getDouble("amount")); p.setTaxAmount(rs.getDouble("tax_amount")); p.setFinalAmount(rs.getDouble("final_amount"));
                p.setPaymentTime(parseTime(rs.getString("payment_time"))); p.setPaymentMethod(rs.getString("payment_method")); result.add(p);
            }
            return result;
        } catch (SQLException | IllegalArgumentException e) { throw new IllegalStateException("Could not load payments", e); }
    }

    private static LocalDateTime parseTime(String value) {
        if (value == null) return null;
        if (value.equalsIgnoreCase("null") || value.isBlank()) {
            throw new IllegalStateException("Persisted required timestamp is null or blank");
        }
        try { return LocalDateTime.parse(value); }
        catch (RuntimeException e) { throw new IllegalStateException("Persisted timestamp is malformed: " + value, e); }
    }

    private static LocalDateTime parseTimeOrNull(String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("null")) return null;
        try { return LocalDateTime.parse(value); }
        catch (RuntimeException e) { throw new IllegalStateException("Persisted timestamp is malformed: " + value, e); }
    }

    private static Garage garageFromRow(ResultSet rows) throws SQLException {
        return new Garage(rows.getString("garage_id"), rows.getString("name"), rows.getString("address"),
                rows.getInt("total_levels"), rows.getDouble("base_hourly_rate"), rows.getString("currency"),
                rows.getInt("free_parking_minutes"), rows.getInt("reservation_hold_minutes"),
                rows.getInt("max_park_hours"), rows.getInt("is_open") == 1, rows.getInt("archived") == 1,
                parseFlexibleTime(rows.getString("created_at")), parseFlexibleTime(rows.getString("updated_at")));
    }

    private static GarageAccess accessFromRow(ResultSet rows) throws SQLException {
        return new GarageAccess(rows.getString("user_id"), rows.getString("garage_id"),
                UserRole.valueOf(rows.getString("access_role")), rows.getInt("active") == 1,
                parseFlexibleTime(rows.getString("created_at")), parseFlexibleTime(rows.getString("updated_at")));
    }

    private static LocalDateTime parseFlexibleTime(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value)) return null;
        String normalized = value.length() > 10 && value.charAt(10) == ' '
                ? value.substring(0, 10) + 'T' + value.substring(11) : value;
        try { return LocalDateTime.parse(normalized); }
        catch (RuntimeException e) { throw new IllegalStateException("Persisted timestamp is malformed: " + value, e); }
    }

    private static String safeFullName(String value, String username) {
        return value == null || value.trim().isEmpty() ? username : value;
    }

    private static int levelFor(ParkingGarage garage, ParkingSpot spot) {
        for (var entry : garage.getLevels().entrySet()) {
            if (entry.getValue().stream().anyMatch(candidate -> candidate == spot
                    || candidate.getSpotId().equals(spot.getSpotId()))) return entry.getKey();
        }
        throw new IllegalArgumentException("Parking spot is not registered with the garage: " + spot.getSpotId());
    }

    private boolean exists(String table, String column, String value) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM " + table + " WHERE " + column + "=? LIMIT 1")) {
            statement.setString(1, value);
            try (ResultSet result = statement.executeQuery()) { return result.next(); }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not validate persisted reference", e);
        }
    }

    private String garageIdForSpot(String spotId) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT garage_id FROM parking_spots WHERE spot_id=?")) {
            statement.setString(1, spotId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new IllegalArgumentException("Invalid ticket parking spot reference: " + spotId);
                return result.getString(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not resolve parking spot garage", e);
        }
    }

    public synchronized String garageIdForParkingSpot(String spotId) {
        return garageIdForSpot(spotId);
    }

    private String garageIdForTicket(String ticketId) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT garage_id FROM tickets WHERE ticket_id=?")) {
            statement.setString(1, ticketId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new IllegalArgumentException("Invalid payment ticket reference: " + ticketId);
                return result.getString(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not resolve ticket garage", e);
        }
    }

    private void requireReference(String table, String column, String value, String relationship) {
        if (value == null || value.isBlank() || !exists(table, column, value)) {
            throw new IllegalArgumentException("Invalid " + relationship + " reference: " + value);
        }
    }

    private void requireOptionalReference(String table, String column, String value, String relationship) {
        if (value != null && !value.isBlank()) requireReference(table, column, value, relationship);
    }

    /** Exposed for connection-level persistence tests; SQLite PRAGMAs are per connection. */
    public synchronized boolean foreignKeysEnabled() {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("PRAGMA foreign_keys")) {
            return result.next() && result.getInt(1) == 1;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not inspect SQLite foreign-key state", e);
        }
    }

    /** Executes a group of persistence operations as one SQLite transaction. */
    public synchronized <T> T inTransaction(TransactionWork<T> work) throws Exception {
        require(work, "transaction work");
        boolean outermost = transactionDepth++ == 0;
        try {
            T result = work.run();
            if (outermost) connection.commit();
            return result;
        } catch (Exception | Error failure) {
            if (outermost) {
                try { connection.rollback(); } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
            }
            throw failure;
        } finally {
            transactionDepth--;
        }
    }

    /** Test-only failure injection used to verify transaction rollback. */
    public synchronized void failAfterWritesForTests(int writesBeforeFailure) {
        if (writesBeforeFailure < 0) throw new IllegalArgumentException("writesBeforeFailure cannot be negative");
        failAfterWritesForTests = writesBeforeFailure;
    }

    private static void addUserColumnIfMissing(Statement statement, String name, String definition) throws SQLException {
        try {
            statement.executeUpdate("ALTER TABLE users ADD COLUMN " + name + " " + definition);
        } catch (SQLException e) {
            if (!e.getMessage().toLowerCase().contains("duplicate column name")) throw e;
        }
    }

    private static void addColumnIfMissing(Statement statement, String table, String name, String definition) throws SQLException {
        try {
            statement.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + name + " " + definition);
        } catch (SQLException e) {
            if (!e.getMessage().toLowerCase().contains("duplicate column name")) throw e;
        }
    }

    private static void validateTicketValues(Ticket ticket) {
        requireText(ticket.getTicketId(), "ticketId");
        requireText(ticket.getVehicleId(), "vehicleId");
        requireText(ticket.getParkingSpotId(), "parkingSpotId");
        requireText(ticket.getGarageId(), "garageId");
        if (ticket.getStatus() == null) throw new IllegalArgumentException("ticket status cannot be null");
        if (ticket.getEntryTime() == null) throw new IllegalArgumentException("ticket entryTime cannot be null");
        requireMoney(ticket.getAmount(), "ticket amount");
        requireMoney(ticket.getFinalAmount(), "ticket finalAmount");
    }

    private static void validatePaymentValues(Payment payment) {
        requireText(payment.getPaymentId(), "paymentId");
        requireText(payment.getTicketId(), "ticketId");
        requireText(payment.getGarageId(), "garageId");
        if (payment.getStatus() == null) throw new IllegalArgumentException("payment status cannot be null");
        if (payment.getPaymentTime() == null) throw new IllegalArgumentException("paymentTime cannot be null");
        requireMoney(payment.getAmount(), "payment amount");
        requireMoney(payment.getTaxAmount(), "payment taxAmount");
        requireMoney(payment.getFinalAmount(), "payment finalAmount");
        if (!Money.same(payment.getFinalAmount(), Money.round(payment.getAmount() + payment.getTaxAmount()))) {
            throw new IllegalArgumentException("payment finalAmount must equal rounded amount plus tax");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " cannot be null or blank");
    }

    private static void requireMoney(double value, String name) {
        if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException(name + " must be finite and non-negative");
    }

    private static String timeValue(LocalDateTime value) {
        return value == null ? null : value.toString();
    }

    public synchronized void audit(String action, String targetId, String actorId, boolean success, String metadata) {
        execute("INSERT INTO audit_events(event_time,action,target_id,actor_id,success,metadata) VALUES(datetime('now'),?,?,?,?,?)", action, targetId, actorId, success ? 1 : 0, metadata == null ? "" : metadata);
    }

    public record NotificationPreferenceRow(String userId, String enabledTypes, String frequency, boolean soundEnabled) { }

    public record NotificationRow(String notificationId, String recipientId, String type, String message, String createdAt, boolean unread) { }

    public synchronized void saveNotificationPreference(String userId, String enabledTypes, String frequency, boolean soundEnabled) {
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("userId cannot be null or blank");
        if (enabledTypes == null) throw new IllegalArgumentException("enabledTypes cannot be null");
        if (frequency == null || frequency.isBlank()) throw new IllegalArgumentException("frequency cannot be null or blank");
        execute("INSERT INTO notification_prefs(user_id,enabled_types,frequency,sound_enabled) VALUES(?,?,?,?) ON CONFLICT(user_id) DO UPDATE SET enabled_types=excluded.enabled_types,frequency=excluded.frequency,sound_enabled=excluded.sound_enabled",
                userId, enabledTypes, frequency, soundEnabled ? 1 : 0);
    }

    public synchronized Optional<NotificationPreferenceRow> loadNotificationPreference(String userId) {
        if (userId == null || userId.isBlank()) return Optional.empty();
        try (PreparedStatement statement = connection.prepareStatement("SELECT user_id,enabled_types,frequency,sound_enabled FROM notification_prefs WHERE user_id=?")) {
            statement.setString(1, userId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                return Optional.of(new NotificationPreferenceRow(rows.getString("user_id"), rows.getString("enabled_types"),
                        rows.getString("frequency"), rows.getInt("sound_enabled") == 1));
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Could not load notification preference", failure);
        }
    }

    public synchronized void insertNotification(String notificationId, String recipientId, String type, String message, String createdAt) {
        if (notificationId == null || notificationId.isBlank()) throw new IllegalArgumentException("notificationId cannot be null or blank");
        if (recipientId == null || recipientId.isBlank()) throw new IllegalArgumentException("recipientId cannot be null or blank");
        if (type == null || type.isBlank()) throw new IllegalArgumentException("type cannot be null or blank");
        if (message == null || message.isBlank()) throw new IllegalArgumentException("message cannot be null or blank");
        if (createdAt == null || createdAt.isBlank()) throw new IllegalArgumentException("createdAt cannot be null or blank");
        execute("INSERT INTO notifications(notification_id,recipient_id,type,message,created_at,unread) VALUES(?,?,?,?,?,1)",
                notificationId, recipientId, type, message, createdAt);
    }

    public synchronized List<NotificationRow> loadUnreadNotifications(String recipientId) {
        List<NotificationRow> result = new ArrayList<>();
        if (recipientId == null || recipientId.isBlank()) return result;
        try (PreparedStatement statement = connection.prepareStatement("SELECT notification_id,recipient_id,type,message,created_at,unread FROM notifications WHERE recipient_id=? AND unread=1 ORDER BY created_at,rowid")) {
            statement.setString(1, recipientId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(new NotificationRow(rows.getString("notification_id"), rows.getString("recipient_id"),
                            rows.getString("type"), rows.getString("message"), rows.getString("created_at"),
                            rows.getInt("unread") == 1));
                }
            }
            return result;
        } catch (SQLException failure) {
            throw new IllegalStateException("Could not load notifications", failure);
        }
    }

    public synchronized void markNotificationsRead(String recipientId) {
        if (recipientId == null || recipientId.isBlank()) throw new IllegalArgumentException("recipientId cannot be null or blank");
        execute("UPDATE notifications SET unread=0 WHERE recipient_id=? AND unread=1", recipientId);
    }

    public synchronized void deleteNotifications(String recipientId) {
        if (recipientId == null || recipientId.isBlank()) throw new IllegalArgumentException("recipientId cannot be null or blank");
        execute("DELETE FROM notifications WHERE recipient_id=?", recipientId);
    }

    public record ReservationRow(String reservationId, String userId, String spotId, String createdAt, String expiresAt, String status) { }

    public synchronized void saveReservation(String reservationId, String userId, String spotId, String createdAt, String expiresAt, String status) {
        if (reservationId == null || reservationId.isBlank()) throw new IllegalArgumentException("reservationId cannot be null or blank");
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("userId cannot be null or blank");
        if (spotId == null || spotId.isBlank()) throw new IllegalArgumentException("spotId cannot be null or blank");
        if (createdAt == null || createdAt.isBlank()) throw new IllegalArgumentException("createdAt cannot be null or blank");
        if (expiresAt == null || expiresAt.isBlank()) throw new IllegalArgumentException("expiresAt cannot be null or blank");
        requireReservationStatus(status);
        requireReference("users", "user_id", userId, "reservation holder");
        requireReference("parking_spots", "spot_id", spotId, "reservation spot");
        execute("INSERT INTO reservations(reservation_id,user_id,spot_id,created_at,expires_at,status) VALUES(?,?,?,?,?,?) ON CONFLICT(reservation_id) DO UPDATE SET status=excluded.status",
                reservationId, userId, spotId, createdAt, expiresAt, status);
    }

    public synchronized Optional<ReservationRow> loadReservation(String reservationId) {
        if (reservationId == null || reservationId.isBlank()) return Optional.empty();
        try (PreparedStatement statement = connection.prepareStatement("SELECT reservation_id,user_id,spot_id,created_at,expires_at,status FROM reservations WHERE reservation_id=?")) {
            statement.setString(1, reservationId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(reservationFromRow(rows)) : Optional.empty();
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Could not load reservation", failure);
        }
    }

    public synchronized List<ReservationRow> loadActiveReservations() {
        return loadReservationsWhere("status='ACTIVE' ORDER BY expires_at,rowid");
    }

    public synchronized List<ReservationRow> loadActiveReservationsForUser(String userId) {
        List<ReservationRow> result = new ArrayList<>();
        if (userId == null || userId.isBlank()) return result;
        try (PreparedStatement statement = connection.prepareStatement("SELECT reservation_id,user_id,spot_id,created_at,expires_at,status FROM reservations WHERE user_id=? AND status='ACTIVE' ORDER BY expires_at,rowid")) {
            statement.setString(1, userId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.add(reservationFromRow(rows));
            }
            return result;
        } catch (SQLException failure) {
            throw new IllegalStateException("Could not load reservations", failure);
        }
    }

    public synchronized Optional<ReservationRow> loadActiveReservationForSpot(String spotId) {
        if (spotId == null || spotId.isBlank()) return Optional.empty();
        try (PreparedStatement statement = connection.prepareStatement("SELECT reservation_id,user_id,spot_id,created_at,expires_at,status FROM reservations WHERE spot_id=? AND status='ACTIVE' ORDER BY expires_at,rowid LIMIT 1")) {
            statement.setString(1, spotId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(reservationFromRow(rows)) : Optional.empty();
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Could not load reservation", failure);
        }
    }

    public synchronized void updateReservationStatus(String reservationId, String status) {
        if (reservationId == null || reservationId.isBlank()) throw new IllegalArgumentException("reservationId cannot be null or blank");
        requireReservationStatus(status);
        execute("UPDATE reservations SET status=? WHERE reservation_id=?", status, reservationId);
    }

    private ReservationRow reservationFromRow(ResultSet rows) throws SQLException {
        return new ReservationRow(rows.getString("reservation_id"), rows.getString("user_id"),
                rows.getString("spot_id"), rows.getString("created_at"), rows.getString("expires_at"),
                rows.getString("status"));
    }

    private List<ReservationRow> loadReservationsWhere(String clause) {
        List<ReservationRow> result = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT reservation_id,user_id,spot_id,created_at,expires_at,status FROM reservations WHERE " + clause)) {
            while (rows.next()) result.add(reservationFromRow(rows));
            return result;
        } catch (SQLException failure) {
            throw new IllegalStateException("Could not load reservations", failure);
        }
    }

    private static void requireReservationStatus(String status) {
        if (status == null || !(status.equals("ACTIVE") || status.equals("CLAIMED")
                || status.equals("CANCELLED") || status.equals("EXPIRED"))) {
            throw new IllegalArgumentException("reservation status must be ACTIVE, CLAIMED, CANCELLED or EXPIRED");
        }
    }

    public record DutySessionRow(String sessionId, String attendantId, String shift, String zone,
                                 String startedAt, String endedAt, String status) { }

    public synchronized void saveDutySession(String sessionId, String attendantId, String shift, String zone,
                                             String startedAt, String endedAt, String status) {
        if (sessionId == null || sessionId.isBlank()) throw new IllegalArgumentException("sessionId cannot be null or blank");
        if (attendantId == null || attendantId.isBlank()) throw new IllegalArgumentException("attendantId cannot be null or blank");
        if (shift == null || shift.isBlank()) throw new IllegalArgumentException("shift cannot be null or blank");
        if (zone == null || zone.isBlank()) throw new IllegalArgumentException("zone cannot be null or blank");
        if (startedAt == null || startedAt.isBlank()) throw new IllegalArgumentException("startedAt cannot be null or blank");
        requireDutyStatus(status);
        requireReference("users", "user_id", attendantId, "duty session attendant");
        execute("INSERT INTO duty_sessions(session_id,attendant_id,shift,zone,started_at,ended_at,status) VALUES(?,?,?,?,?,?,?) ON CONFLICT(session_id) DO UPDATE SET ended_at=excluded.ended_at,status=excluded.status",
                sessionId, attendantId, shift, zone, startedAt, endedAt, status);
    }

    public synchronized void updateDutySessionEnd(String sessionId, String endedAt, String status) {
        if (sessionId == null || sessionId.isBlank()) throw new IllegalArgumentException("sessionId cannot be null or blank");
        if (endedAt == null || endedAt.isBlank()) throw new IllegalArgumentException("endedAt cannot be null or blank");
        requireDutyStatus(status);
        int updated = executeUpdateCount("UPDATE duty_sessions SET ended_at=?,status=? WHERE session_id=? AND status='OPEN'", endedAt, status, sessionId);
        if (updated == 0) {
            throw new IllegalStateException("Duty session is no longer open: " + sessionId);
        }
    }

    public synchronized Optional<DutySessionRow> loadOpenDutySession(String attendantId) {
        if (attendantId == null || attendantId.isBlank()) return Optional.empty();
        try (PreparedStatement statement = connection.prepareStatement("SELECT session_id,attendant_id,shift,zone,started_at,ended_at,status FROM duty_sessions WHERE attendant_id=? AND status='OPEN' ORDER BY started_at,rowid LIMIT 1")) {
            statement.setString(1, attendantId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(dutySessionFromRow(rows)) : Optional.empty();
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Could not load duty session", failure);
        }
    }

    public synchronized List<DutySessionRow> loadDutySessionsForAttendant(String attendantId) {
        List<DutySessionRow> result = new ArrayList<>();
        if (attendantId == null || attendantId.isBlank()) return result;
        try (PreparedStatement statement = connection.prepareStatement("SELECT session_id,attendant_id,shift,zone,started_at,ended_at,status FROM duty_sessions WHERE attendant_id=? ORDER BY started_at DESC,rowid DESC")) {
            statement.setString(1, attendantId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.add(dutySessionFromRow(rows));
            }
            return result;
        } catch (SQLException failure) {
            throw new IllegalStateException("Could not load duty sessions", failure);
        }
    }

    public synchronized List<DutySessionRow> loadAllDutySessions() {
        List<DutySessionRow> result = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT session_id,attendant_id,shift,zone,started_at,ended_at,status FROM duty_sessions ORDER BY started_at DESC,rowid DESC")) {
            while (rows.next()) result.add(dutySessionFromRow(rows));
            return result;
        } catch (SQLException failure) {
            throw new IllegalStateException("Could not load duty sessions", failure);
        }
    }

    private DutySessionRow dutySessionFromRow(ResultSet rows) throws SQLException {
        return new DutySessionRow(rows.getString("session_id"), rows.getString("attendant_id"),
                rows.getString("shift"), rows.getString("zone"), rows.getString("started_at"),
                rows.getString("ended_at"), rows.getString("status"));
    }

    private static void requireDutyStatus(String status) {
        if (status == null || !(status.equals("OPEN") || status.equals("CLOSED"))) {
            throw new IllegalArgumentException("duty status must be OPEN or CLOSED");
        }
    }

    private void execute(String sql, Object... values) {
        executeUpdateCount(sql, values);
    }

    /** Same as {@link #execute} but returns the JDBC update count for guarded writes. */
    private int executeUpdateCount(String sql, Object... values) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (failAfterWritesForTests == 0) {
                failAfterWritesForTests = -1;
                throw new SQLException("Injected persistence failure for transaction test");
            }
            if (failAfterWritesForTests > 0) failAfterWritesForTests--;
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            int updated = statement.executeUpdate();
            if (transactionDepth == 0) connection.commit();
            return updated;
        } catch (SQLException e) {
            // An enclosing inTransaction call owns rollback for grouped writes.
            // Rolling back here would make a nested persistence operation partly
            // control the transaction and could invalidate the caller's state
            // restoration path.
            if (transactionDepth == 0) {
                try { connection.rollback(); } catch (SQLException ignored) { }
            }
            throw new IllegalStateException("Could not persist parking state", e);
        }
    }

    private static void require(Object value, String name) {
        if (value == null) throw new IllegalArgumentException(name + " cannot be null");
    }

    private static Path databasePath() {
        if (System.getProperty("surefire.test.class.path") != null) return Path.of("__memory__");
        String configured = System.getProperty("parkingos.database");
        return configured == null || configured.isBlank()
                ? Path.of(System.getProperty("user.home"), ".parkingos", "parking.db")
                : Path.of(configured);
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        try {
            if (connection != null && !connection.isClosed()) {
                if (!connection.getAutoCommit()) connection.rollback();
                connection.close();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not close parking database", e);
        }
    }

    public synchronized boolean isClosed() {
        if (closed || connection == null) return true;
        try { return connection.isClosed(); }
        catch (SQLException e) { throw new IllegalStateException("Could not inspect database connection", e); }
    }
}
