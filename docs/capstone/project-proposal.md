# ParkingOS — Project Proposal

## 1. Problem statement

Small and mid-size parking operators track occupancy on paper or in
spreadsheets. That produces three measurable losses: revenue leaks from
unenforced overstays and informal fee handling, double-booked or phantom spots
because no single record owns a parking session, and unusable history because
each site keeps its own format. Multi-site operators feel this hardest, since
records cannot move between facilities and staff access cannot be scoped per site.

## 2. Proposed solution

ParkingOS, a desktop parking-management application (Java 21, JavaFX, SQLite)
that gives administrators, attendants, and customers one workflow per garage:

- Ticketed entry and exit with enforced state transitions and fee calculation.
- Cash, card, and wallet payments with receipts and persisted notifications.
- Time-boxed reservation holds with atomic claim-and-park.
- Attendant duty sessions with shift revenue summaries.
- Per-garage data isolation with per-user access grants and an archived state
  for retired facilities.
- Revenue analytics with CSV/PDF export and scheduled reports.

## 3. Stakeholders

| Stakeholder | Interest |
|---|---|
| Garage owners / administrators | Revenue visibility, staff control, per-site isolation |
| Parking attendants | Fast entry, exit, and payment handling per shift |
| Customers (drivers) | Vehicle registration, reservations, transparent billing, wallet |
| DEPI reviewers / mentor (Mina Yonan) | Requirements traceability, design rigor, working demo, test evidence |

## 4. Scope

**In scope:** desktop JavaFX client; multi-garage management; ticket, payment,
reservation, duty, notification, and reporting modules; embedded SQLite
persistence with migrations; role-based access; 238-test automated suite;
user manual and defense materials.

**Out of scope:** mobile clients; license-plate camera hardware; online payment
gateways (card authorization is local/simulated through
`CardAuthorizationProvider`); multi-workstation networking; cloud sync.

## 5. Risks and mitigations

| Risk | Likelihood | Mitigation |
|---|---|---|
| Scope creep across 7 modules | High | Phased plan below; each phase demos independently |
| Data loss on schema change | Medium | Versioned migrations that abort startup instead of wiping data; backup runbook |
| Payment logic defects (money handling) | Medium | Atomic service transactions; 32 payment flow tests plus refund guards |
| Unauthorized cross-garage access | Medium | Deny-by-default `GarageService.requireAccess`; multi-garage tests |
| JavaFX behavior differences across machines | Low | Headless-safe test suite; Xvfb fallback for display-less hosts |

## 6. Phased plan

1. **Foundation** — domain model, SQLite schema v11, garage lifecycle, access control.
2. **Core flows** — entry/exit ticketing, fee calculation, cash/card/wallet payments.
3. **Reservations and duty** — holds, claim-and-park, expiry sweeps, duty sessions.
4. **Reporting and hardening** — snapshots, analytics, scheduled reports, exports.
5. **Verification and packaging** — traceability, full suite green, docs, manual, defense.

## 7. Success criteria

- All functional requirements in `srs.md` trace to passing tests in
  `test-plan.md` (currently 238 tests, 0 failures).
- Demo scenario runs end to end: setup admin, add garage, park, pay, refund,
  reserve, close duty, export a report.
- Defense deliverables complete: proposal, SRS, SDS, test plan, user manual,
  final report, slides.
