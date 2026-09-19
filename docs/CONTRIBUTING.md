# Contributing to ParkingOS

## Setup

Requirements: JDK 21+, Maven 3.9+ (`README.md:19-25`). JavaFX comes via Maven.

```bash
git clone git@github.com:alaa157/parking-management-system-javafx.git
cd parking-management-system-javafx
mvn -Dparkingos.demo=true javafx:run   # demo fixtures for local dev
```

First launch with an empty DB opens the admin setup screen; demo mode seeds
users instead (`AppServices.java:81`).

## Ubiquitous language (`CONTEXT.md` — normative)

Use `Garage` (not site/branch/location), `Garage access`, `Operational access`,
`Archived garage`, `Active garage`, `Ticket` (not visit/booking),
`Active parking session`. PRs introducing banned terms will be asked to rename.

## Boundaries

- `gui/` renders and delegates; no SQL, no fee math in views.
- `services/` own transactions and authorization (`requireAccess`,
  `requireOperationalActor`); UI selection (`GarageContext`) never authorizes —
  see `AUTHORIZATION.md`.
- `model/` holds state transitions; invalid transitions throw
  `InvalidTicketStatusException` (or return `false` for entity-level no-ops) —
  see `LIFECYCLE.md`.
- `persistence/` is the only JDBC boundary; schema changes add a migration step
  and bump `CURRENT_SCHEMA_VERSION` — never delete user data on upgrade.

## Theming contract (`docs/theming.md`)

Edit `DesignTokens.java` / `LightDesignTokens.java` constants, then run
`mvn generate-resources`. Never hand-edit generated `parkingos*.css`, never add
raw hex/`rgba` to UI code or templates.

## Docs & diagrams

Behavioral docs live in `docs/`; diagrams are D2 sources in `docs/diagrams/`
rendered by `scripts/render_diagrams.sh` (install `d2` first). Commit `.d2` +
`.svg` together. The plan and acceptance gate: `docs/DOCS_AND_DIAGRAMS_PLAN.md`.

## Workflow

1. Branch from `main`; keep PRs focused (one concern).
2. Add/adjust tests per `TESTING.md`; run `mvn test`.
3. Conventional commits (`feat:`, `fix:`, `docs:`, `test:`, `chore:`).
4. No secrets, card numbers, CVVs, or passwords in code, tests, or fixtures
   (`README.md` security notes).
