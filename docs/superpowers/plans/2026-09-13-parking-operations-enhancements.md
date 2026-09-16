# Parking Operations Enhancements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace unfinished/demo behavior with production-ready parking operations: live dashboard data, reservations, attendant duty/shift workflows, real notifications, and reliable bulk ticket actions.

**Architecture:** Keep the existing JavaFX shell and service layer, but add small domain/application services at the existing seams. UI screens consume typed snapshots and operation results instead of calculating business outcomes or generating simulated data. Persistence remains SQLite-backed through `PersistenceStore`, with every new workflow covered by service and persistence tests before UI integration.

**Tech Stack:** Java 21, JavaFX 21, SQLite JDBC, Maven, JUnit 5, existing CSS token generator.

**Spec:** `codebase.xml` (read-only repository baseline and current implementation reference)

## Global Constraints

- Do not replace working persistence, payment, analytics, theming, or export features already present.
- Do not use random or simulated values for operational metrics or notifications.
- Preserve the existing role model: `ADMIN`, `ATTENDANT`, and `CUSTOMER`.
- Preserve existing public service behavior unless a new typed result is required to expose partial failure safely.
- Keep database writes inside `PersistenceStore.inTransaction(...)`.
- Do not store card numbers, CVV values, or raw passwords.
- Keep CSS colors in the existing token classes and generated templates.
- Every task must add or update automated tests before implementation is considered complete.

---

## Existing functionality explicitly excluded

The following already exists and should not be rebuilt:

- SQLite schema validation, foreign keys, migrations, rollback injection, and backup documentation.
- Payment processing, refund flow, ticket lifecycle, payment receipt generation, and basic persistence tests.
- Analytics date-range refresh, CSV export, branded PDF export, and scheduled report tests.
- User search/filtering, bulk user actions, theme switching, CSS token generation, and accessibility helpers.
- Basic parking spot reservation methods in `ParkingSpot` and `ParkingService`; this plan completes their user-facing and lifecycle behavior.

---

### Task 1: Replace simulated dashboard mode with live operational snapshots

**Files:**
- Create: `src/main/java/com/parking/services/OperationsSnapshotService.java`
- Create: `src/main/java/com/parking/services/OperationsSnapshot.java`
- Modify: `src/main/java/com/parking/services/ParkingService.java`
- Modify: `src/main/java/com/parking/services/PaymentService.java`
- Modify: `src/main/java/com/parking/services/TicketService.java`
- Modify: `src/main/java/com/parking/gui/ParkingApplication.java`
- Test: `src/test/java/com/parking/OperationsSnapshotServiceTest.java`

**Interfaces:**
- `OperationsSnapshot` is a record with fields `int available`, `int occupied`, `int reserved`, `int maintenance`, `double occupancyPercent`, `int activeTicketCount`, `double completedRevenue`, and `List<Integer> occupancyHistory`.
- `OperationsSnapshotService.snapshot(User actor, LocalDateTime now)` returns an immutable `OperationsSnapshot` containing available, occupied, reserved, maintenance, occupancy percentage, active ticket count, completed revenue for the selected day, and a short occupancy history.
- `ParkingApplication` consumes `OperationsSnapshotService.snapshot(...)`; it must no longer mutate `simulatedRevenue`, `simulatedActiveTickets`, or `simulatedOccupancy`.

- [ ] **Step 1: Write failing service tests** for admin/attendant snapshots, customer-scoped snapshots, zero-capacity garages, and revenue filtering to completed payments on the requested date.
- [ ] **Step 2: Run the focused test** with `mvn -q -Dtest=OperationsSnapshotServiceTest test`; verify the new service/types are unavailable.
- [ ] **Step 3: Implement immutable snapshot types** using records and compute all values from `ParkingGarage`, `TicketService`, and `PaymentService` data visible to the actor.
- [ ] **Step 4: Add a refresh method** to `ParkingApplication` using the existing `BackgroundTaskRunner`; update labels and charts only on the JavaFX thread.
- [ ] **Step 5: Replace the Live toggle** with `Refresh` plus optional auto-refresh interval (`Off`, `5 seconds`, `30 seconds`) and stop the timer on navigation and shutdown.
- [ ] **Step 6: Delete the random-data path** (`Random liveRandom`, `injectLiveData`, simulated counters, and simulation-only notification creation).
- [ ] **Step 7: Add tests** proving refresh uses persisted/real service values and that auto-refresh cancellation prevents updates after view disposal.
- [ ] **Step 8: Run the focused test and compile** with `mvn -q -Dtest=OperationsSnapshotServiceTest test` and `mvn -q -DskipTests package`.
- [ ] **Step 9: Commit** with `feat: replace simulated dashboard with live operations data`.

### Task 2: Complete reservations as an end-to-end workflow

**Files:**
- Create: `src/main/java/com/parking/model/Reservation.java`
- Create: `src/main/java/com/parking/enums/ReservationStatus.java`
- Create: `src/main/java/com/parking/services/ReservationService.java`
- Modify: `src/main/java/com/parking/persistence/PersistenceStore.java`
- Modify: `src/main/java/com/parking/services/ParkingService.java`
- Modify: `src/main/java/com/parking/gui/ParkingApplication.java`
- Modify: `src/main/java/com/parking/gui/TicketPaymentView.java`
- Modify: `src/main/java/com/parking/gui/NotificationCenter.java`
- Test: `src/test/java/com/parking/ReservationServiceTest.java`
- Test: `src/test/java/com/parking/GaragePersistenceTest.java`

**Interfaces:**
- `ReservationStatus` has exactly `ACTIVE`, `CLAIMED`, `CANCELLED`, and `EXPIRED` values.
- `Reservation` is a record with fields `String reservationId`, `String userId`, `String spotId`, `LocalDateTime createdAt`, `LocalDateTime expiresAt`, and `ReservationStatus status`.
- `ReservationService.reserve(User actor, String spotId, LocalDateTime now)` returns a persisted `Reservation` with ID, user ID, spot ID, created time, expiry time, and `ReservationStatus`.
- `ReservationService.cancel(User actor, String reservationId, LocalDateTime now)` cancels only reservations the actor may manage.
- `ReservationService.expire(LocalDateTime now)` returns the IDs transitioned to `EXPIRED` and releases their spots atomically.
- `ReservationService.claimForEntry(User actor, Vehicle vehicle, String reservationId)` validates ownership/role and lets `ParkingService.vehicleEntry(...)` consume the reservation.

- [ ] **Step 1: Add failing tests** for customer reservation, duplicate reservation, unauthorized cancellation, expiry, restart persistence, and claiming a reservation during entry.
- [ ] **Step 2: Add the migration test first** and extend `PersistenceStore` with a `reservations` table, status checks, indexes by user/status/expiry, and load/save/update methods.
- [ ] **Step 3: Implement `Reservation`** with explicit statuses `ACTIVE`, `CLAIMED`, `CANCELLED`, and `EXPIRED`; reject invalid transitions.
- [ ] **Step 4: Implement `ReservationService`** with one transaction for reservation creation/cancellation/expiry and use the existing spot state as the source of truth.
- [ ] **Step 5: Integrate entry** so a matching active reservation is claimed before allocation; reject another user's reservation with `AuthorizationException`.
- [ ] **Step 6: Add reservation controls** to the spot detail panel and customer ticket view: reserve, cancel, countdown/expiry text, and “reserved for you” entry action.
- [ ] **Step 7: Run expiry on the existing application lifecycle timer** and publish one notification per transition, without random notification generation.
- [ ] **Step 8: Run `mvn -q -Dtest=ReservationServiceTest,GaragePersistenceTest test`** and verify migration/restart behavior.
- [ ] **Step 9: Commit** with `feat: add persisted reservation workflow`.

### Task 3: Implement attendant duty and shift management

**Files:**
- Create: `src/main/java/com/parking/model/DutySession.java`
- Create: `src/main/java/com/parking/services/DutySummary.java`
- Create: `src/main/java/com/parking/services/DutyService.java`
- Modify: `src/main/java/com/parking/persistence/PersistenceStore.java`
- Modify: `src/main/java/com/parking/model/Attendant.java`
- Modify: `src/main/java/com/parking/gui/ParkingApplication.java`
- Modify: `src/main/java/com/parking/gui/SettingsView.java`
- Test: `src/test/java/com/parking/DutyServiceTest.java`
- Test: `src/test/java/com/parking/GaragePersistenceTest.java`

**Interfaces:**
- `DutySession` is a record with fields `String sessionId`, `String attendantId`, `String shift`, `String zone`, `LocalDateTime startedAt`, `LocalDateTime endedAt`, and `String status` where status is `OPEN` or `CLOSED`.
- `DutySummary` is a record with fields `String attendantId`, `String attendantName`, `long completedTransactions`, `double revenue`, and `Duration totalDuration`.
- `DutyService.start(User actor, String shift, String zone, LocalDateTime now)` returns a persisted `DutySession` with `OPEN` status.
- `DutyService.end(User actor, LocalDateTime now)` closes the actor's open session and returns duration, processed-ticket count, and revenue total.
- `DutyService.current(User actor)` returns `Optional<DutySession>`.
- `DutyService.summary(User actor, LocalDate from, LocalDate to)` is admin-only and returns `List<DutySummary>`.

- [ ] **Step 1: Write failing tests** for one open session per attendant, start/end authorization, restart persistence, duration, ticket count, revenue, and duplicate start rejection.
- [ ] **Step 2: Add the duty-session migration** and persistence methods with an index on `(attendant_id, status)`.
- [ ] **Step 3: Implement `DutyService`** and connect successful ticket/payment operations to the open attendant session when an attendant is the actor.
- [ ] **Step 4: Replace `buildComingSoonPage("Duty", ...)`** with a duty page showing current shift/zone, start/end controls, open-session duration, and transaction summary.
- [ ] **Step 5: Replace `buildComingSoonPage("Shift", ...)`** with an admin shift summary showing attendants, open sessions, and date-range totals.
- [ ] **Step 6: Add confirmation and error states** for ending a session, starting twice, ending with no session, and attempting admin-only summaries as a non-admin.
- [ ] **Step 7: Run `mvn -q -Dtest=DutyServiceTest,GaragePersistenceTest test`** and compile the JavaFX application.
- [ ] **Step 8: Commit** with `feat: add attendant duty and shift workflows`.

### Task 4: Make notifications event-driven and preferences persistent

**Files:**
- Create: `src/main/java/com/parking/model/NotificationPreference.java`
- Create: `src/main/java/com/parking/enums/NotificationType.java`
- Create: `src/main/java/com/parking/services/NotificationService.java`
- Modify: `src/main/java/com/parking/services/InMemoryNotificationPublisher.java`
- Modify: `src/main/java/com/parking/persistence/PersistenceStore.java`
- Modify: `src/main/java/com/parking/gui/NotificationCenter.java`
- Modify: `src/main/java/com/parking/gui/SettingsView.java`
- Modify: `src/main/java/com/parking/gui/ParkingApplication.java`
- Test: `src/test/java/com/parking/NotificationServiceTest.java`
- Test: `src/test/java/com/parking/UserPersistenceTest.java`

**Interfaces:**
- `NotificationType` has exactly `ENTRY_EXIT`, `PAYMENT_SUCCESS`, `MAINTENANCE`, and `WEEKLY_SUMMARY` values.
- `NotificationPreference` is a record with fields `String userId`, `Set<NotificationType> enabledTypes`, `String frequency`, and `boolean soundEnabled`.
- `NotificationService.publish(String recipientId, NotificationType type, String message)` persists an unread notification only when the recipient preference permits it.
- `NotificationService.listUnread(User actor)` and `markAllRead(User actor)` enforce recipient ownership.
- `NotificationPreference` stores event flags, delivery frequency, and sound preference per user.

- [ ] **Step 1: Write failing tests** for preference persistence, disabled event types, unread counts, recipient isolation, and mark-all-read.
- [ ] **Step 2: Add preference and notification tables/migrations** with indexes by recipient and unread state.
- [ ] **Step 3: Implement `NotificationService`** and inject it into parking, payment, reservation, and duty workflows.
- [ ] **Step 4: Update `NotificationCenter`** to render persisted notifications and subscribe to new events; remove its demo/random notification path.
- [ ] **Step 5: Update `SettingsView`** so notification flags, frequency, and sound are loaded and saved rather than stored only in fields.
- [ ] **Step 6: Keep sound settings compatible** with the existing `Preferences` keys while making event enablement user-specific.
- [ ] **Step 7: Run `mvn -q -Dtest=NotificationServiceTest,UserPersistenceTest test`** and verify no notification is generated by dashboard simulation.
- [ ] **Step 8: Commit** with `feat: persist event-driven notifications and preferences`.

### Task 5: Make bulk ticket actions safe and explainable

**Files:**
- Create: `src/main/java/com/parking/services/BulkTicketUpdateResult.java`
- Modify: `src/main/java/com/parking/services/TicketService.java`
- Modify: `src/main/java/com/parking/gui/TicketPaymentView.java`
- Modify: `src/main/java/com/parking/gui/NotificationCenter.java`
- Test: `src/test/java/com/parking/BulkTicketUpdateTest.java`

**Interfaces:**
- `TicketService.updateStatuses(User actor, Set<String> ticketIds, TicketStatus target)` returns `BulkTicketUpdateResult` containing successful IDs and `Map<String, String>` failure messages.
- The operation validates all ticket access before writing and persists successful changes in one transaction; invalid transitions do not get silently ignored.

- [ ] **Step 1: Write failing tests** for mixed valid/invalid selections, unauthorized tickets, invalid lifecycle transitions, empty selection, and rollback on persistence failure.
- [ ] **Step 2: Implement the typed result and service method** using the existing actor access rules and status transition validation.
- [ ] **Step 3: Replace the UI loop and `catch (Exception ignored)`** with one service call and a result dialog/toast showing updated count and failed ticket reasons.
- [ ] **Step 4: Refresh the list only after the transaction completes** and retain failed selections for correction.
- [ ] **Step 5: Run `mvn -q -Dtest=BulkTicketUpdateTest test`** and verify the existing payment/ticket tests still pass.
- [ ] **Step 6: Commit** with `fix: report bulk ticket update failures`.

### Task 6: Integration verification and release documentation

**Files:**
- Modify: `src/test/java/com/parking/AppTest.java`
- Modify: `src/test/java/com/parking/GaragePersistenceTest.java`
- Modify: `README.md`
- Modify: `docs/pms-sprint-dashboard.html`

- [ ] **Step 1: Add one end-to-end test** covering reserve → notification → claim on entry → payment → duty totals → dashboard snapshot.
- [ ] **Step 2: Add restart verification** proving reservations, duty sessions, notification preferences, and unread notifications survive reopening SQLite.
- [ ] **Step 3: Run the complete suite** with `mvn clean test` and record the exit code and test count.
- [ ] **Step 4: Run resource generation** with `mvn generate-resources` and verify generated CSS has no unexpected source edits.
- [ ] **Step 5: Launch the JavaFX app** with `mvn javafx:run` and manually verify each new workflow with admin, attendant, and customer accounts.
- [ ] **Step 6: Document** the new reservation, duty, notification, and live dashboard workflows plus backup/recovery implications.
- [ ] **Step 7: Commit** with `test: verify parking operations enhancements`.

## Self-review checklist

- All proposed work targets observable unfinished behavior rather than rebuilding existing payment, reporting, persistence, or theme functionality.
- Every new public interface is named before a later task consumes it.
- Reservation, duty, notification, and bulk-update workflows each have isolated service tests and persistence coverage.
- No task depends on random data, an unpersisted setting, or a silent exception.
- The implementation order keeps persistence and service contracts ahead of JavaFX integration.
