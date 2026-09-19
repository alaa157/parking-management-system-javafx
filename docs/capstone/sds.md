# ParkingOS — Software Design Specification

## 1. Architecture overview

Layered desktop application (Java 21, JavaFX, SQLite). Full container view,
startup sequence, and service catalog: `docs/ARCHITECTURE.md`
(`docs/diagrams/architecture.svg`).

- **GUI** (`com.parking.gui`): `ParkingApplication` orchestrates navigation;
  `shell/AppShell` renders sidebar, header, and pages; `shell/AppServices`
  bootstraps the service graph; 16 view classes render one screen each.
- **Services** (`com.parking.services`): own transactions and authorization.
  `ParkingService` (entry/exit), `TicketService` (lifecycle),
  `PaymentService` (fees/processing/refunds), `ReservationService`
  (holds/claim/expiry), `GarageService` + `GarageContext` (lifecycle/access/
  selection), `DutyService`, `NotificationService`,
  `OperationsSnapshotService`, `ScheduledReportService`, `ReportExporter`.
- **Domain** (`com.parking.model`, `enums`, `exceptions`): entities, state
  enums, and guard exceptions.
- **Persistence** (`com.parking.persistence.PersistenceStore`): the only JDBC
  boundary; schema v11, WAL, foreign keys on.
- **Cross-cutting** (`security`, `config`, `util`): password hashing, local
  card authorization, `AppConfig` settings, money math.

## 2. Key design decisions

1. **Service-layer transactions:** multi-aggregate mutations (spot + vehicle +
   ticket + payment + notification) commit in one `inTransaction` block with
   compensating rollback. Rationale and alternatives: `docs/ADRs.md` ADR-5.
2. **Deny-by-default garage access:** `GarageService.requireAccess` gates every
   scoped operation; the UI selection (`GarageContext`) never authorizes on its
   own. See `docs/AUTHORIZATION.md`.
3. **Single-table inheritance:** `User ← Admin/Attendant/Customer` and
   `Payment ← Card/Cash/WalletPayment` share tables with role/method
   discriminators, avoiding join overhead for an embedded store.
4. **Lazy reservation expiry:** no timer thread; holds lapse on sweeps and on
   the next spot operation, which keeps the desktop idle-cheap.
5. **Tokenized theming:** `DesignTokens`/`LightDesignTokens` constants generate
   all CSS via `scripts/generate_palette_css.py`; generated files are never
   hand-edited (`docs/theming.md`).

## 3. Data design

Entity-relationship diagram, ownership rules, all 14 tables with columns, keys,
and migration origins: `docs/DATA_MODEL.md` (`docs/diagrams/erd.svg`).
Invariants the schema enforces:

- One active parking session per vehicle (partial unique index on
  `ACTIVE`/`AWAITING_PAYMENT` tickets).
- One occupant per spot (partial unique index on `parking_spots.vehicle_id`).
- One open duty session per attendant (partial unique index).
- Tickets and payments each bound to exactly one garage (`RESTRICT` FKs).
- Refund arithmetic (`final = amount + tax`) and status CHECKs validated on
  the hardening rebuild.

## 4. Behavior design

- State machines for tickets, reservations, spots, and payments with the
  exception raised on each invalid transition: `docs/LIFECYCLE.md`
  (`docs/diagrams/lifecycle.svg`).
- Sequences for entry, payment-to-exit, reservation claim, and duty:
  `docs/FLOWS.md` (`docs/diagrams/flows-*.svg`).

## 5. Interface design

Role-shaped navigation (`ParkingApplication.buildRoleNavigation`): customers
see vehicles, parking, tickets, wallet, profile; attendants see operations,
entry, exit, spots, duty, shift; admins see users, garages, access, analytics,
maintenance, settings, refunds. A header garage picker scopes operations; the
command palette (`Ctrl/Cmd+K`) offers fuzzy navigation. Per-screen behavior is
specified in `docs/USER_GUIDES.md`.

## 6. Deployment design

Single workstation: `mvn javafx:run`; data under `~/.parkingos/`
(overridable per property). Backup, restore, migration-failure handling, and
troubleshooting: `docs/OPERATIONS.md`.
