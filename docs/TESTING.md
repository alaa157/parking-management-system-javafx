# ParkingOS Testing Strategy

## Tiers (`src/test/java/com/parking/`)

| Tier | Scope | Examples | Notes |
|---|---|---|---|
| Domain / model | `ParkingSpotModelTest`, `config/AppConfigTest` | Spot transitions, settings load | Pure JUnit, no I/O |
| Service | `TicketServiceTest`, `PaymentServiceFlowTest`, `ReservationServiceTest`, `DutyServiceTest`, `ParkingServiceFlowTest`, `UserServiceTest`, `GarageServiceTest`, `NotificationServiceTest`, `BulkTicketUpdateTest`, `GlobalVehicleParkingTest`, `MultiGarageTicketPaymentTest`, `PaymentNotificationTest`, `NotificationBackboneEndToEndTest`, `OperationsSnapshotServiceTest`, `ScheduledReportServiceTest`, `services/GarageContextTest`, `services/ReportExporterTest` | Entry→pay→exit, claim-and-park, refunds, multi-garage scoping | `TestFixtures` builders; persistence tests use in-memory SQLite (`PersistenceStore` `:memory:` when surefire runs) |
| Persistence / migration | `GarageMigrationTest`, `GaragePersistenceTest`, `UserPersistenceTest` | Schema upgrades, garage backfill, FK integrity | Assert `schema_version` + `validateSchema` behavior |
| GUI characterization | `gui/*Test` (16 files: `AdminNavigationTest`, `MultiGarageUiTest`, `WalletPageTest`, …) | Layout, theming, role nav, formatting | Plain JUnit — no TestFX, no display needed |

## Commands

```bash
mvn test                  # full suite (default surefire config, pom.xml:76)
mvn -Dtest=TicketServiceTest test   # single class
mvn -Dtest='*ServiceTest' test      # pattern
mvn generate-resources    # regenerate token CSS (theming contract)
```

GUI tests run headless (no `Platform.startup`/TestFX in `src/test`); only
`mvn javafx:run` needs a display (Xvfb on servers).

## Conventions for new tests

- One vertical slice per test (see `mattpocock/tdd` skill in
  `.agents/skills/mattpocock/engineering/tdd/`): failing assertion first for
  bug fixes, then the minimal fix.
- Service tests go through public service methods with an actor + garage id —
  never bypass `requireAccess` paths (keeps `AUTHORIZATION.md` honest).
- Use `TestFixtures` factories instead of hand-rolled entities.
- Status-transition tests must cover the rejecting edge (see `LIFECYCLE.md`
  exception table), not just the happy path.
