# ParkingOS — Software Requirements Specification

## 1. Introduction

ParkingOS is a desktop parking-management application for operators running
one or more garages. This SRS defines what the system must do. Each
requirement carries an ID used by the traceability matrix in `test-plan.md`.
Terms follow `CONTEXT.md` (Garage, Garage access, Ticket, Active parking
session).

## 2. Functional requirements

### 2.1 Authentication and users

- **FR-1:** The system shall force creation of the initial administrator
  account on first launch when no users exist.
- **FR-2:** The system shall authenticate users by username and password, lock
  accounts after repeated failures, and reject inactive accounts.
- **FR-3:** Customers shall self-register; admins shall create, edit,
  activate, deactivate, and delete users.
- **FR-4:** Passwords shall be stored as hashes; the system shall ship no
  default credentials.

### 2.2 Garages and access

- **FR-5:** Admins shall create, configure, archive, and reopen garages.
- **FR-6:** Admins shall grant and revoke per-user, per-garage access; revoked
  users shall lose the garage immediately.
- **FR-7:** Non-admin users shall operate only inside garages they hold active
  access to; global admins shall hold scope over every open garage.
- **FR-8:** Archived garages shall reject new operational work while keeping
  history queryable by authorized admins.

### 2.3 Ticketing and parking

- **FR-9:** The system shall create a ticket on vehicle entry after rejecting
  duplicate active sessions for the same vehicle.
- **FR-10:** The system shall allocate a free compatible spot and mark it
  occupied atomically with ticket creation.
- **FR-11:** The system shall enforce the ticket state machine
  (`CREATED → ACTIVE → AWAITING_PAYMENT → PAID → CLOSED`, with cancellation
  and refund transitions) and reject invalid transitions.
- **FR-12:** The system shall calculate fees from spot rate and parked
  duration plus configured tax, and support bulk ticket status updates.

### 2.4 Payments

- **FR-13:** The system shall process cash, card, and wallet payments against
  tickets awaiting payment.
- **FR-14:** Payment, ticket closure, and spot release shall commit atomically;
  any failure shall roll back all three.
- **FR-15:** The system shall issue receipts and persist payment notifications
  to the payer.
- **FR-16:** Admins shall refund completed payments in full within 14 days;
  refunded tickets shall move to `REFUNDED`.

### 2.5 Reservations

- **FR-17:** Users shall hold an available spot for a configured number of
  minutes; the spot shall show reserved while held.
- **FR-18:** Holders (or admins) shall claim a hold at entry, creating and
  activating the ticket in the same transaction as spot allocation.
- **FR-19:** Elapsed holds shall expire and free the spot, eagerly on a sweep
  and lazily on the next spot operation.

### 2.6 Duty and reporting

- **FR-20:** Attendants shall open and close duty sessions; only one open
  session per attendant is allowed.
- **FR-21:** Admins shall view shift summaries attributing transactions and
  revenue to attendant and time window.
- **FR-22:** The system shall produce operations snapshots, revenue analytics,
  scheduled reports, and CSV/PDF exports.

### 2.7 Notifications

- **FR-23:** Entry, payment, reservation, and duty events shall persist as
  recipient-scoped notifications honoring user preferences.

## 3. Non-functional requirements

- **NFR-1 (Deployability):** One-command launch (`mvn javafx:run`) with no
  server and no DBA; embedded SQLite only.
- **NFR-2 (Durability):** WAL journaling with busy timeout; versioned
  migrations that abort startup rather than destroy data; documented backup
  and restore.
- **NFR-3 (Usability):** Role-shaped navigation; garage scope picker; global
  command palette; light and dark themes with reduced-motion support.
- **NFR-4 (Security):** Deny-by-default garage access; hashed credentials;
  no persisted card numbers or CVVs; lockout on repeated failures.
- **NFR-5 (Testability):** Headless automated suite; every functional
  requirement traces to at least one passing test.

## 4. Use cases

- **UC-1 Customer parks and pays:** register → add vehicle → park → exit →
  pay → receipt. Covers FR-2, FR-3, FR-9–FR-15.
- **UC-2 Attendant shift:** start duty → process entries, exits, payments →
  end duty → handoff. Covers FR-7, FR-9–FR-15, FR-20.
- **UC-3 Admin onboards a site:** create garage → grant attendant access →
  verify isolation → archive at end of life. Covers FR-5–FR-8.
- **UC-4 Reservation holder claims:** hold → claim at entry → ticket active;
  alternate path: hold lapses → spot frees. Covers FR-17–FR-19.
- **UC-5 Admin refunds and reports:** refund a payment → schedule revenue
  report → export PDF. Covers FR-16, FR-21, FR-22.
