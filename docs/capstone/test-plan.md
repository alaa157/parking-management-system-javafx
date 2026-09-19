# ParkingOS — Test Plan and Results

## 1. Strategy

Three tiers, all plain JUnit (headless, in-memory SQLite under surefire):

- **Service tests** drive public service methods with an actor and garage id,
  never bypassing access checks, and cover happy paths plus the rejecting edge
  of every state transition (`docs/LIFECYCLE.md` exception table).
- **Persistence/migration tests** assert schema versioning, garage backfill,
  and referential integrity.
- **GUI characterization tests** pin layout, theming, role navigation, and
  formatting without a display (no TestFX).

Run: `mvn test`. Conventions: `docs/TESTING.md`.

## 2. Traceability matrix (SRS → tests)

| Requirement | Covering test classes |
|---|---|
| FR-1 first-run admin | `UserServiceTest` (22), `AuthWindowLayoutTest` (2) |
| FR-2 auth, lockout, inactive | `UserServiceTest` (22) |
| FR-3 registration, user CRUD | `UserServiceTest` (22), `UserPersistenceTest` (9) |
| FR-4 hashed credentials, no defaults | `UserServiceTest` (22) |
| FR-5 garage lifecycle | `GarageServiceTest` (3), `GaragePersistenceTest` (16), `GarageManagementViewTest` (1) |
| FR-6 grant/revoke access | `GarageServiceTest` (3), `GarageContextTest` (2), `MultiGarageUiTest` (2) |
| FR-7 scoped operations, admin bypass | `MultiGarageTicketPaymentTest` (5), `GlobalVehicleParkingTest` (3), `GarageContextTest` (2) |
| FR-8 archived garages | `GarageServiceTest` (3), `GaragePersistenceTest` (16) |
| FR-9 entry, duplicate rejection | `ParkingServiceFlowTest` (14), `GlobalVehicleParkingTest` (3) |
| FR-10 atomic spot allocation | `ParkingServiceFlowTest` (14), `ParkingSpotModelTest` (15) |
| FR-11 ticket state machine | `TicketServiceTest` (15), `BulkTicketUpdateTest` (6) |
| FR-12 fees, tax, bulk updates | `PaymentServiceFlowTest` (32), `BulkTicketUpdateTest` (6) |
| FR-13 cash/card/wallet processing | `PaymentServiceFlowTest` (32) |
| FR-14 atomic pay/close/release | `PaymentServiceFlowTest` (32), `MultiGarageTicketPaymentTest` (5) |
| FR-15 receipts, notifications | `PaymentNotificationTest` (2), `NotificationServiceTest` (6), `ReportExporterTest` (4) |
| FR-16 refunds | `PaymentServiceFlowTest` (32) |
| FR-17 holds | `ReservationServiceTest` (9) |
| FR-18 claim-and-park | `ReservationServiceTest` (9), `ParkingServiceFlowTest` (14) |
| FR-19 expiry sweep/lazy | `ReservationServiceTest` (9) |
| FR-20 duty sessions | `DutyServiceTest` (8) |
| FR-21 shift summaries | `DutyServiceTest` (8), `OperationsSnapshotServiceTest` (9) |
| FR-22 snapshots, analytics, reports | `OperationsSnapshotServiceTest` (9), `ScheduledReportServiceTest` (5), `ReportExporterTest` (4) |
| FR-23 persisted notifications | `NotificationServiceTest` (6), `NotificationBackboneEndToEndTest` (1), `PaymentNotificationTest` (2) |
| NFR-2 migrations, integrity | `GarageMigrationTest` (1), `GaragePersistenceTest` (16), `AppConfigTest` (12) |
| NFR-3 navigation, theming | `AdminNavigationTest`, `NavigationStyleTest`, `AdminVisualThemeTest`, `LightThemeTest`, `ThemeAndShellChromeTest`, `ShellRefactorCharacterizationTest`, `SettingsRoleTabsTest`, `UserManagementLayoutTest`, `WalletPageTest`, `TicketStyleTest`, `UiFormatTest`, `InteractionRegressionTest`, `ParkingApplicationSizeTest` (38 GUI tests total) |

Every functional requirement traces to at least one passing class (NFR-5 satisfied).

## 3. Results

Measured run (`mvn test`, JDK 21, Ubuntu):

- **238 tests, 0 failures, 0 errors, 0 skipped — BUILD SUCCESS.**
- 38 test classes: 22 service/domain/persistence classes (~200 tests),
  16 GUI characterization classes (38 tests).
- Largest classes: `PaymentServiceFlowTest` (32), `UserServiceTest` (22),
  `GaragePersistenceTest` (16), `TicketServiceTest` (15),
  `ParkingSpotModelTest` (15), `ParkingServiceFlowTest` (14).
- The same suite runs in CI on every push and pull request to `main`
  (`.github/workflows/ci.yml`).

## 4. Known limits

- Card authorization is local (`LocalCardAuthorizationProvider`); no live
  gateway is exercised.
- GUI tests assert structure and style, not pixel rendering or OS window
  behavior.
- Concurrency testing is limited to single-writer SQLite semantics with busy
  timeout; multi-workstation use is out of scope.
