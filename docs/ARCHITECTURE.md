# ParkingOS Architecture

Container view of the desktop application (Java 21, JavaFX, SQLite).
Diagram source: `diagrams/architecture.d2` — re-render with
`scripts/render_diagrams.sh` after any change.

![ParkingOS container diagram](diagrams/architecture.svg)

## Startup sequence

Entry: `gui/ParkingApplication.java:795` (`main()` → `launch()`).

1. `start(Stage)` (`ParkingApplication.java:129`) → `initSystem()` (`:146`) →
   `shell/AppServices.java:71` `initialize()`:
   - `AppConfig.load()` (`:72`; settings file `~/.parkingos/settings.properties`,
     override `-Dparkingos.settings.file`).
   - In-memory `ParkingGarage` from config (`:73-78`), `restoreGarage()` (`:100`),
     demo seeds only with `-Dparkingos.demo=true` (`:81`).
   - Wires `TicketService` (`:85`), `ParkingService` (`:87`),
     `PaymentService` (`:88`), `NotificationService` (`:89`),
     `ReservationService` (`:90`), `DutyService` (`:91`),
     `OperationsSnapshotService` (`:94`).
2. First run (no users) → `showFirstRunSetup()` (`:182`); otherwise
   `showLogin()` (`:166`).
3. On login → `showDashboard()` (`:200`): builds `GarageContext` (`:201`) and
   `AppShell` (`:211`, sidebar + header + center), themed `Scene` (`:260`).
4. Shutdown: `shutdownResources()` (`:772`) — stops timers, disposes views,
   closes background tasks and `persistence`.

## Layers

| Layer | Package | Key classes |
|---|---|---|
| GUI entry / shell | `com.parking.gui`, `gui.shell` | `ParkingApplication` (navigation, overlays), `AppShell` (sidebar/header, `PageFactory:43-56`), `AppServices` (bootstrap), `BackgroundTaskRunner`, `ToastManager`, `NotificationCenter`, `OperationsViewFactory`, `GarageSelectorView`, `GarageUiScope` |
| Views | `com.parking.gui` | `LoginView`, `RegistrationView`, `FirstRunSetupView`, `DashboardView`, `CustomerVehiclesView`, `OccupancyMapView`, `TicketPaymentView`, `WalletView`, `UserManagementView`, `GarageManagementView`, `GarageAccessView`, `AnalyticsReportingView`, `MaintenanceView`, `DutyView`, `SettingsView`, `CommandPalette` |
| Services | `com.parking.services` | See service catalog below |
| Domain | `com.parking.model`, `enums`, `exceptions` | `Ticket`, `Payment` (+`Card/Cash/WalletPayment`), `Vehicle`, `ParkingSpot`, `Garage`/`ParkingGarage`, `GarageAccess`, `Reservation`, `DutySession`, `User` (`Admin`/`Attendant`/`Customer`); see `DATA_MODEL.md` |
| Persistence | `com.parking.persistence` | `PersistenceStore` (SQLite boundary, schema v11) |
| Cross-cutting | `security`, `config`, `util` | `PasswordHasher`, `LocalCardAuthorizationProvider`, `AppConfig`, `Money` |

## Service catalog

| Service | Purpose | Key methods |
|---|---|---|
| `UserService` | Registration, auth, profiles | `registerUser:82,87`, `authenticateUser:150`, `changePassword:313`, `adminUpdateUser:345,442`, `hasPermission:623` |
| `GarageService` | Garage lifecycle + garage access | `createGarage:22`, `archiveGarage:43`, `reopenGarage:44`, `grantAccess:52`, `revokeAccess:61`, `requireAccess:77` |
| `GarageContext` | Active-garage selection (UI context only) | `select:37`, `selectAll:44` (admin-only), `accessibleGarages:35` |
| `ParkingService` | Vehicle entry/exit, spot allocation | `vehicleEntry:82,129`, `vehicleExit:319,360`, `releaseAfterPayment:389`, `reserveSpot:492,516` |
| `TicketService` | Ticket lifecycle, durations | `createTicket:57,69,106`, `updateTicketStatus:239`, `markAwaitingPayment:374`, `closeTicket:431`, `cancelTicket:577` |
| `PaymentService` | Fee calc, processing, refunds | `processPayment:76,197`, `calculateParkingFee:234`, `validateCardPayment:259`, `refundPayment:304,362`, `generateReceipt:421` |
| `ReservationService` | Spot holds, claim, expiry | `reserve:42,46`, `cancel:90`, `expire:127`, `claimForEntry:140,144` |
| `NotificationService` | Persisted event backbone | `publish:37`, `listUnread:67`, `markAllRead:77`, `savePreference:101` |
| `DutyService` | Attendant duty sessions | `start:52`, `end:85`, `summary:124,130` |
| `OperationsSnapshotService` | Point-in-time ops aggregates | `snapshot:45` |
| `ScheduledReportService` / `ReportExporter` | Interval reports, branded PDF export | `scheduleEvery:27`, `writeBrandedPdf:50,61` |

(Line numbers refer to files under `src/main/java/com/parking/services/`.)

## Persistence

`persistence/PersistenceStore.java`:

- Path (`:1269-1275`): tests → in-memory; `-Dparkingos.database=<path>`;
  default `~/.parkingos/parking.db`. JDBC `jdbc:sqlite:<path>`.
- Pragmas (`:81-94`): `busy_timeout=5000`, `foreign_keys=ON`, file-backed only
  `journal_mode=WAL` + `synchronous=NORMAL`.
- Versioning: `CURRENT_SCHEMA_VERSION=11` (`:38`), `schema_version` table;
  newer DBs rejected (`:187-189`). `initializeSchema` (`:96-146`) is
  `CREATE TABLE/INDEX IF NOT EXISTS`, then garage migration (`:149`),
  ticket/payment `garage_id` columns (`:159`), referential-integrity rebuild
  (`:185`), and `validateSchema` (`:254`) before stamping v11.

## Role navigation

`buildRoleNavigation` (`ParkingApplication.java:276-333`):

- CUSTOMER → Overview / My Vehicles / Active Parking / Tickets / Wallet / Profile.
- ATTENDANT → Operations / Vehicle Entry / Vehicle Exit / Available Spots /
  Garage Status / Duty / Shift.
- ADMIN → Overview / Parking Spots / Users / Garages / Garage Access /
  Revenue & Analytics / Maintenance / Settings / Refunds.

Page swap via `navigateTo()` (`:533`).
