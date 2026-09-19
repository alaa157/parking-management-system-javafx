# ParkingOS — Capstone Project

**Program:** Digital Egypt Pioneers Initiative (DEPI), Java desktop application track
**Project:** ParkingOS — multi-garage parking management system
**Mentor:** Mina Yonan

## Team

| Member | Responsibility |
|---|---|
| Alaa Adel | Team lead, backend services and system architecture |
| Ebrahim Sadek | Frontend, JavaFX views and theming |
| Mahmoud Hewidy | Database, persistence layer, and migrations |
| Mohamed Hemdan | Quality assurance, testing, and documentation |

## Abstract

Parking facilities lose revenue to manual ticketing, unenforced overstays, and
 fragmented records across sites. ParkingOS is a desktop application that runs
multiple parking garages from one workstation: it tracks vehicle entry and
exit, enforces ticket state transitions, processes cash, card, and wallet
payments, holds spots through reservations, records attendant duty, and reports
on revenue. Role-based access separates customers, attendants, and
administrators, and per-garage access grants isolate each facility's data. The
system stores everything in an embedded SQLite database with versioned
migrations, so it deploys with no server and no database administrator. The
suite holds 238 automated tests across 38 classes with zero failures.

## Objectives

1. Replace paper tickets with a ticket lifecycle the software enforces
   (`CREATED` to `CLOSED`, with cancellation and refund paths).
2. Isolate multiple garages in one installation through per-record ownership
   and per-user access grants.
3. Process cash, card, and wallet payments atomically with receipts and
   persisted notifications.
4. Reduce no-show waste with time-boxed reservation holds and atomic
   claim-and-park.
5. Attribute revenue to attendant shifts through duty sessions and summaries.
6. Ship with engineering evidence: requirements traceability, design records,
   and a green test suite.

## Deliverables in this folder

| Document | Contents |
|---|---|
| `project-proposal.md` | Problem, scope, stakeholders, risks, phased plan |
| `srs.md` | Software Requirements Specification (FR/NFR, use cases) |
| `sds.md` | Software Design Specification (architecture, data design, patterns) |
| `test-plan.md` | Strategy, requirements traceability matrix, real results |
| `user-manual.md` | Installation and role-based operating guide |
| `final-report.md` | Methodology, results, challenges, future work, conclusion |
| `defense-slides.md` | Defense presentation outline |

Technical references live one level up in `docs/` (`ARCHITECTURE.md`,
`DATA_MODEL.md`, `LIFECYCLE.md`, `FLOWS.md`, `AUTHORIZATION.md`,
`OPERATIONS.md`, `TESTING.md`, `ADRs.md`). The capstone documents point at
them instead of duplicating them.
