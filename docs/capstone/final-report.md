# ParkingOS — Final Report

## 1. Executive summary

The team designed and built ParkingOS, a multi-garage parking-management
desktop application (Java 21, JavaFX, SQLite), as a DEPI capstone under mentor
Mina Yonan. The system covers ticketing, cash/card/wallet payments,
reservations, attendant duty, notifications, and reporting across isolated
garages with role-based access. All 23 functional requirements trace to
passing tests: **238 tests in 38 classes, zero failures**, running headless in
CI on every push to `main`.

## 2. Methodology

Work followed the phased plan in `project-proposal.md`: foundation (domain,
schema, access), core flows (entry, fees, payments), reservations and duty,
reporting and hardening, then verification and packaging. Each phase ended
with a runnable demo. The service layer carries transactions and authorization
so the UI stays thin; state machines are enforced in code, not convention
(`docs/LIFECYCLE.md`). Decisions are recorded as ADRs (`docs/ADRs.md`).

## 3. Results

- **Ticketing:** entry rejects duplicate sessions, allocates compatible spots
  atomically, and enforces all nine transitions plus terminal states
  (`TicketServiceTest`, `ParkingServiceFlowTest`).
- **Payments:** 32 flow tests cover fee math, method validation, atomic
  pay/close/release, failure persistence, and 14-day full refunds.
- **Multi-garage isolation:** per-record `garage_id` ownership plus
  deny-by-default access, verified by dedicated multi-garage tests.
- **Reservations:** holds, atomic claim-and-park with hold restore on failure,
  and eager plus lazy expiry.
- **Operations:** duty sessions with revenue-attributed summaries, snapshots,
  scheduled reports, branded PDF/CSV export.
- **Quality:** 238/238 green; docs set (10 technical guides + 7 capstone
  documents); D2 diagrams generated from sources.

## 4. Challenges and resolutions

- **Atomic multi-aggregate writes:** entry and payment touch up to five
  aggregates. Resolution: service-owned `inTransaction` blocks with
  compensating rollback, each covered by a failure-path test.
- **Cross-garage leaks:** early reads could span facilities. Resolution:
  mandatory actor + garage id on scoped queries, `RESTRICT` FKs, and
  regression tests per garage pair behavior.
- **Schema evolution without data loss:** Resolution: idempotent migrations,
  `validateSchema` before stamping, abort-on-failure startup, and a backup
  runbook (`docs/OPERATIONS.md`).
- **Card payments without a gateway:** Resolution: `CardAuthorizationProvider`
  interface with a local implementation, keeping the seam open for a live
  provider later.

## 5. Future work

Live payment gateway behind `CardAuthorizationProvider`; license-plate camera
integration at entry lanes; multi-workstation sync replacing single-writer
SQLite; mobile companion for customer reservations and wallet; statement-level
audit exports for accountants.

## 6. Conclusion

ParkingOS meets its proposal success criteria: every requirement is
implemented, traced, and tested; the demo scenario runs end to end; and the
defense package (proposal, SRS, SDS, test plan, manual, report, slides) is
complete. The system is deployable today on a single workstation with no
server, and its seams (auth provider, reports directory, settings) leave room
to grow.
