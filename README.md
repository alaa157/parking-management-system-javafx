# ParkingOS

ParkingOS is a desktop parking-management application built with Java 21,
JavaFX, and SQLite. It gives administrators, attendants, and customers a
single workflow for garage operations, vehicle entry, ticketing, payments,
reservations, notifications, and operational reporting.

## Highlights

- Role-aware JavaFX application shell for administrators, attendants, and customers.
- Multi-garage management with garage-scoped access control.
- Vehicle entry, ticket lifecycle management, and bulk ticket operations.
- Reservation holds with atomic claim-and-park behavior and expiry handling.
- Attendant duty sessions, shift summaries, and garage operations snapshots.
- Cash, card, and wallet payment flows with persisted notification events.
- SQLite persistence with versioned migrations and backup-friendly WAL support.
- Automated unit, service, persistence, and UI characterization tests.

## Requirements

- JDK 21 or newer
- Maven 3.9 or newer

JavaFX dependencies are resolved by Maven; no separate JavaFX installation is
required.

## Quick start

```bash
git clone git@github.com:alaa157/parking-management-system-javafx.git
cd parking-management-system-javafx
mvn javafx:run
```

On first launch, ParkingOS opens a setup screen for the initial administrator.
For local development, opt-in demo fixtures can be enabled explicitly:

```bash
mvn -Dparkingos.demo=true javafx:run
```

Run the full test suite with:

```bash
mvn test
```

## Data and backups

The database path is read from the `parkingos.database` system property. When
it is not set, the application uses `~/.parkingos/parking.db`. Close the app
before copying the database and its `-wal` and `-shm` companions. For a
consistent online backup, use SQLite's backup command:

```bash
sqlite3 "$HOME/.parkingos/parking.db" ".backup '$HOME/.parkingos/parking.db.backup'"
```

Startup migrations are versioned and validated. A failed migration aborts
startup instead of deleting or resetting application data. See
`docs/OPERATIONS.md` for the full backup/restore runbook.

## Project structure

```text
src/main/java/com/parking/
├── config/       Application configuration and persistence setup
├── gui/          JavaFX views, components, theming, and navigation
├── model/        Domain entities and payment implementations
├── security/     Authentication and access-control helpers
└── services/     Garage, ticket, payment, reservation, duty, and reporting use cases
```

CSS resources and icons live under `src/main/resources`. Tests mirror the
domain, service, persistence, and GUI boundaries under `src/test/java`.

## Design notes

The application uses service-layer transactions to keep garage state,
reservations, tickets, payments, and notifications consistent. Notifications
are persisted and recipient-scoped rather than simulated in the UI. See
`CONTEXT.md` and `docs/` for the domain model, design notes, and supporting
project artifacts.

## Documentation

| Doc | Contents |
|---|---|
| `docs/ARCHITECTURE.md` | Container view, startup sequence, service catalog, persistence |
| `docs/DATA_MODEL.md` | ER diagram, ownership rules, schema v11 tables |
| `docs/LIFECYCLE.md` | Ticket/reservation/spot/payment state machines |
| `docs/FLOWS.md` | Entry, payment-to-exit, reservation, duty sequences |
| `docs/AUTHORIZATION.md` | Operational-access decision and per-service enforcement |
| `docs/OPERATIONS.md` | Deploy, backup/restore, migrations, troubleshooting |
| `docs/USER_GUIDES.md` | Customer, attendant, and administrator click-paths |
| `docs/TESTING.md` | Test tiers, commands, and conventions |
| `docs/CONTRIBUTING.md` | Boundaries, theming contract, and workflow |
| `docs/ADRs.md` | Architecture decision records |

Diagrams are D2 sources in `docs/diagrams/` with rendered SVGs; regenerate
with `scripts/render_diagrams.sh` (requires `d2`). Shared agent skills live in
`.agents/skills/` (see `.agents/skills/SOURCES.md`).

## Security notes

ParkingOS does not ship predictable demo accounts by default. Passwords are
stored as hashes, and payment flows do not persist card numbers, CVV values, or
raw passwords. Keep the SQLite database and backup files private.
