package com.parking.gui;

import com.parking.config.AppConfig;
import com.parking.services.ReportExporter;
import com.parking.services.ScheduledReportService;
import com.parking.enums.PaymentStatus;
import com.parking.enums.TicketStatus;
import com.parking.model.ParkingGarage;
import com.parking.model.Payment;
import com.parking.model.Ticket;
import com.parking.services.TicketService;
import com.parking.services.PaymentService;
import com.parking.services.GarageContext;
import com.parking.model.User;
import com.parking.gui.components.EmptyState;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Popup;
import javafx.util.Duration;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.Future;

import static com.parking.gui.DesignTokens.*;

/**
 * Phase 5 - Analytics & Reporting center content.
 * Uses the application's existing shared sidebar/shell in ParkingApplication.
 */
public final class AnalyticsReportingView {

    public enum OperationState { IDLE, RUNNING, SUCCEEDED, FAILED, CANCELLED }

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("dd MMM");

    private final PaymentService paymentService;
    private final ParkingGarage garage;
    private final TicketService ticketService;
    private final User currentUser;
    private final GarageContext garageContext;
    private final Runnable backToDashboard;
    private final BackgroundTaskRunner backgroundTasks;

    private final StackPane host = new StackPane();
    private LocalDate reportStart = LocalDate.now().minusDays(6);
    private LocalDate reportEnd = LocalDate.now();
    private boolean lightTheme = ThemeManager.get().isLight();
    private Timeline refreshSpinner;
    private Label refreshIcon;
    private final ScheduledReportService reportScheduler = new ScheduledReportService();
    private Future<?> refreshTask;
    private OperationState refreshState = OperationState.IDLE;
    private OperationState exportState = OperationState.IDLE;
    private volatile boolean disposed;
    private final List<Animation> ownedAnimations = new ArrayList<>();
    private ReportSnapshot snapshot = ReportSnapshot.empty();

    private double[] revenue = new double[7];
    private int[] occupancy = new int[24];
    private int[] peak = new int[24];
    private final LinkedHashMap<String, Double> paymentDistribution = new LinkedHashMap<>();

    public AnalyticsReportingView(PaymentService paymentService, ParkingGarage garage,
                                  TicketService ticketService, User currentUser, Runnable backToDashboard) {
        this(paymentService, garage, ticketService, currentUser, null, backToDashboard, new BackgroundTaskRunner());
    }

    public AnalyticsReportingView(PaymentService paymentService, ParkingGarage garage,
                                  TicketService ticketService, User currentUser, Runnable backToDashboard,
                                  BackgroundTaskRunner backgroundTasks) {
        this(paymentService, garage, ticketService, currentUser, null, backToDashboard, backgroundTasks);
    }

    public AnalyticsReportingView(PaymentService paymentService, ParkingGarage garage,
                                  TicketService ticketService, User currentUser, GarageContext garageContext,
                                  Runnable backToDashboard, BackgroundTaskRunner backgroundTasks) {
        this.paymentService = paymentService;
        this.garage = garage;
        this.ticketService = ticketService;
        this.currentUser = currentUser;
        this.garageContext = garageContext;
        this.backToDashboard = backToDashboard;
        this.backgroundTasks = Objects.requireNonNull(backgroundTasks, "backgroundTasks");
        if (garageContext != null) garageContext.addListener(ignored -> requestRefresh(null));
    }

    public Node build() {
        host.getChildren().clear();
        host.getStyleClass().remove("analytics-light");
        host.getStyleClass().add("analytics-host");
        applyAnalyticsTheme();
        host.getChildren().add(buildPage());
        if (snapshot.completedPayments() == 0) {
            host.getChildren().add(new EmptyState(IconView.Name.CHART, "No analytics data yet",
                    "Complete a parking payment to start seeing revenue and occupancy trends.", null, null));
        }
        SkeletonView.show(host, SkeletonView.cards(4), 520);
        requestRefresh(null);
        return host;
    }

    public boolean owns(Node node) { return host == node; }

    private ReportSnapshot computeSnapshot(LocalDate start, LocalDate end) {
        double[] nextRevenue = new double[7];
        int[] nextOccupancy = new int[24];
        int[] nextPeak = new int[24];
        LinkedHashMap<String, Double> nextDistribution = new LinkedHashMap<>();
        List<Payment> payments = visiblePayments();
        List<Ticket> visibleTickets = ticketService == null
                ? Collections.emptyList() : visibleTickets();

        for (Payment payment : payments) {
            if (payment == null || payment.getStatus() != PaymentStatus.COMPLETED) continue;
            if (payment.getPaymentTime() != null) {
                LocalDate paymentDate = payment.getPaymentTime().toLocalDate();
                if (!paymentDate.isBefore(start) && !paymentDate.isAfter(end)) {
                    long daysFromEnd = java.time.temporal.ChronoUnit.DAYS.between(paymentDate, end);
                    if (daysFromEnd >= 0 && daysFromEnd < nextRevenue.length) {
                        nextRevenue[nextRevenue.length - 1 - (int) daysFromEnd] += payment.getFinalAmount();
                    }
                }
            }
            String method = payment.getPaymentType();
            if (method == null || method.isBlank()) method = payment.getPaymentMethod();
            if (method == null || method.isBlank()) method = "Other";
            nextDistribution.merge(prettyMethod(method), 1.0, Double::sum);
        }
        if (!nextDistribution.isEmpty()) {
            double count = nextDistribution.values().stream().mapToDouble(Double::doubleValue).sum();
            nextDistribution.replaceAll((key, value) -> value * 100.0 / count);
        }
        for (Ticket ticket : visibleTickets) {
            if (ticket == null || ticket.getEntryTime() == null) continue;
            nextPeak[ticket.getEntryTime().getHour()]++;
            for (int slot = 0; slot < nextOccupancy.length; slot++) {
                LocalDateTime slotStart = ticket.getEntryTime().toLocalDate().atTime(slot, 0);
                LocalDateTime slotEnd = ticket.getExitTime();
                if (!ticket.getEntryTime().isAfter(slotStart)
                        && (slotEnd == null || slotEnd.isAfter(slotStart))) nextOccupancy[slot]++;
            }
        }
        if (garage != null && garage.getTotalCapacity() > 0) {
            for (int i = 0; i < nextOccupancy.length; i++) {
                nextOccupancy[i] = (int) Math.round(nextOccupancy[i] * 100.0 / garage.getTotalCapacity());
            }
        }
        List<ReportRow> rows = buildReportRows(start, end, visibleTickets, payments);
        double rangeRevenue = payments.stream()
                .filter(p -> p != null && p.getStatus() == PaymentStatus.COMPLETED && p.getPaymentTime() != null
                        && !p.getPaymentTime().toLocalDate().isBefore(start)
                        && !p.getPaymentTime().toLocalDate().isAfter(end))
                .mapToDouble(Payment::getFinalAmount).sum();
        double averageDuration = visibleTickets.stream().filter(t -> t != null && t.getEntryTime() != null)
                .mapToDouble(Ticket::getParkingDuration).average().orElse(0.0);
        return new ReportSnapshot(nextRevenue, nextOccupancy, nextPeak, nextDistribution, rows,
                rangeRevenue, (int) payments.stream().filter(p -> p != null && p.getStatus() == PaymentStatus.COMPLETED).count(),
                averageDuration, garage == null ? 0 : (int) Math.round(garage.getOccupancyRate() * 100));
    }

    private List<ReportRow> buildReportRows(LocalDate start, LocalDate end,
                                            List<Ticket> tickets, List<Payment> payments) {
        List<ReportRow> rows = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            final LocalDate reportDate = date;
            List<Ticket> dayTickets = tickets.stream().filter(t -> t != null && t.getEntryTime() != null
                    && t.getEntryTime().toLocalDate().equals(reportDate)).collect(Collectors.toList());
            List<Payment> dayPayments = payments.stream().filter(p -> p != null && p.getStatus() == PaymentStatus.COMPLETED
                    && p.getPaymentTime() != null && p.getPaymentTime().toLocalDate().equals(reportDate)).toList();
            double dayRevenue = dayPayments.stream().mapToDouble(Payment::getFinalAmount).sum();
            String method = dayPayments.stream().collect(Collectors.groupingBy(p -> prettyMethod(p.getPaymentType()), Collectors.counting()))
                    .entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("N/A");
            double avgDuration = dayTickets.stream().mapToDouble(Ticket::getParkingDuration).average().orElse(0.0);
            rows.add(new ReportRow(scopeLabel(), date.format(SHORT), String.valueOf(dayTickets.size()),
                    String.valueOf(dayTickets.stream().filter(t -> t.getExitTime() != null).count()),
                    money(dayRevenue), avgDuration <= 0 ? "N/A" : String.format(Locale.US, "%.1fh", avgDuration), method));
        }
        return rows;
    }

    private void applySnapshot(ReportSnapshot next) {
        snapshot = next;
        revenue = next.revenue();
        occupancy = next.occupancy();
        peak = next.peak();
        paymentDistribution.clear();
        paymentDistribution.putAll(next.paymentDistribution());
    }

    private void requestRefresh(Button source) {
        if (disposed || refreshState == OperationState.RUNNING) return;
        refreshState = OperationState.RUNNING;
        if (source != null) source.setDisable(true);
        LocalDate start = reportStart;
        LocalDate end = reportEnd;
        refreshTask = backgroundTasks.submit(() -> computeSnapshot(start, end), next -> {
            if (disposed) return;
            applySnapshot(next);
            refreshState = OperationState.SUCCEEDED;
            if (source != null) source.setDisable(false);
            if (refreshSpinner != null) {
                refreshSpinner.stop();
                ownedAnimations.remove(refreshSpinner);
            }
            if (refreshIcon != null) {
                refreshIcon.setGraphic(IconView.of(IconView.Name.CHECK, 16, GREEN));
                refreshIcon.setTextFill(DesignTokens.color(GREEN));
            }
            if (!start.equals(reportStart) || !end.equals(reportEnd)) {
                requestRefresh(source);
                return;
            }
            if (host.getScene() != null) host.getChildren().setAll(buildPage());
            if (source != null) showToast("Analytics data refreshed successfully", GREEN);
        }, error -> {
            if (disposed) return;
            refreshState = OperationState.FAILED;
            if (source != null) source.setDisable(false);
            if (refreshSpinner != null) {
                refreshSpinner.stop();
                ownedAnimations.remove(refreshSpinner);
            }
            showToast("Analytics refresh failed: " + message(error), RED);
        });
    }

    private String message(Throwable error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    private String prettyMethod(String method) {
        if (method == null || method.isBlank()) {
            return "Other";
        }
        String normalized = method.toLowerCase(Locale.ROOT);
        if (normalized.contains("card")) return "Card";
        if (normalized.contains("cash")) return "Cash";
        if (normalized.contains("wallet")) return "Wallet";
        return method;
    }

    private BorderPane buildPage() {
        BorderPane page = new BorderPane();
        page.getStyleClass().add("page-root");

        VBox content = new VBox(20);
        content.setPadding(new Insets(24));

        content.getChildren().addAll(
                buildHeader(),
                buildKpis(),
                buildChartsGrid(),
                buildReportTable()
        );

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("dark-scroll");
        page.setCenter(scroll);

        return page;
    }

    private Node buildHeader() {
        VBox title = new VBox(3,
                text("Analytics & Reports", 28, TEXT, true),
                text("Business intelligence for revenue, occupancy, and operational performance", 14, MUTED, false));

        ComboBox<String> range = new ComboBox<>(FXCollections.observableArrayList(
                "This Month", "Last 7 Days", "Last 30 Days", "This Quarter"));
        range.setValue("This Month");
        range.setPrefWidth(145);
        range.getStyleClass().add("dark-combo");
        range.setTooltip(new Tooltip("Select reporting range"));
        range.setOnAction(e -> {
            LocalDate today = LocalDate.now();
            switch (range.getValue()) {
                case "Last 7 Days" -> setDateRange(today.minusDays(6), today);
                case "Last 30 Days" -> setDateRange(today.minusDays(29), today);
                case "This Quarter" -> setDateRange(today.withMonth(((today.getMonthValue() - 1) / 3) * 3 + 1)
                        .withDayOfMonth(1), today);
                default -> setDateRange(today.withDayOfMonth(1), today);
            }
        });

        Button calendar = iconButton(IconView.Name.CALENDAR);
        calendar.setTooltip(new Tooltip("Date range picker"));
        calendar.setOnAction(e -> showDatePickerPopup(calendar));

        Button refresh = new Button();
        refresh.getStyleClass().add("outline-button");
        refresh.setPrefHeight(38);
        refreshIcon = text("", 16, TEAL, true);
        refreshIcon.setGraphic(IconView.of(IconView.Name.REFRESH, 16, TEAL));
        refresh.setGraphic(refreshIcon);
        refresh.setText("Refresh");
        refresh.setOnAction(e -> refreshNow(refresh));

        MenuButton schedule = new MenuButton("Schedule");
        schedule.getStyleClass().add("outline-button");
        MenuItem everyMinute = new MenuItem("Every minute · local PDF");
        everyMinute.setOnAction(e -> scheduleReport(java.time.Duration.ofMinutes(1), ScheduledReportService.Delivery.LOCAL));
        MenuItem everyHour = new MenuItem("Every hour · email hook");
        everyHour.setOnAction(e -> scheduleReport(java.time.Duration.ofHours(1), ScheduledReportService.Delivery.EMAIL));
        MenuItem stop = new MenuItem("Stop scheduled reports");
        stop.setOnAction(e -> { reportScheduler.cancel(); showToast("Scheduled reports stopped", MUTED); });
        schedule.getItems().addAll(everyMinute, everyHour, stop);

        ToggleButton theme = new ToggleButton("Light", IconView.of(IconView.Name.PALETTE, 16, MUTED));
        theme.getStyleClass().add("outline-button");
        theme.setSelected(lightTheme);
        theme.setOnAction(e -> {
            lightTheme = theme.isSelected();
            theme.setText(lightTheme ? "Dark" : "Light");
            ThemeManager.get().setLight(lightTheme);
            applyAnalyticsTheme();
        });

        Button dashboard = new Button("Dashboard", IconView.of(IconView.Name.BACK, 16, MUTED));
        dashboard.getStyleClass().add("ghost-button");
        dashboard.setPrefHeight(38);
        dashboard.setOnAction(e -> backToDashboard.run());

        HBox controls = new HBox(8, calendar, range, refresh, schedule, theme, dashboard);
        controls.setAlignment(Pos.CENTER_RIGHT);

        HBox top = new HBox(20, title, spacer(), controls);
        top.setAlignment(Pos.CENTER_LEFT);
        return top;
    }

    private void applyAnalyticsTheme() {
        if (lightTheme) {
            if (!host.getStyleClass().contains("analytics-light")) host.getStyleClass().add("analytics-light");
        } else {
            host.getStyleClass().remove("analytics-light");
        }
        if (host.getScene() != null && host.getScene().getRoot() != null) {
            ThemeManager.get().apply(host.getScene());
            host.getScene().getRoot().applyCss();
            host.getScene().getRoot().requestLayout();
        }
    }

    private Node buildKpis() {
        HBox row = new HBox(20);

        String duration = snapshot.averageDuration() <= 0 ? "N/A"
                : String.format(Locale.US, "%.1fh", snapshot.averageDuration());

        row.getChildren().addAll(
                kpiCard("TOTAL REVENUE", "$0", money(snapshot.rangeRevenue()), "Selected date range", TEAL, true),
                kpiCard("TOTAL TRANSACTIONS", "0", String.valueOf(snapshot.completedPayments()), "Completed payments", TEXT, true),
                kpiCard("AVG PARKING DURATION", "0", duration, "Average across all sessions", ORANGE, false),
                kpiCard("PEAK OCCUPANCY", "0%", snapshot.occupancyRate() + "%", "Current garage occupancy", YELLOW, false)
        );
        for (Node node : row.getChildren()) HBox.setHgrow(node, Priority.ALWAYS);
        return row;
    }

    private VBox kpiCard(String title, String initial, String targetText, String sub, String accent, boolean countNumeric) {
        VBox card = new VBox(10);
        card.getStyleClass().add("analytics-kpi");
        card.setPadding(new Insets(18));
        card.setMinHeight(126);

        HBox header = new HBox(text(title, 11, MUTED, true), spacer(), sparklinePlaceholder(accent));
        header.setAlignment(Pos.CENTER_LEFT);
        Label value = text(countNumeric ? initial : targetText, 32, accent, true);
        Label subtitle = text(sub, 12, GREEN, true);

        card.getChildren().addAll(header, value, subtitle, sparkline(accent));

        if (countNumeric) {
            double target = targetText.startsWith("$")
                    ? parseDouble(targetText.substring(1))
                    : parseDouble(targetText);
            playCountUp(value, target, targetText.startsWith("$") ? "$" : "");
        }

        card.setOnMouseEntered(e -> {
            card.getStyleClass().add("analytics-card-hover");
            scale(card, 1.02);
        });
        card.setOnMouseExited(e -> {
            card.getStyleClass().remove("analytics-card-hover");
            scale(card, 1.0);
        });
        return card;
    }

    private Node buildChartsGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(20);
        ColumnConstraints a = new ColumnConstraints();
        a.setPercentWidth(50);
        ColumnConstraints b = new ColumnConstraints();
        b.setPercentWidth(50);
        grid.getColumnConstraints().addAll(a, b);

        grid.add(chartCard("Revenue Trend", "Last 7 Days", buildRevenueChart()), 0, 0);
        grid.add(chartCard("Occupancy Rate", "This Week", buildOccupancyChart()), 1, 0);
        grid.add(chartCard("Payment Methods", "This Month", buildPaymentChart()), 0, 1);
        grid.add(chartCard("Peak Hours", "24-Hour Distribution", buildPeakChart()), 1, 1);
        return grid;
    }

    private VBox chartCard(String title, String subtitle, Node chart) {
        VBox card = new VBox(12,
                new HBox(8, text(title, 16, TEXT, true), text("•  " + subtitle, 12, MUTED, false)),
                chart);
        card.getStyleClass().add("analytics-chart-card");
        card.setPadding(new Insets(16));
        return card;
    }

    private Node buildRevenueChart() {
        CategoryAxis x = new CategoryAxis();
        x.setCategories(FXCollections.observableArrayList("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"));
        NumberAxis y = new NumberAxis(0, 3000, 500);
        y.setForceZeroInRange(true);

        LineChart<String, Number> chart = new LineChart<>(x, y);
        chart.setAnimated(false);
        chart.setLegendVisible(false);
        chart.setCreateSymbols(true);
        chart.setPrefHeight(255);
        chart.getStyleClass().add("premium-line-chart");

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (int i = 0; i < revenue.length; i++) {
            XYChart.Data<String, Number> point = new XYChart.Data<>(x.getCategories().get(i), revenue[i]);
            point.nodeProperty().addListener((obs, old, node) -> {
                if (node != null) attachChartHover(node, money(point.getYValue().doubleValue()));
            });
            series.getData().add(point);
        }
        chart.getData().add(series);
        return chart;
    }

    private Node buildOccupancyChart() {
        CategoryAxis x = new CategoryAxis();
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < 24; i += 3) labels.add(String.format("%02d", i));
        x.setCategories(FXCollections.observableArrayList(labels));
        NumberAxis y = new NumberAxis(0, 100, 20);
        AreaChart<String, Number> chart = new AreaChart<>(x, y);
        chart.setAnimated(false);
        chart.setLegendVisible(false);
        chart.setPrefHeight(255);
        chart.getStyleClass().add("premium-area-chart");

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (int i = 0; i < 24; i += 3) {
            XYChart.Data<String, Number> point = new XYChart.Data<>(String.format("%02d", i), occupancy[i]);
            series.getData().add(point);
            point.nodeProperty().addListener((obs, old, node) -> {
                if (node != null) attachChartHover(node, point.getYValue().intValue() + "% occupancy");
            });
        }
        chart.getData().add(series);
        return chart;
    }

    private Node buildPaymentChart() {
        StackPane wrapper = new StackPane();
        PieChart pie = new PieChart();
        pie.setLegendVisible(false);
        pie.setLabelsVisible(false);
        pie.setStartAngle(90);
        pie.setAnimated(true);
        pie.setPrefSize(220, 220);
        pie.getStyleClass().add("premium-pie-chart");

        for (Map.Entry<String, Double> entry : paymentDistribution.entrySet()) {
            PieChart.Data data = new PieChart.Data(entry.getKey(), entry.getValue());
            pie.getData().add(data);
            data.nodeProperty().addListener((obs, old, node) -> {
                if (node != null) {
                    node.setOnMouseEntered(e -> {
                        ScaleTransition scale = new ScaleTransition(Duration.millis(200), node);
                        scale.setToX(1.06);
                        scale.setToY(1.06);
                        scale.playFromStart();
                        showMiniTooltip(node, entry.getKey() + "  •  " + entry.getValue().intValue() + "%");
                    });
                    node.setOnMouseExited(e -> {
                        ScaleTransition scale = new ScaleTransition(Duration.millis(200), node);
                        scale.setToX(1.0);
                        scale.setToY(1.0);
                        scale.playFromStart();
                    });
                }
            });
        }

        Circle center = new Circle(55, DesignTokens.color(CARD));
        center.setMouseTransparent(true);
        VBox centerText = new VBox(0,
                text(money(rangeRevenue()), 22, TEXT, true),
                text("TOTAL", 10, MUTED, true));
        centerText.setAlignment(Pos.CENTER);
        centerText.setMouseTransparent(true);

        VBox legend = new VBox(7);
        for (Map.Entry<String, Double> e : paymentDistribution.entrySet()) {
            String color = e.getKey().equals("Card") ? BLUE : e.getKey().equals("Cash") ? ORANGE : TEAL;
            HBox item = new HBox(7,
                    new Circle(4, DesignTokens.color(color)),
                    text(e.getKey(), 11, MUTED, true),
                    spacer(),
                    text(e.getValue().intValue() + "%", 11, TEXT, true));
            item.setAlignment(Pos.CENTER_LEFT);
            legend.getChildren().add(item);
        }

        BorderPane body = new BorderPane();
        body.setCenter(new StackPane(pie, center, centerText));
        body.setRight(legend);
        BorderPane.setAlignment(legend, Pos.CENTER);
        BorderPane.setMargin(legend, new Insets(0, 10, 0, 0));
        return body;
    }

    private Node buildPeakChart() {
        CategoryAxis x = new CategoryAxis();
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < 24; i++) labels.add(String.format("%02d", i));
        x.setCategories(FXCollections.observableArrayList(labels));
        NumberAxis y = new NumberAxis(0, 70, 10);

        BarChart<String, Number> chart = new BarChart<>(x, y);
        chart.setAnimated(false);
        chart.setLegendVisible(false);
        chart.setCategoryGap(3);
        chart.setBarGap(1);
        chart.setPrefHeight(255);
        chart.getStyleClass().add("premium-bar-chart");

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (int i = 0; i < peak.length; i++) {
            XYChart.Data<String, Number> point = new XYChart.Data<>(labels.get(i), peak[i]);
            series.getData().add(point);
            point.nodeProperty().addListener((obs, old, node) -> {
                if (node != null) {
                    attachChartHover(node, point.getYValue().intValue() + " vehicles");
                    node.setOnMouseEntered(e -> node.getStyleClass().add("chart-bar-hot"));
                    node.setOnMouseExited(e -> node.getStyleClass().remove("chart-bar-hot"));
                }
            });
        }
        chart.getData().add(series);
        return chart;
    }

    private Node buildReportTable() {
        VBox card = new VBox(14);
        card.getStyleClass().add("analytics-chart-card");
        card.setPadding(new Insets(16));

        Label title = text("Revenue Report — This Month", 16, TEXT, true);
        if (garageContext != null) title = text("Revenue Report — " + scopeLabel(), 16, TEXT, true);
        Button export = new Button("Export CSV");
        export.getStyleClass().add("outline-button");
        export.setOnAction(e -> exportCsv(export));
        Button pdf = new Button("Export PDF");
        pdf.getStyleClass().add("outline-button");
        pdf.setOnAction(e -> exportPdf(pdf));
        HBox header = new HBox(title, spacer(), export, pdf);
        header.setAlignment(Pos.CENTER_LEFT);

        TableView<ReportRow> table = new TableView<>();
        table.getStyleClass().add("analytics-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(270);

        table.getColumns().addAll(
                col("Garage", ReportRow::garage, 150),
                col("Date", ReportRow::date, 120),
                col("Entries", ReportRow::entries, 90),
                col("Exits", ReportRow::exits, 90),
                col("Revenue", ReportRow::revenue, 110),
                col("Avg Duration", ReportRow::duration, 120),
                col("Top Payment Method", ReportRow::method, 160)
        );

        ObservableList<ReportRow> rows = FXCollections.observableArrayList(snapshot.rows());
        if (rows.isEmpty()) {
            table.setPlaceholder(text("No operational data for the selected date range.", 13, MUTED, false));
        }
        table.setItems(rows);
        card.getChildren().addAll(header, table);
        return card;
    }

    private double rangeRevenue() {
        return snapshot.rangeRevenue();
    }

    private List<Payment> visiblePayments() {
        return garageContext == null ? paymentService.getPaymentsFor(currentUser)
                : GarageUiScope.payments(paymentService, currentUser, garageContext);
    }

    private List<Ticket> visibleTickets() {
        return garageContext == null ? ticketService.getTicketsFor(currentUser)
                : GarageUiScope.tickets(ticketService, currentUser, garageContext);
    }

    private String scopeLabel() {
        if (garageContext == null || garageContext.isAllGarages()) return "All garages";
        return GarageUiScope.label(garageContext.getSelectedGarageId());
    }

    private <T> TableColumn<ReportRow, String> col(String title, java.util.function.Function<ReportRow, String> extractor, double width) {
        TableColumn<ReportRow, String> c = new TableColumn<>(title.toUpperCase(Locale.ROOT));
        c.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(extractor.apply(cell.getValue())));
        c.setPrefWidth(width);
        return c;
    }

    private void refreshNow(Button button) {
        if (refreshState == OperationState.RUNNING) return;
        if (refreshSpinner != null) {
            refreshSpinner.stop();
            ownedAnimations.remove(refreshSpinner);
        }
        refreshIcon.setGraphic(IconView.of(IconView.Name.REFRESH, 16, TEAL));
        refreshSpinner = new Timeline(new KeyFrame(Duration.millis(90), e -> {
            refreshIcon.setRotate(refreshIcon.getRotate() + 35);
        }));
        refreshSpinner.setCycleCount(Timeline.INDEFINITE);
        own(refreshSpinner).play();
        requestRefresh(button);
    }

    private void exportCsv(Button source) {
        if (disposed) return;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Revenue Report");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        chooser.setInitialFileName("parkingos-revenue-report.csv");
        File file = chooser.showSaveDialog(host.getScene() == null ? null : host.getScene().getWindow());
        if (file == null) return;
        if (exportState == OperationState.RUNNING) return;
        exportState = OperationState.RUNNING;
        source.setDisable(true);
        List<ReportRow> rows = List.copyOf(snapshot.rows());
        backgroundTasks.submit(() -> {
            try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))) {
                out.println("Garage,Date,Entries,Exits,Revenue,Avg Duration,Top Payment Method");
                for (ReportRow row : rows) {
                    out.printf(Locale.US, "%s,%s,%d,%d,%.2f,%s,%s%n", row.garage(), row.date(),
                            Integer.parseInt(row.entries()), Integer.parseInt(row.exits()),
                            Double.parseDouble(row.revenue().replace("$", "")), row.duration(), row.method());
                }
                return null;
            }
        }, ignored -> {
            if (disposed) return;
            exportState = OperationState.SUCCEEDED;
            source.setDisable(false);
            showToast("Revenue report exported", GREEN);
            playCheck(source);
        }, error -> {
            if (disposed) return;
            exportState = OperationState.FAILED;
            source.setDisable(false);
            showToast("Export failed: " + message(error), RED);
        });
    }

    private void exportPdf(Button source) {
        if (disposed) return;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export branded PDF report");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
        chooser.setInitialFileName("parkingos-revenue-report.pdf");
        File file = chooser.showSaveDialog(host.getScene() == null ? null : host.getScene().getWindow());
        if (file == null) return;
        if (exportState == OperationState.RUNNING) return;
        exportState = OperationState.RUNNING;
        source.setDisable(true);
        LocalDate start = reportStart;
        LocalDate end = reportEnd;
        ReportSnapshot current = snapshot;
        // Phase 5: totals footer mirrors the on-screen KPI strip so paper matches UI.
        String totalsLine = "TOTAL  |  Revenue " + money(current.rangeRevenue())
                + "  |  Completed " + current.completedPayments()
                + "  |  Occupancy " + current.occupancyRate() + "%";
        backgroundTasks.submit(() -> {
            ReportExporter.writeBrandedPdf(file.toPath(), "Revenue & Operations Report", start + " to " + end,
                    List.of("Revenue: " + money(current.rangeRevenue()),
                            "Completed payments: " + current.completedPayments(),
                            "Garage occupancy: " + current.occupancyRate() + "%"),
                    current.rows().stream().map(r -> r.garage() + " | " + r.date() + " | entries " + r.entries() + " | exits " + r.exits()
                            + " | " + r.revenue() + " | " + r.method()).toList(),
                    List.of(totalsLine));
            return null;
        }, ignored -> {
            if (disposed) return;
            exportState = OperationState.SUCCEEDED;
            source.setDisable(false);
            showToast("Branded PDF report exported", GREEN);
            playCheck(source);
        }, error -> {
            if (disposed) return;
            exportState = OperationState.FAILED;
            source.setDisable(false);
            showToast("PDF export failed: " + message(error), RED);
        });
    }

    private void scheduleReport(java.time.Duration interval, ScheduledReportService.Delivery delivery) {
        if (disposed) return;
        LocalDate start = reportStart;
        LocalDate end = reportEnd;
        reportScheduler.scheduleEvery(interval, () -> {
            try {
                ReportSnapshot current = computeSnapshot(start, end);
                Path file = ReportExporter.createUniqueReportPath(AppConfig.reportsDirectory(),
                        "parkingos-report", ".pdf");
                ReportExporter.writeBrandedPdf(file, "Scheduled Revenue Report", start + " to " + end,
                        List.of("Revenue: " + money(current.rangeRevenue())),
                        current.rows().stream().map(r -> r.date() + " | " + r.revenue()).toList());
                return file;
            } catch (Exception ex) { throw new RuntimeException(ex); }
        }, delivery, message -> Platform.runLater(() -> {
            if (!disposed) showToast(message, message.startsWith("Scheduled report failed") ? RED : GREEN);
        }));
        showToast("Scheduled report enabled", GREEN);
    }

    private void showDatePickerPopup(Node owner) {
        DatePicker start = new DatePicker(LocalDate.now().minusDays(6));
        DatePicker end = new DatePicker(LocalDate.now());
        start.getStyleClass().add("dark-picker");
        end.getStyleClass().add("dark-picker");

        VBox box = new VBox(10,
                text("Select date range", 14, TEXT, true),
                field("FROM", start),
                field("TO", end));
        Popup popup = new Popup();
        Button apply = new Button("Apply");
        apply.getStyleClass().add("primary-button");
        apply.setOnAction(e -> {
            if (start.getValue() != null && end.getValue() != null && !start.getValue().isAfter(end.getValue())) {
                setDateRange(start.getValue(), end.getValue());
                popup.hide();
            }
        });
        box.getChildren().add(apply);
        box.setPadding(new Insets(14));
        box.getStyleClass().add("analytics-popup");

        popup.setAutoHide(true);
        popup.getContent().add(box);
        javafx.geometry.Point2D p = owner.localToScreen(0, owner.getBoundsInLocal().getHeight() + 8);
        popup.show(owner, p.getX(), p.getY());
    }

    private void setDateRange(LocalDate start, LocalDate end) {
        reportStart = start;
        reportEnd = end;
        requestRefresh(null);
    }

    public void cancelAndClose() {
        disposed = true;
        if (refreshTask != null) refreshTask.cancel(true);
        if (refreshState == OperationState.RUNNING) refreshState = OperationState.CANCELLED;
        if (exportState == OperationState.RUNNING) exportState = OperationState.CANCELLED;
        ownedAnimations.forEach(Animation::stop);
        ownedAnimations.clear();
        hideMiniTooltip();
        if (refreshSpinner != null) {
            refreshSpinner.stop();
            ownedAnimations.remove(refreshSpinner);
        }
        reportScheduler.close();
    }

    private <T extends Animation> T own(T animation) {
        return UiMotion.own(ownedAnimations, animation);
    }

    private VBox field(String labelText, Control control) {
        VBox box = new VBox(4, text(labelText, 10, MUTED, true), control);
        return box;
    }

    private void attachChartHover(Node node, String message) {
        node.setOnMouseEntered(e -> showMiniTooltip(node, message));
        node.setOnMouseExited(e -> hideMiniTooltip());
    }

    private Popup miniPopup;
    private void showMiniTooltip(Node owner, String message) {
        hideMiniTooltip();
        Label label = text(message, 11, TEXT, true);
        StackPane bubble = new StackPane(label);
        bubble.setPadding(new Insets(8));
        bubble.getStyleClass().add("chart-tooltip");
        miniPopup = new Popup();
        miniPopup.setAutoHide(false);
        miniPopup.getContent().add(bubble);
        javafx.geometry.Point2D p = owner.localToScreen(owner.getBoundsInLocal().getWidth() / 2, -8);
        miniPopup.show(owner, p.getX(), p.getY());
        bubble.setScaleX(.9);
        bubble.setScaleY(.9);
        bubble.setOpacity(0);
        Timeline t = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(bubble.opacityProperty(), 0), new KeyValue(bubble.scaleXProperty(), .9), new KeyValue(bubble.scaleYProperty(), .9)),
                new KeyFrame(Duration.millis(150), new KeyValue(bubble.opacityProperty(), 1), new KeyValue(bubble.scaleXProperty(), 1), new KeyValue(bubble.scaleYProperty(), 1)));
        own(t).play();
    }

    private void hideMiniTooltip() {
        if (miniPopup != null) {
            miniPopup.hide();
            miniPopup = null;
        }
    }

    private void showToast(String message, String color) {
        if (disposed || host.getScene() == null) return;
        Label toast = text(message, 12, WHITE, true);
        StackPane pill = new StackPane(toast);
        pill.setPadding(new Insets(10, 14, 10, 14));
        pill.getStyleClass().add(GREEN.equals(color) ? "user-toast-success" : RED.equals(color) ? "user-toast-error" : "user-toast-info");
        pill.setEffect(new DropShadow(10, DesignTokens.color(SHADOW_BLACK_35)));
        host.getChildren().add(pill);
        StackPane.setAlignment(pill, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(pill, new Insets(0, 22, 20, 0));
        pill.setTranslateX(260);
        pill.setOpacity(0);
        Timeline enter = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(pill.translateXProperty(), 260), new KeyValue(pill.opacityProperty(), 0)),
                new KeyFrame(Duration.millis(220), new KeyValue(pill.translateXProperty(), 0, Interpolator.EASE_OUT), new KeyValue(pill.opacityProperty(), 1)));
        enter.setOnFinished(e -> {
            PauseTransition pause = new PauseTransition(Duration.seconds(2.5));
            pause.setOnFinished(ev -> {
                FadeTransition fade = new FadeTransition(Duration.millis(150), pill);
                fade.setToValue(0);
                fade.setOnFinished(x -> { if (!disposed) host.getChildren().remove(pill); });
                if (!disposed && host.getScene() != null) own(fade).play();
            });
            if (!disposed && host.getScene() != null) own(pause).play();
        });
        own(enter).play();
    }

    private void playCheck(Button button) {
        String old = button.getText();
        button.setText("Exported");
        button.getStyleClass().add("export-done");
        PauseTransition pause = new PauseTransition(Duration.seconds(1));
        pause.setOnFinished(e -> {
            if (!disposed && button.getScene() != null) {
                button.setText(old);
                button.getStyleClass().remove("export-done");
            }
        });
        own(pause).play();
    }

    private void playCountUp(Label label, double target, String prefix) {
        final long end = Math.round(target);
        Timeline timeline = new Timeline();
        timeline.getKeyFrames().add(new KeyFrame(Duration.seconds(1), new KeyValue(label.textProperty(), prefix + formatInteger(end), Interpolator.EASE_OUT)));
        // Bind a synthetic numeric counter to text via keyframes for smooth visual count-up.
        timeline.getKeyFrames().clear();
        int steps = 25;
        for (int i = 0; i <= steps; i++) {
            double fraction = i / (double) steps;
            long value = Math.round(end * fraction);
            timeline.getKeyFrames().add(new KeyFrame(Duration.millis(i * 40L), e -> {
                if (!disposed && label.getScene() != null) label.setText(prefix + formatInteger(value));
            }));
        }
        own(timeline).play();
    }

    private String formatInteger(long value) { return String.format(Locale.US, "%,d", value); }
    private double parseDouble(String s) { try { return Double.parseDouble(s.replace(",", "")); } catch (Exception e) { return 0; } }
    private String money(double amount) { return UiFormat.money(amount); }

    private StackPane sparklinePlaceholder(String accent) {
        String wash = TEAL.equals(accent) ? GLOW_TEAL_10 : BLUE.equals(accent) ? GLOW_BLUE_10 : ORANGE.equals(accent) ? GLOW_ORANGE_10 : GLOW_TEAL_10;
        Rectangle rect = new Rectangle(32, 16, DesignTokens.color(wash));
        rect.setArcWidth(6); rect.setArcHeight(6);
        return new StackPane(rect, text("⌁", 10, accent, true));
    }

    private LineChart<Number, Number> sparkline(String accent) {
        NumberAxis x = new NumberAxis();
        NumberAxis y = new NumberAxis();
        LineChart<Number, Number> chart = new LineChart<>(x, y);
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setCreateSymbols(false);
        chart.setHorizontalGridLinesVisible(false);
        chart.setVerticalGridLinesVisible(false);
        chart.setPrefHeight(22);
        chart.getStyleClass().add("sparkline-chart");
        XYChart.Series<Number, Number> s = new XYChart.Series<>();
        double[] a = {4, 5, 4.5, 6, 5.8, 7, 6.6};
        for (int i = 0; i < a.length; i++) s.getData().add(new XYChart.Data<>(i, a[i]));
        chart.getData().add(s);
        return chart;
    }

    private Button iconButton(IconView.Name icon) {
        Button b = UiNodes.iconButton(icon);
        b.setPrefSize(38, 38);
        return b;
    }

    private Node spacer() {
        return UiNodes.spacer();
    }

    private Label text(String value, double size, String color, boolean bold) {
        return DesignTokens.text(value, size, color, bold);
    }

    private void scale(Node node, double factor) {
        ScaleTransition st = new ScaleTransition(Duration.millis(180), node);
        st.setToX(factor); st.setToY(factor); own(st).play();
    }

    private static final class ReportRow {
        private final String garage, date, entries, exits, revenue, duration, method;
        ReportRow(String garage, String date, String entries, String exits, String revenue, String duration, String method) {
            this.garage = garage; this.date = date; this.entries = entries; this.exits = exits; this.revenue = revenue; this.duration = duration; this.method = method;
        }
        String garage() { return garage; }
        String date() { return date; }
        String entries() { return entries; }
        String exits() { return exits; }
        String revenue() { return revenue; }
        String duration() { return duration; }
        String method() { return method; }
    }

    private record ReportSnapshot(double[] revenue, int[] occupancy, int[] peak,
                                 LinkedHashMap<String, Double> paymentDistribution,
                                 List<ReportRow> rows, double rangeRevenue, int completedPayments,
                                 double averageDuration, int occupancyRate) {
        static ReportSnapshot empty() {
            return new ReportSnapshot(new double[7], new int[24], new int[24],
                    new LinkedHashMap<>(), List.of(), 0, 0, 0, 0);
        }
    }
}
