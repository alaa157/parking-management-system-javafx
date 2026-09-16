# Parking Notification-Backbone Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship persisted event-driven notifications first, then reservations, duty/shift, and bulk-safe ticket updates on that backbone.

**Architecture:** Keep `AppServices` service graph and SQLite `PersistenceStore`; add small domain services at existing seams. UI consumes typed snapshots/results. No simulated data.

**Tech Stack:** Java 21, JavaFX 21, SQLite JDBC, Maven, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-09-15-parking-notification-backbone-design.md`

## Global Constraints

- Keep role model `ADMIN`, `ATTENDANT`, `CUSTOMER`.
- Keep all DB writes inside `PersistenceStore.inTransaction(...)`.
- Do not store card numbers, CVV, or raw passwords.
- Keep CSS colors in token classes/templates.
- No random or simulated operational metrics or notifications.
- Every task adds/updates automated tests before done.

---

### Task 1: Persisted notification backbone

**Files:**
- Create: `src/main/java/com/parking/enums/NotificationType.java`
- Create: `src/main/java/com/parking/model/NotificationPreference.java`
- Create: `src/main/java/com/parking/services/NotificationService.java`
- Modify: `src/main/java/com/parking/persistence/PersistenceStore.java`
- Modify: `src/main/java/com/parking/gui/NotificationCenter.java`
- Modify: `src/main/java/com/parking/gui/SettingsView.java`
- Modify: `src/main/java/com/parking/gui/shell/AppServices.java`
- Test: `src/test/java/com/parking/NotificationServiceTest.java`
- Test: `src/test/java/com/parking/UserPersistenceTest.java`

**Interfaces:**
- Consumes: `PersistenceStore.inTransaction(TransactionWork<T>)`, `User` actor with `getUserId()`, `getRole()`, `isActive()`.
- Produces: `NotificationType { ENTRY_EXIT, PAYMENT_SUCCESS, MAINTENANCE, WEEKLY_SUMMARY }`; `NotificationPreference(String userId, Set<NotificationType> enabledTypes, String frequency, boolean soundEnabled)`; `NotificationService.publish(String recipientId, NotificationType type, String message)`, `listUnread(User actor)`, `markAllRead(User actor)`.

- [ ] **Step 1: Write failing service test**

```java
@Test
void disabledTypeProducesNoRow(@TempDir Path dir) {
    var store = new PersistenceStore(dir.resolve("n.db"));
    var users = new UserService(store);
    var svc = new NotificationService(store);
    var u = users.registerUser("n1", "Customer@123!", "n1@x.com", UserRole.CUSTOMER);
    svc.savePreference(new NotificationPreference(u.getUserId(), Set.of(NotificationType.PAYMENT_SUCCESS), "DAILY", true));
    svc.publish(u.getUserId(), NotificationType.MAINTENANCE, "m");
    assertTrue(svc.listUnread(u).isEmpty());
}
```

- [ ] **Step 2: Run it, verify it fails**

Run: `mvn -q -Dtest=NotificationServiceTest test`
Expected: FAIL — `NotificationService` / `NotificationType` not defined.

- [ ] **Step 3: Add tables + migration (schema v9)**

```java
s.executeUpdate("CREATE TABLE IF NOT EXISTS notification_prefs (user_id TEXT PRIMARY KEY, enabled_types TEXT NOT NULL, frequency TEXT NOT NULL, sound_enabled INTEGER NOT NULL)");
s.executeUpdate("CREATE TABLE IF NOT EXISTS notifications (notification_id TEXT PRIMARY KEY, recipient_id TEXT NOT NULL, type TEXT NOT NULL, message TEXT NOT NULL, created_at TEXT NOT NULL, unread INTEGER NOT NULL)");
s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_notifications_recipient ON notifications(recipient_id, unread)");
```

All writes via `inTransaction(...)`.

- [ ] **Step 4: Implement types + service (preference-filtered publish, recipient-owned list/mark-read)**

```java
public void publish(String recipientId, NotificationType type, String message) {
    NotificationPreference pref = loadPreference(recipientId);
    if (pref != null && !pref.enabledTypes().contains(type)) return;
    store.inTransaction(() -> { insertNotification(recipientId, type, message); return null; });
}
```

- [ ] **Step 5: Rewire `NotificationCenter` to persisted list + delete seed/demo path**

Delete `seed()` demo notices; render `service.listUnread(currentUser)`; `markAllRead` calls service. Keep `sound.*` `Preferences` keys compatible.

- [ ] **Step 6: Wire `SettingsView` flags/frequency/sound to load/save per-user prefs**

- [ ] **Step 7: Run tests + compile**

Run: `mvn -q -Dtest=NotificationServiceTest,UserPersistenceTest test`
Run: `mvn -q -DskipTests package`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/parking/enums/NotificationType.java src/main/java/com/parking/model/NotificationPreference.java src/main/java/com/parking/services/NotificationService.java src/main/java/com/parking/persistence/PersistenceStore.java src/main/java/com/parking/gui/NotificationCenter.java src/main/java/com/parking/gui/SettingsView.java src/test/java/com/parking/NotificationServiceTest.java
git commit -m "feat: persist event-driven notifications and preferences"
```

### Task 2: Reservations end-to-end on backbone

**Files:**
- Create: `src/main/java/com/parking/model/Reservation.java`
- Create: `src/main/java/com/parking/enums/ReservationStatus.java`
- Create: `src/main/java/com/parking/services/ReservationService.java`
- Modify: `src/main/java/com/parking/persistence/PersistenceStore.java`
- Modify: `src/main/java/com/parking/services/ParkingService.java`
- Modify: `src/main/java/com/parking/gui/TicketPaymentView.java`
- Modify: `src/main/java/com/parking/gui/ParkingApplication.java`
- Test: `src/test/java/com/parking/ReservationServiceTest.java`
- Test: `src/test/java/com/parking/GaragePersistenceTest.java`

**Interfaces:**
- Consumes: `NotificationService.publish(...)` from Task 1, `AppConfig.reservationHoldMinutes()`.
- Produces: `ReservationStatus { ACTIVE, CLAIMED, CANCELLED, EXPIRED }`; `Reservation(String reservationId, String userId, String spotId, LocalDateTime createdAt, LocalDateTime expiresAt, ReservationStatus status)`; `reserve(actor, spotId, now)`, `cancel(actor, reservationId, now)`, `expire(now) -> List<String>`, `claimForEntry(actor, vehicle, reservationId)`.

- [ ] **Step 1: Write failing reservation tests (reserve, duplicate, unauthorized cancel, expiry, claim)**

```java
@Test
void unauthorizedCancelThrows() {
    assertThrows(AuthorizationException.class, () -> svc.cancel(otherUser, reservationId, now));
}
```

- [ ] **Step 2: Run, verify failure**

Run: `mvn -q -Dtest=ReservationServiceTest test`
Expected: FAIL — types missing.

- [ ] **Step 3: Add `reservations` table + indexes by `(user_id, status, expires_at)`**

- [ ] **Step 4: Implement service with one transaction per create/cancel/expire; spot state is source of truth**

- [ ] **Step 5: Integrate `ParkingService.vehicleEntry` to claim matching active reservation; reject чужой with `AuthorizationException`**

- [ ] **Step 6: Add spot-detail reserve/cancel + countdown/expiry UI; run expiry on existing lifecycle timer, one backbone notification per transition**

- [ ] **Step 7: Run tests**

Run: `mvn -q -Dtest=ReservationServiceTest,GaragePersistenceTest test`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/parking/model/Reservation.java src/main/java/com/parking/enums/ReservationStatus.java src/main/java/com/parking/services/ReservationService.java src/test/java/com/parking/ReservationServiceTest.java
git commit -m "feat: add persisted reservation workflow"
```

### Task 3: Attendant duty and shift on backbone

**Files:**
- Create: `src/main/java/com/parking/model/DutySession.java`
- Create: `src/main/java/com/parking/services/DutySummary.java`
- Create: `src/main/java/com/parking/services/DutyService.java`
- Modify: `src/main/java/com/parking/persistence/PersistenceStore.java`
- Modify: `src/main/java/com/parking/gui/ParkingApplication.java`
- Modify: `src/main/java/com/parking/gui/SettingsView.java`
- Test: `src/test/java/com/parking/DutyServiceTest.java`
- Test: `src/test/java/com/parking/GaragePersistenceTest.java`

**Interfaces:**
- Consumes: `NotificationService.publish(...)` for start/end events.
- Produces: `DutySession(String sessionId, String attendantId, String shift, String zone, LocalDateTime startedAt, LocalDateTime endedAt, String status)` status `OPEN/CLOSED`; `DutySummary(String attendantId, String attendantName, long completedTransactions, double revenue, Duration totalDuration)`; `start(actor, shift, zone, now)`, `end(actor, now)`, `current(actor) -> Optional<DutySession>`, `summary(actor, from, to) admin-only`.

- [ ] **Step 1: Write failing tests (one open session, auth, restart, totals, duplicate-start rejection)**
- [ ] **Step 2: Run, verify failure** — `mvn -q -Dtest=DutyServiceTest test`
- [ ] **Step 3: Add `duty_sessions` table + index `(attendant_id, status)`**
- [ ] **Step 4: Implement service; connect ticket/payment success to open attendant session**
- [ ] **Step 5: Replace `buildComingSoonPage("Duty"/"Shift")` in `ParkingApplication.java:294,296` with duty page + admin summary**
- [ ] **Step 6: Add confirm/error states (start twice, end without session, non-admin summary)**
- [ ] **Step 7: Run** — `mvn -q -Dtest=DutyServiceTest,GaragePersistenceTest test` + `mvn -q -DskipTests package`
- [ ] **Step 8: Commit** — `feat: add attendant duty and shift workflows`

### Task 4: Bulk-safe ticket updates + fresh UI cleanups

**Files:**
- Create: `src/main/java/com/parking/services/BulkTicketUpdateResult.java`
- Modify: `src/main/java/com/parking/services/TicketService.java`
- Modify: `src/main/java/com/parking/gui/TicketPaymentView.java`
- Modify: `src/main/java/com/parking/gui/AnalyticsReportingView.java`
- Test: `src/test/java/com/parking/BulkTicketUpdateTest.java`

**Interfaces:**
- Consumes: existing `TicketService.updateTicketStatus`, `TicketStatus.isValidTransition`.
- Produces: `BulkTicketUpdateResult(Set<String> succeeded, Map<String,String> failures)`; `TicketService.updateStatuses(User actor, Set<String> ticketIds, TicketStatus target)`.

- [ ] **Step 1: Write failing tests (mixed valid/invalid, unauthorized, bad transition, empty set, rollback on injected persistence failure)**

```java
@Test
void mixedSelectionReportsFailures() {
    var r = tickets.updateStatuses(admin, Set.of(okId, badId), TicketStatus.CANCELLED);
    assertEquals(Set.of(okId), r.succeeded());
    assertTrue(r.failures().containsKey(badId));
}
```

- [ ] **Step 2: Run, verify failure** — `mvn -q -Dtest=BulkTicketUpdateTest test`
- [ ] **Step 3: Implement typed result; validate access first, persist successes in one transaction**
- [ ] **Step 4: Replace `TicketPaymentView.java:542-551` loop + `catch (Exception ignored)` with one service call + result dialog/toast; retain failed selections**
- [ ] **Step 5: Replace `AnalyticsReportingView.simulateRefresh` with real refresh; fix demo printer text in `TicketPaymentView.java:1016`**
- [ ] **Step 6: Run** — `mvn -q -Dtest=BulkTicketUpdateTest,TicketServiceTest,PaymentServiceFlowTest test`
- [ ] **Step 7: Commit** — `fix: report bulk ticket update failures`

### Task 5: Integration verification and docs

**Files:**
- Modify: `src/test/java/com/parking/GaragePersistenceTest.java` (add restart coverage)
- Create: `src/test/java/com/parking/NotificationBackboneEndToEndTest.java`
- Modify: `README.md`

**Interfaces:**
- Consumes: all Tasks 1–4 services.

- [ ] **Step 1: Add end-to-end test reserve → notification → claim → payment → duty totals → snapshot**
- [ ] **Step 2: Add restart test (reservations, duty sessions, prefs, unread survive SQLite reopen)**
- [ ] **Step 3: Run full suite** — `mvn clean test` (record exit code + count)
- [ ] **Step 4: Run** — `mvn generate-resources` (verify no unexpected CSS edits)
- [ ] **Step 5: Manual launch** — `mvn javafx:run` with admin/attendant/customer
- [ ] **Step 6: Document new workflows + backup/recovery in README**
- [ ] **Step 7: Commit** — `test: verify notification-backbone enhancements`
