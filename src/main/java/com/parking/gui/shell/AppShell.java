package com.parking.gui.shell;

import com.parking.enums.UserRole;
import com.parking.gui.DesignTokens;
import com.parking.gui.GarageSelectorView;
import com.parking.gui.IconView;
import com.parking.gui.NotificationCenter;
import com.parking.gui.ThemeManager;
import com.parking.gui.UiFormat;
import com.parking.gui.UiNodes;
import com.parking.model.User;
import com.parking.services.GarageContext;
import com.parking.services.GarageService;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import static com.parking.gui.DesignTokens.MUTED;
import static com.parking.gui.DesignTokens.RED;
import static com.parking.gui.DesignTokens.TEXT;
import static com.parking.gui.DesignTokens.TEAL;

/** Owns the persistent application shell, navigation, and responsive sidebar state. */
public final class AppShell {
    public interface PageFactory {
        Node dashboard(BorderPane root);
        Node customerVehicles(BorderPane root);
        Node parking(BorderPane root);
        Node tickets(BorderPane root);
        Node wallet(BorderPane root);
        Node settings();
        Node comingSoon(String title, String subtitle);
        Node users();
        Node garages();
        Node garageAccess();
        Node analytics();
        Node maintenance();
    }

    private final BorderPane root;
    private final StackPane content;
    private final Consumer<Node> navigator;
    private final User currentUser;
    private final GarageService garageService;
    private final GarageContext garageContext;
    private final NotificationCenter notificationCenter;
    private final PageFactory pages;
    private final Consumer<VBox> navigationBuilder;
    private final Consumer<String> errorToast;
    private final Runnable logout;

    private VBox sidebar;
    private VBox sidebarBrand;
    private Label sidebarBrandTitle;
    private Label sidebarBrandSubtitle;
    private Label sidebarWorkspaceLabel;
    private VBox sidebarUserDetails;
    private Button sidebarLogout;
    private boolean sidebarCollapsed = ThemeManager.get().isSidebarCollapsed();
    private boolean mobileSidebarOpen;
    private final List<Button> sidebarButtons = new ArrayList<>();
    private final Map<Button, String> sidebarTitles = new HashMap<>();
    private Button activeSidebarButton;

    public AppShell(BorderPane root, StackPane content, Consumer<Node> navigator, User currentUser,
                    GarageService garageService, GarageContext garageContext,
                    NotificationCenter notificationCenter, PageFactory pages, Consumer<VBox> navigationBuilder,
                    Consumer<String> errorToast, Runnable logout) {
        this.root = Objects.requireNonNull(root);
        this.content = Objects.requireNonNull(content);
        this.navigator = Objects.requireNonNull(navigator);
        this.currentUser = Objects.requireNonNull(currentUser);
        this.garageService = Objects.requireNonNull(garageService);
        this.garageContext = Objects.requireNonNull(garageContext);
        this.notificationCenter = Objects.requireNonNull(notificationCenter);
        this.pages = Objects.requireNonNull(pages);
        this.navigationBuilder = Objects.requireNonNull(navigationBuilder);
        this.errorToast = Objects.requireNonNull(errorToast);
        this.logout = Objects.requireNonNull(logout);
    }

    public BorderPane root() {
        return root;
    }

    public StackPane content() {
        return content;
    }

    public void navigate(Node page) {
        navigator.accept(Objects.requireNonNull(page));
    }

    public VBox sidebar() {
        if (sidebar == null) {
            sidebar = buildSidebar();
        }
        return sidebar;
    }

    public HBox header() {
        HBox header = new HBox(16);
        header.getStyleClass().add("app-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(13, 24, 13, 24));

        VBox heading = new VBox(2,
                DesignTokens.text("Workspace", 11, MUTED, true),
                DesignTokens.text(role(currentUser.getRole()), 18, TEXT, true));
        Region spacer = UiNodes.spacer();
        ComboBox<String> garageSelector = new GarageSelectorView(
                garageService, garageContext, currentUser, message -> errorToast.accept(message)).build();
        Button notifications = new Button("Notifications", IconView.of(IconView.Name.BELL, 16, MUTED));
        notifications.getStyleClass().add("header-action");
        notifications.setOnAction(e -> notificationCenter.toggleAt(notifications));

        Label avatar = DesignTokens.text(UiFormat.initials(currentUser.getFullName()), 12, DesignTokens.BG, true);
        avatar.setAlignment(Pos.CENTER);
        avatar.setMinSize(32, 32);
        avatar.getStyleClass().add("avatar-solid");
        VBox identity = new VBox(1,
                DesignTokens.text(currentUser.getFullName(), 12, TEXT, true),
                DesignTokens.text(role(currentUser.getRole()), 10, MUTED, false));
        HBox profile = new HBox(9, avatar, identity);
        profile.setAlignment(Pos.CENTER_LEFT);
        profile.setAccessibleRole(javafx.scene.AccessibleRole.PARENT);
        profile.setAccessibleText("Signed in as " + currentUser.getFullName());

        header.getChildren().addAll(heading, spacer, garageSelector, notifications, profile);
        return header;
    }

    public void toggleMobileSidebar(Button hamburger) {
        mobileSidebarOpen = !mobileSidebarOpen;
        root.setLeft(mobileSidebarOpen ? sidebar() : null);
        hamburger.setGraphic(IconView.of(
                mobileSidebarOpen ? IconView.Name.CLOSE : IconView.Name.LIST, 20, TEXT));
    }

    public void applyResponsiveLayout(double width, Button hamburger) {
        boolean mobile = width < 768;
        if (mobile) {
            root.setLeft(mobileSidebarOpen ? sidebar() : null);
            hamburger.setVisible(true);
            applySidebarState(false, false);
        } else {
            mobileSidebarOpen = false;
            root.setLeft(sidebar());
            hamburger.setVisible(false);
            applySidebarState(width < 1200 || ThemeManager.get().isSidebarCollapsed(), false);
        }
    }

    private VBox buildSidebar() {
        VBox side = new VBox(8);
        sidebarButtons.clear();
        sidebarTitles.clear();
        activeSidebarButton = null;
        side.getStyleClass().add("sidebar");
        side.setPrefWidth(240);
        side.setPadding(new Insets(22, 14, 16, 14));

        VBox brand = new VBox(2,
                new HBox(8, IconView.of(IconView.Name.PARKING, 20, TEAL),
                        DesignTokens.text("PARKINGOS", 20, TEXT, true)),
                DesignTokens.text("ENTERPRISE PLATFORM", 9, TEAL, true));
        brand.setPadding(new Insets(0, 8, 18, 8));
        sidebarBrand = brand;
        sidebarBrandTitle = (Label) ((HBox) brand.getChildren().get(0)).getChildren().get(1);
        sidebarBrandSubtitle = (Label) brand.getChildren().get(1);

        VBox nav = new VBox(5);
        nav.getChildren().add(DesignTokens.text(currentUser.getRole().name() + " WORKSPACE", 10, MUTED, true));
        sidebarWorkspaceLabel = (Label) nav.getChildren().get(0);
        nav.setSpacing(7);
        navigationBuilder.accept(nav);

        Region push = UiNodes.spacer();
        VBox.setVgrow(push, Priority.ALWAYS);
        Label avatar = DesignTokens.text(UiFormat.initials(currentUser.getFullName()), 13, DesignTokens.BG, true);
        avatar.setAlignment(Pos.CENTER);
        avatar.setMinSize(36, 36);
        avatar.getStyleClass().add("avatar-solid");
        HBox user = new HBox(10, avatar, new VBox(2,
                DesignTokens.text(currentUser.getFullName(), 13, TEXT, true),
                DesignTokens.text(role(currentUser.getRole()), 10, MUTED, false)));
        user.setAlignment(Pos.CENTER_LEFT);
        sidebarUserDetails = (VBox) user.getChildren().get(1);

        Button logoutButton = new Button("↪  Logout");
        logoutButton.getStyleClass().add("ghost-button");
        logoutButton.setMaxWidth(Double.MAX_VALUE);
        logoutButton.setAlignment(Pos.CENTER_LEFT);
        logoutButton.setOnAction(e -> logout.run());
        sidebarLogout = logoutButton;
        VBox footer = new VBox(8, UiNodes.separator(), user, logoutButton);
        footer.setFillWidth(true);

        Button collapse = new Button(null, IconView.of(IconView.Name.LIST, 16, MUTED));
        collapse.getStyleClass().add("sidebar-collapse");
        collapse.setTooltip(new Tooltip("Collapse navigation"));
        collapse.setOnAction(e -> applySidebarState(!sidebarCollapsed, true));

        side.getChildren().addAll(collapse, brand, nav, push, footer);
        return side;
    }


    public void navButton(VBox nav, IconView.Name icon, String title, boolean active, Runnable action) {
        Button button = new Button(title, IconView.of(icon, 18, active ? TEAL : MUTED));
        button.setGraphicTextGap(12);
        button.getStyleClass().add(active ? "nav-button-active" : "nav-button");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setPrefHeight(48);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setOnMouseEntered(e -> {
            if (!active) button.getStyleClass().add("nav-button-hover");
        });
        button.setOnMouseExited(e -> button.getStyleClass().remove("nav-button-hover"));
        if (active) activeSidebarButton = button;
        button.setOnAction(e -> {
            setActiveSidebarButton(button);
            action.run();
        });
        nav.getChildren().add(button);
        sidebarButtons.add(button);
        sidebarTitles.put(button, title);
    }

    private void setActiveSidebarButton(Button selected) {
        if (activeSidebarButton != null && activeSidebarButton != selected) {
            activeSidebarButton.getStyleClass().remove("nav-button-active");
            if (!activeSidebarButton.getStyleClass().contains("nav-button")) {
                activeSidebarButton.getStyleClass().add("nav-button");
            }
        }
        selected.getStyleClass().remove("nav-button");
        if (!selected.getStyleClass().contains("nav-button-active")) {
            selected.getStyleClass().add("nav-button-active");
        }
        activeSidebarButton = selected;
    }

    private void applySidebarState(boolean collapsed, boolean persist) {
        sidebarCollapsed = collapsed;
        if (persist) ThemeManager.get().setSidebarCollapsed(collapsed);
        if (sidebar == null) return;
        sidebar.setPrefWidth(collapsed ? 76 : 240);
        sidebar.setMinWidth(collapsed ? 76 : 240);
        sidebar.setMaxWidth(collapsed ? 76 : 240);
        sidebarBrand.setAlignment(collapsed ? Pos.CENTER : Pos.CENTER_LEFT);
        sidebarBrand.setPadding(collapsed ? new Insets(0, 0, 18, 0) : new Insets(0, 8, 18, 8));
        sidebarBrandTitle.setVisible(!collapsed);
        sidebarBrandTitle.setManaged(!collapsed);
        sidebarBrandSubtitle.setVisible(!collapsed);
        sidebarBrandSubtitle.setManaged(!collapsed);
        sidebarWorkspaceLabel.setVisible(!collapsed);
        sidebarWorkspaceLabel.setManaged(!collapsed);
        sidebarUserDetails.setVisible(!collapsed);
        sidebarUserDetails.setManaged(!collapsed);
        sidebarLogout.setText(collapsed ? "↪" : "↪  Logout");
        sidebarLogout.setAlignment(collapsed ? Pos.CENTER : Pos.CENTER_LEFT);
        for (Button button : sidebarButtons) {
            String title = sidebarTitles.get(button);
            button.setText(collapsed ? "" : title);
            button.setTooltip(collapsed ? new Tooltip(title) : null);
            button.setGraphicTextGap(collapsed ? 0 : 12);
            button.setAlignment(collapsed ? Pos.CENTER : Pos.CENTER_LEFT);
        }
    }

    private String role(UserRole role) {
        if (role == UserRole.ADMIN) return "Administrator";
        if (role == UserRole.ATTENDANT) return "Attendant";
        return "Customer";
    }
}
