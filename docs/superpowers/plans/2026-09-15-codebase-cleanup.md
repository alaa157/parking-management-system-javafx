# Parking JavaFX Codebase Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the JavaFX parking app maintainable by shrinking god classes, deleting duplicate/dead UI types, and putting code in the right package — without changing user-visible behavior.

**Architecture:** Treat cleanup as a series of behavior-preserving extractions behind existing seams (`LoginView`, `OperationsViewFactory`, `GarageUiScope`, `ToastManager`). `ParkingApplication` becomes a thin application host: start/stop, service wiring, scene ownership, and navigation callbacks. Views own their pages. Shared widgets live in `com.parking.gui.components`. Domain/persistence stay put except for logging and one circular-dependency constructor fix.

**Tech Stack:** Java 21, JavaFX 17 controls, JUnit 5, SQLite via `PersistenceStore`, Maven (`mvn test`, `mvn javafx:run`).

**Spec:** This plan is the spec. Source of truth for current code is `codebase1.xml` (first occurrence of each path). Docs under `docs/` were not used.

## Global Constraints

- Behavior-preserving: no new features, no UX redesign, no FXML migration, no DI framework.
- Do not merge `Garage` and `ParkingGarage` in this plan (two real concepts: multi-site identity vs in-memory spot map). Track as follow-up.
- Do not rewrite SQLite schema or `PersistenceStore` migrations except to split files if tests stay green.
- Do not read or edit presentation docs; only code, tests, CSS generators, `pom.xml`.
- Keep existing CSS class names and `DesignTokens` color constants.
- Characterization tests first; then extract; then `mvn test`.
- `ParkingApplication` after this plan should be well under 800 lines (target ~400–700).
- Delete dump artifacts that are not part of the build: `javafxbase.xml` is not a source file (optional delete only if unused by Maven).

---

## Diagnosis (from `codebase1.xml`)

### What this app is

Desktop ParkingOS: login/register → role shell (sidebar) → occupancy map, tickets/payments, users, garages, analytics, settings. Domain services (`UserService`, `ParkingService`, `TicketService`, `PaymentService`, `GarageService`) plus SQLite `PersistenceStore`. JavaFX is handwritten (no FXML).

### Highest-leverage mess

| Problem | Evidence |
|---|---|
| God shell | `ParkingApplication.java` **3784 lines**, ~160 methods, no inner types. Owns init, registration, shell, dashboard, occupancy map, park/exit, wallet, customer vehicles, maintenance, animations, a11y, command palette. |
| Partial extraction already started | `LoginView`, `TicketPaymentView` (1283), `UserManagementView` (1193), `AnalyticsReportingView` (992), `SettingsView`, `GarageManagementView`, `OperationsViewFactory`, `ShellRefactorCharacterizationTest`. |
| Duplicate types | `gui.EmptyState` vs `gui.components.EmptyState` (near-identical). `gui.components.ModalDialog` is a one-line facade over `gui.ModalDialog`. `components.Skeleton` facades `SkeletonView`. |
| Dead / unused widgets | `LoadingSpinner` has **zero** call sites. |
| Helpers copy-pasted | `money`, `initials`, `spacer`, `separator`, `formLabel`, `iconButton`, `shake` repeated in 3–6 view classes. |
| Misplaced types | `ScheduledReportService` and `ReportExporter` live in `gui` but are file/IO schedulers. |
| Domain noise | `System.out.println` in `Ticket`, `ParkingSpot`, `Attendant`, `PaymentService`, `TicketService`. Commented-out prints in `ParkingService`. |
| Setter cycle | `PaymentService.setParkingService` after construction. |
| Kitchen-sink test | `AppTest.java` **2328 lines** covering users, spots, tickets, payments, garage. |
| Dual garage types | `Garage` (id/name/access) vs `ParkingGarage` (levels/spots). Keep both; document the seam. |
| Persistence blob | `PersistenceStore` **1041 lines** (schema + CRUD). Optional second wave. |
| CSS twin | `parkingos.css` (~1994) and `parkingos.css.template` (~1993) plus `scripts/generate_palette_css.py`. Do not hand-edit both; generate from template. |

### Approaches considered

1. **Recommended — structural extract, behavior freeze.** Split the shell, unify components, move services, split tests. Lowest risk, highest readability.
2. **Cosmetic only.** Logging, unused imports, format. Leaves the 3784-line file. Rejected.
3. **Deep domain redesign.** Merge garage types, repository pattern, constructor graph. High risk; out of scope here.

---

## File structure after cleanup

**Create**

- `src/main/java/com/parking/gui/shell/AppServices.java` — constructed graph of persistence + services (replaces field soup in `ParkingApplication`).
- `src/main/java/com/parking/gui/shell/AppShell.java` — sidebar, header, navigateTo, responsive layout, overlays.
- `src/main/java/com/parking/gui/RegistrationView.java` — extracted from `showRegistration` + validation helpers.
- `src/main/java/com/parking/gui/FirstRunSetupView.java` — extracted from `showFirstRunSetup`.
- `src/main/java/com/parking/gui/DashboardView.java` — admin/attendant/customer dashboard + live refresh.
- `src/main/java/com/parking/gui/OccupancyMapView.java` — grid/list map, filters, detail panel, park/exit/maintenance actions.
- `src/main/java/com/parking/gui/CustomerVehiclesView.java` — customer vehicle list + form.
- `src/main/java/com/parking/gui/WalletView.java` — wallet page + add-balance dialog.
- `src/main/java/com/parking/gui/MaintenanceView.java` — maintenance table/dialog.
- `src/main/java/com/parking/gui/UiFormat.java` — `money`, `initials`, `prettyType`, `statusText`.
- `src/main/java/com/parking/gui/UiNodes.java` — `spacer`, `separator`, `formLabel`, `iconButton`, input wraps.
- `src/main/java/com/parking/gui/UiMotion.java` — `shake`, `scale`, `animateIn`, `staggerIn` (uses `DesignTokens` motion).
- `src/main/java/com/parking/services/ScheduledReportService.java` — moved from `gui`.
- `src/main/java/com/parking/services/ReportExporter.java` — moved from `gui`.
- `src/test/java/com/parking/gui/ParkingApplicationSizeTest.java` — line-count / method-count guard so the shell cannot grow back.
- Split tests: `UserServiceTest`, `ParkingSpotTest`, `TicketLifecycleTest`, `ParkingFlowTest`, `PaymentFlowTest` extracted from `AppTest` (or nested `@Nested` classes in new files).

**Modify**

- `ParkingApplication.java` — wiring + `start`/`stop` only.
- `gui/EmptyState.java` — delete after callers use `components.EmptyState`.
- `gui/components/ModalDialog.java` — delete facade; callers use `gui.ModalDialog`.
- `gui/components/Skeleton.java` — optional keep as alias **or** delete and use `SkeletonView` only (pick one: **use `SkeletonView` + delete facade**).
- `gui/components/LoadingSpinner.java` — delete if still unused after occupancy extract (or wire it once on dashboard refresh; prefer delete).
- View files that duplicate helpers — call `UiFormat` / `UiNodes` / `UiMotion`.
- Domain classes that `System.out.println` — throw or return false; keep current control flow.
- `PaymentService` — accept `ParkingService` in constructor (nullable-safe overload for tests that construct without it).
- `OperationsViewFactory` — construct the new views if they belong with tickets/garages; occupancy stays shell-adjacent.

**Do not create** a DI container, FXML, or new CSS theme.

---

### Task 1: Guardrails — characterization tests for the shell

**Files:**
- Modify: `src/test/java/com/parking/gui/ShellRefactorCharacterizationTest.java`
- Create: `src/test/java/com/parking/gui/ParkingApplicationSizeTest.java`

**Interfaces:**
- Consumes: current `ParkingApplication.java` source on disk
- Produces: tests that fail if login/registration/dashboard/map/wallet methods disappear *before* they are moved to named types, and a size assertion to tighten after extract

- [ ] **Step 1: Extend characterization tests**

```java
@Test
void occupancyAndAuthFlowsStillExistSomewhere() throws Exception {
    String app = Files.readString(Path.of("src/main/java/com/parking/gui/ParkingApplication.java"));
    // After later tasks these strings may live in OccupancyMapView / RegistrationView.
    // This task only records the baseline in ParkingApplication.
    assertTrue(app.contains("private void showRegistration("));
    assertTrue(app.contains("private void buildDashboardContent("));
    assertTrue(app.contains("private void performPark("));
    assertTrue(app.contains("private void buildWalletPage("));
    assertTrue(app.contains("private void buildSidebar("));
}
```

- [ ] **Step 2: Add a size test that currently PASSES with a high ceiling, then lower it in Task 8**

```java
class ParkingApplicationSizeTest {
    @Test
    void parkingApplicationIsNotUnlimited() throws Exception {
        long lines = Files.lines(Path.of("src/main/java/com/parking/gui/ParkingApplication.java")).count();
        assertTrue(lines < 4000, "ParkingApplication is " + lines + " lines");
    }
}
```

- [ ] **Step 3: Run**

```bash
mvn -q -Dtest=ShellRefactorCharacterizationTest,ParkingApplicationSizeTest test
```

Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/parking/gui/ShellRefactorCharacterizationTest.java src/test/java/com/parking/gui/ParkingApplicationSizeTest.java
git commit -m "test: characterize parking shell before cleanup extracts"
```

---

### Task 2: Shared UI helpers (one place for money/spacer/shake)

**Files:**
- Create: `src/main/java/com/parking/gui/UiFormat.java`
- Create: `src/main/java/com/parking/gui/UiNodes.java`
- Create: `src/main/java/com/parking/gui/UiMotion.java`
- Modify: `ParkingApplication`, `UserManagementView`, `TicketPaymentView`, `AnalyticsReportingView`, `SettingsView`, `LoginView`, `StatCard` (spacer only if identical)

**Interfaces:**
- Produces:

```java
public final class UiFormat {
    private UiFormat() {}
    public static String money(double amount);           // Locale.US "$%.2f"
    public static String initials(String name);
    public static String prettyType(SpotType type);
    public static String statusText(SpotStatus status);
}

public final class UiNodes {
    private UiNodes() {}
    public static Region spacer();
    public static Separator separator();
    public static Label formLabel(String text);
    public static Button iconButton(IconView.Name icon);
}

public final class UiMotion {
    private UiMotion() {}
    public static void shake(Node node);
    public static <T extends Animation> T own(List<Animation> running, T animation);
}
```

Copy method bodies **verbatim** from `ParkingApplication` (money ~L3290, spacer ~L3697, shake ~L3078).

- [ ] **Step 1: Write a small unit test**

```java
class UiFormatTest {
    @Test
    void moneyUsesUsCurrencyFormat() {
        assertEquals("$12.50", UiFormat.money(12.5));
    }
}
```

- [ ] **Step 2: Implement helpers by moving code, then replace private methods in views with calls**

Do **not** change CSS classes on the produced nodes.

- [ ] **Step 3: Run**

```bash
mvn -q test
```

Expected: PASS (including `AppTest`)

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/parking/gui/UiFormat.java src/main/java/com/parking/gui/UiNodes.java src/main/java/com/parking/gui/UiMotion.java src/main/java/com/parking/gui/*.java src/main/java/com/parking/gui/components/StatCard.java src/test/java/com/parking/gui/UiFormatTest.java
git commit -m "refactor: share UI format, node, and motion helpers"
```

---

### Task 3: Collapse duplicate / dead GUI components

**Files:**
- Delete: `src/main/java/com/parking/gui/EmptyState.java` after switching imports to `com.parking.gui.components.EmptyState`
- Delete: `src/main/java/com/parking/gui/components/ModalDialog.java` (facade)
- Delete: `src/main/java/com/parking/gui/components/Skeleton.java` (facade)
- Delete: `src/main/java/com/parking/gui/components/LoadingSpinner.java` if still unused
- Modify callers: `AnalyticsReportingView`, `TicketPaymentView`, `UserManagementView` (`EmptyState`); anyone calling `components.ModalDialog` or `components.Skeleton`

**Rule:** One type per widget. Canonical:

| Widget | Canonical type |
|---|---|
| Empty state | `com.parking.gui.components.EmptyState` (keep `z-1` + `StyleManager` button) |
| Modal | `com.parking.gui.ModalDialog` |
| Skeleton | `com.parking.gui.SkeletonView` |
| Toast stack | `components.ToastStack` used by `ToastManager` |
| Stat card | `components.StatCard` |

- [ ] **Step 1: Grep (inside the repo, not docs)** for `import com.parking.gui.EmptyState`, `components.ModalDialog`, `components.Skeleton`, `LoadingSpinner`
- [ ] **Step 2: Retarget imports and delete files**
- [ ] **Step 3: `mvn -q test`** — PASS
- [ ] **Step 4: Commit** `refactor: remove duplicate GUI component facades`

---

### Task 4: Extract auth pages (`RegistrationView`, `FirstRunSetupView`)

**Files:**
- Create: `RegistrationView.java`, `FirstRunSetupView.java`
- Modify: `ParkingApplication.showLogin` already uses `LoginView`; replace `showRegistration` / `showFirstRunSetup` bodies with view construction
- Modify: `ShellRefactorCharacterizationTest` — assert new files exist; allow methods to leave `ParkingApplication`

**Interfaces:**
- Consumes: `UserService`, callbacks `Runnable showLogin`, `Consumer<User> onAuthenticated`
- Produces: `Parent build()` like `LoginView`

Move from `ParkingApplication` approximately L401–745 (`showRegistration` through `showFirstRunSetup` and the `registrationTextField` / `bindValidation` / `usernameValidationError` helpers if they are only used there). If dashboard still uses `bindValidation`, put validation in `UiNodes` or a `FormValidation` helper instead of duplicating.

- [ ] **Step 1: Characterization test for new files (same style as LoginView)**
- [ ] **Step 2: Move code, keep CSS class names (`login-card`, `primary-button`, `ghost-button`, `link-label`)**
- [ ] **Step 3: `mvn -q test`**
- [ ] **Step 4: Commit** `refactor: extract registration and first-run views`

---

### Task 5: Extract `AppServices` wiring

**Files:**
- Create: `src/main/java/com/parking/gui/shell/AppServices.java`
- Modify: `ParkingApplication.initSystem`

**Interfaces:**

```java
public final class AppServices {
    public final PersistenceStore persistence;
    public final BackgroundTaskRunner backgroundTasks;
    public final UserService users;
    public final GarageService garages;
    public ParkingGarage garage;
    public ParkingService parking;
    public TicketService tickets;
    public PaymentService payments;
    public OperationsSnapshotService snapshots;
    public GarageContext garageContext;
    public ToastManager toasts;
    public NotificationCenter notifications;

    public static AppServices boot(); // current initSystem + seed + restore
    public void shutdown();           // current shutdownResources
}
```

Keep seed methods (`seedUsers`, `seedPhase4Users`, `buildPhase2Spots`, `restorePersistedVehicles`) **inside `AppServices`** (or `DemoDataSeeder` if `AppServices` exceeds ~250 lines).

Replace `PaymentService.setParkingService(parking)` with a constructor parameter:

```java
public PaymentService(ParkingGarage garage, UserService userService, TicketService ticketService,
                      PersistenceStore persistence, ParkingService parkingService)
```

Keep a 4-arg constructor that passes `null` parking and documents that `releaseAfterPayment` needs parking — **only if tests compile**. Prefer wiring in one place so production never has null.

- [ ] **Step 1: Compile-fail by adding constructor; fix all `new PaymentService(` in tests (`AppTest`, `MultiGarageTicketPaymentTest`, etc.)**
- [ ] **Step 2: Move `initSystem` / restore / seed**
- [ ] **Step 3: `mvn -q test`**
- [ ] **Step 4: Commit** `refactor: centralize service graph in AppServices`

---

### Task 6: Extract occupancy map, dashboard, customer, wallet, maintenance

**Files:**
- Create the view classes listed in File structure
- Modify: `ParkingApplication.showDashboard` / `buildRoleNavigation` to instantiate views
- Modify: `OperationsViewFactory` only if it already constructs a sibling view

**Split map (from `ParkingApplication` comments):**

| New type | Approximate current lines |
|---|---|
| `AppShell` | `buildSidebar` L815–1111, `navigateTo`, `applyResponsiveLayout`, `applySidebarState`, overlays, command palette, a11y |
| `DashboardView` | L1128–1766 |
| `OccupancyMapView` | L1768–2995 + stats/animation used only by the map |
| `CustomerVehiclesView` | L1354–1554 (if not inside dashboard) |
| `WalletView` | L2906–2986 |
| `MaintenanceView` | L940–1048 |

`OccupancyMapView` needs a small **callback seam** into the shell:

```java
public interface OccupancyHost {
    void toast(String message, String accent);
    TicketPaymentView ticketView(); // or Runnable openTickets
    boolean isShuttingDown();
}
```

Do **not** pass the whole `ParkingApplication`.

- [ ] **Step 1: Move one view per commit if the file is >400 lines; otherwise one commit for all**
- [ ] **Step 2: Update `ShellRefactorCharacterizationTest` to require the new files and that `ParkingApplication` no longer contains `private void performPark(`**
- [ ] **Step 3: `mvn -q test`**
- [ ] **Step 4: Commit** `refactor: extract dashboard, occupancy, wallet, and maintenance views`

---

### Task 7: Move report scheduler/exporter out of `gui`

**Files:**
- Create: `src/main/java/com/parking/services/ScheduledReportService.java` (move)
- Create: `src/main/java/com/parking/services/ReportExporter.java` (move)
- Delete old `gui` copies
- Modify: `AnalyticsReportingView` imports; tests `ScheduledReportServiceTest`, `ReportExporterTest` packages if they import `gui`

Keep class names. Only the package changes.

- [ ] **Step 1: Move + fix imports**
- [ ] **Step 2: `mvn -q test`**
- [ ] **Step 3: Commit** `refactor: move report export/schedule services out of gui`

---

### Task 8: Domain logging + shrink `ParkingApplication` guard

**Files:**
- Modify: `Ticket.java`, `ParkingSpot.java`, `Attendant.java`, `PaymentService.java`, `TicketService.java`
- Modify: `ParkingApplicationSizeTest` ceiling to **800**
- Delete leftover commented `System.out` in `ParkingService`

**Rule:** If the method already returns `boolean` or throws a domain exception, **delete the print** and keep the return/throw. Do not add a logging framework.

Examples from dump:

- `ParkingSpot` occupied→maintenance: keep `return false`, drop `System.out`.
- `Ticket` payment validation: keep existing exception/status behavior; drop prints.
- `PaymentService` “Ticket not found”: keep catch; drop print (or rethrow if tests expect throw — **check `AppTest` first**).

- [ ] **Step 1: Change prints; run `mvn -q test`**
- [ ] **Step 2: Lower size test:**

```java
assertTrue(lines < 800, "ParkingApplication is " + lines + " lines");
```

If still above 800, extract leftover `themedScene` / `label` into `UiNodes` until under.

- [ ] **Step 3: Commit** `refactor: drop stdout domain logs; cap ParkingApplication size`

---

### Task 9: Split `AppTest` without dropping cases

**Files:**
- Create: `src/test/java/com/parking/UserServiceTest.java`
- Create: `src/test/java/com/parking/ParkingSpotModelTest.java`
- Create: `src/test/java/com/parking/TicketServiceTest.java`
- Create: `src/test/java/com/parking/ParkingServiceFlowTest.java`
- Create: `src/test/java/com/parking/PaymentServiceFlowTest.java`
- Modify: `AppTest.java` — leave a short file that points at remaining integration-only tests **or** delete once empty

Move methods **verbatim**. Shared fixtures (`newUser`, garage setup) go to `src/test/java/com/parking/TestFixtures.java`.

- [ ] **Step 1: Extract fixtures used by the first 10 tests**
- [ ] **Step 2: Move tests file by file; after each file `mvn -q test`**
- [ ] **Step 3: Commit** `test: split AppTest into domain-focused test classes`

---

### Task 10: Optional hygiene (only if Tasks 1–9 are green)

- Delete `javafxbase.xml` if nothing in `pom.xml` references it (it is a packed dump, not a module).
- Do **not** hand-sync `parkingos.css` and `.template`; if a color change is needed, edit the template and run `scripts/generate_palette_css.py`.
- Add a one-paragraph comment on `Garage` vs `ParkingGarage` in each class (not a new markdown doc).

Out of scope (explicit follow-ups):

- Merge `Garage` + `ParkingGarage`
- Split `PersistenceStore` into schema/migrate/repositories
- Introduce SLF4J
- FXML
- CSS architecture rewrite

---

## Verification

After every task: `mvn -q test`.

After Task 8:

```bash
wc -l src/main/java/com/parking/gui/ParkingApplication.java
# expect < 800
```

Manual smoke (human): `mvn javafx:run` — login, park, pay, logout. Not required for unit-test-only agents; note if skipped.

## Self-review

- Duplicate EmptyState/Modal/Skeleton/LoadingSpinner → Task 3
- God shell → Tasks 4–6
- Service graph / Payment setter → Task 5
- Report types in gui → Task 7
- stdout → Task 8
- AppTest blob → Task 9
- Garage merge → explicitly out of scope
- No TBD placeholders
- Type names (`OccupancyHost`, `AppServices`, `UiFormat`) used consistently in later tasks
