# ParkingOS

[![CI](https://github.com/alaa157/parking-management-system-javafx/actions/workflows/ci.yml/badge.svg)](https://github.com/alaa157/parking-management-system-javafx/actions/workflows/ci.yml)
![Java 21](https://img.shields.io/badge/Java-21-blue)
![JavaFX 21](https://img.shields.io/badge/JavaFX-21-teal)
![SQLite](https://img.shields.io/badge/SQLite-WAL-green)
![Maven](https://img.shields.io/badge/Build-Maven-orange)

Multi-garage parking management for the desktop. ParkingOS gives
**administrators**, **attendants**, and **customers** one workflow for garage
operations, vehicle entry, ticketing, payments, reservations, notifications,
and operational reporting — backed by an embedded SQLite store with versioned
migrations.

## Features

- **Role-aware shell** — dedicated navigation for administrators, attendants,
  and customers, with global search (`Ctrl/Cmd+K`) and per-garage scope picker.
- **Multi-garage operations** — each garage owns its capacity, tickets,
  payments, reservations, and reports; per-user garage access with an archived
  state for historical facilities.
- **Ticket lifecycle** — vehicle entry with spot allocation, exit with fee
  calculation, bulk updates, cancellations, and refunds.
- **Payments** — cash, card, and wallet flows with receipts, tax handling, and
  persisted `PAYMENT_SUCCESS` notifications.
- **Reservations** — time-boxed spot holds with atomic claim-and-park and lazy
  expiry sweeps.
- **Attendant duty** — duty sessions with shift summaries attributing
  transactions and revenue.
- **Reporting** — operations snapshots, revenue analytics, scheduled reports,
  and branded PDF/CSV export.

## Quick start

Requirements: **JDK 21+**, **Maven 3.9+**. JavaFX resolves via Maven — no
separate install needed.

```bash
git clone git@github.com:alaa157/parking-management-system-javafx.git
cd parking-management-system-javafx
mvn javafx:run
```

First launch with an empty database opens the administrator setup screen.
For local development with demo fixtures:

```bash
mvn -Dparkingos.demo=true javafx:run
```

## Configuration

| Property | Default | Purpose |
|---|---|---|
| `parkingos.database` | `~/.parkingos/parking.db` | SQLite database path |
| `parkingos.settings.file` | `~/.parkingos/settings.properties` | Rates, tax, holds, currency |
| `parkingos.reports.directory` | app default | Report export directory |
| `parkingos.demo` | `false` | Seed demo users and fixtures |
| `parkingos.reduceMotion` | `false` | Disable UI animations |

## Project structure

```text
src/main/java/com/parking/
├── config/       Application configuration and persistence setup
├── gui/          JavaFX views, shell, theming, and navigation
├── model/        Domain entities and payment implementations
├── enums/        Ticket, payment, spot, and reservation states
├── exceptions/   Domain and authorization failures
├── security/     Authentication and access-control helpers
├── services/     Garage, ticket, payment, reservation, duty, reporting use cases
├── persistence/  SQLite store, schema versions, and migrations
└── util/         Shared helpers (money, formatting)

src/main/resources/   CSS themes (generated), icons
src/test/java/        Unit, service, persistence, and UI characterization tests
docs/                 Guides, records, and generated diagrams
.agents/skills/       Shared AI-agent skills
```

## Documentation

| Doc | Contents |
|---|---|
| `docs/ARCHITECTURE.md` | Container view, startup sequence, service catalog, persistence |
| `docs/DATA_MODEL.md` | Entity-relationship diagram, ownership rules, schema tables |
| `docs/LIFECYCLE.md` | Ticket, reservation, spot, and payment state machines |
| `docs/FLOWS.md` | Entry, payment-to-exit, reservation, and duty sequences |
| `docs/AUTHORIZATION.md` | Operational-access decision and per-service enforcement |
| `docs/OPERATIONS.md` | Deploy, backup/restore, migrations, troubleshooting |
| `docs/USER_GUIDES.md` | Customer, attendant, and administrator click-paths |
| `docs/TESTING.md` | Test tiers, commands, and conventions |
| `docs/CONTRIBUTING.md` | Boundaries, theming contract, and workflow |
| `docs/ADRs.md` | Architecture decision records |
| `CONTEXT.md` | Ubiquitous language for garages, access, and tickets |

Diagrams are [D2](https://d2lang.com) sources in `docs/diagrams/` with rendered
SVGs — regenerate with `scripts/render_diagrams.sh`.

## Data and backups

Close the app before copying the database together with its `-wal` and `-shm`
companions. For a consistent online backup:

```bash
sqlite3 "$HOME/.parkingos/parking.db" ".backup '$HOME/.parkingos/parking.db.backup'"
```

Startup migrations are versioned and validated — a failed migration aborts
startup instead of deleting or resetting data. Full procedures:
`docs/OPERATIONS.md`.

## Testing

```bash
mvn test                          # full suite (headless, no display needed)
mvn -Dtest=TicketServiceTest test  # single class
mvn generate-resources            # regenerate themed CSS from design tokens
```

## Security

- No predictable demo accounts — accounts are created via setup, registration,
  or admin provisioning; demo fixtures require explicit opt-in.
- Passwords are stored as hashes; payment flows never persist card numbers,
  CVV values, or raw passwords.
- Keep the SQLite database and backup files private.

## Contributing

See `docs/CONTRIBUTING.md` for the ubiquitous language, module boundaries,
theming contract, and commit conventions. Contributors run `mvn test` and follow
conventional commits (`feat:`, `fix:`, `docs:`, `test:`, `chore:`).
