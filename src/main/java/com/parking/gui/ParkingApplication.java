package com.parking.gui;

import com.parking.enums.SpotStatus;
import com.parking.enums.SpotType;
import com.parking.enums.PaymentStatus;
import com.parking.enums.TicketStatus;
import com.parking.enums.UserRole;
import com.parking.enums.VehicleType;
import com.parking.config.AppConfig;
import com.parking.exceptions.InvalidTicketStatusException;
import com.parking.exceptions.SpotNotAvailableException;
import com.parking.exceptions.TicketNotFoundException;
import com.parking.exceptions.VehicleAlreadyParkedException;
import com.parking.model.Customer;
import com.parking.model.ParkingGarage;
import com.parking.model.ParkingSpot;
import com.parking.model.Payment;
import com.parking.model.Ticket;
import com.parking.model.User;
import com.parking.model.Vehicle;
import com.parking.services.ParkingService;
import com.parking.services.PaymentService;
import com.parking.services.OperationsSnapshot;
import com.parking.services.OperationsSnapshotService;
import com.parking.services.TicketService;
import com.parking.services.UserService;
import com.parking.services.GarageContext;
import com.parking.services.GarageService;
import com.parking.services.DutyService;
import com.parking.services.NotificationService;
import com.parking.services.ReservationService;
import com.parking.persistence.PersistenceStore;
import com.parking.gui.shell.AppServices;
import com.parking.gui.shell.AppShell;

import javafx.animation.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.parking.gui.DesignTokens.*;
import com.parking.gui.components.StatCard;
import com.parking.gui.components.StyleManager;

/**
 * ParkingOS desktop JavaFX application.
 *
 * Phase 1: Login + Dashboard
 * Phase 2: Real-time Garage Occupancy Map + Parking Management
 *
 * No FXML. Existing OOP domain/service classes remain the source of truth.
 */
public final class ParkingApplication extends Application {

    // ---------------------------------------------------------------------
    // Design system
    // ---------------------------------------------------------------------
    private static final int MIN_WIDTH = 720;
    private static final int MIN_HEIGHT = 600;

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("EEE, dd MMM  •  HH:mm");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy  •  HH:mm");

    // ---------------------------------------------------------------------
    // Existing application services
    // ---------------------------------------------------------------------
    private PersistenceStore persistence;
    private BackgroundTaskRunner backgroundTasks;
    private UserService userService;
    private GarageService garageService;
    private ToastManager toastManager;
    private ParkingGarage garage;
    private ParkingService parkingService;
    private TicketService ticketService;
    private PaymentService paymentService;
    private OperationsSnapshotService operationsSnapshots;
    private NotificationService notificationService;
    private DutyService dutyService;
    private ReservationService reservationService;
    private User currentUser;
    private GarageContext garageContext;
    private String authNotice;
    private Stage stage;
    private NotificationCenter notificationCenter;
    private Timeline overstayTimer;
    private AnalyticsReportingView analyticsView;
    private final Set<String> notifiedOverstayTickets = new HashSet<>();
    private volatile boolean shuttingDown;
    private volatile boolean shutdownComplete;
    private TicketPaymentView ticketView;

    // ---------------------------------------------------------------------
    // Phase 2 state
    // ---------------------------------------------------------------------
    private final List<Animation> runningAnimations = new ArrayList<>();

    private StackPane dashboardRoot;
    private AppShell appShell;
    private Label notificationBadge;
    private BorderPane shellRoot;
    private Button hamburger;


    // ---------------------------------------------------------------------
    // Startup / system initialization
    // ---------------------------------------------------------------------
    @Override
    public void start(Stage stage) {
        this.stage = stage;
        stage.setOnCloseRequest(event -> shutdownResources());
        try {
            initSystem();
            if (userService.getAllUsers().isEmpty()) showFirstRunSetup();
            else showLogin();
        } catch (RuntimeException failure) {
            shutdownResources();
            throw failure;
        }

        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);
        stage.show();
    }

    private void initSystem() {
        AppServices services = AppServices.boot();
        persistence = services.persistence;
        backgroundTasks = services.backgroundTasks;
        userService = services.users;
        garageService = services.garages;
        toastManager = services.toasts;
        garage = services.garage;
        parkingService = services.parking;
        ticketService = services.tickets;
        paymentService = services.payments;
        operationsSnapshots = services.snapshots;
        notificationService = services.notifications;
        dutyService = services.duty;
        reservationService = services.reservations;
    }

    // ---------------------------------------------------------------------
    // Phase 1: Login
    // ---------------------------------------------------------------------
    private void showLogin() {
        Parent root = new LoginView(userService, authNotice, user -> {
            currentUser = user;
            showDashboard();
        }, this::showRegistration).build();
        authNotice = null;
        showAuthenticationScene(root, "PARKINGOS — Enterprise Platform", 1180, 760);
    }
    private void showRegistration() {
        Parent root = new RegistrationView(userService, this::showLogin, user -> {
            authNotice = "Account created. You can sign in now.";
            showLogin();
        }).build();
        showAuthenticationScene(root, "PARKINGOS — Create customer account", 1180, 760);
    }

    private void showFirstRunSetup() {
        Parent root = new FirstRunSetupView(userService, this::showLogin, user -> {
            authNotice = "Administrator account created. Sign in to continue.";
            showLogin();
        }).build();
        showAuthenticationScene(root, "PARKINGOS — Initial administrator setup", 1180, 760);
    }

    private void showAuthenticationScene(Parent root, String title, double requestedWidth, double requestedHeight) {
        boolean maximized = stage.isMaximized(); double width = stage.getWidth() > 0 ? stage.getWidth() : requestedWidth;
        double height = stage.getHeight() > 0 ? stage.getHeight() : requestedHeight;
        stage.setScene(themedScene(root, requestedWidth, requestedHeight)); stage.setTitle(title); stage.setMaximized(maximized);
        if (!maximized) { stage.setWidth(width); stage.setHeight(height); }
    }

    // ---------------------------------------------------------------------
    // Main shell
    // ---------------------------------------------------------------------
    private void showDashboard() {
        garageContext = new GarageContext(garageService, currentUser);
        try { garageContext.select(garage.getGarageId()); } catch (RuntimeException ignored) { }
        BorderPane root = new BorderPane();
        shellRoot = root;
        root.getStyleClass().add("app-root");

        notificationCenter = new NotificationCenter(
                notificationService, () -> currentUser,
                () -> updateNotificationBadge());
        dashboardRoot = new StackPane();
        appShell = new AppShell(root, dashboardRoot, this::navigateTo, currentUser, garageService, garageContext,
                notificationCenter, new AppShell.PageFactory() {
                    @Override public Node dashboard(BorderPane shell) { return buildDashboardContent(shell); }
                    @Override public Node customerVehicles(BorderPane shell) { return buildCustomerVehicles(shell); }
                    @Override public Node parking(BorderPane shell) { return buildParkingManagement(shell); }
                    @Override public Node tickets(BorderPane shell) { return buildTicketManagement(shell); }
                    @Override public Node wallet(BorderPane shell) { return buildWalletPage(shell); }
                    @Override public Node settings() { return buildSettings(); }
                    @Override public Node comingSoon(String title, String subtitle) { return buildComingSoonPage(title, subtitle); }
                    @Override public Node users() { return buildUserManagement(); }
                    @Override public Node garages() { return buildGarageManagement(); }
                    @Override public Node garageAccess() { return buildGarageAccess(); }
                    @Override public Node analytics() { return buildAnalyticsReports(); }
                    @Override public Node maintenance() { return buildMaintenanceView(); }
                }, this::buildRoleNavigation, message -> toast(message, RED), this::logout);
        root.setLeft(appShell.sidebar());
        root.setTop(appShell.header());
        dashboardRoot.getStyleClass().add("app-root");
        dashboardRoot.setFocusTraversable(true);
        dashboardRoot.setAccessibleRole(javafx.scene.AccessibleRole.PARENT);
        dashboardRoot.setAccessibleText("ParkingOS main content");

        checkOverstayTickets();
        startOverstayTimer();

        root.setCenter(dashboardRoot);
        dashboardRoot.getChildren().setAll(buildDashboardContent(root));

        StackPane wrap = new StackPane(root);
        wrap.getStyleClass().add("app-root");

        hamburger = new Button(null, IconView.of(IconView.Name.LIST, 20, TEXT));
        hamburger.getStyleClass().add("hamburger-button");
        hamburger.setTooltip(new Tooltip("Open navigation"));
        hamburger.setOnAction(e -> appShell.toggleMobileSidebar(hamburger));
        wrap.getChildren().add(hamburger);
        StackPane.setAlignment(hamburger, Pos.TOP_LEFT);
        StackPane.setMargin(hamburger, new Insets(12));
        Button skip = new Button("Skip to content");
        skip.getStyleClass().add("skip-link");
        skip.setAccessibleText("Skip navigation and focus main content");
        skip.setOnAction(e -> dashboardRoot.requestFocus());
        wrap.getChildren().add(skip);
        StackPane.setAlignment(skip, Pos.TOP_LEFT);
        StackPane.setMargin(skip, new Insets(4, 0, 0, 76));

        installGlobalOverlays();
        ThemeManager.get().apply(root);

        Scene scene = themedScene(wrap, 1280, 820);
        scene.widthProperty().addListener((obs, old, width) -> appShell.applyResponsiveLayout(width.doubleValue(), hamburger));
        appShell.applyResponsiveLayout(scene.getWidth(), hamburger);
        installCommandPalette(scene);
        installButtonMotion(scene);
        installAccessibility(scene, wrap);
        stage.setScene(scene);
        stage.setTitle("PARKINGOS — " + role(currentUser.getRole()));

        FadeTransition fade = new FadeTransition(Duration.millis(400), wrap);
        fade.setFromValue(0);
        fade.setToValue(1);
        own(fade).play();
    }

    /** Builds the persistent navigation from the user's role, not from the CLI menu. */
    private void buildRoleNavigation(VBox nav) {
        BorderPane root = shellRoot;
        switch (currentUser.getRole()) {
            case CUSTOMER:
                navButton(nav, IconView.Name.HOME, "Overview", true,
                        () -> navigateTo(buildDashboardContent(root)));
                navButton(nav, IconView.Name.PARKING, "My Vehicles", false,
                        () -> navigateTo(buildCustomerVehicles(root)));
                navButton(nav, IconView.Name.PARKING, "Active Parking", false,
                        () -> navigateTo(buildParkingManagement(root)));
                navButton(nav, IconView.Name.TICKET, "Tickets", false,
                        () -> navigateTo(buildTicketManagement(root)));
                navButton(nav, IconView.Name.WALLET, "Wallet", false,
                        () -> navigateTo(buildWalletPage(root)));
                navButton(nav, IconView.Name.USER, "Profile", false,
                        () -> navigateTo(buildSettings()));
                break;
            case ATTENDANT:
                navButton(nav, IconView.Name.HOME, "Operations", true,
                        () -> navigateTo(buildDashboardContent(root)));
                navButton(nav, IconView.Name.PARKING, "Vehicle Entry", false,
                        () -> navigateTo(buildParkingManagement(root)));
                navButton(nav, IconView.Name.BACK, "Vehicle Exit", false,
                        () -> navigateTo(buildTicketManagement(root)));
                navButton(nav, IconView.Name.GRID, "Available Spots", false,
                        () -> navigateTo(buildParkingManagement(root)));
                navButton(nav, IconView.Name.GARAGE, "Garage Status", false,
                        () -> navigateTo(buildDashboardContent(root)));
                navButton(nav, IconView.Name.CLOCK, "Duty", false,
                        () -> navigateTo(new DutyView(dutyService, currentUser, toastManager, DutyView.Mode.DUTY).build()));
                navButton(nav, IconView.Name.CALENDAR, "Shift", false,
                        () -> navigateTo(new DutyView(dutyService, currentUser, toastManager, DutyView.Mode.SHIFT).build()));
                break;
            case ADMIN:
                navButton(nav, IconView.Name.HOME, "Overview", true,
                        () -> navigateTo(buildDashboardContent(root)));
                navButton(nav, IconView.Name.PARKING, "Parking Spots", false,
                        () -> navigateTo(buildParkingManagement(root)));
                navButton(nav, IconView.Name.USERS, "Users", false,
                        () -> navigateTo(buildUserManagement()));
                navButton(nav, IconView.Name.GARAGE, "Garages", false,
                        () -> navigateTo(buildGarageManagement()));
                navButton(nav, IconView.Name.USER, "Garage Access", false,
                        () -> navigateTo(buildGarageAccess()));
                navButton(nav, IconView.Name.CHART, "Revenue & Analytics", false,
                        () -> navigateTo(buildAnalyticsReports()));
                navButton(nav, IconView.Name.SETTINGS, "Maintenance", false,
                        () -> navigateTo(buildMaintenanceView()));
                navButton(nav, IconView.Name.SETTINGS, "Settings", false,
                        () -> navigateTo(buildSettings()));
                navButton(nav, IconView.Name.TICKET, "Refunds", false,
                        () -> navigateTo(buildTicketManagement(root)));
                break;
            default:
                navButton(nav, IconView.Name.HOME, "Overview", true,
                        () -> navigateTo(buildDashboardContent(root)));
        }
    }

    private void navButton(VBox nav, IconView.Name icon, String title, boolean active, Runnable action) {
        appShell.navButton(nav, icon, title, active, action);
    }

    private Node buildDashboardContent(BorderPane shell) {
        return new DashboardView(garage, parkingService, ticketService, paymentService, operationsSnapshots,
                backgroundTasks, currentUser, message -> toast(message, RED), this::navigateTo,
                this::buildParkingManagement, this::buildCustomerVehicles, this::buildTicketManagement,
                this::buildWalletPage).build(shell);
    }

    private Node buildCustomerVehicles(BorderPane shell) {
        return new CustomerVehiclesView(persistence, garage, (Customer) currentUser, this::navigateTo,
                this::buildParkingManagement, this::buildTicketManagement,
                message -> toast(message, GREEN)).build(shell);
    }

    private OccupancyMapView.OccupancyHost occupancyHost() {
        return new OccupancyMapView.OccupancyHost() {
            @Override public void toast(String message, String accent) { ParkingApplication.this.toast(message, accent); }
            @Override public TicketPaymentView ticketView() { return ParkingApplication.this.ticketView; }
            @Override public boolean isShuttingDown() { return ParkingApplication.this.shuttingDown; }
        };
    }

    private Node buildParkingManagement(BorderPane shell) {
        return new OccupancyMapView(garage, parkingService, ticketService, userService, persistence, currentUser,
                occupancyHost(), this::navigateTo, this::buildVehicleFormPage, this::buildTicketManagement).build(shell);
    }

    private Node buildVehicleFormPage(BorderPane shell) {
        return new CustomerVehiclesView(persistence, garage, (Customer) currentUser, this::navigateTo,
                this::buildParkingManagement, this::buildTicketManagement,
                message -> toast(message, GREEN)).build(shell);
    }

    private Node buildWalletPage(BorderPane shell) {
        if (!(currentUser instanceof Customer customer)) return buildTicketManagement(shell);
        return new WalletView(paymentService, customer, this::navigateTo, this::buildTicketManagement,
                message -> toast(message, message.contains("valid") ? RED : GREEN)).build(shell);
    }

    private Node buildMaintenanceView() {
        return new MaintenanceView(garage, message -> toast(message,
                message.contains("reason") || message.contains("available") ? RED : GREEN)).build();
    }


    private Node buildTicketManagement(BorderPane shell) {
        if (ticketView != null) ticketView.dispose();
        ticketView = new OperationsViewFactory(ticketService, paymentService, parkingService, userService,
                garageService, garage, currentUser, garageContext, backgroundTasks,
                message -> toast(message, TEAL), ignored -> {
                    navigateTo(buildDashboardContent(shell));
                }, () -> navigateTo(buildDashboardContent(shell))).ticketPaymentView();
        return ticketView.build();
    }

    // ---------------------------------------------------------------------
    // Animation helpers
    // ---------------------------------------------------------------------
    private <T extends Animation> T own(T animation) {
        return UiMotion.own(runningAnimations, animation);
    }

    private void stopOwnedAnimations() {
        runningAnimations.forEach(Animation::stop);
        runningAnimations.clear();
    }




    private void animateIn(Node node, double fromY, double ms) {
        if (ThemeManager.get().isReduceMotion()) {
            node.setOpacity(1);
            node.setTranslateY(0);
            return;
        }
        node.setOpacity(0);
        node.setTranslateY(fromY);

        FadeTransition fade = new FadeTransition(Duration.millis(ms), node);
        fade.setInterpolator(DesignTokens.motionInterpolator());
        fade.setToValue(1);

        TranslateTransition translate = new TranslateTransition(Duration.millis(ms), node);
        translate.setInterpolator(DesignTokens.motionInterpolator());
        translate.setToY(0);

        own(new ParallelTransition(fade, translate)).play();
    }




    // ---------------------------------------------------------------------
    // Stats / text
    // ---------------------------------------------------------------------
    private String initials(String name) {
        return UiFormat.initials(name);
    }

    private Scene themedScene(Parent root, double width, double height) {
        // UiNodes applies LightDesignTokens.LIGHT_BG for the active theme.
        return UiNodes.themedScene(root, width, height);
    }

    private void stopDashboardRefresh() {
        // DashboardView owns refresh cancellation.
    }

    private String role(UserRole role) {
        if (role == UserRole.ADMIN)
            return "Administrator";
        if (role == UserRole.ATTENDANT)
            return "Attendant";
        return "Customer";
    }

    // ---------------------------------------------------------------------
    // Generic UI helpers
    // ---------------------------------------------------------------------



    private Node buildComingSoonPage(String title, String subtitle) {
        VBox page = new VBox(
                8,
                UiNodes.label(title, 26, TEXT, true),
                UiNodes.label(subtitle, 14, MUTED, false));
        page.setPadding(new Insets(35));
        page.getStyleClass().add("page-root");
        return page;
    }


    private Node buildUserManagement() {
        return new UserManagementView(
                userService,
                ticketService,
                paymentService,
                currentUser,
                () -> navigateTo(buildDashboardContent(null)),
                () -> navigateTo(buildGarageAccess())).build();
    }

    private Node buildGarageManagement() {
        if (garageContext == null) garageContext = new GarageContext(garageService, currentUser);
        return new OperationsViewFactory(ticketService, paymentService, parkingService, userService,
                garageService, garage, currentUser, garageContext, backgroundTasks,
                message -> toast(message, TEAL), ignored -> {},
                () -> navigateTo(buildDashboardContent(null)))
                .garageManagement(() -> navigateTo(buildGarageAccess())).build();
    }

    private Node buildGarageAccess() {
        if (garageContext == null) garageContext = new GarageContext(garageService, currentUser);
        return new OperationsViewFactory(ticketService, paymentService, parkingService, userService,
                garageService, garage, currentUser, garageContext, backgroundTasks,
                message -> toast(message, TEAL), ignored -> {},
                () -> navigateTo(buildDashboardContent(null))).garageAccessView().build();
    }

    private Node buildAnalyticsReports() {
        if (analyticsView != null) analyticsView.cancelAndClose();
        analyticsView = new OperationsViewFactory(ticketService, paymentService, parkingService, userService,
                garageService, garage, currentUser, garageContext, backgroundTasks,
                message -> toast(message, TEAL), ignored -> {},
                () -> navigateTo(buildDashboardContent(null))).analyticsView();
        return analyticsView.build();
    }

    private Node buildSettings() {
        return new SettingsView(
                userService,
                garage,
                currentUser,
                toastManager,
                notificationService,
                dutyService,
                light -> applyThemeChanges(),
                () -> navigateTo(buildDashboardContent(null))).build();
    }

    private void applyThemeChanges() {
        if (stage == null || stage.getScene() == null) return;
        Scene scene = stage.getScene();
        ThemeManager.get().apply(scene);
        if (dashboardRoot != null) {
            ThemeManager.get().apply(dashboardRoot);
        }
        if (scene.getRoot() != null) {
            scene.getRoot().applyCss();
            scene.getRoot().requestLayout();
        }
    }

    private void navigateTo(Node page) {
        if (shuttingDown || dashboardRoot == null || page == null)
            return;
        if (analyticsView != null && !analyticsView.owns(page)) {
            analyticsView.cancelAndClose();
            analyticsView = null;
        }
        if (ticketView != null && !ticketView.owns(page)) {
            ticketView.dispose();
            ticketView = null;
        }
        stopOwnedAnimations();
        stopDashboardRefresh();
        dashboardRoot.getChildren().setAll(page);
        applyAccessibility(page);
        installGlobalOverlays();
        ThemeManager.get().apply(dashboardRoot);
        animateIn(page, 10, 220);
    }




    private void installButtonMotion(Scene scene) {
        scene.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
            if (!ThemeManager.get().isReduceMotion() && e.getTarget() instanceof Button button && !button.isDisabled()) {
                button.setScaleX(.98); button.setScaleY(.98);
            }
        });
        scene.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_RELEASED, e -> {
            if (e.getTarget() instanceof Button button) {
                // Phase 4: release springs back with a decelerate ease, not a dead snap.
                ScaleTransition reset = new ScaleTransition(DesignTokens.motionDuration(MOTION_FAST), button);
                reset.setToX(1); reset.setToY(1);
                reset.setInterpolator(DesignTokens.motionDecelerate());
                own(reset).play();
            }
        });
    }

    /** Phase 4: staggered list entrance — children cascade with a 35ms step. */

    private void installAccessibility(Scene scene, Parent root) {
        applyAccessibility(root);
        scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.isShortcutDown() && e.isShiftDown() && e.getCode() == javafx.scene.input.KeyCode.H) {
                ThemeManager.get().setHighContrast(!ThemeManager.get().isHighContrast());
                ThemeManager.get().apply(scene);
                e.consume();
            } else if (e.isShortcutDown() && e.isShiftDown() && e.getCode() == javafx.scene.input.KeyCode.M) {
                ThemeManager.get().setReduceMotion(!ThemeManager.get().isReduceMotion());
                ThemeManager.get().apply(scene);
                e.consume();
            }
        });
    }

    private void applyAccessibility(Node node) {
        if (node instanceof Button button && (button.getAccessibleText() == null || button.getAccessibleText().isBlank())) {
            String text = button.getText();
            button.setAccessibleText(text == null || text.isBlank() ? "Button" : text);
            button.getStyleClass().add("focus-visible");
        }
        if (node instanceof TextInputControl input && (input.getAccessibleText() == null || input.getAccessibleText().isBlank())) {
            String prompt = input.getPromptText();
            input.setAccessibleText(prompt == null || prompt.isBlank() ? "Text field" : prompt);
            input.getStyleClass().add("focus-visible");
        }
        if (node instanceof Region region && region.getId() != null && Set.of("toast-card", "modal-card", "phase6-modal").contains(region.getId())) {
            // Ensure overlay regions have accessible description
            if (region.getAccessibleText() == null || region.getAccessibleText().isBlank()) {
                region.setAccessibleText("Dialog or notification container");
            }
            region.getStyleClass().add("focus-visible");
        }
        if (node instanceof Parent parent) for (Node child : parent.getChildrenUnmodifiable()) applyAccessibility(child);
    }

    private void installCommandPalette(Scene scene) {
        scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.K && e.isShortcutDown()) {
                e.consume();
                CommandPalette.show(dashboardRoot, commandList());
            }
        });
    }

    private List<CommandPalette.Command> commandList() {
        List<CommandPalette.Command> commands = new ArrayList<>();
        commands.add(new CommandPalette.Command("Dashboard", "Open the operations dashboard", () -> navigateTo(buildDashboardContent(shellRoot))));
        commands.add(new CommandPalette.Command("Parking", "Browse garage spots", () -> navigateTo(buildParkingManagement(shellRoot))));
        commands.add(new CommandPalette.Command("Tickets", "Manage active tickets and payments", () -> navigateTo(buildTicketManagement(shellRoot))));
        commands.add(new CommandPalette.Command("Payments", "Open payment history", () -> navigateTo(buildTicketManagement(shellRoot))));
        commands.add(new CommandPalette.Command("Reports", "Open analytics and reports", () -> navigateTo(buildAnalyticsReports())));
        commands.add(new CommandPalette.Command("Settings", "Manage account and garage settings", () -> navigateTo(buildSettings())));
        if (currentUser != null && userService.isAdmin(currentUser)) {
            commands.add(new CommandPalette.Command("Users", "Manage users and roles", () -> navigateTo(buildUserManagement())));
        }
        if (ticketService != null) for (Ticket ticket : ticketService.getTicketsFor(currentUser)) {
            commands.add(new CommandPalette.Command("Ticket " + ticket.getTicketId(), "Ticket " + ticket.getStatus(), () -> navigateTo(buildTicketManagement(shellRoot))));
        }
        for (User user : userService.getAllUsers()) {
            commands.add(new CommandPalette.Command("User " + user.getFullName(), "@" + user.getUsername() + " · " + role(user.getRole()), () -> navigateTo(buildUserManagement())));
        }
        if (paymentService != null) for (var payment : paymentService.getPaymentsFor(currentUser)) {
            commands.add(new CommandPalette.Command("Payment " + payment.getPaymentId(), "Payment " + payment.getStatus(), () -> navigateTo(buildAnalyticsReports())));
        }
        return commands;
    }


    private void installGlobalOverlays() {
        if (dashboardRoot == null) {
            return;
        }

        Node toastOverlay = toastManager.getOverlay();

        dashboardRoot.getChildren().remove(toastOverlay);
        dashboardRoot.getChildren().add(toastOverlay);

        StackPane.setAlignment(toastOverlay, Pos.BOTTOM_RIGHT);

        if (notificationCenter != null) {
            dashboardRoot.getChildren().remove(notificationCenter.getPanel());
            dashboardRoot.getChildren().add(notificationCenter.getPanel());

            StackPane.setAlignment(
                    notificationCenter.getPanel(),
                    Pos.TOP_RIGHT);

            StackPane.setMargin(
                    notificationCenter.getPanel(),
                    new Insets(58, 18, 0, 0));

            notificationCenter.hide();
        }
    }

    private void updateNotificationBadge() {
        if (notificationBadge == null || notificationCenter == null)
            return;
        int unread = notificationCenter.unreadCount();
        notificationBadge.setText(unread > 0 ? String.valueOf(unread) : "");
        notificationBadge.setTextFill(Color.web(unread > 0 ? RED : MUTED));
        notificationBadge.setTooltip(new Tooltip(unread + " unread notification" + (unread == 1 ? "" : "s")));
    }

    private void startOverstayTimer() {
        stopOverstayTimer();
        overstayTimer = new Timeline(
                new KeyFrame(Duration.minutes(5), e -> checkOverstayTickets()));
        overstayTimer.setCycleCount(Animation.INDEFINITE);
        overstayTimer.play();
    }

    private void stopOverstayTimer() {
        if (overstayTimer != null) {
            overstayTimer.stop();
            overstayTimer = null;
        }
    }

    private void checkOverstayTickets() {
        if (notificationCenter == null || ticketService == null) {
            return;
        }

        Set<String> currentlyExpired = new HashSet<>();
        for (Ticket ticket : ticketService.getTicketsFor(currentUser)) {
            if (ticket.getStatus() == TicketStatus.ACTIVE
                    && ticketService.isTicketExpired(ticket)) {
                currentlyExpired.add(ticket.getTicketId());
                if (notifiedOverstayTickets.add(ticket.getTicketId())) {
                    notificationCenter.add(
                            "Overstay alert: ticket " + ticket.getTicketId()
                                    + " has exceeded " + AppConfig.maxParkHours() + " hours.",
                            LocalDateTime.now().format(TIME),
                            "WARNING");
                }
            }
        }

        notifiedOverstayTickets.retainAll(currentlyExpired);
        if (reservationService != null && notificationCenter != null
                && !reservationService.expire(LocalDateTime.now()).isEmpty()) {
            notificationCenter.refresh();
            updateNotificationBadge();
        }
    }






    private void logout() {
        if (shuttingDown) return;
        stopOwnedAnimations();
        stopDashboardRefresh();
        stopOverstayTimer();
        try {
            userService.logoutUser(currentUser);
        } catch (RuntimeException ignored) {
        }

        currentUser = null;
        showLogin();
    }

    public void toast(String message, String accent) {
        if (toastManager == null)
            return;

        if (GREEN.equalsIgnoreCase(accent)) {
            toastManager.showSuccess("Success", message);
        } else if (RED.equalsIgnoreCase(accent)) {
            toastManager.showError("Action failed", message);
        } else if (YELLOW.equalsIgnoreCase(accent)) {
            toastManager.showWarning("Warning", message);
        } else {
            toastManager.showInfo("ParkingOS", message);
        }
    }

    public TicketPaymentView ticketView() {
        return ticketView;
    }

    public boolean isShuttingDown() {
        return shuttingDown;
    }

    @Override
    public void stop() {
        shutdownResources();
    }

    /** Shared cleanup path for a window close, startup failure, and Application.stop(). */
    private void shutdownResources() {
        if (shutdownComplete) return;
        shutdownComplete = true;
        shuttingDown = true;

        // Stop recurring work first so no new UI/database work can begin.
        stopOwnedAnimations();
        stopDashboardRefresh();
        stopOverstayTimer();
        if (ticketView != null) ticketView.dispose();
        if (analyticsView != null) analyticsView.cancelAndClose();

        // BackgroundTaskRunner and ScheduledReportService cancel without waiting
        // on the FX thread; their callbacks are suppressed after closure.
        backgroundTasks.close();
        toastManager.clear();
        if (notificationCenter != null) notificationCenter.hide();

        // SQLite close is the final resource operation. PersistenceStore.close()
        // is idempotent, so repeated lifecycle notifications are harmless.
        persistence.close();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
