# ParkingOS Operations Runbook

Deploy, backup, restore, and troubleshoot. See `ARCHITECTURE.md` (persistence)
and `README.md` (quick start) for background.

## Runtime locations

| What | Default | Override |
|---|---|---|
| SQLite database | `~/.parkingos/parking.db` (+ `-wal`/`-shm` companions) | `-Dparkingos.database=<path>` (`PersistenceStore.java:1271`) |
| Settings | `~/.parkingos/settings.properties` (schema v1, `AppConfig.java:19`) | `-Dparkingos.settings.file=<path>` (`AppConfig.java:240`) |
| Reports | `AppConfig.reportsDirectory()` (`AppConfig.java:249`) | `-Dparkingos.reports.directory=<path>` (`:250`) |
| Demo fixtures | off | `-Dparkingos.demo=true` (`AppServices.java:81`, `README.md:39`) |
| Reduced motion | off | `-Dparkingos.reduceMotion` (`ThemeManager.java:27`) |

`AppConfig` defaults (`AppConfig.java:21-26`): tax 10%, base rate 5.0,
reservation hold 5 min, max park 48 h, free minutes 0, currency USD.
Saves are atomic (temp + `ATOMIC_MOVE`, `:83-127`).

## Backup

SQLite runs with `journal_mode=WAL` + `busy_timeout=5000`
(`PersistenceStore.java:87-91`), so the directory holds `parking.db`,
`parking.db-wal`, `parking.db-shm`.

- Cold copy: **close the app first**, then copy all three files together.
- Online backup (preferred): `sqlite3 "$HOME/.parkingos/parking.db" ".backup
  '$HOME/.parkingos/parking.db.backup'"` (`README.md:54-57`).
- Settings: copy `settings.properties` alongside (small text file).

## Restore

1. Stop the app.
2. Replace `parking.db` (and delete stale `-wal`/`-shm`, or restore all three
   from the same cold copy).
3. Restore `settings.properties` if config changed.
4. Start the app; watch logs for migration output.

## Migrations

- DB schema is versioned (`CURRENT_SCHEMA_VERSION=11`, `schema_version` table).
  Startup runs idempotent `CREATE TABLE/INDEX IF NOT EXISTS`, garage
  backfills, the FK-hardening rebuild, and `validateSchema` before stamping v11
  (`PersistenceStore.java:96-254`).
- A **failed migration aborts startup** instead of resetting data
  (`README.md:59-60`). A DB from a newer schema version is rejected (`:187-189`).
- On abort: restore from backup (above), never delete the DB to "fix" startup.
  Forward-fix with a new migration; there is no downgrade path.

## Troubleshooting

| Symptom | Likely cause / fix |
|---|---|
| `SQLITE_BUSY` / slow writes | Another writer holds the DB; `busy_timeout` covers brief contention — check for a second app instance or an open `sqlite3` write tx |
| Login loop / locked account | `failedLoginAttempts`/`lockedUntil` (`User.java:23-24`); wait out the lock or have an admin reset via User Management |
| Empty garage on first start | Normal: `restoreGarage` builds 3×20 spots when none persisted (`AppServices.java:100-188`) |
| Spots show stale reservations | Holds expire lazily on next spot search/entry (`ReservationService:56,160`; `releaseExpiredReservations:482`); run any entry/search to sweep |
| Theming looks wrong | Regenerate: `mvn generate-resources` (`scripts/generate_palette_css.py`); never hand-edit generated `parkingos*.css` (`docs/theming.md`) |
| `mvn javafx:run` fails on display-less hosts | JavaFX needs a display; use Xvfb or run headless tests only (`mvn test` needs no display — GUI tests are plain JUnit) |
