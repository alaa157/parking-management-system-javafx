# ParkingOS User Guides

Role click-paths. Navigation map: `ParkingApplication.java:276-333`.
Auth: `LoginView` (`users.authenticateUser`), first-run setup creates the
initial `ADMIN` when no users exist (`ParkingApplication.java:134`,
`FirstRunSetupView.java:65`). Header garage picker: `GarageSelectorView`
(admins also get "All garages", `:31`); global search: `Ctrl/Cmd+K`
`CommandPalette`. See `AUTHORIZATION.md` for what each role may do.

## Customer

1. **Create account** — Login → register link → fill name/username/email/phone +
   password (strength meter) → Create account (`RegistrationView.java:97-148`).
2. **Add a vehicle** — My Vehicles → Add vehicle → save
   (`CustomerVehiclesView.java:56-79`).
3. **Park** — Active Parking (or Dashboard → Park a vehicle): pick a level tab,
   select an `AVAILABLE` tile → Park Here → choose vehicle → Park Vehicle
   (`OccupancyMapView.java:94-170`); or Reserve Spot first, then claim at entry
   (hold lasts `reservationHoldMinutes`, default 5).
4. **Pay & exit** — Tickets → select ticket → Exit (moves it to
   `AWAITING_PAYMENT`) → pick CARD/CASH/WALLET tab → Pay
   (`TicketPaymentView.java:766-1019`). Receipt via print/copy (`:421`
   `generateReceipt`).
5. **Wallet** — Wallet → Add balance → amount → confirm
   (`WalletView.java:46-59`); history shows `id/ticket/amount/status/time`.
6. **Profile** — Profile (Settings → Profile/Security/Notifications): edit
   details, change password, toggle notification types (`SettingsView.java:185-303`).

## Attendant

1. **Pick scope** — header garage picker → one garage (`GarageSelectorView:35-39`).
2. **Start duty** — Duty → Start duty with shift + zone (`DutyView.java:93-97`,
   `DutyService.start`); only one `OPEN` session at a time.
3. **Vehicle Entry** — Operations/Vehicle Entry: find spot → Park Here → select
   customer vehicle → Park Vehicle (`OccupancyMapView:120-170`).
4. **Vehicle Exit** — Tickets/Vehicle Exit: select `ACTIVE` ticket → Exit →
   collect payment on the CARD/CASH/WALLET tabs → Pay (`TicketPaymentView:766-1019`).
5. **Shift** — Shift tab: current session, history, handoff card
   (`DutyView.java:54-55,131`); End duty closes the session (`:107-111`).

## Administrator

1. **Users** — Users: table + search/filters → Add (role select) / Edit /
   Activate-Deactivate / Delete; exports to CSV/PDF
   (`UserManagementView.java:155-185,349-350,654-675`).
2. **Garages** — Garages: + Add garage / Edit / Archive-reopen / Manage access
   (`GarageManagementView.java:45-54,102-107`); archiving closes a garage to new
   work but keeps history (`GarageService.archiveGarage:43`).
3. **Garage access** — Garage Access: pick user + garage + role → Grant/Revoke
   (`GarageAccessView.java:57-67`); revoked users lose the garage immediately
   (`GarageContext.refresh`).
4. **Revenue & Analytics** — date range → Refresh; schedule recurring reports
   (every minute/hour, local/email) → Export CSV/PDF
   (`AnalyticsReportingView.java:300-347,590-595,742`).
5. **Maintenance** — Maintenance: Put under maintenance (pick `AVAILABLE` spot +
   reason) / Resolve (`MaintenanceView.java:41-51`); spot actions also live in
   Parking Spots for staff (`OccupancyMapView:108`).
6. **Refunds** — Tickets → refund section → `refundPayment` (full amount only,
   within 14 days, `Payment.java:97-107`; `TicketPaymentView:1155`).
7. **Settings** — Parking Config tab (admin-only): rates, tax, holds, currency
   (`SettingsView.java:362`); Appearance: theme swatches.
