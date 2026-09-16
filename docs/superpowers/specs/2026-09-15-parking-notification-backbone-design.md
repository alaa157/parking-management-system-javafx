# Parking Notification-Backbone Enhancements — Design

Date: 2026-09-15
Approach: A — Notification backbone first (approved)
Prior plan: `docs/superpowers/plans/2026-09-13-parking-operations-enhancements.md`
Baseline: `codebase.xml` (read-only)

## 1. Goal
Finish unfinished parking operations on top of the already-live dashboard,
with persisted event-driven notifications as the backbone that reservations,
duty/shift, and bulk ticket outcomes all publish through.

## 2. Architecture
- Keep `gui/shell/AppServices` service graph and SQLite-backed `PersistenceStore`.
- Add small domain/application services at existing seams; UI consumes typed
  snapshots and typed operation results, never simulated values.
- Task 1 stays done: `OperationsSnapshotService.snapshot(actor, now)` +
  `DashboardView` Refresh + auto-refresh interval. No `simulatedRevenue`,
  `liveRandom`, or `injectLiveData` reintroduction.
- New backbone: `NotificationService` + `NotificationType` + per-user
  `NotificationPreference`, persisted. `NotificationCenter` renders persisted
  unread and subscribes to new events; demo/random path deleted.

## 3. Components
- **Notifications (first):** `enums/NotificationType` exactly
  `ENTRY_EXIT, PAYMENT_SUCCESS, MAINTENANCE, WEEKLY_SUMMARY`;
  `model/NotificationPreference` record
  `(userId, enabledTypes, frequency, soundEnabled)`;
  `services/NotificationService` with
  `publish(recipientId, type, message)`, `listUnread(actor)`,
  `markAllRead(actor)` with recipient-ownership enforcement.
  `PersistenceStore` gains notification + preference tables, indexes by
  `(recipient, unread)`.
- **Reservations (second):** `model/Reservation` record
  `(reservationId, userId, spotId, createdAt, expiresAt, status)`;
  `enums/ReservationStatus` exactly `ACTIVE, CLAIMED, CANCELLED, EXPIRED`;
  `services/ReservationService` with `reserve / cancel / expire / claimForEntry`.
  One transaction per lifecycle transition; expiry timer publishes one
  notification per transition via backbone.
- **Duty/shift (third):** `model/DutySession` record
  `(sessionId, attendantId, shift, zone, startedAt, endedAt, status OPEN/CLOSED)`;
  `services/DutySummary` + `services/DutyService` with
  `start / end / current / summary(admin-only)`.
  Replaces `ParkingApplication.buildComingSoonPage("Duty"/"Shift")`.
- **Bulk tickets (fourth):** `services/BulkTicketUpdateResult`
  `(successIds, failures Map<id, reason>)`;
  `TicketService.updateStatuses(actor, ids, target)` validates access first,
  persists successes in one transaction, never silently ignores invalid
  transitions. Replaces `TicketPaymentView.applyBulkStatus` loop with
  `catch (Exception ignored)`.
- **Fresh cleanups (folded in):** `AnalyticsReportingView.simulateRefresh`
  becomes real refresh; `TicketPaymentView` demo printer text becomes real
  receipt path or honest disabled state; no new `comingSoon` pages.

## 4. Data flow
Entry/payment/reservation-expiry/duty-start-end → domain service transaction →
`NotificationService.publish` (preference-filtered, persisted) →
`NotificationCenter` update + `OperationsSnapshotService` refresh on next
dashboard tick. Bulk updates return typed result → dialog/toast with
updated-count + per-ticket reasons; failed selections retained.

## 5. Error handling
- Unauthorized reservation cancel/claim → `AuthorizationException`.
- Duplicate reservation / double duty-start / end-without-session →
  explicit domain errors surfaced in UI, no silent catch.
- Preference-disabled types produce no rows (not filtered at render time).
- Recipient isolation enforced at service layer, not just UI hiding.

## 6. Constraints
- Keep `ADMIN, ATTENDANT, CUSTOMER` role model and existing public service
  behavior unless a typed result is required for partial failure.
- Keep writes inside `PersistenceStore.inTransaction(...)`.
- No card numbers, CVV, or raw passwords stored.
- CSS colors stay in token classes/templates.
- No random/simulated operational metrics or notifications.

## 7. Testing
- Each task: failing service + persistence tests first, then implementation,
  then UI integration test.
- Suggested: `NotificationServiceTest`, `UserPersistenceTest` (prefs),
  `ReservationServiceTest`, `GaragePersistenceTest` (reservations/duty),
  `DutyServiceTest`, `BulkTicketUpdateTest`.
- Final: one end-to-end reserve → notify → claim → pay → duty → snapshot
  test + SQLite restart survival; `mvn clean test`; `mvn javafx:run`
  manual check with admin/attendant/customer.

## 8. Rollout order
1. Notifications backbone + `NotificationCenter`/`SettingsView` wiring.
2. Reservations end-to-end on backbone.
3. Duty/shift on backbone.
4. Bulk-safe tickets (publishes outcome toast, no new persistence).
5. Integration verification + docs.
