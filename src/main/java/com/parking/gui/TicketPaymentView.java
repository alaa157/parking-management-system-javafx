package com.parking.gui;

import com.parking.config.AppConfig;
import com.parking.enums.PaymentStatus;
import com.parking.enums.TicketStatus;
import com.parking.exceptions.InvalidTicketStatusException;
import com.parking.exceptions.PaymentFailedException;
import com.parking.exceptions.TicketNotFoundException;
import com.parking.model.CardPayment;
import com.parking.model.CashPayment;
import com.parking.model.Customer;
import com.parking.model.ParkingGarage;
import com.parking.model.ParkingSpot;
import com.parking.model.Payment;
import com.parking.model.Reservation;
import com.parking.model.Ticket;
import com.parking.model.User;
import com.parking.model.Vehicle;
import com.parking.model.WalletPayment;
import com.parking.services.BulkTicketUpdateResult;
import com.parking.services.ParkingService;
import com.parking.services.PaymentService;
import com.parking.services.ReservationService;
import com.parking.services.TicketService;
import com.parking.services.GarageContext;
import com.parking.gui.components.EmptyState;

import javafx.animation.*;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.print.PrinterJob;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.parking.gui.DesignTokens.*;

/**
 * Phase 3: Ticket Management + Payment Processing.
 * Pure programmatic JavaFX; no FXML. Uses the existing domain/services as the source of truth.
 */
public final class TicketPaymentView {
    private enum Method { CARD, CASH, WALLET }

    private final TicketService ticketService;
    private final com.parking.services.UserService userService;
    private final PaymentService paymentService;
    private final ParkingService parkingService;
    private final ParkingGarage garage;
    private final User currentUser;
    private final GarageContext garageContext;
    private final Consumer<String> toast;
    private final Consumer<Void> backToDashboard;

    private final Map<String, Payment> paymentsByTicket = new HashMap<>();

    private VBox root;
    private StackPane host;
    private VBox ticketList;
    private TableView<Ticket> customerTicketTable;
    private VBox detail;
    private Label countBadge;
    private TextField search;
    private ComboBox<String> sortBy;
    private ComboBox<String> statusFilter;
    private ComboBox<String> paymentFilter;
    private DatePicker dateFrom;
    private DatePicker dateTo;
    private Label selectedTitle;
    private Ticket selectedTicket;
    private Method selectedMethod = Method.CARD;
    private Label paymentValidation;
    private VBox paymentFields;
    private TextField cardNumber;
    private TextField cardHolder;
    private TextField expiry;
    private TextField cvv;
    private TextField cashReceived;
    private Button activePaymentButton;
    private Ticket paymentTicket;
    private Label cashChangeLabel;
    private Label walletBalanceLabel;
    private Label walletRemainingLabel;
    private Timeline liveTimer;
    private RotateTransition paymentRotate;
    private PauseTransition paymentDelay;
    private final List<Animation> ownedAnimations = new ArrayList<>();
    private boolean disposed;
    private String typedReceipt = "";
    private final Set<String> selectedTicketIds = new HashSet<>();
    private ComboBox<TicketStatus> bulkStatus;
    private VBox reservationBox;
    private Label reservationSummary;

    public TicketPaymentView(TicketService ticketService,
                             PaymentService paymentService,
                             ParkingService parkingService,
                             com.parking.services.UserService userService,
                             ParkingGarage garage,
                             User currentUser,
                             Consumer<String> toast,
                             Consumer<Void> backToDashboard) {
        this(ticketService, paymentService, parkingService, userService, garage, currentUser, null, toast, backToDashboard);
    }

    public TicketPaymentView(TicketService ticketService,
                             PaymentService paymentService,
                             ParkingService parkingService,
                             com.parking.services.UserService userService,
                             ParkingGarage garage,
                             User currentUser,
                             GarageContext garageContext,
                             Consumer<String> toast,
                             Consumer<Void> backToDashboard) {
        this.ticketService = Objects.requireNonNull(ticketService);
        this.paymentService = Objects.requireNonNull(paymentService);
        this.parkingService = Objects.requireNonNull(parkingService);
        this.userService = Objects.requireNonNull(userService);
        this.garage = Objects.requireNonNull(garage);
        this.currentUser = currentUser;
        this.garageContext = garageContext;
        this.toast = toast == null ? s -> {} : toast;
        this.backToDashboard = backToDashboard == null ? x -> {} : backToDashboard;
        if (garageContext != null) garageContext.addListener(ignored -> {
            if (!disposed && host != null) refreshList();
        });
    }

    public Node build() {
        disposed = false;
        stopTimer();

        BorderPane shell = new BorderPane();
        shell.setPadding(new Insets(24));
        shell.getStyleClass().add("ticket-shell");

        HBox header = buildHeader();
        ScrollPane detailScroll = new ScrollPane(buildDetailPane());
        detailScroll.setFitToWidth(true);
        detailScroll.getStyleClass().add("transparent-scroll");
        SplitPane split = new SplitPane(buildTicketPane(), detailScroll);
        split.setDividerPositions(0.55);
        split.setOrientation(Orientation.HORIZONTAL);
        split.widthProperty().addListener((obs, oldWidth, newWidth) -> {
            boolean narrow = newWidth.doubleValue() < 760;
            split.setOrientation(narrow ? Orientation.VERTICAL : Orientation.HORIZONTAL);
            split.setDividerPositions(narrow ? 0.42 : 0.55);
        });
        split.getStyleClass().add("ticket-split");
        shell.setCenter(split);

        root = new VBox(20, header, split);
        root.setPadding(new Insets(0));
        root.getStyleClass().add("ticket-root");
        VBox.setVgrow(split, Priority.ALWAYS);
        host = new StackPane(root);
        host.getStyleClass().add("ticket-host");

        refreshList();
        SkeletonView.show(host, SkeletonView.cards(4), 420);
        animateIn(root);
        return host;
    }

    public boolean owns(Node node) { return host == node; }

    public void dispose() {
        disposed = true;
        stopTimer();
        if (paymentRotate != null) paymentRotate.stop();
        if (paymentDelay != null) paymentDelay.stop();
        paymentRotate = null;
        paymentDelay = null;
        ownedAnimations.forEach(Animation::stop);
        ownedAnimations.clear();
    }

    private <T extends Animation> T own(T animation) {
        return UiMotion.own(ownedAnimations, animation);
    }

    private boolean active(Node node) {
        return !disposed && node != null && node.getScene() != null;
    }

    private HBox buildHeader() {
        VBox title = new VBox(4,
                label("Ticket Management", 26, TEXT, true),
                label("Track active sessions, payments, receipts and refunds", 14, MUTED, false));
        if (garageContext != null) title.getChildren().add(label(scopeLabel(), 12, TEAL, true));
        Region push = new Region();
        HBox.setHgrow(push, Priority.ALWAYS);
        Button back = new Button("Dashboard", IconView.of(IconView.Name.BACK, 16, MUTED));
        back.getStyleClass().add("outline-button");
        back.setOnAction(e -> {
            stopTimer();
            backToDashboard.accept(null);
        });
        HBox h = new HBox(18, title, push, back);
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    private VBox buildTicketPane() {
        if (currentUser != null && currentUser.getRole() == com.parking.enums.UserRole.CUSTOMER) {
            return buildCustomerTicketPane();
        }
        VBox pane = panel();
        pane.setPrefWidth(560);
        pane.setPadding(new Insets(18));

        HBox top = new HBox(10);
        top.setAlignment(Pos.CENTER_LEFT);
        Label title = label("Active Tickets", 18, TEXT, true);
        countBadge = label("0", 11, BG, true);
        countBadge.setAlignment(Pos.CENTER);
        countBadge.setMinSize(34, 24);
        countBadge.getStyleClass().add("count-badge");
        Region push = new Region();
        HBox.setHgrow(push, Priority.ALWAYS);
        Button refresh = iconButton(IconView.Name.REFRESH);
        refresh.setTooltip(new Tooltip("Refresh tickets"));
        refresh.setOnAction(e -> {
            refreshList();
            if (selectedTicket != null) showDetails(selectedTicket);
        });
        CheckBox selectAll = new CheckBox("Select all");
        selectAll.setAccessibleText("Select all visible tickets");
        selectAll.setOnAction(e -> {
            List<Ticket> visible = visibleTickets();
            if (selectAll.isSelected()) visible.forEach(t -> selectedTicketIds.add(t.getTicketId()));
            else selectedTicketIds.clear();
            refreshList();
        });
        bulkStatus = new ComboBox<>();
        bulkStatus.getItems().addAll(TicketStatus.CREATED, TicketStatus.ACTIVE, TicketStatus.CANCELLED, TicketStatus.AWAITING_PAYMENT);
        bulkStatus.setPromptText("Bulk status"); styleCombo(bulkStatus);
        Button applyBulk = new Button("Apply"); applyBulk.getStyleClass().add("outline-button");
        applyBulk.setOnAction(e -> applyBulkStatus());
        top.getChildren().addAll(title, countBadge, push, selectAll, bulkStatus, applyBulk, refresh);

        search = textField("Search ticket, vehicle, spot...");
        search.textProperty().addListener((o, a, b) -> refreshList());

        HBox filters = new HBox(8);
        statusFilter = new ComboBox<>();
        statusFilter.getItems().addAll("All Status", "ACTIVE", "AWAITING_PAYMENT", "PAID", "REFUNDED", "CANCELLED");
        statusFilter.setValue("All Status");
        statusFilter.setPrefWidth(150);
        styleCombo(statusFilter);
        statusFilter.valueProperty().addListener((o, a, b) -> refreshList());

        sortBy = new ComboBox<>();
        sortBy.getItems().addAll("Date", "Amount", "Status");
        sortBy.setValue("Date");
        sortBy.setPrefWidth(120);
        styleCombo(sortBy);
        sortBy.valueProperty().addListener((o, a, b) -> refreshList());
        paymentFilter = new ComboBox<>();
        paymentFilter.getItems().addAll("All Methods", "Card", "Cash", "Wallet");
        paymentFilter.setValue("All Methods"); styleCombo(paymentFilter);
        paymentFilter.valueProperty().addListener((o, a, b) -> refreshList());
        dateFrom = new DatePicker(); dateFrom.setPromptText("From"); dateFrom.setPrefWidth(115);
        dateTo = new DatePicker(); dateTo.setPromptText("To"); dateTo.setPrefWidth(115);
        dateFrom.valueProperty().addListener((o, a, b) -> refreshList());
        dateTo.valueProperty().addListener((o, a, b) -> refreshList());
        filters.getChildren().addAll(statusFilter, sortBy, paymentFilter, dateFrom, dateTo);

        ScrollPane scroll = new ScrollPane();
        ticketList = new VBox(10);
        ticketList.setPadding(new Insets(4, 2, 10, 2));
        scroll.setContent(ticketList);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("transparent-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Label footer = label("Tip: click a ticket to open payment and receipt controls.", 11, MUTED, false);
        reservationSummary = label("", 11, MUTED, false);
        pane.getChildren().addAll(top, search, filters, scroll, reservationSummary, footer);
        return pane;
    }

    private VBox buildCustomerTicketPane() {
        VBox pane = panel();
        pane.setPadding(new Insets(18));

        HBox top = new HBox(10);
        top.setAlignment(Pos.CENTER_LEFT);
        countBadge = label("0", 11, BG, true);
        countBadge.setAlignment(Pos.CENTER);
        countBadge.setMinSize(34, 24);
        countBadge.getStyleClass().add("count-badge");
        Region push = new Region();
        HBox.setHgrow(push, Priority.ALWAYS);
        Button refresh = iconButton(IconView.Name.REFRESH);
        refresh.setOnAction(e -> refreshList());
        top.getChildren().addAll(label("My tickets", 18, TEXT, true), countBadge, push, refresh);

        search = textField("Search ticket, plate, spot...");
        search.textProperty().addListener((o, a, b) -> refreshList());
        statusFilter = new ComboBox<>();
        statusFilter.getItems().addAll("All statuses", "Active", "Awaiting payment", "Paid", "Closed", "Cancelled");
        statusFilter.setValue("All statuses");
        statusFilter.setPrefWidth(170);
        styleCombo(statusFilter);
        statusFilter.valueProperty().addListener((o, a, b) -> refreshList());

        customerTicketTable = new TableView<>();
        customerTicketTable.getStyleClass().add("data-table");
        customerTicketTable.setPlaceholder(label("No matching tickets.", 13, MUTED, false));
        customerTicketTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        customerTicketTable.setOnMouseClicked(e -> {
            Ticket ticket = customerTicketTable.getSelectionModel().getSelectedItem();
            if (ticket != null) showDetails(ticket);
        });
        addCustomerColumn("Ticket", 1.4, t -> safe(t.getTicketId()));
        addCustomerColumn("Vehicle", 1.2, t -> {
            Vehicle vehicle = findVehicle(t);
            return vehicle == null ? "—" : safe(vehicle.getLicensePlate());
        });
        addCustomerColumn("Spot", .9, t -> safe(t.getParkingSpotId()));
        addCustomerColumn("Garage", 1.2, t -> GarageUiScope.label(t.getGarageId()));
        addCustomerColumn("Entry", 1.2, t -> formatTicketTime(t.getEntryTime()));
        addCustomerColumn("Exit", 1.2, t -> formatTicketTime(t.getExitTime()));
        addCustomerColumn("Status", 1.1, t -> statusLabel(t.getStatus()));
        addCustomerColumn("Amount", 1.0, t -> money(totalDue(t)));
        VBox.setVgrow(customerTicketTable, Priority.ALWAYS);

        reservationBox = new VBox(8);
        pane.getChildren().addAll(top, search, statusFilter, reservationBox, customerTicketTable);
        return pane;
    }

    private void refreshReservationBox() {
        if (reservationBox == null) return;
        reservationBox.getChildren().clear();
        ReservationService reservations = parkingService.getReservationService();
        if (reservations == null || currentUser == null) return;
        List<Reservation> holds;
        try {
            holds = reservations.activeForUser(currentUser);
        } catch (RuntimeException ignored) {
            return;
        }
        DateTimeFormatter stamp = DateTimeFormatter.ofPattern("dd MMM HH:mm");
        for (Reservation hold : holds) {
            long minutes = ChronoUnit.MINUTES.between(LocalDateTime.now(), hold.expiresAt());
            String countdown = hold.expiresAt().format(stamp) + (minutes <= 0 ? " (expiring)" : " (in " + minutes + " min)");
            Button cancel = new Button("Cancel");
            cancel.getStyleClass().add("outline-button");
            cancel.setOnAction(e -> {
                try {
                    reservations.cancel(currentUser, hold.reservationId(), LocalDateTime.now());
                    toast.accept("Reservation for spot " + hold.spotId() + " cancelled.");
                    refreshList();
                } catch (RuntimeException ex) {
                    toast.accept("Cancellation failed: " + ex.getMessage());
                }
            });
            Region push = new Region();
            HBox.setHgrow(push, Priority.ALWAYS);
            HBox card = new HBox(10,
                    label("Reserved " + safe(hold.spotId()), 13, TEAL, true),
                    push,
                    label(countdown, 11, MUTED, false),
                    cancel);
            card.setAlignment(Pos.CENTER_LEFT);
            card.getStyleClass().add("ticket-card");
            card.setPadding(new Insets(10));
            reservationBox.getChildren().add(card);
        }
    }

    private void addCustomerColumn(String title, double widthWeight,
                                   java.util.function.Function<Ticket, String> value) {
        TableColumn<Ticket, String> column = new TableColumn<>(title);
        column.setCellValueFactory(data -> new SimpleStringProperty(value.apply(data.getValue())));
        column.setUserData(widthWeight);
        customerTicketTable.getColumns().add(column);
    }

    private String formatTicketTime(LocalDateTime time) {
        return time == null ? "—" : time.format(DateTimeFormatter.ofPattern("dd MMM HH:mm"));
    }

    private String statusLabel(TicketStatus status) {
        if (status == null) return "—";
        return switch (status) {
            case ACTIVE -> "Active";
            case AWAITING_PAYMENT -> "Awaiting payment";
            case PAID -> "Paid";
            case CLOSED -> "Closed";
            case CANCELLED -> "Cancelled";
            case CREATED -> "Created";
            case REFUNDED -> "Refunded";
        };
    }

    private TicketStatus statusFromCustomerFilter(String filter) {
        return switch (filter) {
            case "Active" -> TicketStatus.ACTIVE;
            case "Awaiting payment" -> TicketStatus.AWAITING_PAYMENT;
            case "Paid" -> TicketStatus.PAID;
            case "Closed" -> TicketStatus.CLOSED;
            case "Cancelled" -> TicketStatus.CANCELLED;
            default -> null;
        };
    }

    private VBox buildDetailPane() {
        detail = panel();
        detail.setPadding(new Insets(22));
        detail.getChildren().add(noSelection());
        return detail;
    }

    private Node noSelection() {
        VBox empty = new VBox(10,
                IconView.of(IconView.Name.TICKET, 48, TEAL),
                label("Select a ticket to view details", 16, MUTED, true),
                label("Ticket details, live duration and payment actions will appear here.", 12, MUTED, false));
        empty.setAlignment(Pos.CENTER);
        return centered(empty);
    }

    private void refreshList() {
        if (currentUser.getRole() == com.parking.enums.UserRole.CUSTOMER) {
            refreshCustomerTable();
            return;
        }
        if (ticketList == null) return;
        ticketList.getChildren().clear();
        List<Ticket> tickets = new ArrayList<>(visibleTickets());
        String q = search == null ? "" : search.getText().trim().toLowerCase();
        String sf = statusFilter == null ? "All Status" : statusFilter.getValue();

        tickets = tickets.stream().filter(t -> {
            boolean statusOk = "All Status".equals(sf) || t.getStatus().name().equals(sf);
            String spotId = t.getParkingSpotId() == null ? "" : t.getParkingSpotId();
            Vehicle searchableVehicle = findVehicle(t);
            String plate = searchableVehicle == null ? "" : safe(searchableVehicle.getLicensePlate());
            boolean textOk = q.isEmpty() || (t.getTicketId() + " " + t.getVehicleId() + " " + plate + " " + spotId).toLowerCase().contains(q);
            String method = paymentFor(t) == null ? "" : paymentFor(t).getPaymentType();
            boolean methodOk = paymentFilter == null || "All Methods".equals(paymentFilter.getValue())
                    || (method != null && method.toLowerCase().contains(paymentFilter.getValue().toLowerCase()));
            boolean fromOk = dateFrom == null || dateFrom.getValue() == null || (t.getEntryTime() != null && !t.getEntryTime().toLocalDate().isBefore(dateFrom.getValue()));
            boolean toOk = dateTo == null || dateTo.getValue() == null || (t.getEntryTime() != null && !t.getEntryTime().toLocalDate().isAfter(dateTo.getValue()));
            return statusOk && textOk && methodOk && fromOk && toOk;
        }).collect(Collectors.toCollection(ArrayList::new));

        if ("Amount".equals(sortBy == null ? "Date" : sortBy.getValue())) {
            tickets.sort(Comparator.comparingDouble(this::totalDue).reversed());
        } else if ("Status".equals(sortBy == null ? "Date" : sortBy.getValue())) {
            tickets.sort(Comparator.comparing(t -> t.getStatus().name()));
        } else {
            tickets.sort(Comparator.comparing(Ticket::getEntryTime, Comparator.nullsLast(Comparator.reverseOrder())));
        }

        countBadge.setText(String.valueOf(tickets.size()));
        updateReservationSummary();
        for (Ticket t : tickets) {
            ticketList.getChildren().add(currentUser.getRole() == com.parking.enums.UserRole.CUSTOMER
                    ? customerTicketRow(t) : ticketCard(t));
        }
        if (tickets.isEmpty()) {
            if (currentUser.getRole() == com.parking.enums.UserRole.CUSTOMER) {
                ticketList.getChildren().add(label("No matching tickets.", 13, MUTED, false));
                return;
            }
            ticketList.getChildren().add(new EmptyState(IconView.Name.TICKET,
                    q.isEmpty() && "All Status".equals(sf) ? "No tickets yet" : "No matching tickets",
                    q.isEmpty() && "All Status".equals(sf)
                            ? "Park a vehicle to create your first ticket."
                            : "Try changing the search text or status filter.",
                    null, null));
        }
    }

    private void updateReservationSummary() {
        if (reservationSummary == null) return;
        ReservationService reservations = parkingService.getReservationService();
        if (reservations == null) {
            reservationSummary.setText("");
            return;
        }
        try {
            int active = reservations.activeReservations().size();
            reservationSummary.setText(active == 0 ? "No active reservations." : "Active reservations: " + active);
        } catch (RuntimeException ignored) {
            reservationSummary.setText("");
        }
    }

    private void refreshCustomerTable() {
        if (customerTicketTable == null) return;
        refreshReservationBox();
        String query = search == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        TicketStatus selectedStatus = statusFromCustomerFilter(statusFilter == null ? "All statuses" : statusFilter.getValue());
        List<Ticket> filtered = visibleTickets().stream()
                .filter(t -> selectedStatus == null || t.getStatus() == selectedStatus)
                .filter(t -> {
                    Vehicle vehicle = findVehicle(t);
                    String plate = vehicle == null ? "" : safe(vehicle.getLicensePlate());
                    String searchable = (safe(t.getTicketId()) + " " + safe(t.getVehicleId()) + " "
                            + plate + " " + safe(t.getParkingSpotId())).toLowerCase(Locale.ROOT);
                    return query.isEmpty() || searchable.contains(query);
                })
                .sorted(Comparator.comparing(Ticket::getEntryTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
        customerTicketTable.setItems(FXCollections.observableArrayList(filtered));
        countBadge.setText(String.valueOf(filtered.size()));
    }

    private GridPane customerTicketRow(Ticket ticket) {
        Vehicle vehicle = findVehicle(ticket);
        String entry = ticket.getEntryTime() == null ? "—" : ticket.getEntryTime().format(DateTimeFormatter.ofPattern("dd MMM HH:mm"));
        String exit = ticket.getExitTime() == null ? "—" : ticket.getExitTime().format(DateTimeFormatter.ofPattern("dd MMM HH:mm"));
        GridPane row = customerTicketRow(
                safe(ticket.getTicketId()),
                vehicle == null ? "—" : safe(vehicle.getLicensePlate()),
                safe(ticket.getParkingSpotId()), entry, exit,
                ticket.getStatus().name(), money(totalDue(ticket)), false);
        row.setCursor(javafx.scene.Cursor.HAND);
        row.setOnMouseClicked(e -> showDetails(ticket));
        return row;
    }

    private GridPane customerTicketRow(String ticket, String vehicle, String spot,
                                       String entry, String exit, String status,
                                       String amount, boolean header) {
        GridPane row = new GridPane();
        row.setHgap(10);
        row.setVgap(4);
        row.setPadding(new Insets(11, 4, 11, 4));
        double[] widths = {18, 17, 13, 15, 15, 12, 10};
        String[] values = {ticket, vehicle, spot, entry, exit, status, amount};
        for (int i = 0; i < values.length; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(widths[i]);
            row.getColumnConstraints().add(column);
            Node cell = i == 5 && !header
                    ? statusBadge(TicketStatus.valueOf(status))
                    : label(values[i], header ? 10 : 11, header ? MUTED : TEXT, header);
            row.add(cell, i, 0);
        }
        if (!header) row.getStyleClass().add("activity-row");
        return row;
    }

    private VBox ticketCard(Ticket ticket) {
        boolean selected = selectedTicket != null && selectedTicket.getTicketId().equals(ticket.getTicketId());
        VBox card = new VBox(7);
        card.setPadding(new Insets(SPACE_3));
        card.getStyleClass().add(selected ? "ticket-card-selected" : "ticket-card");
        card.setCursor(javafx.scene.Cursor.HAND);

        HBox row1 = new HBox(8);
        Label id = label(ticket.getTicketId(), 13, TEAL, true);
        Region push = new Region();
        HBox.setHgrow(push, Priority.ALWAYS);
        Label badge = statusBadge(ticket.getStatus());
        CheckBox selectBox = new CheckBox();
        selectBox.setSelected(selectedTicketIds.contains(ticket.getTicketId()));
        selectBox.setAccessibleText("Select ticket " + ticket.getTicketId());
        selectBox.setOnAction(e -> { if (selectBox.isSelected()) selectedTicketIds.add(ticket.getTicketId()); else selectedTicketIds.remove(ticket.getTicketId()); e.consume(); });
        row1.getChildren().addAll(selectBox, id, push, badge);

        ParkingSpot spot = garage.getSpotById(ticket.getParkingSpotId());
        VBox row2 = new VBox(3,
                label("Vehicle  " + safe(ticket.getVehicleId()) + "    •    Spot  " + safe(ticket.getParkingSpotId()), 12, TEXT, true),
                label("Duration  " + durationText(ticket) + "    •    Amount Due  " + money(totalDue(ticket)), 11, MUTED, false));
        card.getChildren().addAll(row1, row2);
        card.getChildren().add(label(GarageUiScope.label(ticket.getGarageId()), 10, TEAL, true));
        if (spot != null) card.getChildren().add(label(prettyType(spot) + "  •  " + spot.getLocation(), 10, MUTED, false));

        card.setOnMouseClicked(e -> showDetails(ticket));
        card.setOnMouseEntered(e -> {
            if (!selected) { card.getStyleClass().remove("ticket-card"); card.getStyleClass().add("ticket-card-hover"); }
            ScaleTransition s = new ScaleTransition(Duration.millis(160), card);
            own(s).play();
        });
        card.setOnMouseExited(e -> {
            card.getStyleClass().remove("ticket-card-hover");
            if (!card.getStyleClass().contains(selected ? "ticket-card-selected" : "ticket-card")) card.getStyleClass().add(selected ? "ticket-card-selected" : "ticket-card");
            ScaleTransition s = new ScaleTransition(Duration.millis(160), card);
            own(s).play();
        });
        return card;
    }

    private void applyBulkStatus() {
        if (bulkStatus == null || bulkStatus.getValue() == null || selectedTicketIds.isEmpty()) return;
        TicketStatus target = bulkStatus.getValue();
        Set<String> requested = new HashSet<>(selectedTicketIds);
        BulkTicketUpdateResult result;
        try {
            result = ticketService.updateStatuses(currentUser, requested, target);
        } catch (RuntimeException ex) {
            String message = ex.getMessage() == null || ex.getMessage().isBlank()
                    ? "Bulk update failed." : "Bulk update failed: " + ex.getMessage();
            toast.accept(message);
            return;
        }
        selectedTicketIds.clear();
        selectedTicketIds.addAll(result.failures().keySet());
        refreshList();
        if (result.failures().isEmpty()) {
            toast.accept(result.succeeded().size() + " ticket(s) updated.");
            return;
        }
        showBulkResult(result, target);
    }

    private void showBulkResult(BulkTicketUpdateResult result, TicketStatus target) {
        int updated = result.succeeded().size();
        String summary = updated + " ticket(s) updated to " + target + "; "
                + result.failures().size() + " failed. Failed selections were kept.";
        toast.accept(summary);
        StringBuilder details = new StringBuilder();
        result.failures().entrySet().stream().limit(10).forEach(entry ->
                details.append(entry.getKey()).append(" — ").append(entry.getValue()).append("\n"));
        if (result.failures().size() > 10) details.append("…and ").append(result.failures().size() - 10).append(" more.\n");
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Bulk update result");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.OK);
        dialog.getDialogPane().setContent(new VBox(10,
                label("Bulk update result", 18, TEXT, true),
                label(summary, 12, MUTED, false),
                label(details.toString().trim(), 12, TEXT, false)));
        dialog.showAndWait();
    }

    private void showDetails(Ticket ticket) {
        selectedTicket = ticket;
        if (currentUser != null && currentUser.getRole() == com.parking.enums.UserRole.CUSTOMER) {
            showCustomerTicketDetails(ticket);
            return;
        }
        typedReceipt = "";
        refreshList();
        detail.getChildren().clear();

        ParkingSpot spot = garage.getSpotById(ticket.getParkingSpotId());
        Vehicle vehicle = findVehicle(ticket);
        Payment payment = paymentFor(ticket);
        boolean paid = payment != null && payment.getStatus() == PaymentStatus.COMPLETED;
        if (ticket.getStatus() == TicketStatus.PAID) paid = true;

        HBox top = new HBox();
        VBox heading = new VBox(3,
                label(ticket.getTicketId(), 24, TEAL, true),
                label(statusText(ticket.getStatus()), 11, statusColor(ticket.getStatus()), true));
        Region push = new Region(); HBox.setHgrow(push, Priority.ALWAYS);
        Button close = iconButton(IconView.Name.CLOSE);
        close.setOnAction(e -> { selectedTicket = null; stopTimer(); detail.getChildren().setAll(noSelection()); refreshList(); });
        top.getChildren().addAll(heading, push, close);

        GridPane info = new GridPane();
        info.setHgap(18); info.setVgap(9);
        addInfo(info, 0, "Vehicle", vehicle == null ? safe(ticket.getVehicleId()) : vehicle.getMake() + " " + vehicle.getModel() + "  •  " + vehicle.getLicensePlate());
        addInfo(info, 1, "Customer", customerName(ticket, vehicle));
        addInfo(info, 2, "Spot", spot == null ? safe(ticket.getParkingSpotId()) : spot.getLocation() + "  •  " + spot.getSpotId());
        addInfo(info, 3, "Garage", GarageUiScope.label(ticket.getGarageId()));
        addInfo(info, 4, "Entry Time", ticket.getEntryTime() == null ? "—" : ticket.getEntryTime().toString());

        Label duration = label(durationText(ticket), 28, TEXT, true);
        HBox durationBox = new HBox(12, label("LIVE DURATION", 10, MUTED, true), duration);
        durationBox.setAlignment(Pos.CENTER_LEFT);
        VBox fee = feeBox(ticket, spot);

        detail.getChildren().addAll(top, separator(), info, durationBox, fee, separator());
        if (!paid && (ticket.getStatus() == TicketStatus.ACTIVE || ticket.getStatus() == TicketStatus.AWAITING_PAYMENT)) {
            paymentValidation = label("", 11, RED, false);
            paymentValidation.getStyleClass().add("validation-error");
            detail.getChildren().addAll(paymentMethods(ticket), paymentValidation, paymentButton(ticket));
        } else if (paid) {
            detail.getChildren().addAll(
                    section("PAYMENT COMPLETED"),
                    actionButton("Generate Receipt", TEAL, e -> showReceipt(ticket, payment)),
                    actionButton("Refund", RED, e -> showRefund(ticket, payment))
            );
        } else {
            detail.getChildren().add(label("This ticket is " + ticket.getStatus() + ".", 13, MUTED, false));
        }

        startTimer(duration);
    }

    private void showCustomerTicketDetails(Ticket ticket) {
        typedReceipt = "";
        refreshList();
        detail.getChildren().clear();

        ParkingSpot spot = garage.getSpotById(ticket.getParkingSpotId());
        Vehicle vehicle = findVehicle(ticket);
        String plate = vehicle == null ? "Vehicle" : safe(vehicle.getLicensePlate());
        String type = vehicle == null ? "—" : prettyVehicleType(vehicle);
        String entry = ticket.getEntryTime() == null ? "—"
                : ticket.getEntryTime().format(DateTimeFormatter.ofPattern("dd MMM yyyy  •  HH:mm"));

        Region headingPush = new Region();
        HBox.setHgrow(headingPush, Priority.ALWAYS);
        HBox heading = new HBox(12,
                new VBox(4,
                        label("Parking ticket", 24, TEXT, true),
                        label(safe(ticket.getTicketId()), 12, MUTED, true)),
                headingPush,
                statusBadge(ticket.getStatus()));
        heading.setAlignment(Pos.CENTER_LEFT);

        VBox parking = new VBox(10,
                label("Vehicle", 11, MUTED, true),
                label(plate + "  ·  " + type, 16, TEXT, true),
                separator(),
                label("Parking", 11, MUTED, true),
                detailLine("Spot", spot == null ? "—" : spot.getSpotId()),
                detailLine("Level", spot == null ? "—" : spot.getLocation()),
                separator(),
                label("Entry", 11, MUTED, true),
                label(entry, 15, TEXT, true),
                separator(),
                label("Current status", 11, MUTED, true),
                statusBadge(ticket.getStatus()));
        parking.getStyleClass().add("ticket-detail-card");
        parking.setPadding(new Insets(18));

        detail.getChildren().addAll(heading, parking);
        if (ticket.getStatus() == TicketStatus.ACTIVE) {
            detail.getChildren().add(label("Step 1 · Review exit", 11, MUTED, true));
            Button exit = actionButton("Exit parking", TEAL, e -> exitCustomerParking(ticket));
            exit.setMaxWidth(Double.MAX_VALUE);
            detail.getChildren().add(exit);
        } else if (ticket.getStatus() == TicketStatus.AWAITING_PAYMENT) {
            detail.getChildren().addAll(
                    label("Step 2 · Exit summary", 11, MUTED, true),
                    detailLine("Exit", ticket.getExitTime() == null ? "—" : ticket.getExitTime().format(DateTimeFormatter.ofPattern("dd MMM yyyy  •  HH:mm"))),
                    detailLine("Parking duration", durationText(ticket)),
                    feeBox(ticket, spot),
                    label("Step 3 · Payment", 11, MUTED, true),
                    paymentMethods(ticket));
            paymentValidation = label("", 11, RED, false);
            paymentValidation.getStyleClass().add("validation-error");
            detail.getChildren().addAll(paymentValidation, paymentButton(ticket));
        }
        animateIn(detail);
    }

    private void exitCustomerParking(Ticket ticket) {
        try {
            parkingService.vehicleExit(currentUser, ticket);
            toast.accept("Vehicle exit recorded. Payment is required to release the spot.");
            refreshList();
            showCustomerTicketDetails(ticket);
        } catch (InvalidTicketStatusException | TicketNotFoundException ex) {
            toast.accept(ex.getMessage());
        } catch (RuntimeException ex) {
            toast.accept("Exit failed: " + ex.getMessage());
        }
    }

    private void showCustomerPaymentComplete(Ticket ticket, Payment payment) {
        detail.getChildren().clear();
        VBox card = new VBox(12,
                label("Payment completed", 24, TEXT, true),
                label("Ticket closed", 15, GREEN, true),
                detailLine("Parking spot", safe(ticket.getParkingSpotId())),
                detailLine("Transaction", safe(payment == null ? null : payment.getPaymentId())));
        card.getStyleClass().add("ticket-detail-card");
        card.setPadding(new Insets(20));
        Button receipt = actionButton("View receipt", TEAL, e -> showReceipt(ticket, payment));
        Button done = actionButton("Done", MUTED, e -> backToDashboard.accept(null));
        detail.getChildren().addAll(card, receipt, done);
        animateIn(detail);
    }

    private HBox detailLine(String title, String value) {
        Region push = new Region();
        HBox.setHgrow(push, Priority.ALWAYS);
        HBox line = new HBox(12, label(title, 12, MUTED, false), push, label(value, 13, TEXT, true));
        line.setAlignment(Pos.CENTER_LEFT);
        return line;
    }

    private String prettyVehicleType(Vehicle vehicle) {
        if (vehicle.getVehicleType() == null) return "Vehicle";
        String value = vehicle.getVehicleType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private VBox feeBox(Ticket ticket, ParkingSpot spot) {
        double base = spot == null ? Math.max(0, ticket.getAmount()) : ticket.calculateAmount(spot);
        double tax = base * AppConfig.taxRate();
        double total = base + tax;
        if (ticket.getStatus() == TicketStatus.PAID && ticket.getFinalAmount() > 0) total = ticket.getFinalAmount();
        VBox v = new VBox(6);
        v.setPadding(new Insets(14));
        v.getStyleClass().add("fee-box");
        v.getChildren().addAll(
                label("CALCULATED FEE", 10, MUTED, true),
                label(money(total), 25, ORANGE, true),
                label("Base  " + money(base) + "     +     Tax "
                        + (int) (AppConfig.taxRate() * 100) + "%  " + money(tax), 11, MUTED, false));
        return v;
    }

    private VBox paymentMethods(Ticket ticket) {
        paymentTicket = ticket;
        ToggleGroup group = new ToggleGroup();
        RadioButton card = method(IconView.Name.CARD, "Card", Method.CARD, group);
        RadioButton cash = method(IconView.Name.CASH, "Cash", Method.CASH, group);
        RadioButton wallet = method(IconView.Name.WALLET, "Wallet", Method.WALLET, group);
        if (selectedMethod == Method.CARD) card.setSelected(true);
        else if (selectedMethod == Method.CASH) cash.setSelected(true);
        else wallet.setSelected(true);
        FlowPane h = new FlowPane(10, 10, card, cash, wallet);
        h.setPrefWrapLength(260);
        paymentFields = new VBox(8);
        updatePaymentFields();
        VBox panel = new VBox(10,
                label("Payment method", 11, MUTED, true),
                h,
                paymentFields);
        panel.getStyleClass().add("payment-panel");
        return panel;
    }

    private RadioButton method(IconView.Name icon, String text, Method m, ToggleGroup g) {
        RadioButton r = new RadioButton();
        r.setToggleGroup(g);
        r.setUserData(m);
        r.setText(text);
        r.setGraphic(IconView.of(icon, 18, MUTED));
        r.setPrefHeight(54);
        r.setMaxWidth(Double.MAX_VALUE);
        r.getStyleClass().add("method-option");
        HBox.setHgrow(r, Priority.ALWAYS);
        r.setOnAction(e -> {
            selectedMethod = (Method) r.getUserData();
            updatePaymentFields();
            validatePaymentSelection();
        });
        return r;
    }

    private void updatePaymentFields() {
        if (paymentFields == null) return;
        paymentFields.getChildren().clear();
        switch (selectedMethod) {
            case CARD:
                cardNumber = new PasswordField();
                cardNumber.setPromptText("Card number");
                cardNumber.setPrefHeight(40);
                cardNumber.getStyleClass().add("dark-input");
                cardHolder = textField("Cardholder name");
                expiry = textField("MM/YY");
                cvv = new PasswordField();
                cvv.setPromptText("CVV");
                cvv.setPrefHeight(40);
                cvv.getStyleClass().add("dark-input");
                FlowPane cardRow = new FlowPane(8, 8, cardNumber, expiry, cvv);
                cardRow.setPrefWrapLength(260);
                cardNumber.setPrefWidth(180);
                expiry.setPrefWidth(90);
                cvv.setPrefWidth(90);
                paymentFields.getChildren().addAll(cardHolder, cardRow);
                watchPaymentField(cardNumber);
                watchPaymentField(cardHolder);
                watchPaymentField(expiry);
                watchPaymentField(cvv);
                break;
            case CASH:
                cashReceived = textField("Cash received");
                double due = paymentTicket == null ? 0 : totalDue(paymentTicket);
                cashChangeLabel = label("Enter cash received to calculate change.", 12, MUTED, false);
                paymentFields.getChildren().addAll(
                        detailLine("Amount due", money(due)),
                        cashReceived,
                        cashChangeLabel);
                cashReceived.textProperty().addListener((obs, oldValue, newValue) -> {
                    updateCashFeedback(due);
                    updatePaymentButtonState();
                });
                break;
            case WALLET:
                Customer customer = currentUser instanceof Customer ? (Customer) currentUser : customerFor(selectedTicket);
                double balance = customer == null ? 0 : customer.getWalletBalance();
                double walletDue = paymentTicket == null ? 0 : totalDue(paymentTicket);
                walletBalanceLabel = label("Wallet balance     " + money(balance), 13, TEXT, true);
                walletRemainingLabel = label("Remaining balance  " + money(balance - walletDue), 13,
                        balance >= walletDue ? GREEN : RED, true);
                Button addFunds = new Button("Add funds");
                addFunds.getStyleClass().add("outline-button");
                addFunds.setOnAction(e -> showAddFundsDialog());
                paymentFields.getChildren().addAll(
                        walletBalanceLabel,
                        detailLine("Amount due", money(walletDue)),
                        walletRemainingLabel);
                if (balance < walletDue) {
                    paymentFields.getChildren().addAll(
                            label("Insufficient wallet balance", 13, RED, true),
                            label("Current balance:  " + money(balance) + "\nRequired:        " + money(walletDue), 12, MUTED, false),
                            addFunds);
                }
                break;
            default:
                break;
        }
    }

    private Button paymentButton(Ticket ticket) {
        Button b = actionButton("Pay " + money(totalDue(ticket)), TEAL, e -> processPayment(ticket, (Button) e.getSource()));
        activePaymentButton = b;
        updatePaymentButtonState();
        return b;
    }

    private void watchPaymentField(TextField field) {
        field.textProperty().addListener((obs, oldValue, newValue) -> updatePaymentButtonState());
    }

    private void updatePaymentButtonState() {
        if (activePaymentButton != null) activePaymentButton.setDisable(!validatePaymentSelection());
    }

    private void updateCashFeedback(double due) {
        if (cashChangeLabel == null || cashReceived == null) return;
        try {
            double received = Double.parseDouble(cashReceived.getText().trim());
            double change = received - due;
            if (change >= 0) {
                cashChangeLabel.setText("Change  " + money(change));
                cashChangeLabel.setTextFill(DesignTokens.color(GREEN));
            } else {
                cashChangeLabel.setText(money(-change) + " more required");
                cashChangeLabel.setTextFill(DesignTokens.color(RED));
            }
        } catch (NumberFormatException ex) {
            cashChangeLabel.setText("Enter cash received to calculate change.");
            cashChangeLabel.setTextFill(DesignTokens.color(MUTED));
        }
    }

    private void showAddFundsDialog() {
        Customer customer = currentUser instanceof Customer ? (Customer) currentUser : customerFor(paymentTicket);
        if (customer == null) return;
        TextField amount = textField("Amount");
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Add funds");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        dialog.getDialogPane().setContent(new VBox(10,
                label("Add funds to wallet", 18, TEXT, true),
                label("Enter the amount to add.", 12, MUTED, false), amount));
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;
        try {
            double value = Double.parseDouble(amount.getText().trim());
            if (value <= 0) throw new NumberFormatException();
            customer.addWalletBalance(value);
            userService.getPersistenceStore().saveUser(customer);
            updatePaymentFields();
            updatePaymentButtonState();
        } catch (NumberFormatException ex) {
            toast.accept("Enter a valid amount greater than zero.");
        }
    }

    private void processPayment(Ticket ticket, Button button) {
        ParkingSpot spot = garage.getSpotById(ticket.getParkingSpotId());
        if (spot == null) { toast.accept("Parking spot not found."); return; }
        if (!validatePaymentSelection()) return;

        button.setDisable(true);
        button.setText("Processing…");
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(22, 22);
        button.setGraphic(spinner);
        RotateTransition rotate = new RotateTransition(Duration.millis(1000), spinner);
        rotate.setByAngle(360); rotate.setCycleCount(Animation.INDEFINITE); own(rotate).play();
        paymentRotate = rotate;

        PauseTransition delay = new PauseTransition(Duration.millis(900));
        paymentDelay = delay;
        delay.setOnFinished(e -> {
            rotate.stop();
            paymentRotate = null;
            paymentDelay = null;
            if (!active(button)) return;
            try {
                if (ticket.getStatus() == TicketStatus.ACTIVE) {
                    parkingService.vehicleExit(currentUser, ticket);
                }

                if (ticket.getStatus() != TicketStatus.AWAITING_PAYMENT) {
                    throw new PaymentFailedException(
                            "Ticket is not ready for payment: " + ticket.getStatus());
                }

                double base = ticket.calculateAmount(spot);
                double total = base * (1 + AppConfig.taxRate());
                Payment payment = createPayment(ticket, total);
                paymentService.processPayment(currentUser, ticket, payment);
                paymentsByTicket.put(ticket.getTicketId(), payment);
                parkingService.releaseAfterPayment(ticket);
                clearPaymentFields();
                successPulse(button);
                if (currentUser != null && currentUser.getRole() == com.parking.enums.UserRole.CUSTOMER) {
                    showCustomerPaymentComplete(ticket, payment);
                    return;
                }
                toast.accept("Payment completed successfully.");
                backToDashboard.accept(null);
                showDetails(ticket);
            } catch (PaymentFailedException ex) {
                button.setDisable(false); button.setGraphic(null); button.setText("Process Payment");
                toast.accept(ex.getMessage());
                shake(button);
            } catch (InvalidTicketStatusException | TicketNotFoundException ex) {
                button.setDisable(false); button.setGraphic(null); button.setText("Process Payment");
                toast.accept(ex.getMessage());
            } catch (RuntimeException ex) {
                button.setDisable(false); button.setGraphic(null); button.setText("Process Payment");
                toast.accept("Payment error: " + ex.getMessage());
            }
        });
        own(delay).play();
    }

    private Payment createPayment(Ticket ticket, double amount) {
        String id = "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String customerId = ticket.getUserId();
        if (selectedMethod == Method.CARD) {
            if (cardNumber == null || cardHolder == null || expiry == null || cvv == null) {
                throw new IllegalStateException("Enter card details.");
            }
            return new CardPayment(id, ticket.getTicketId(), customerId, amount,
                    cardNumber.getText().replaceAll("\\s+", ""), cardHolder.getText().trim(),
                    expiry.getText().trim(), cvv.getText().trim());
        }
        if (selectedMethod == Method.CASH) {
            if (cashReceived == null || cashReceived.getText().trim().isEmpty()) {
                throw new IllegalStateException("Enter the cash amount received.");
            }
            double received;
            try { received = Double.parseDouble(cashReceived.getText().trim()); }
            catch (NumberFormatException ex) { throw new IllegalStateException("Enter a valid cash amount."); }
            CashPayment p = new CashPayment(id, ticket.getTicketId(), customerId, amount, received, currentUser == null ? "System" : currentUser.getUsername());
            return p;
        }
        if (selectedMethod == Method.WALLET) {
            Customer customer = customerFor(ticket);
            if (customer == null) throw new IllegalStateException("Customer wallet not found.");
            if (customer.getWalletBalance() < amount) throw new IllegalStateException("Insufficient wallet balance.");
            return new WalletPayment(id, ticket.getTicketId(), customerId, amount, "GUI-WALLET", "ParkingOS Wallet", customer.getEmail(), customer);
        }
        throw new IllegalStateException("Select a payment method.");
    }

    private void clearPaymentFields() {
        if (cardNumber != null) cardNumber.clear();
        if (cardHolder != null) cardHolder.clear();
        if (expiry != null) expiry.clear();
        if (cvv != null) cvv.clear();
        if (cashReceived != null) cashReceived.clear();
    }

    private void showReceipt(Ticket ticket, Payment payment) {
        if (payment == null) { toast.accept("No completed payment found for this ticket."); return; }
        StackPane overlay = overlay();
        VBox modal = modal(420);
        Vehicle vehicle = findVehicle(ticket);
        ParkingSpot spot = garage.getSpotById(ticket.getParkingSpotId());
        String transactionId = payment.getTransactionId() == null
                ? payment.getPaymentId() : payment.getTransactionId();
        modal.getChildren().addAll(
                label("Payment receipt", 22, TEXT, true),
                label("Payment completed", 12, GREEN, true),
                separator(),
                receiptLine("Ticket ID", payment.getTicketId()),
                receiptLine("Garage", GarageUiScope.label(payment.getGarageId() == null ? ticket.getGarageId() : payment.getGarageId())),
                receiptLine("Vehicle", vehicle == null ? "—" : safe(vehicle.getLicensePlate())),
                receiptLine("Spot", spot == null ? safe(ticket.getParkingSpotId()) : spot.getLocation() + " · " + spot.getSpotId()),
                receiptLine("Payment method", payment.getPaymentMethod()),
                receiptLine("Amount", money(payment.getAmount())),
                receiptLine("Transaction ID", transactionId),
                receiptLine("Payment time", payment.getPaymentTime() == null ? "—"
                        : payment.getPaymentTime().format(DateTimeFormatter.ofPattern("dd MMM yyyy  •  HH:mm"))),
                receiptLine("Status", ticket.getStatus().name()),
                separator(),
                label("Thank you for your visit!", 13, MUTED, false));
        String copiedTransactionId = transactionId;
        HBox actions = new HBox(10,
                actionButton("Print", MUTED, e -> printReceipt(modal)),
                actionButton("Copy transaction ID", TEAL, e -> {
                    ClipboardContent content = new ClipboardContent();
                    content.putString(copiedTransactionId);
                    Clipboard.getSystemClipboard().setContent(content);
                    toast.accept("Transaction ID copied.");
                }),
                actionButton("Close", ORANGE, e -> closeOverlay(overlay, modal)));
        modal.getChildren().add(actions);
        overlay.getChildren().add(modal);
        host.getChildren().add(overlay);
        overlay.setOnMouseClicked(e -> { if (e.getTarget() == overlay) closeOverlay(overlay, modal); });
        overlay.setOnKeyPressed(e -> { if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) closeOverlay(overlay, modal); });
        overlay.requestFocus();
        typedReceiptEffect(modal);
    }

    private void printReceipt(Node receipt) {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            toast.accept("No printer available. The receipt remains on screen.");
            return;
        }
        if (job.printPage(receipt)) {
            job.endJob();
            toast.accept("Receipt sent to printer.");
        } else {
            toast.accept("Printing was cancelled.");
        }
    }

    private void showRefund(Ticket ticket, Payment payment) {
        if (payment == null || !payment.canBeRefunded()) {
            toast.accept("This payment is outside the 14-day refund window or already refunded.");
            return;
        }
        StackPane overlay = overlay();
        VBox modal = modal(420);
        ComboBox<String> reason = new ComboBox<>();
        reason.getItems().addAll("Customer request", "Duplicate payment", "Service issue", "Other");
        reason.setValue("Customer request");
        styleCombo(reason);
        Label amount = label("Are you sure you want to refund " + money(payment.getFinalAmount()) + "?", 16, TEXT, true);
        HBox actions = new HBox(10,
                actionButton("Cancel", MUTED, e -> closeOverlay(overlay, modal)),
                actionButton("Confirm Refund", RED, e -> {
                    try {
                        paymentService.refundPayment(currentUser, payment);
                        ticket.markRefunded();
                        closeOverlay(overlay, modal);
                        showDetails(ticket);
                        toast.accept("Refund completed: " + reason.getValue());
                    } catch (PaymentFailedException ex) {
                        toast.accept(ex.getMessage());
                    }
                }));
        modal.getChildren().addAll(label("REFUND PAYMENT", 20, TEXT, true), separator(), amount,
                label("Reason", 10, MUTED, true), reason, actions);
        overlay.getChildren().add(modal);
        host.getChildren().add(overlay);
        overlay.setOnMouseClicked(e -> { if (e.getTarget() == overlay) closeOverlay(overlay, modal); });
        overlay.setOnKeyPressed(e -> { if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) closeOverlay(overlay, modal); });
        overlay.requestFocus();
        modal.setOpacity(0); modal.setTranslateY(-20);
        own(new ParallelTransition(fade(modal), slide(modal))).play();
    }

    private Vehicle findVehicleBySpot(ParkingSpot spot) {
        if (spot == null || spot.getVehicleId() == null) return null;
        return garage.getVehicle(spot.getVehicleId());
    }

    private Vehicle findVehicle(Ticket ticket) {
        if (ticket == null) return null;
        Vehicle v = garage.getVehicle(ticket.getVehicleId());
        if (v != null) return v;
        v = garage.getRegisteredVehicle(ticket.getVehicleId());
        return v;
    }

    private Customer customerFor(Ticket ticket) {
        if (ticket == null || ticket.getUserId() == null) return null;
        try {
            User u = currentUser != null && currentUser.getUserId().equals(ticket.getUserId()) ? currentUser : null;
            if (u instanceof Customer) return (Customer) u;
        } catch (RuntimeException ignored) { }
        try {
            User u = userService.getUserById(ticket.getUserId());
            if (u instanceof Customer) return (Customer) u;
        } catch (RuntimeException ignored) { }
        return null;
    }

    private List<Ticket> visibleTickets() {
        return garageContext == null ? ticketService.getTicketsFor(currentUser)
                : GarageUiScope.tickets(ticketService, currentUser, garageContext);
    }

    private List<Payment> visiblePayments() {
        return garageContext == null ? paymentService.getPaymentsFor(currentUser)
                : GarageUiScope.payments(paymentService, currentUser, garageContext);
    }

    private String scopeLabel() {
        if (garageContext == null || garageContext.isAllGarages()) return "All garages";
        return GarageUiScope.label(garageContext.getSelectedGarageId());
    }

    private String customerName(Ticket ticket, Vehicle v) {
        Customer c = customerFor(ticket);
        if (c != null) return c.getFullName();
        return v == null ? "Guest / Unknown" : "Customer " + safe(v.getUserId());
    }

    private Payment paymentFor(Ticket ticket) {
        if (ticket == null) return null;
        Payment cached = paymentsByTicket.get(ticket.getTicketId());
        if (cached != null) return cached;
        for (Payment p : visiblePayments()) {
            if (ticket.getTicketId().equals(p.getTicketId()) &&
                    (paymentForLatest(p, paymentsByTicket.get(ticket.getTicketId())))) {
                paymentsByTicket.put(ticket.getTicketId(), p);
                cached = p;
            }
        }
        return cached;
    }

    private boolean paymentForLatest(Payment p, Payment existing) {
        return existing == null || (p.getPaymentTime() != null && existing.getPaymentTime() != null && p.getPaymentTime().isAfter(existing.getPaymentTime()));
    }

    private double totalDue(Ticket t) {
        Payment p = paymentFor(t);
        if (p != null && p.getStatus() == PaymentStatus.COMPLETED) return p.getFinalAmount();
        ParkingSpot s = garage.getSpotById(t.getParkingSpotId());
        double base = s == null ? t.getAmount() : t.calculateAmount(s);
        return base * (1 + AppConfig.taxRate());
    }

    private String durationText(Ticket t) {
        if (t == null || t.getEntryTime() == null) return "0h 00m";
        LocalDateTime end = t.getExitTime() == null ? LocalDateTime.now() : t.getExitTime();
        long minutes = Math.max(0, ChronoUnit.MINUTES.between(t.getEntryTime(), end));
        return (minutes / 60) + "h " + String.format("%02d", minutes % 60) + "m";
    }

    private void startTimer(Label duration) {
        stopTimer();
        liveTimer = new Timeline(new KeyFrame(Duration.ZERO, e -> duration.setText(durationText(selectedTicket))),
                new KeyFrame(Duration.seconds(1)));
        liveTimer.setCycleCount(Animation.INDEFINITE);
        own(liveTimer).play();
    }

    private void stopTimer() {
        if (liveTimer != null) {
            liveTimer.stop();
            ownedAnimations.remove(liveTimer);
        }
        liveTimer = null;
    }

    private void typedReceiptEffect(VBox modal) {
        List<Node> nodes = new ArrayList<>(modal.getChildren());
        for (Node n : nodes) if (n instanceof Label) {
            Label l = (Label) n;
            String full = l.getText();
            if (full == null || full.length() < 10) continue;
            l.setText("");
            Timeline t = new Timeline();
            for (int i = 1; i <= full.length(); i++) {
                final int end = i;
                t.getKeyFrames().add(new KeyFrame(Duration.millis(i * 12), e -> l.setText(full.substring(0, end))));
            }
            own(t).play();
        }
    }

    private StackPane overlay() {
        StackPane o = new StackPane();
        o.setFocusTraversable(true);
        o.getStyleClass().add("phase6-modal-overlay");
        return o;
    }

    private VBox modal(double width) {
        VBox m = new VBox(12);
        m.setAlignment(Pos.TOP_LEFT);
        m.setPadding(new Insets(24));
        m.setPrefWidth(width);
        m.setMaxWidth(width);
        m.getStyleClass().add("phase6-modal");
        m.setEffect(new DropShadow(32, DesignTokens.color(SHADOW_BLACK_50)));
        return m;
    }

    private void closeOverlay(StackPane overlay, Node modal) {
        ParallelTransition p = new ParallelTransition(fadeOut(modal), slideOut(modal));
        p.setOnFinished(e -> {
            if (!disposed && overlay.getParent() instanceof Pane) ((Pane) overlay.getParent()).getChildren().remove(overlay);
        });
        own(p).play();
    }

    private FadeTransition fade(Node n) { FadeTransition f = new FadeTransition(Duration.millis(200), n); f.setFromValue(0); f.setToValue(1); return f; }
    private TranslateTransition slide(Node n) { TranslateTransition t = new TranslateTransition(Duration.millis(300), n); t.setFromY(-20); t.setToY(0); return t; }
    private FadeTransition fadeOut(Node n) { FadeTransition f = new FadeTransition(Duration.millis(150), n); f.setToValue(0); return f; }
    private TranslateTransition slideOut(Node n) { TranslateTransition t = new TranslateTransition(Duration.millis(150), n); t.setToY(-12); return t; }

    private void successPulse(Node n) {
        n.getStyleClass().add("success-pulse");
        ScaleTransition p = new ScaleTransition(Duration.millis(160), n); p.setFromX(.98); p.setFromY(.98); p.setToX(1.03); p.setToY(1.03); p.setAutoReverse(true); p.setCycleCount(2); own(p).play();
    }

    private void shake(Node n) { UiMotion.shake(n); }

    private void animateIn(Node n) {
        n.setOpacity(0); n.setTranslateY(10);
        own(new ParallelTransition(fade(n), slideIn(n))).play();
    }
    private TranslateTransition slideIn(Node n) { TranslateTransition t = new TranslateTransition(Duration.millis(220), n); t.setToY(0); t.setFromY(10); return t; }

    private VBox panel() {
        VBox v = new VBox(12);
        v.getStyleClass().add("ticket-panel");
        return v;
    }

    private VBox centered(Node n) { VBox v = new VBox(n); v.setAlignment(Pos.CENTER); v.setFillWidth(true); VBox.setVgrow(v, Priority.ALWAYS); return v; }

    private HBox receiptLine(String left, String right) {
        Label a = label(left, 11, MUTED, false); Label b = label(right == null ? "—" : right, 12, TEXT, true); Region r = new Region(); HBox.setHgrow(r, Priority.ALWAYS); HBox h = new HBox(10, a, r, b); h.setAlignment(Pos.CENTER_LEFT); return h;
    }

    private void addInfo(GridPane g, int row, String key, String value) {
        g.add(label(key.toUpperCase(), 10, MUTED, true), 0, row); g.add(label(value, 13, TEXT, false), 1, row);
    }

    private HBox section(String s) { return new HBox(label(s, 10, TEAL, true)); }
    private Separator separator() { return UiNodes.separator(); }

    private Button button(String text, String cls) { Button b = new Button(text); b.setPrefHeight(40); b.getStyleClass().add(cls); return b; }
    private Button actionButton(String text, String color, javafx.event.EventHandler<javafx.event.ActionEvent> h) { Button b = new Button(text); b.setMaxWidth(Double.MAX_VALUE); b.setPrefHeight(44); b.getStyleClass().add("action-button-gradient"); b.setOnAction(h); return b; }
    private Button iconButton(IconView.Name icon) {
        Button b = UiNodes.iconButton(icon);
        b.getStyleClass().remove("icon-button");
        b.getStyleClass().add("icon-button-card");
        b.setPrefSize(36, 36);
        return b;
    }
    private Label statusBadge(TicketStatus s) { Label l = label(statusText(s), 10, BG, true); l.setPadding(new Insets(4,8,4,8)); l.getStyleClass().add(statusBadgeClass(s)); return l; }
    private String statusBadgeClass(TicketStatus s) { if (s == TicketStatus.ACTIVE) return "status-badge-active"; if (s == TicketStatus.AWAITING_PAYMENT) return "status-badge-awaiting"; if (s == TicketStatus.PAID) return "status-badge-paid"; if (s == TicketStatus.REFUNDED) return "status-badge-refunded"; if (s == TicketStatus.CANCELLED) return "status-badge-cancelled"; return "status-badge-closed"; }
    private Label label(String s, double n, String color, boolean bold) { return DesignTokens.text(s, n, color, bold); }
    private TextField textField(String prompt) { TextField f = new TextField(); f.setPromptText(prompt); f.setPrefHeight(40); f.getStyleClass().add("dark-input"); return f; }
    private boolean validatePaymentSelection() {
        if (paymentValidation == null) return true;
        boolean valid = true;
        String message;
        if (selectedMethod == Method.CARD) {
            String number = cardNumber == null ? "" : cardNumber.getText().replaceAll("\\s+", "");
            String holder = cardHolder == null ? "" : cardHolder.getText().trim();
            String expiration = expiry == null ? "" : expiry.getText().trim();
            String securityCode = cvv == null ? "" : cvv.getText().trim();
            valid = number.matches("\\d{13,19}")
                    && !holder.isEmpty()
                    && expiration.matches("(0[1-9]|1[0-2])/\\d{2}")
                    && securityCode.matches("\\d{3,4}");
            message = valid ? "Card details are ready to verify." : "Enter a valid card number, name, expiry, and CVV.";
        } else if (selectedMethod == Method.WALLET) {
            Customer customer = currentUser instanceof Customer ? (Customer) currentUser : customerFor(paymentTicket);
            double due = paymentTicket == null ? 0 : totalDue(paymentTicket);
            double balance = customer == null ? 0 : customer.getWalletBalance();
            valid = customer != null && balance >= due;
            message = valid ? "Wallet balance is sufficient." : "Insufficient wallet balance.";
        } else {
            double due = paymentTicket == null ? 0 : totalDue(paymentTicket);
            double received = 0;
            try { received = cashReceived == null ? 0 : Double.parseDouble(cashReceived.getText().trim()); }
            catch (NumberFormatException ignored) { }
            valid = received >= due && received > 0;
            message = valid ? "Cash received is sufficient." : "Enter enough cash to cover the amount due.";
        }
        paymentValidation.setText(message);
        paymentValidation.getStyleClass().removeAll("validation-error", "validation-success");
        paymentValidation.getStyleClass().add(valid ? "validation-success" : "validation-error");
        return valid;
    }
    private void styleCombo(ComboBox<?> c) { c.setPrefHeight(40); c.getStyleClass().add("dark-combo"); }

    private String statusText(TicketStatus s) { return s == null ? "UNKNOWN" : s.name(); }
    private String statusColor(TicketStatus s) { if (s == TicketStatus.ACTIVE) return GREEN; if (s == TicketStatus.AWAITING_PAYMENT) return YELLOW; if (s == TicketStatus.PAID) return TEAL; if (s == TicketStatus.REFUNDED) return BLUE; return RED; }
    private String prettyType(ParkingSpot s) { return UiFormat.prettyType(s.getSpotType()); }
    private String money(double v) { return UiFormat.money(v); }
    private String safe(String s) { return s == null ? "—" : s; }
}
