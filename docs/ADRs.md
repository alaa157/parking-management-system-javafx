# ParkingOS Architecture Decision Records

## ADR-1: Multi-garage scoping with per-garage ownership

- Status: Accepted.
- Context: The product must run several physical facilities from one desktop
  app while keeping each garage's tickets, payments, reservations, and reports
  isolated (`CONTEXT.md`: Garage).
- Decision: Every operational record carries `garage_id` (FK → `garages`,
  RESTRICT); access is granted per user per garage via `GarageAccess`
  (`GarageService`, `GarageContext`). Global admins bypass per-garage checks.
- Consequences: All service reads/writes take an actor + garage id; cross-garage
  leaks become authorization bugs, covered by `MultiGarage*Test`. See
  `ARCHITECTURE.md`, `AUTHORIZATION.md`, `DATA_MODEL.md`.

## ADR-2: SQLite + WAL embedded store, versioned migrations

- Status: Accepted.
- Context: Single-workstation deployment, no DBA, must survive power loss and
  stay backup-friendly.
- Decision: Embedded SQLite (`PersistenceStore`, schema v11) with
  `journal_mode=WAL`, `busy_timeout=5000`, `foreign_keys=ON`; idempotent
  migrations + `validateSchema`; failed migration aborts startup, never wipes data.
- Consequences: Simple backup story (`.backup` / cold copy, see
  `OPERATIONS.md`); no concurrent-writer scaling; tests run on `:memory:`.
  Downgrades unsupported — newer DBs are rejected.

## ADR-3: Persisted, recipient-scoped notifications

- Status: Accepted.
- Context: Entry/exit, payment, reservation, and duty events must reach the
  right user across sessions, not vanish with a toast.
- Decision: `NotificationService.publish` writes to `notifications` filtered by
  `notification_prefs`; UI toasts/`NotificationCenter` are views over the store
  (`NotificationBackboneEndToEndTest`).
- Consequences: Notification loss is a data bug with a test; preference schema
  must evolve compatibly.

## ADR-4: No default credentials; hashed passwords

- Status: Accepted.
- Context: Predictable demo accounts ship to production by accident.
- Decision: No seeded users by default (`AppServices` seeds only with
  `-Dparkingos.demo=true`); first launch forces admin creation
  (`FirstRunSetupView`); passwords stored as hashes (`PasswordHasher`,
  `security/`); card numbers/CVV never persisted (`README.md` security notes).
- Consequences: Fresh installs require onboarding; lockout handling
  (`failedLoginAttempts`/`lockedUntil`) is part of ops (see `OPERATIONS.md`).

## ADR-5: Service-layer transactions couple state changes

- Status: Accepted.
- Context: Entry, payment, and claim-and-park each mutate several aggregates
  (spot + vehicle + ticket + payment + notifications) that must not half-apply.
- Decision: Services wrap multi-aggregate mutations in
  `persistence.inTransaction` with compensating rollback (e.g.
  `parkVehicle:113-122`, `processPayment:147-165`, `claimForEntry:174-191`
  with hold restore on failure).
- Consequences: Flows are atomic but hold write txs open across validations —
  keep tx bodies short; GUI must not start transactions.
