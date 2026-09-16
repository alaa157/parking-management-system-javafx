# Multi-Garage Production Readiness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend the existing Parking JavaFX application into a production-ready multi-garage system without rewriting its current domain, persistence, or UI foundations.

**Architecture:** Keep Java 21, JavaFX, Maven, SQLite, the existing services, and `PersistenceStore`. Add garage scope and user-garage access at the service/persistence boundaries, then thread an active-garage context through the existing JavaFX shell. Refactor only the oversized UI responsibilities touched by these flows.

**Tech Stack:** Java 21, JavaFX 21.0.8, SQLite JDBC 3.45.3.0, Maven, JUnit 5, existing CSS token/theme system, Linux-first packaging.

**Spec:** `docs/superpowers/specs/2026-09-14-multi-garage-production-readiness-design.md`

## Global Constraints

- Preserve the existing application architecture; do not rewrite the domain or migrate frameworks.
- Every ticket belongs to exactly one garage.
- Every payment must belong to the same garage as its ticket.
- A vehicle may have at most one active parking session across all garages.
- Global administrators can see all garages; other users require active garage access.
- Existing records migrate into a default garage without deletion.
- Password recovery is removed from the product.
- Linux is the first supported release platform; keep Java/business behavior cross-platform.
- Every task must finish with focused tests before moving to the next task.

## File map

Files likely to be created:

- `src/main/java/com/parking/model/Garage.java` — durable garage domain object.
- `src/main/java/com/parking/model/GarageAccess.java` — user-to-garage access record.
- `src/main/java/com/parking/services/GarageService.java` — garage lifecycle and access rules.
- `src/main/java/com/parking/services/GarageContext.java` — immutable active-garage selection.
- `src/main/java/com/parking/exceptions/GarageAccessException.java` — authorization failure for garage scope.
- `src/main/java/com/parking/exceptions/VehicleAlreadyParkedException.java` — typed global active-vehicle conflict.
- `src/main/java/com/parking/gui/ApplicationShell.java` — extracted stage, session, navigation, and active-garage shell responsibilities.
- `src/main/java/com/parking/gui/LoginView.java` — extracted login and first-run authentication UI.
- `src/main/java/com/parking/gui/GarageManagementView.java` — admin garage CRUD and lifecycle UI.
- `src/main/java/com/parking/gui/GarageAccessView.java` — admin user-to-garage assignment UI.

Files likely to be modified:

- `src/main/java/com/parking/persistence/PersistenceStore.java` — schema migration, garage/access persistence, scoped queries, indexes, and invariants.
- `src/main/java/com/parking/model/Ticket.java` — immutable garage identity and lifecycle accessors.
- `src/main/java/com/parking/model/Payment.java` — garage consistency accessors where required by the current model.
- `src/main/java/com/parking/services/ParkingService.java` — garage access and global active-vehicle checks.
- `src/main/java/com/parking/services/TicketService.java` — garage-scoped creation, lookup, and filtering.
- `src/main/java/com/parking/services/PaymentService.java` — ticket/payment garage validation and scoped queries.
- `src/main/java/com/parking/services/UserService.java` — garage assignments and authorization helpers.
- `src/main/java/com/parking/gui/ParkingApplication.java` — remove recovery, establish garage context, and connect new views incrementally.
- `src/main/java/com/parking/gui/TicketPaymentView.java` — garage display, filtering, and context validation.
- `src/main/java/com/parking/gui/AnalyticsReportingView.java` — garage filters and garage labels in reports.
- `src/main/java/com/parking/gui/UserManagementView.java` — garage access assignment UI.
- `src/main/resources/parkingos.css.template` and generated CSS — only when new component states need styling.
- `README.md` and `docs/theming.md` — migration, backup, Linux release, and UI guidance.
- `pom.xml` — production version, packaging profile, and release plugins.

## Task 1: Establish a green baseline and fix reservation integrity

**Files:**

- Modify: `src/main/java/com/parking/services/ParkingService.java:332-355`
- Modify: `src/main/java/com/parking/persistence/PersistenceStore.java:453-466`
- Test: `src/test/java/com/parking/AppTest.java:957-969`
- Test: `src/test/java/com/parking/GaragePersistenceTest.java`

**Interfaces:**

- Consumes: existing `ParkingService.reserveSpot`, `PersistenceStore.saveParkingSpot`, and test fixtures.
- Produces: a reservation operation that either persists a valid user reference or rejects the operation before mutating the spot.

- [x] **Step 1: Write a regression test** that registers the reservation holder before calling `reserveSpot`, then asserts the reservation persists and expires correctly.
- [x] **Step 2: Add a negative test** that attempts to reserve with an unknown user ID and asserts a typed validation failure with no in-memory or database reservation left behind.
- [x] **Step 3: Run the focused tests** with `mvn -q -Dtest=AppTest,GaragePersistenceTest test` and confirm the current failure is reproduced before the fix.
- [x] **Step 4: Implement the smallest fix** by validating the holder before mutating the spot, or by making the existing service fixture create the referenced user where that is the intended contract.
- [x] **Step 5: Run the focused tests again** and confirm both positive and negative reservation cases pass.
- [ ] **Step 6: Commit** with `git add src/main src/test && git commit -m "fix: preserve reservation reference integrity"`.

## Task 2: Remove password recovery and production demo credentials

**Files:**

- Modify: `src/main/java/com/parking/gui/ParkingApplication.java:324-377,469-476`
- Modify: `src/test/java/com/parking/gui/NavigationStyleTest.java` or create a focused login test beside it.
- Modify: `README.md`

**Interfaces:**

- Consumes: existing login and user-seeding flow.
- Produces: a login screen with no password-recovery action and a production startup path that does not create predictable accounts.

- [x] **Step 1: Write a UI-source regression test** that asserts the login view contains no recovery label/action and no “not connected” recovery message.
- [x] **Step 2: Write a startup test** that verifies production initialization does not require or create the hardcoded demo accounts.
- [x] **Step 3: Run the focused tests** with `mvn -q -Dtest=NavigationStyleTest test` and record the expected failures.
- [x] **Step 4: Remove the recovery control and handler** from `showLogin`; remove related unused styles and copy.
- [x] **Step 5: Gate demo users behind an explicit development fixture/property** and document the property in `README.md`; production startup must use first-run admin setup or an existing database.
- [x] **Step 6: Run the focused tests and full test suite** with `mvn -q -Dtest=NavigationStyleTest test` and `mvn test`.
- [ ] **Step 7: Commit** with `git add src/main src/test README.md && git commit -m "security: remove unfinished password recovery and demo startup accounts"`.

## Task 3: Add garage and user-access persistence

**Files:**

- Create: `src/main/java/com/parking/model/Garage.java`
- Create: `src/main/java/com/parking/model/GarageAccess.java`
- Create: `src/main/java/com/parking/services/GarageService.java`
- Create: `src/main/java/com/parking/exceptions/GarageAccessException.java`
- Modify: `src/main/java/com/parking/persistence/PersistenceStore.java`
- Create: `src/test/java/com/parking/GarageServiceTest.java`
- Create: `src/test/java/com/parking/GarageMigrationTest.java`

**Interfaces:**

- Produces `Garage`: immutable ID plus mutable validated attributes and archived state.
- Produces `GarageAccess`: `(String userId, String garageId, UserRole role, boolean active)` plus timestamps.
- Produces `GarageService.createGarage`, `updateGarage`, `archiveGarage`, `reopenGarage`, `grantAccess`, `revokeAccess`, `canAccess`, and `listAccessibleGarages`.
- Produces persistence methods for garage CRUD and access assignment using the existing transaction boundary.

- [x] **Step 1: Write model tests** for required fields, non-negative rates, valid levels, archive state, and stable IDs.
- [x] **Step 2: Write migration tests** that create the current schema fixture, initialize the new store, and assert a default garage plus access rows for existing users.
- [x] **Step 3: Write authorization tests** for active access, revoked access, archived garages, and global administrators.
- [x] **Step 4: Run the focused tests** with `mvn -q -Dtest=GarageServiceTest,GarageMigrationTest test` and confirm failures.
- [x] **Step 5: Add schema version migration** for `garages` and `user_garages`, including foreign keys, indexes, timestamps, and default-garage backfill.
- [x] **Step 6: Implement the model and service methods** with typed validation and transaction rollback on failed writes.
- [x] **Step 7: Run the focused tests again** and confirm migration and access behavior pass.
- [ ] **Step 8: Commit** with `git add src/main src/test && git commit -m "feat: add garages and multi-garage user access"`.

## Task 4: Make tickets and payments garage-scoped

**Files:**

- Modify: `src/main/java/com/parking/model/Ticket.java`
- Modify: `src/main/java/com/parking/model/Payment.java`
- Modify: `src/main/java/com/parking/persistence/PersistenceStore.java`
- Modify: `src/main/java/com/parking/services/TicketService.java`
- Modify: `src/main/java/com/parking/services/PaymentService.java`
- Create: `src/test/java/com/parking/MultiGarageTicketPaymentTest.java`

**Interfaces:**

- `Ticket` exposes immutable `getGarageId()` set during creation.
- `TicketService.createTicket(..., String garageId)` rejects inaccessible or archived garages.
- `TicketService.findTickets(actor, GarageScope scope, TicketFilter filter)` returns only authorized records.
- `PaymentService.processPayment(... )` derives and validates the ticket garage before writing.
- `PaymentService.findPayments(actor, GarageScope scope, PaymentFilter filter)` applies the same scope rules.

- [x] **Step 1: Write tests** for ticket creation in two garages, garage-filtered queries, admin all-garage queries, and inaccessible-garage rejection.
- [x] **Step 2: Write tests** proving a payment for Garage A cannot use Garage B and that legacy payments receive the ticket's backfilled garage.
- [x] **Step 3: Run the focused test** with `mvn -q -Dtest=MultiGarageTicketPaymentTest test` and confirm failures.
- [x] **Step 4: Add and backfill `garage_id`** on tickets and payments in a validated migration; add foreign keys and indexes.
- [x] **Step 5: Thread garage ID through ticket creation and loading** while preserving existing service overloads only where they can safely use the active garage context.
- [x] **Step 6: Validate payment garage consistency** inside the same transaction as payment persistence.
- [x] **Step 7: Run the focused test and migration tests** and confirm all records remain queryable.
- [ ] **Step 8: Commit** with `git add src/main src/test && git commit -m "feat: scope tickets and payments to garages"`.

## Task 5: Enforce one active parking session globally

**Files:**

- Modify: `src/main/java/com/parking/services/ParkingService.java`
- Modify: `src/main/java/com/parking/persistence/PersistenceStore.java`
- Create: `src/main/java/com/parking/exceptions/VehicleAlreadyParkedException.java`
- Modify: `src/main/java/com/parking/gui/ParkingApplication.java`
- Create: `src/test/java/com/parking/GlobalVehicleParkingTest.java`

**Interfaces:**

- `PersistenceStore.findActiveTicketForVehicle(String vehicleId)` returns the active ticket and garage snapshot or `Optional.empty()`.
- `ParkingService.ensureVehicleNotParked(String vehicleId)` throws `VehicleAlreadyParkedException` containing ticket ID, garage ID, spot ID, and entry time.
- `ParkingService.vehicleEntry(vehicle, garageId, actor)` performs the check and write in one transaction.

- [x] **Step 1: Write a two-garage test** that parks a vehicle in Garage A and asserts parking in Garage B is rejected with Garage A details.
- [x] **Step 2: Write a release test** that exits the vehicle and confirms parking in Garage B then succeeds.
- [x] **Step 3: Write a migration/data test** for duplicate active tickets and define startup rejection with a diagnostic error.
- [x] **Step 4: Run `mvn -q -Dtest=GlobalVehicleParkingTest test`** and confirm the new tests fail.
- [x] **Step 5: Add the SQLite partial unique index** covering active ticket states and the indexed lookup query.
- [x] **Step 6: Implement the service check and typed exception** before any spot mutation, then keep the insert/spot update in the existing transaction boundary.
- [x] **Step 7: Render the conflict in the UI** with the existing garage, ticket, spot, and entry time.
- [ ] **Step 8: Run focused and full tests**, then commit with `git add src/main src/test && git commit -m "feat: enforce global active vehicle parking"`.

## Task 6: Add active-garage context and admin garage management

**Files:**

- Create: `src/main/java/com/parking/services/GarageContext.java`
- Create: `src/main/java/com/parking/gui/GarageManagementView.java`
- Create: `src/main/java/com/parking/gui/GarageAccessView.java`
- Modify: `src/main/java/com/parking/gui/ParkingApplication.java`
- Modify: `src/main/java/com/parking/gui/UserManagementView.java`
- Create: `src/test/java/com/parking/gui/GarageManagementViewTest.java`

**Interfaces:**

- `GarageContext.select(String garageId)` validates accessible active garages and publishes the selected scope.
- `GarageContext.selectAll()` is available only to global admins and is read-only for operational actions.
- `GarageManagementView` consumes `GarageService` and emits create/edit/archive/access-assignment actions.

- [x] **Step 1: Write context tests** for single-garage users, multi-garage users, global-admin all-garage view, stale selection clearing, and archived-garage rejection.
- [x] **Step 2: Write view tests** for validated create/edit forms and access assignment actions.
- [x] **Step 3: Run focused tests** with `mvn -q -Dtest=GarageManagementViewTest test` and confirm failures.
- [x] **Step 4: Implement `GarageContext`** and connect it to the existing navigation shell without changing the current service architecture.
- [x] **Step 5: Build the admin garage screen** using existing modal, table, toast, empty-state, and design-token components.
- [x] **Step 6: Add user-garage assignment controls** to user management and refresh assignments after save.
- [x] **Step 7: Add garage selector and explicit operational-selection validation** to the shell.
- [ ] **Step 8: Commit** with `git add src/main src/test && git commit -m "feat: add garage context and admin garage management"`.

## Task 7: Display and filter garage ownership throughout the UI

**Files:**

- Modify: `src/main/java/com/parking/gui/TicketPaymentView.java`
- Modify: `src/main/java/com/parking/gui/AnalyticsReportingView.java`
- Modify: `src/main/java/com/parking/gui/ParkingApplication.java`
- Modify: `src/main/java/com/parking/gui/ReportExporter.java`
- Modify: `src/main/resources/parkingos.css.template`
- Create: `src/test/java/com/parking/gui/MultiGarageUiTest.java`

**Interfaces:**

- Views consume `GarageContext`, scoped service queries, and typed operation results.
- Exported ticket/payment rows include garage name and garage ID.

- [x] **Step 1: Write UI tests** asserting garage labels appear in ticket/payment rows, details, receipts, and reports.
- [x] **Step 2: Write filter tests** for one garage, all garages, and inaccessible garages.
- [x] **Step 3: Run focused UI tests** and confirm failures.
- [x] **Step 4: Add the garage column and filter controls** to ticket/payment screens.
- [x] **Step 5: Add garage scope to analytics queries and report export data.**
- [x] **Step 6: Add empty/loading/error states** for garage switching and scoped refreshes.
- [x] **Step 7: Add only the needed tokenized CSS states** and regenerate CSS with `mvn generate-resources`.
- [ ] **Step 8: Run focused UI tests and full tests**, then commit with `git add src/main src/test && git commit -m "feat: expose garage scope in operations and reports"`.

## Task 8: Incrementally split the JavaFX shell

**Files:**

- Modify: `src/main/java/com/parking/gui/ParkingApplication.java`
- Create focused view/controller classes for the touched flows under `src/main/java/com/parking/gui/`
- Modify: related GUI tests

**Interfaces:**

- The shell owns stage, session, navigation, and context only.
- Views receive typed services, `GarageContext`, and callbacks for navigation.
- No extracted view directly opens database connections or mutates persistence state outside a service.

- [x] **Step 1: Add characterization tests** for login, dashboard navigation, garage switching, and logout.
- [x] **Step 2: Extract the login view** without changing visible behavior.
- [x] **Step 3: Extract garage context/navigation controls** without changing service contracts.
- [x] **Step 4: Extract garage management and ticket/payment sections** one at a time, running focused tests after each extraction.
- [ ] **Step 5: Remove duplicated event-handler and formatting helpers** only after callers use the extracted components.
- [ ] **Step 6: Run `mvn test` and launch with `mvn javafx:run`** for a manual smoke pass.
- [ ] **Step 7: Commit** with `git add src/main src/test && git commit -m "refactor: isolate multi-garage JavaFX flows"`.

## Task 9: Add production verification and Linux packaging

**Files:**

- Modify: `pom.xml`
- Modify: `README.md`
- Create: `.github/workflows/ci.yml`
- Create: `scripts/package-linux.sh`
- Create: `src/test/java/com/parking/ProductionStartupTest.java`
- Create: `docs/release/linux.md`

**Interfaces:**

- `mvn test` is the required automated test command.
- `mvn -q -DskipTests package` produces the application artifact.
- `scripts/package-linux.sh` produces a Linux distributable in a clean output directory and exits nonzero on failure.

- [ ] **Step 1: Add startup/migration smoke tests** covering a fresh database, upgraded database, archived garage, and failed integrity check.
- [ ] **Step 2: Add CI steps** for Java 21, `mvn generate-resources`, `mvn test`, and `mvn -q -DskipTests package`.
- [ ] **Step 3: Set a release version** in `pom.xml` and configure a Linux packaging profile using `jpackage` with the existing app icon.
- [ ] **Step 4: Write the packaging script** with explicit input/output paths, required Java version checks, and a clean staging directory.
- [ ] **Step 5: Document Linux installation, database location, backup/restore, upgrade, and diagnostics** in `README.md` and `docs/release/linux.md`.
- [ ] **Step 6: Run the full release sequence**: `mvn generate-resources`, `mvn test`, `mvn -q -DskipTests package`, and the Linux packaging script.
- [ ] **Step 7: Perform a fresh-install smoke test** using the packaged application and verify login, garage creation, ticket creation, payment, backup, and restore.
- [ ] **Step 8: Commit** with `git add pom.xml README.md docs scripts .github src/test && git commit -m "build: add production verification and Linux packaging"`.

## Completion checklist

- [ ] `mvn test` passes with zero failures and zero errors.
- [ ] Existing data migrates into a default garage without deletion.
- [ ] Admin can create, edit, archive, reopen, and configure garages.
- [ ] Users can access multiple garages and switch context.
- [ ] Tickets, payments, reservations, reports, and dashboards are garage-scoped.
- [ ] Admin can filter and identify every ticket/payment by garage.
- [ ] A vehicle cannot be active in two garages.
- [ ] Password recovery is absent from the UI and code path.
- [ ] Predictable demo credentials are not created in production startup.
- [ ] Linux package launches successfully and backup/restore are documented.
