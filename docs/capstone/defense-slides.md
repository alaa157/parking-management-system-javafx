# ParkingOS — Defense Slides Outline

14 slides, 20 minutes + questions. Speaker notes under each title.

1. **Title** — ParkingOS, team (Alaa Adel, Ebrahim Sadek, Mahmoud Hewidy,
   Mohamed Hemdan), DEPI, mentor Mina Yonan.
2. **The problem** — paper tickets leak revenue; multi-site records cannot
   move; staff access is all-or-nothing.
3. **Our answer** — one desktop app per workstation running every garage:
   tickets, payments, reservations, duty, reports.
4. **Live architecture** — show `docs/diagrams/architecture.svg`; narrate GUI
   → services → SQLite in one sentence per layer.
5. **Data ownership** — show `docs/diagrams/erd.svg`; one ticket, one garage;
   access grants isolate sites; archived garages keep history.
6. **Ticket lifecycle demo** — park → exit → pay on the demo build; point at
   `docs/diagrams/lifecycle.svg` for the enforced transitions.
7. **Money integrity** — atomic pay/close/release; failure rolls back;
   receipts and persisted notifications.
8. **Reservations and duty** — hold → claim-and-park; duty start → shift
   summary with attributed revenue.
9. **Access control** — show `docs/diagrams/authz.svg`; deny by default,
   admin bypass, revocation applies at once.
10. **Test evidence** — 238 tests, 38 classes, 0 failures; traceability matrix
    maps every FR to test classes (`test-plan.md`).
11. **Operations** — one-command launch, WAL backups, abort-on-failed-migration.
12. **Challenges** — atomic writes, cross-garage leaks, schema evolution,
    gateway seam; one resolution each.
13. **Future work** — live gateway, plate cameras, multi-workstation sync,
    mobile companion.
14. **Thank you / demo** — recap one line per objective; open the app for
    questions. Backup demo path if the projector fails: narrate UC-1 from
    `srs.md` against `docs/FLOWS.md`.
