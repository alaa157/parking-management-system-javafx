package com.parking.gui;

import com.parking.enums.SpotStatus;
import com.parking.enums.TicketStatus;
import com.parking.model.Customer;
import com.parking.model.ParkingGarage;
import com.parking.model.Ticket;
import com.parking.model.User;
import com.parking.model.Vehicle;
import com.parking.gui.BackgroundTaskRunner;
import com.parking.services.OperationsSnapshot;
import com.parking.services.OperationsSnapshotService;
import com.parking.services.PaymentService;
import com.parking.services.ParkingService;
import com.parking.services.TicketService;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.parking.gui.DesignTokens.*;

/** Dashboard page for all roles, including the customer parking summary. */
public final class DashboardView {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("EEE, dd MMM  •  HH:mm");
    private final ParkingGarage garage;
    private final ParkingService parking;
    private final TicketService tickets;
    private final PaymentService payments;
    private final OperationsSnapshotService snapshots;
    private final BackgroundTaskRunner backgroundTasks;
    private final User actor;
    private final Consumer<String> toast;
    private final Consumer<Node> navigate;
    private final Function<BorderPane, Node> parkingPage;
    private final Function<BorderPane, Node> vehiclePage;
    private final Function<BorderPane, Node> ticketPage;
    private final Function<BorderPane, Node> walletPage;
    private final List<javafx.animation.Animation> animations = new java.util.ArrayList<>();
    private Timeline refreshTimer;
    private ComboBox<String> refreshInterval;
    private int refreshGeneration;
    private Label liveRevenueLabel;
    private Label liveTicketsLabel;
    private Label liveOccupancyLabel;
    private ProgressBar liveOccupancyBar;
    private XYChart.Series<Number, Number> liveSeries;

    public DashboardView(ParkingGarage garage, ParkingService parking, TicketService tickets,
                         PaymentService payments, OperationsSnapshotService snapshots,
                         BackgroundTaskRunner backgroundTasks, User actor, Consumer<String> toast,
                         Consumer<Node> navigate, Function<BorderPane, Node> parkingPage,
                         Function<BorderPane, Node> vehiclePage, Function<BorderPane, Node> ticketPage,
                         Function<BorderPane, Node> walletPage) {
        this.garage = Objects.requireNonNull(garage);
        this.parking = Objects.requireNonNull(parking);
        this.tickets = Objects.requireNonNull(tickets);
        this.payments = Objects.requireNonNull(payments);
        this.snapshots = Objects.requireNonNull(snapshots);
        this.backgroundTasks = Objects.requireNonNull(backgroundTasks);
        this.actor = Objects.requireNonNull(actor);
        this.toast = Objects.requireNonNull(toast);
        this.navigate = Objects.requireNonNull(navigate);
        this.parkingPage = Objects.requireNonNull(parkingPage);
        this.vehiclePage = Objects.requireNonNull(vehiclePage);
        this.ticketPage = Objects.requireNonNull(ticketPage);
        this.walletPage = Objects.requireNonNull(walletPage);
    }

    public Node build(BorderPane shell) {
        return actor instanceof Customer ? buildCustomerDashboard(shell) : buildOperationsDashboard();
    }

    private Node buildOperationsDashboard() {
        VBox content = new VBox(24);
        content.setPadding(new Insets(24, 28, 30, 28));
        HBox top = new HBox(16);
        top.setAlignment(Pos.CENTER_LEFT);
        TextField search = new TextField();
        search.setPromptText("Search...");
        search.getStyleClass().add("dark-input");
        search.setPrefHeight(38);
        HBox.setHgrow(search, Priority.ALWAYS);
        Button refresh = new Button("Refresh");
        refresh.getStyleClass().add("outline-button");
        refresh.setOnAction(e -> refreshDashboard());
        refreshInterval = new ComboBox<>();
        refreshInterval.getItems().addAll("Off", "5 seconds", "30 seconds");
        refreshInterval.setValue("Off");
        refreshInterval.getStyleClass().add("dark-combo");
        refreshInterval.valueProperty().addListener((obs, oldValue, value) -> applyRefreshInterval());
        Label time = DesignTokens.text(LocalDateTime.now().format(TIME), 12, MUTED, true);
        top.getChildren().addAll(search, refresh, refreshInterval, time);

        OperationsSnapshot initial = snapshots.snapshot(actor, LocalDateTime.now());
        int total = garage.getTotalCapacity();
        int available = initial.available();
        double occupancy = initial.occupancyPercent();
        GridPane grid = new GridPane();
        grid.setHgap(20); grid.setVgap(20);
        ColumnConstraints left = new ColumnConstraints(); left.setPercentWidth(50);
        ColumnConstraints right = new ColumnConstraints(); right.setPercentWidth(50);
        grid.getColumnConstraints().addAll(left, right);
        grid.add(kpi("TOTAL SPOTS", String.valueOf(total), "Available: " + available + "/" + total, "Live", TEAL), 0, 0);
        grid.add(kpi("REVENUE TODAY", UiFormat.money(initial.completedRevenue()), "Completed payments", "Live", TEAL), 1, 0);
        grid.add(kpi("ACTIVE TICKETS", String.valueOf(initial.activeTicketCount()), "Garage sessions", "Live", ORANGE), 0, 1);
        grid.add(occupancyKpi(occupancy), 1, 1);
        content.getChildren().addAll(top,
                new VBox(3, DesignTokens.text("Welcome back, " + actor.getFullName() + "!", 24, TEXT, true),
                        DesignTokens.text("Here's what's happening today", 14, MUTED, false)), grid, activity());
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true); scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("dark-scroll");
        applyRefreshInterval(); refreshDashboard();
        return scroll;
    }

    private Node buildCustomerDashboard(BorderPane shell) {
        Customer customer = (Customer) actor;
        List<Ticket> customerTickets = tickets.getTicketsFor(customer);
        Ticket active = customerTickets.stream().filter(t -> t.getStatus() == TicketStatus.ACTIVE)
                .max(Comparator.comparing(Ticket::getEntryTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
        VBox content = new VBox(20); content.setPadding(new Insets(26, 30, 32, 30));
        content.getChildren().add(new VBox(4,
                DesignTokens.text("Good morning, " + firstName(customer.getFullName()), 25, TEXT, true),
                DesignTokens.text("Here is your current parking status.", 13, MUTED, false)));
        VBox status = new VBox(14); status.getStyleClass().add("card"); status.setPadding(new Insets(20));
        status.getChildren().add(new HBox(12, DesignTokens.text(active == null ? "Parking status" : "Currently parked", 18, TEXT, true), UiNodes.spacer()));
        if (active == null) {
            HBox empty = new HBox(18, IconView.of(IconView.Name.PARKING, 30, MUTED),
                    new VBox(3, DesignTokens.text("Not currently parked", 16, TEXT, true),
                            DesignTokens.text("Park one of your registered vehicles when you are ready.", 12, MUTED, false)));
            empty.setAlignment(Pos.CENTER_LEFT);
            Button park = new Button("Park a vehicle", IconView.of(IconView.Name.PARKING, 16, BG));
            park.getStyleClass().add("primary-button"); park.setOnAction(e -> navigate.accept(parkingPage.apply(shell)));
            status.getChildren().addAll(empty, park);
        } else {
            Vehicle vehicle = garage.getRegisteredVehicle(active.getVehicleId());
            String vehicleName = vehicle == null ? active.getVehicleId() : safe(vehicle.getLicensePlate(), vehicle.getVehicleId());
            HBox details = new HBox(32, detail("VEHICLE", vehicleName), detail("SPOT", safe(active.getParkingSpotId(), "—")),
                    detail("ENTRY", active.getEntryTime() == null ? "—" : active.getEntryTime().format(DateTimeFormatter.ofPattern("HH:mm"))),
                    new VBox(4, DesignTokens.text("STATUS", 10, MUTED, true), statusBadge("Parked")));
            Button view = new Button("View ticket", IconView.of(IconView.Name.TICKET, 15, BG));
            view.getStyleClass().add("primary-button"); view.setOnAction(e -> navigate.accept(ticketPage.apply(shell)));
            status.getChildren().addAll(details, view);
        }
        HBox summaries = new HBox(14, summary("MY VEHICLES", String.valueOf(customer.getVehicleIds().size()), "registered"),
                summary("ACTIVE TICKET", active == null ? "None" : "Active", active == null ? "No current session" : active.getTicketId()),
                summary("WALLET", UiFormat.money(customer.getWalletBalance()), "available balance"));
        summaries.getChildren().forEach(child -> HBox.setHgrow(child, Priority.ALWAYS));
        VBox recent = new VBox(12); recent.getStyleClass().add("card"); recent.setPadding(new Insets(18));
        recent.getChildren().addAll(DesignTokens.text("Recent activity", 17, TEXT, true), DesignTokens.text("Your latest parking sessions", 12, MUTED, false));
        customerTickets.stream().sorted(Comparator.comparing(Ticket::getEntryTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5).forEach(ticket -> recent.getChildren().add(new HBox(16,
                        DesignTokens.text(ticket.getTicketId(), 12, TEXT, true),
                        DesignTokens.text(safe(ticket.getVehicleId(), "—"), 12, MUTED, false), UiNodes.spacer(),
                        DesignTokens.text(ticket.getStatus().name(), 11, ticket.getStatus() == TicketStatus.ACTIVE ? RED : GREEN, true))));
        if (recent.getChildren().size() == 2) recent.getChildren().add(DesignTokens.text("No ticket activity yet.", 13, MUTED, false));
        content.getChildren().addAll(status, summaries, recent);
        ScrollPane scroll = new ScrollPane(content); scroll.setFitToWidth(true); scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("dark-scroll"); return scroll;
    }

    private VBox occupancyKpi(double percent) {
        VBox box = new VBox(10); box.setPadding(new Insets(18)); box.setMinHeight(145); box.getStyleClass().add("card");
        HBox title = new HBox(DesignTokens.text("OCCUPANCY RATE", 11, MUTED, true), UiNodes.spacer(),
                DesignTokens.text(percent > 80 ? "High" : "Healthy", 10, percent > 80 ? YELLOW : GREEN, true));
        title.setAlignment(Pos.CENTER_LEFT);
        liveOccupancyLabel = DesignTokens.text(String.format(Locale.US, "%.0f%%", percent), 32, TEAL, true);
        liveOccupancyBar = new ProgressBar(Math.max(0, Math.min(1, percent / 100))); liveOccupancyBar.setMaxWidth(Double.MAX_VALUE);
        liveOccupancyBar.getStyleClass().add("teal-progress");
        box.getChildren().addAll(title, liveOccupancyLabel, liveOccupancyBar);
        return box;
    }

    private VBox kpi(String title, String value, String detail, String trend, String accent) {
        VBox box = new VBox(5, DesignTokens.text(title, 10, MUTED, true), DesignTokens.text(value, 21, TEAL, true), DesignTokens.text(detail, 11, MUTED, false));
        box.getStyleClass().add("card"); box.setPadding(new Insets(15));
        if ("REVENUE TODAY".equals(title)) liveRevenueLabel = (Label) box.getChildren().get(1);
        if ("ACTIVE TICKETS".equals(title)) liveTicketsLabel = (Label) box.getChildren().get(1);
        return box;
    }

    private VBox activity() {
        VBox card = new VBox(0); card.getStyleClass().add("card"); card.setPadding(new Insets(18));
        card.getChildren().add(new VBox(3, DesignTokens.text("Recent Activity", 17, TEXT, true), DesignTokens.text("Latest garage events", 12, MUTED, false)));
        String[][] events = {{"ENTRY", "Vehicle ABC-123 entered the garage", "2 min ago", GREEN}, {"PAYMENT", "Payment processed successfully", "8 min ago", TEAL}, {"EXIT", "Vehicle XYZ-789 exited Level 2", "14 min ago", ORANGE}};
        for (String[] event : events) card.getChildren().add(new HBox(12, new javafx.scene.shape.Circle(4.5, DesignTokens.color(event[3])),
                new VBox(2, DesignTokens.text(event[1], 12, TEXT, false), DesignTokens.text(event[0] + "  •  " + event[2], 10, MUTED, true))));
        return card;
    }

    private void refreshDashboard() {
        if (backgroundTasks.isClosed()) return;
        int generation = refreshGeneration;
        backgroundTasks.submit(() -> snapshots.snapshot(actor, LocalDateTime.now()), snapshot -> {
            if (generation != refreshGeneration) return;
            liveRevenueLabel.setText(UiFormat.money(snapshot.completedRevenue()));
            liveTicketsLabel.setText(String.valueOf(snapshot.activeTicketCount()));
            liveOccupancyLabel.setText(String.format(Locale.US, "%.0f%%", snapshot.occupancyPercent()));
            liveOccupancyBar.setProgress(snapshot.occupancyPercent() / 100);
        }, error -> { if (generation == refreshGeneration) toast.accept("Dashboard refresh failed: " + error.getMessage()); });
    }

    private void applyRefreshInterval() {
        if (refreshTimer != null) refreshTimer.stop();
        if (refreshInterval == null) return;
        double seconds = "5 seconds".equals(refreshInterval.getValue()) ? 5 : "30 seconds".equals(refreshInterval.getValue()) ? 30 : 0;
        if (seconds <= 0) return;
        refreshTimer = new Timeline(new KeyFrame(Duration.seconds(seconds), e -> refreshDashboard()));
        refreshTimer.setCycleCount(Animation.INDEFINITE); refreshTimer.play();
    }

    private VBox summary(String title, String value, String detail) {
        VBox box = new VBox(5, DesignTokens.text(title, 10, MUTED, true), DesignTokens.text(value, 21, TEAL, true), DesignTokens.text(detail, 11, MUTED, false));
        box.getStyleClass().add("card"); box.setPadding(new Insets(15)); return box;
    }
    private VBox detail(String title, String value) { return new VBox(4, DesignTokens.text(title, 10, MUTED, true), DesignTokens.text(value, 14, TEXT, true)); }
    private Label statusBadge(String value) { Label label = DesignTokens.text(value, 11, GREEN, true); label.getStyleClass().addAll("status-badge", "status-badge-parked"); return label; }
    private String firstName(String value) { return value == null || value.isBlank() ? "there" : value.trim().split("\\s+")[0]; }
    private String safe(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
}
