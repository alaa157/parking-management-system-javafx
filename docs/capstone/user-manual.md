# ParkingOS — User Manual

## 1. Installation

You need JDK 21 or newer and Maven 3.9 or newer. No database server and no
JavaFX SDK install: Maven resolves both.

```bash
git clone git@github.com:alaa157/parking-management-system-javafx.git
cd parking-management-system-javafx
mvn javafx:run
```

The first launch opens administrator setup because the database is empty.
Create the admin account and sign in. To explore with demo data instead:

```bash
mvn -Dparkingos.demo=true javafx:run
```

System properties you can set: `parkingos.database` (default
`~/.parkingos/parking.db`), `parkingos.settings.file`, `parkingos.reports.directory`.

## 2. Signing in and choosing scope

Sign in with your username and password. The header shows a garage picker with
every garage you hold access to; administrators also see **All garages**.
Press `Ctrl/Cmd+K` anywhere for the command palette and jump to any screen.

## 3. Customer tasks

- **Register:** from the login screen, open registration, fill your details,
  and create the account.
- **Add a vehicle:** **My Vehicles** → **Add vehicle** → save.
- **Park:** **Active Parking** → level tab → free tile → **Park Here** →
  choose the vehicle → **Park Vehicle**.
- **Reserve:** select a free tile → **Reserve Spot**; claim it at entry before
  the hold expires (default 5 minutes).
- **Pay and exit:** **Tickets** → select the ticket → **Exit** → pick
  **CARD**, **CASH**, or **WALLET** → **Pay**. Keep the receipt.
- **Wallet:** **Wallet** → **Add balance**; history lists every payment.
- **Profile:** **Profile** edits details, password, and notification preferences.

## 4. Attendant tasks

- **Start duty:** **Duty** → enter shift and zone → **Start duty**.
- **Park a customer:** **Vehicle Entry** → tile → **Park Here** → select the
  customer's vehicle → **Park Vehicle**.
- **Check out and charge:** **Vehicle Exit** → ticket → **Exit** → collect on
  the payment tabs → **Pay**.
- **End shift:** **Shift** → review history → **End duty**; hand the summary
  to the next attendant.

## 5. Administrator tasks

- **Users:** **Users** → Add, Edit, Activate/Deactivate, Delete; export CSV/PDF.
- **Garages:** **Garages** → Add, Edit, Archive/Reopen; **Manage access** opens
  grants.
- **Access:** **Garage Access** → pick user, garage, role → **Grant** or
  **Revoke**. Revocation applies at once.
- **Analytics:** **Revenue & Analytics** → date range → Refresh; schedule
  recurring reports; **Export CSV/PDF**.
- **Maintenance:** **Maintenance** → put a free spot under maintenance with a
  reason, or resolve it back to service.
- **Refunds:** **Tickets** → refund section (full amount, within 14 days).
- **Settings:** **Parking Config** edits rates, tax, holds, and currency.

Task-level click paths with screen references: `docs/USER_GUIDES.md`.
Backup, restore, and troubleshooting: `docs/OPERATIONS.md`.
