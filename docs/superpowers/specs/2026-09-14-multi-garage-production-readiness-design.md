# Multi-Garage Production Readiness Design

**Status:** Approved design

**Date:** 2026-09-14

## Goal

Improve the existing Parking JavaFX application into a production-ready,
multi-garage desktop product without rewriting the domain, persistence, or
JavaFX application from scratch.

The system must allow users to work across multiple garages while keeping
tickets, parking operations, payments, reservations, reports, and analytics
scoped to the garage where the operation occurred. A vehicle may have at most
one active parking session across the entire system.

## Product decisions

- Administrators are global and can see and manage every garage.
- Users may be assigned to multiple garages through explicit access records.
- The active garage is a UI context, not a security boundary by itself; every
  service operation must validate the actor's access to the target garage.
- Every ticket belongs to exactly one garage.
- Every payment belongs to exactly one ticket and must have the same garage as
  that ticket.
- Reservations belong to a garage and are subject to the same user/garage
  access checks as parking operations.
- A vehicle cannot be parked in two garages simultaneously.
- Existing records must be preserved by migrating them into one default garage.
- Password recovery is removed from the product. Authenticated password change
  remains available through permitted settings/admin workflows.
- Linux is the first supported release platform. The Java code and business
  behavior remain cross-platform; packaging and native smoke tests are added
  for other operating systems later.

## Current-system constraints

The implementation keeps the current Java 21, JavaFX, SQLite, Maven, service,
model, and `PersistenceStore` approach. It improves boundaries incrementally:
large UI classes may be split where a change naturally requires it, but there
is no wholesale rewrite or framework migration.

The current baseline must be made green before feature work proceeds. At the
time of this design, `mvn test` runs 162 tests and has one error in
`AppTest.testReservationExpiryAndAvailability`: a reservation holder ID is
written to `parking_spots` without an existing `users` row.

## Domain model

### Garage

A garage is a durable operational boundary with:

- stable ID;
- name and address;
- number of levels;
- opening/closing state;
- base hourly rate and currency;
- free-parking, reservation-hold, and maximum-parking policies;
- active/archived lifecycle state;
- created and updated timestamps.

Existing garage configuration values become the first default garage during
migration. New garages receive their own spots and policies and do not inherit
operational records from another garage.

### User access

Users and garages have a many-to-many relationship. An access record contains
the user ID, garage ID, access role/capability, active state, and audit
timestamps. The existing global user role remains the primary authorization
role; garage access is an additional scope check.

An administrator can manage garage assignments. Attendants and customers can
only use garages to which they have active access. A user may have access to
multiple garages and may switch the active garage in the UI.

### Tickets and payments

Tickets contain an immutable `garage_id`. The garage is assigned when the
ticket is created and cannot change during its lifecycle. Payments derive the
garage from their ticket and must reject mismatched garage references.

Ticket and payment queries must support:

- one garage;
- a selected set of garages;
- all garages for global administrators;
- time range, status, vehicle, customer, and ticket filters.

Administrative tables, receipts, PDFs, dashboards, and exports must display
the garage name and ID wherever a ticket or payment is shown.

### Global active-vehicle invariant

The system must reject a parking attempt when the vehicle has any active ticket
in any garage. The active states are the states that represent a vehicle still
inside a garage, including `CREATED`, `ACTIVE`, and `AWAITING_PAYMENT` where
applicable to the existing lifecycle.

The rejection result must identify the existing garage, ticket ID, spot, and
entry time. The service check and a SQLite partial unique index both enforce
the invariant. Parking, exit, payment closeout, and reservation transitions
must run transactionally so the domain objects and database cannot silently
diverge.

## User experience

### Garage context

Authenticated users with more than one garage access see a garage selector in
the application shell. Changing it refreshes all garage-scoped screens and
clears stale selections. A single-garage user does not need to interact with a
selector.

Global administrators get an `All garages` option for cross-garage analytics,
ticket search, payment history, and audit review. Operational actions that
require one physical garage must require a concrete garage selection.

### Admin garage management

Administrators can:

- create a garage with validated attributes;
- edit its name, address, levels, rates, currency, and policies;
- add, edit, archive, and reopen garage parking spots;
- assign and remove user access;
- view garage health, current occupancy, open tickets, and payment totals;
- filter tickets and payments by garage.

Archiving a garage does not delete history. An archived garage cannot receive
new parking sessions or reservations until reopened.

### Error and state handling

All core screens must provide explicit loading, empty, success, and failure
states. Database, validation, authorization, and operational errors must be
converted into user-readable messages with a useful next action. Production
code must not use `System.out` as its error-reporting mechanism.

The login screen no longer displays or handles password recovery. The product
must not contain a dead link or message claiming that recovery is unavailable.

## Persistence and migration

The migration must be forward-only, validated, and non-destructive:

1. Create the garage table and insert the existing configured garage as the
   default garage.
2. Create the user-garage access table and grant existing users access to the
   default garage.
3. Add and backfill `garage_id` on tickets using the default garage.
4. Add and backfill `garage_id` on payments using each payment's ticket garage.
5. Validate all foreign keys, non-null values, and ticket/payment garage
   consistency.
6. Create indexes for garage, status, time, and active-vehicle queries.
7. Refuse startup with a diagnostic message if migration or integrity checks
   fail; never reset or delete user data automatically.

SQLite remains the persistence engine. The existing backup and recovery
workflow remains supported and must be updated to mention the new schema.

## Architecture

The existing services remain the business boundary. New garage and access
operations should be introduced as focused services or focused service methods,
not embedded in JavaFX event handlers.

The JavaFX shell should progressively separate:

- application bootstrap and database initialization;
- authenticated session and active-garage context;
- navigation and role-aware route selection;
- garage management views;
- parking, ticket, payment, and reporting views.

The split is incremental and driven by touched flows. Existing reusable
components and design tokens remain the visual foundation.

## Security and authorization

- Global admins can view all garages and manage access.
- Every garage-scoped operation checks actor identity, role, garage access, and
  garage lifecycle state.
- Customers can see only their own tickets/payments in garages they can use.
- Attendants can operate only in assigned, active garages.
- Admin actions and garage assignment changes are audited.
- Seed accounts and predictable passwords are removed from normal production
  startup; demo data is available only through an explicit development mode or
  fixture.
- Passwords remain hashed with the existing password hasher.

## Quality and release requirements

Before release:

- all unit, persistence, service, and UI tests pass;
- migration tests cover the existing schema and malformed data;
- multi-garage authorization and filtering tests pass;
- global active-vehicle tests cover two garages;
- payment/ticket garage consistency is tested;
- Linux packaging produces a runnable installer or distributable image;
- database backup and restore are verified;
- application versioning, release notes, and diagnostics are documented.

## Acceptance criteria

The design is complete when an implementation can demonstrate all of the
following:

1. An admin creates two garages with different attributes and parking spots.
2. A user receives access to both garages and can switch between them.
3. A ticket created in Garage A never appears as an unscoped operational ticket
   in Garage B.
4. An admin can search all tickets and see each ticket's garage clearly.
5. A payment for a Garage A ticket cannot be recorded against Garage B.
6. A vehicle parked in Garage A is rejected when someone attempts to park it in
   Garage B, with the existing ticket and garage identified.
7. Exiting the vehicle clears the global active-vehicle restriction.
8. Archiving a garage blocks new operations but preserves its history.
9. Password recovery is absent from the login UI and code path.
10. Existing database data survives migration into the default garage.
11. `mvn test` passes, and the Linux production package starts successfully.
