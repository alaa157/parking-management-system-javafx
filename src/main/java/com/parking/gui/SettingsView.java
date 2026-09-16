package com.parking.gui;

import com.parking.config.AppConfig;
import com.parking.enums.NotificationType;
import com.parking.enums.UserRole;
import com.parking.model.NotificationPreference;
import com.parking.model.User;
import com.parking.model.ParkingGarage;
import com.parking.services.DutyService;
import com.parking.services.NotificationService;
import com.parking.services.UserService;
import javafx.animation.ScaleTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import static com.parking.gui.DesignTokens.*;
import com.parking.gui.components.SegmentedControl;

/** Phase 6 settings center content. */
public final class SettingsView {
    private final UserService userService;
    private final User currentUser;
    private final ParkingGarage garage;
    private final ToastManager toasts;
    private final NotificationService notificationService;
    private final DutyService dutyService;
    private static final NotificationType[] NOTIFICATION_TYPES = {
            NotificationType.ENTRY_EXIT, NotificationType.PAYMENT_SUCCESS,
            NotificationType.MAINTENANCE, NotificationType.WEEKLY_SUMMARY};
    private final Consumer<Boolean> onThemeChanged;
    private final Runnable onBack;
    private int activeTab = 0;
    private VBox settingsNav;
    private StackPane host;
    private StackPane contentTarget;
    private TextField fullName;
    private TextField email;
    private TextField phone;
    private TextField garageName;
    private TextField garageAddress;
    private TextField baseRate;
    private TextField taxRate;
    private TextField maxDuration;
    private TextField freeMinutes;
    private TextField currency;
    private boolean[] notificationFlags = {true, true, false, true};
    private String frequency = "Immediate";
    private boolean sound = true;
    private boolean twoFactor = false;

    public SettingsView(UserService userService, ParkingGarage garage, User currentUser, ToastManager toasts,
                        Consumer<Boolean> onThemeChanged, Runnable onBack) {
        this(userService, garage, currentUser, toasts, null, onThemeChanged, onBack);
    }

    public SettingsView(UserService userService, ParkingGarage garage, User currentUser, ToastManager toasts,
                        NotificationService notificationService, Consumer<Boolean> onThemeChanged, Runnable onBack) {
        this(userService, garage, currentUser, toasts, notificationService, null, onThemeChanged, onBack);
    }

    public SettingsView(UserService userService, ParkingGarage garage, User currentUser, ToastManager toasts,
                        NotificationService notificationService, DutyService dutyService,
                        Consumer<Boolean> onThemeChanged, Runnable onBack) {
        this.userService = Objects.requireNonNull(userService);
        this.garage = Objects.requireNonNull(garage);
        this.currentUser = Objects.requireNonNull(currentUser);
        this.toasts = Objects.requireNonNull(toasts);
        this.notificationService = notificationService;
        this.dutyService = dutyService;
        this.onThemeChanged = Objects.requireNonNull(onThemeChanged);
        this.onBack = Objects.requireNonNull(onBack);
        loadPreferences();
    }

    private void loadPreferences() {
        if (notificationService == null || currentUser.getUserId() == null) return;
        try {
            NotificationPreference pref = notificationService.loadPreference(currentUser.getUserId());
            if (pref == null) return;
            Set<NotificationType> enabled = pref.enabledTypes();
            for (int i = 0; i < NOTIFICATION_TYPES.length; i++) {
                notificationFlags[i] = enabled.contains(NOTIFICATION_TYPES[i]);
            }
            frequency = displayFrequency(pref.frequency());
            sound = pref.soundEnabled();
        } catch (RuntimeException ex) {
            toasts.showError("Preferences unavailable", ex.getMessage());
        }
    }

    private void persistPreferences() {
        if (notificationService == null || currentUser.getUserId() == null) return;
        try {
            notificationService.savePreference(new NotificationPreference(currentUser.getUserId(),
                    enabledTypes(), storedFrequency(), sound));
        } catch (RuntimeException ex) {
            toasts.showError("Preferences failed", ex.getMessage());
        }
    }

    private Set<NotificationType> enabledTypes() {
        Set<NotificationType> enabled = EnumSet.noneOf(NotificationType.class);
        for (int i = 0; i < NOTIFICATION_TYPES.length && i < notificationFlags.length; i++) {
            if (notificationFlags[i]) enabled.add(NOTIFICATION_TYPES[i]);
        }
        return enabled;
    }

    private String storedFrequency() {
        return switch (frequency) {
            case "Daily Digest" -> "DAILY";
            case "Weekly" -> "WEEKLY";
            default -> "IMMEDIATE";
        };
    }

    private static String displayFrequency(String stored) {
        if (stored == null) return "Immediate";
        return switch (stored.trim().toUpperCase()) {
            case "DAILY", "DAILY DIGEST" -> "Daily Digest";
            case "WEEKLY" -> "Weekly";
            default -> "Immediate";
        };
    }

    public Node build() {
        host = new StackPane();
        host.getStyleClass().add("settings-host");
        BorderPane page = new BorderPane();
        page.getStyleClass().add("page-root");
        page.setPadding(new Insets(24));

        VBox header = new VBox(4,
                text("Settings", 28, TEXT, true),
                text("Manage your account and system preferences", 14, MUTED, false));
        Button back = new Button("Dashboard", IconView.of(IconView.Name.BACK, 16, MUTED));
        back.getStyleClass().add("ghost-button");
        back.setOnAction(e -> onBack.run());
        HBox top = new HBox(header, spacer(), back);
        top.setAlignment(Pos.CENTER_LEFT);
        page.setTop(top);

        HBox body = new HBox(20);
        body.setPadding(new Insets(22, 0, 0, 0));
        VBox nav = buildSettingsNav();
        StackPane content = new StackPane();
        content.getStyleClass().add("settings-content");
        contentTarget = content;
        body.getChildren().addAll(nav, content);
        HBox.setHgrow(content, Priority.ALWAYS);
        page.setCenter(body);
        host.getChildren().add(page);
        renderContent(content);

        host.sceneProperty().addListener((obs, old, scene) -> {
            if (scene != null) installShortcuts(scene);
        });
        return host;
    }

    private VBox buildSettingsNav() {
        VBox nav = new VBox(6);
        settingsNav = nav;
        nav.setPrefWidth(210);
        nav.getStyleClass().add("settings-tabs");
        boolean admin = currentUser.getRole() == UserRole.ADMIN;
        IconView.Name[] icons = admin
                ? new IconView.Name[]{IconView.Name.USER, IconView.Name.SECURITY, IconView.Name.NOTIFICATIONS,
                IconView.Name.GARAGE, IconView.Name.PALETTE, IconView.Name.INFO}
                : new IconView.Name[]{IconView.Name.USER, IconView.Name.SECURITY, IconView.Name.NOTIFICATIONS,
                IconView.Name.PALETTE, IconView.Name.INFO};
        String[] names = admin
                ? new String[]{"Profile", "Security", "Notifications", "Parking Config", "Appearance", "About"}
                : new String[]{"Profile", "Security", "Notifications", "Appearance", "About"};
        for (int i = 0; i < names.length; i++) {
            final int index = i;
            Button b = new Button(names[i], IconView.of(icons[i], 16, MUTED));
            b.setMaxWidth(Double.MAX_VALUE);
            b.setPrefHeight(44);
            b.setAlignment(Pos.CENTER_LEFT);
            b.getStyleClass().add("settings-tab");
            if (i == activeTab) b.getStyleClass().add("settings-tab-active");
            b.setOnAction(e -> selectTab(index));
            nav.getChildren().add(b);
        }
        return nav;
    }

    private void selectTab(int index) {
        int maxTab = settingsNav == null ? 5 : settingsNav.getChildren().size() - 1;
        activeTab = Math.max(0, Math.min(maxTab, index));
        if (settingsNav != null) {
            for (int i = 0; i < settingsNav.getChildren().size(); i++) {
                Node n = settingsNav.getChildren().get(i);
                n.getStyleClass().remove("settings-tab-active");
                if (i == activeTab) n.getStyleClass().add("settings-tab-active");
            }
        }
        renderContent(contentTarget);
    }

    private void renderContent(StackPane target) {
        target.getChildren().clear();
        Node content = currentUser.getRole() == UserRole.ADMIN
                ? switch (activeTab) {
                    case 0 -> profileTab();
                    case 1 -> securityTab();
                    case 2 -> notificationsTab();
                    case 3 -> parkingConfigTab();
                    case 4 -> appearanceTab();
                    default -> aboutTab();
                }
                : switch (activeTab) {
                    case 0 -> profileTab();
                    case 1 -> securityTab();
                    case 2 -> notificationsTab();
                    case 3 -> appearanceTab();
                    default -> aboutTab();
                };
        target.getChildren().add(content);
    }

    private Node profileTab() {
        VBox box = section("Profile", "Update your account information.");
        StackPane avatar = avatar(96, currentUser.getFullName());
        Button camera = new Button(null, IconView.of(IconView.Name.EDIT, 16, TEAL));
        camera.getStyleClass().add("avatar-camera");
        StackPane.setAlignment(camera, Pos.BOTTOM_RIGHT);
        avatar.getChildren().add(camera);

        fullName = input(currentUser.getFullName());
        email = input(currentUser.getEmail());
        phone = input(currentUser.getPhoneNumber() == null ? "" : currentUser.getPhoneNumber());
        TextField role = input(pretty(currentUser.getRole())); role.setDisable(true);
        TextField member = input(currentUser.getCreatedAt() == null ? "Unknown" : currentUser.getCreatedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy"))); member.setDisable(true);

        Button pw = new Button("Change Password"); pw.getStyleClass().add("outline-button"); pw.setOnAction(e -> showPasswordModal());
        Button save = new Button("Save Changes"); save.getStyleClass().add("primary-button"); save.setPrefHeight(42); save.setOnAction(e -> saveProfile());
        Label emailError = text("", 11, RED, false);
        Label fullNameError = text("", 11, RED, false);
        fullName.textProperty().addListener((obs, old, now) -> markRequired(fullName, fullNameError, now, "Full name is required."));
        email.textProperty().addListener((obs, old, now) -> {
            boolean valid = now.trim().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
            email.getStyleClass().removeAll("field-invalid", "field-valid");
            if (!now.isBlank()) email.getStyleClass().add(valid ? "field-valid" : "field-invalid");
            emailError.setText(now.isBlank() || valid ? "" : "Enter a valid email address.");
            emailError.setGraphic(valid && !now.isBlank() ? IconView.of(IconView.Name.CHECK, 12, GREEN) : null);
        });
        box.getChildren().addAll(center(avatar), new VBox(6, text("FULL NAME *", 10, MUTED, true), fullName, fullNameError), new VBox(6, text("EMAIL *", 10, MUTED, true), email, emailError), gridField("PHONE", phone),
                gridField("ROLE", role), gridField("MEMBER SINCE", member), dutyStatusCard(), new HBox(10, pw, spacer(), save));
        return scroll(box);
    }

    private Node dutyStatusCard() {
        VBox card = new VBox(6);
        if (dutyService == null
                || (currentUser.getRole() != UserRole.ATTENDANT && currentUser.getRole() != UserRole.ADMIN)) {
            return card;
        }
        try {
            var open = dutyService.current(currentUser);
            card.getChildren().add(text("DUTY STATUS", 10, MUTED, true));
            card.getChildren().add(open.isPresent()
                    ? text("On duty: " + open.get().shift() + " • " + open.get().zone(), 13, GREEN, true)
                    : text("Off duty — manage sessions from the Duty page.", 13, MUTED, false));
        } catch (RuntimeException ex) {
            toasts.showError("Duty status unavailable", ex.getMessage());
        }
        return card;
    }

    private Node securityTab() {
        VBox box = section("Security", "Protect your account and review sign-in activity.");
        PasswordField current = pass(), next = pass(), confirm = pass();
        Label currentError = text("", 11, RED, false), nextError = text("", 11, RED, false), confirmError = text("", 11, RED, false);
        HBox fields = new HBox(12, labeledPass("CURRENT PASSWORD *", current, currentError), labeledPass("NEW PASSWORD *", next, nextError), labeledPass("CONFIRM PASSWORD *", confirm, confirmError));
        for (Node n : fields.getChildren()) HBox.setHgrow(n, Priority.ALWAYS);
        Label strength = text("Password strength: —", 12, MUTED, false);
        current.textProperty().addListener((obs, o, n) -> markRequired(current, currentError, n, "Current password is required."));
        next.textProperty().addListener((obs, o, n) -> {
            strength.setText(n.isBlank() ? "Password strength: —" : passwordStrength(n));
            markValidity(next, nextError, n, userService.validatePasswordStrength(n), "Use 8+ characters with upper/lowercase, a number and a special character.");
        });
        confirm.textProperty().addListener((obs, o, n) -> markValidity(confirm, confirmError, n, !n.isBlank() && n.equals(next.getText()), "Passwords do not match."));
        ToggleSwitch tfa = new ToggleSwitch("Two-Factor Auth", twoFactor, v -> twoFactor = v);
        VBox sessions = card("Active Sessions", session("Desktop • Firefox", "Cairo • Active now", IconView.Name.DESKTOP), session("Android • Chrome", "Cairo • 32 min ago", IconView.Name.MOBILE));
        VBox logins = card("Login History", session("Desktop", "Today • Cairo", IconView.Name.DESKTOP), session("Android", "Yesterday • Cairo", IconView.Name.MOBILE), session("Desktop", "03 Sep • Cairo", IconView.Name.DESKTOP));
        Button save = new Button("Update Password"); save.getStyleClass().add("primary-button"); save.setOnAction(e -> {
            try {
                if (!next.getText().equals(confirm.getText())) throw new RuntimeException("Passwords do not match.");
                userService.changePassword(currentUser, current.getText(), next.getText());
                toasts.showSuccess("Security updated", "Your password has been changed.");
            } catch (RuntimeException ex) { toasts.showError("Update failed", ex.getMessage()); }
        });
        box.getChildren().addAll(fields, strength, tfa, sessions, logins, save);
        return scroll(box);
    }

    private Node notificationsTab() {
        VBox box = section("Notifications", "Choose which alerts you want to receive.");
        String[] labels = {"Parking Alerts", "Payment Confirmations", "Maintenance Alerts", "Weekly Reports"};
        String[] desc = {"Entry and exit notifications", "Successful payment confirmations", "Garage maintenance warnings", "Weekly operational summaries"};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            ToggleSwitch sw = new ToggleSwitch(labels[i], notificationFlags[i], v -> {
                notificationFlags[index] = v;
                persistPreferences();
            });
            sw.getChildren().add(text("  " + desc[i], 11, MUTED, false));
            box.getChildren().add(sw);
        }
        HBox frequencyBox = segmented(new String[]{"Immediate", "Daily Digest", "Weekly"}, frequency, v -> {
            frequency = v;
            persistPreferences();
        });
        ToggleSwitch soundToggle = new ToggleSwitch("Play notification sound", sound, v -> {
            sound = v;
            persistPreferences();
        });
        HBox typeSounds = new HBox(8);
        for (String type : new String[]{"SUCCESS", "ERROR", "WARNING", "INFO"}) {
            ToggleSwitch typeToggle = new ToggleSwitch(type.substring(0, 1) + type.substring(1).toLowerCase(),
                    NotificationCenter.isSoundEnabled(type), v -> NotificationCenter.setSoundEnabled(type, v));
            typeSounds.getChildren().add(typeToggle);
        }
        Button test = new Button("Test Notification"); test.getStyleClass().add("outline-button"); test.setOnAction(e -> toasts.showInfo("Test notification", "ParkingOS notifications are working."));
        box.getChildren().addAll(text("FREQUENCY", 11, MUTED, true), frequencyBox, soundToggle,
                text("SOUNDS BY TYPE", 11, MUTED, true), typeSounds, test);
        return scroll(box);
    }

    private Node parkingConfigTab() {
        VBox box = section("Parking Configuration", "Configure the garage defaults used by the desktop application.");
        boolean admin = currentUser.getRole() == UserRole.ADMIN;
        garageName = input(AppConfig.garageName());
        garageAddress = input(AppConfig.garageAddress());
        baseRate = input(String.format(java.util.Locale.US, "%.2f", AppConfig.baseRate()));
        taxRate = input(String.format(java.util.Locale.US, "%.2f", AppConfig.taxRate() * 100));
        maxDuration = input(Integer.toString(AppConfig.maxParkHours()));
        freeMinutes = input(Integer.toString(AppConfig.freeParkingMinutes()));
        currency = input(AppConfig.currency());
        box.getChildren().addAll(configField("GARAGE NAME", "Display name used across the application.", garageName),
                configField("GARAGE ADDRESS", "Location shown to operators.", garageAddress),
                configField("BASE RATE ($/HR)", "Default hourly parking rate.", baseRate),
                configField("TAX RATE (%)", "Tax added to completed payments.", taxRate),
                configField("MAX PARKING DURATION (H)", "Maximum allowed parking duration.", maxDuration),
                configField("FREE PARKING (MIN)", "Grace period before charging starts.", freeMinutes),
                configField("CURRENCY", "Three-letter currency code.", currency));
        if (!admin) box.getChildren().add(text("Only administrators can change operational configuration.", 12, ORANGE, false));
        Button save = new Button("Save Configuration"); save.getStyleClass().add("primary-button"); save.setDisable(!admin); save.setOnAction(e -> validateConfig());
        box.getChildren().add(save);
        return scroll(box);
    }

    private Node appearanceTab() {
        VBox box = section("Appearance", "Customize the look and density of ParkingOS.");
        boolean light = ThemeManager.get().isLight();
        HBox theme = segmented(new String[]{"Dark", "Light", "System Default"}, light ? "Light" : "Dark", v -> {
            if ("Light".equals(v)) ThemeManager.get().setLight(true);
            else ThemeManager.get().setLight(false);
            ThemeManager.get().apply(host);
            onThemeChanged.accept(ThemeManager.get().isLight());
        });
        HBox density = segmented(new String[]{"Comfortable", "Compact", "Dense"}, ThemeManager.get().getDensity(), v -> {
            ThemeManager.get().setDensity(v);
            onThemeChanged.accept(ThemeManager.get().isLight());
        });
        box.getChildren().addAll(text("THEME", 11, MUTED, true), theme,
                text("ACCENT COLOR", 11, MUTED, true), swatches(),
                text("DENSITY", 11, MUTED, true), density,
                text("FONT SIZE", 11, MUTED, true), segmented(new String[]{"Small", "Medium", "Large"}, "Medium", v -> {}));
        return scroll(box);
    }

    private Node aboutTab() {
        VBox box = section("About", "ParkingOS desktop platform.");
        box.setAlignment(Pos.TOP_CENTER);
        box.getChildren().addAll(IconView.of(IconView.Name.PARKING, 72, TEAL), text("ParkingOS v2.0.0", 20, TEXT, true),
                text("Enterprise Parking Management Platform", 14, MUTED, false),
                text("© 2025 ParkingOS Team", 12, MUTED, false), text("Built with ☕ Java", 12, MUTED, false),
                pill("LICENSE: ENTERPRISE", TEAL),
                new Button("Check for Updates"));
        ((Button) box.getChildren().get(box.getChildren().size()-1)).getStyleClass().add("outline-button");
        return box;
    }

    private void saveProfile() {
        try {
            if (fullName.getText().trim().isEmpty() || email.getText().trim().isEmpty()) throw new RuntimeException("Name and email are required.");
            if (!userService.validateEmail(email.getText().trim())) throw new RuntimeException("Enter a valid email address.");
            currentUser.setFullName(fullName.getText().trim());
            currentUser.setEmail(email.getText().trim());
            currentUser.setPhoneNumber(phone.getText().trim());
            toasts.showSuccess("Profile saved", "Your account details were updated.");
        } catch (RuntimeException ex) { toasts.showError("Save failed", ex.getMessage()); }
    }

    private void validateConfig() {
        try {
            if (currentUser.getRole() != UserRole.ADMIN) throw new RuntimeException("Only administrators can change parking configuration.");
            double rate = Double.parseDouble(baseRate.getText());
            double tax = Double.parseDouble(taxRate.getText());
            int duration = Integer.parseInt(maxDuration.getText());
            int free = Integer.parseInt(freeMinutes.getText());
            AppConfig.update(garageName.getText(), garageAddress.getText(), rate, tax, free, duration, currency.getText().trim().toUpperCase(java.util.Locale.ROOT));
            garage.setName(AppConfig.garageName());
            garage.setAddress(AppConfig.garageAddress());
            garage.setBaseHourlyRate(rate);
            toasts.showSuccess("Configuration saved", "Parking configuration is ready for use.");
        } catch (RuntimeException ex) { toasts.showError("Configuration failed", ex.getMessage()); }
    }

    private void showPasswordModal() {
        VBox card = new VBox(12); card.getStyleClass().add("phase6-modal"); card.setMaxWidth(430);
        PasswordField a = pass(), b = pass(), c = pass();
        card.getChildren().addAll(text("Change Password", 20, TEXT, true), labeledPass("CURRENT PASSWORD", a), labeledPass("NEW PASSWORD", b), labeledPass("CONFIRM PASSWORD", c));
        ModalDialog overlay = ModalDialog.show(host, card);
        Label validation = text("", 11, RED, false);
        card.getChildren().add(2, validation);
        b.textProperty().addListener((obs, old, now) -> {
            boolean valid = now.length() >= 8 && now.matches(".*[A-Z].*") && now.matches(".*[0-9].*");
            validation.setText(now.isEmpty() ? "" : valid ? "Password strength: strong" : "Use 8+ characters with a number and uppercase letter.");
            validation.setTextFill(Color.web(valid ? GREEN : RED));
            b.getStyleClass().removeAll("field-invalid", "field-valid");
            if (!now.isEmpty()) b.getStyleClass().add(valid ? "field-valid" : "field-invalid");
        });
        HBox buttons = new HBox(10);
        Button cancel = new Button("Cancel"); cancel.getStyleClass().add("outline-button"); cancel.setOnAction(e -> overlay.close());
        Button save = new Button("Save"); save.getStyleClass().add("primary-button"); save.setOnAction(e -> {
            try { if (!b.getText().equals(c.getText())) throw new RuntimeException("Passwords do not match."); userService.changePassword(currentUser, a.getText(), b.getText()); overlay.close(); toasts.showSuccess("Password changed", "Your password was updated successfully."); }
            catch (RuntimeException ex) { toasts.showError("Password change failed", ex.getMessage()); }
        });
        buttons.getChildren().addAll(spacer(), cancel, save); card.getChildren().add(buttons);
    }

    private StackPane modalOverlay() { StackPane o = new StackPane(); o.getStyleClass().add("phase6-modal-overlay"); o.setAlignment(Pos.CENTER); return o; }

    private String passwordStrength(String s) { int score = 0; if (s.length() >= 8) score++; if (s.matches(".*[A-Z].*")) score++; if (s.matches(".*\\d.*")) score++; if (s.matches(".*[^A-Za-z0-9].*")) score++; return "Password strength: " + (score <= 1 ? "Weak" : score == 2 ? "Medium" : "Strong"); }
    private HBox swatches() { HBox h = new HBox(10); String[] cs = {TEAL, ORANGE, BLUE, GREEN, PURPLE, RED}; for (String c : cs) { Button b = new Button(null, IconView.of(IconView.Name.CHECK, 15, WHITE)); b.setPrefSize(32,32); b.getStyleClass().add(swatchClass(c)); h.getChildren().add(b);} return h; }
    private HBox segmented(String[] values, String selected, Consumer<String> cb) { return new SegmentedControl(values, selected, cb); }
    private HBox session(String title, String sub, IconView.Name icon) { return new HBox(8, IconView.of(icon, 16, MUTED), new VBox(2, text(title,13,TEXT,true), text(sub,11,MUTED,false))); }
    private VBox card(String title, Node... children) { VBox v=new VBox(10,text(title,15,TEXT,true)); v.getChildren().addAll(children); v.getStyleClass().add("settings-card"); v.setPadding(new Insets(14)); return v; }
    private VBox section(String title,String sub) { VBox v=new VBox(14); v.getStyleClass().add("settings-section"); v.getChildren().addAll(text(title,19,TEXT,true),text(sub,12,MUTED,false)); return v; }
    private VBox labeledPass(String title, PasswordField f, Node... feedback) { VBox box = new VBox(6,text(title,10,MUTED,true),f); box.getChildren().addAll(feedback); return box; }
    private VBox labeledPass(String title, PasswordField f) { return labeledPass(title, f, new Node[0]); }
    private void markRequired(TextInputControl field, Label message, String value, String error) {
        boolean valid = value != null && !value.trim().isEmpty();
        field.getStyleClass().removeAll("field-invalid", "field-valid");
        if (value != null && !value.isBlank()) field.getStyleClass().add(valid ? "field-valid" : "field-invalid");
        if (message != null) message.setText(valid || value == null || value.isBlank() ? "" : error);
    }
    private void markValidity(TextInputControl field, Label message, String value, boolean valid, String error) {
        field.getStyleClass().removeAll("field-invalid", "field-valid");
        if (!value.isBlank()) field.getStyleClass().add(valid ? "field-valid" : "field-invalid");
        message.setText(valid || value.isBlank() ? "" : error);
    }
    private VBox gridField(String title,TextField f){ return new VBox(6,text(title,10,MUTED,true),f); }
    private VBox configField(String title,String desc,TextField f){ return new VBox(4,text(title,10,MUTED,true),f,text(desc,11,MUTED,false)); }
    private PasswordField pass(){ PasswordField p=new PasswordField(); p.getStyleClass().add("dark-input"); p.setPrefHeight(42); return p; }
    private TextField input(String v){ TextField t=new TextField(v); t.getStyleClass().add("dark-input"); t.setPrefHeight(42); return t; }
    private StackPane avatar(double size,String name){ StackPane s=new StackPane(); Circle c=new Circle(size/2,DesignTokens.color(TEAL)); Label l=text(initials(name),28,BG,true); s.getChildren().addAll(c,l); return s; }
    private Node center(Node n){ StackPane s=new StackPane(n); s.setAlignment(Pos.CENTER_LEFT); return s; }
    private Label pill(String s,String color){ Label l=text(s,10,WHITE,true); l.setPadding(new Insets(6,10,6,10)); l.getStyleClass().add(pillClass(color)); return l; }
    private String pillClass(String color){ if (TEAL.equals(color)) return "pill-teal"; if (ORANGE.equals(color)) return "pill-orange"; if (BLUE.equals(color)) return "pill-blue"; if (GREEN.equals(color)) return "pill-green"; if (PURPLE.equals(color)) return "pill-purple"; return "pill-red"; }
    private String swatchClass(String color){ if (TEAL.equals(color)) return "swatch-teal"; if (ORANGE.equals(color)) return "swatch-orange"; if (BLUE.equals(color)) return "swatch-blue"; if (GREEN.equals(color)) return "swatch-green"; if (PURPLE.equals(color)) return "swatch-purple"; return "swatch-red"; }
    private ScrollPane scroll(Node n){ ScrollPane s=new ScrollPane(n); s.setFitToWidth(true); s.getStyleClass().add("dark-scroll"); return s; }
    private Region spacer(){ return UiNodes.spacer(); }
    private static String initials(String n){ return UiFormat.initials(n); }
    private static String pretty(UserRole role){ return role==null?"Unknown":role.name().charAt(0)+role.name().substring(1).toLowerCase(); }
    private static Label text(String s,double size,String color,boolean bold){ return DesignTokens.text(s,size,color,bold); }
    private void installShortcuts(javafx.scene.Scene scene) {
        scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.isControlDown() && e.getCode() == javafx.scene.input.KeyCode.S) {
                e.consume();
                if (activeTab == 0) saveProfile();
                else if (currentUser.getRole() == UserRole.ADMIN && activeTab == 3) validateConfig();
            } else if (e.isControlDown() && e.getCode() == javafx.scene.input.KeyCode.TAB) {
                e.consume();
                activeTab = (activeTab + 1) % settingsNav.getChildren().size();
                renderContent((StackPane) ((HBox) host.lookup(".settings-content")).getChildren().get(0));
            } else if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                for (Node n : host.getChildren()) {
                    if (n.getStyleClass().contains("phase6-modal-overlay")) host.getChildren().remove(n);
                }
            }
        });
    }

    public static final class ToggleSwitch extends HBox {
        private boolean value;
        private final Circle thumb = new Circle(8);
        public ToggleSwitch(String title, boolean initial, Consumer<Boolean> listener) {
            value=initial; setAlignment(Pos.CENTER_LEFT); setSpacing(10); getStyleClass().add("toggle-row");
            StackPane track=new StackPane(); track.setPrefSize(42,24); track.getStyleClass().add("toggle-track"); thumb.getStyleClass().add("toggle-thumb"); track.getChildren().add(thumb); update(track);
            Label label=new Label(title); label.getStyleClass().add("toggle-label");
            getChildren().addAll(track,label); setOnMouseClicked(e->{ value=!value; update(track); listener.accept(value); });
        }
        private void update(StackPane track){ track.getStyleClass().removeAll("toggle-on","toggle-off"); track.getStyleClass().add(value?"toggle-on":"toggle-off"); thumb.setTranslateX(value?9:-9); }
    }
}
