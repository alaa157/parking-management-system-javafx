package com.parking.gui;

import com.parking.enums.SpotStatus;
import com.parking.enums.SpotType;
import com.parking.enums.TicketStatus;
import com.parking.enums.VehicleType;
import com.parking.exceptions.InvalidTicketStatusException;
import com.parking.exceptions.SpotNotAvailableException;
import com.parking.exceptions.VehicleAlreadyParkedException;
import com.parking.exceptions.TicketNotFoundException;
import com.parking.model.Customer;
import com.parking.model.ParkingGarage;
import com.parking.model.ParkingSpot;
import com.parking.model.Reservation;
import com.parking.model.Ticket;
import com.parking.model.User;
import com.parking.model.Vehicle;
import com.parking.persistence.PersistenceStore;
import com.parking.services.ParkingService;
import com.parking.services.ReservationService;
import com.parking.services.TicketService;
import com.parking.services.UserService;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.parking.gui.DesignTokens.*;

/** Real-time garage occupancy map and parking actions. */
public final class OccupancyMapView {
    public interface OccupancyHost {
        void toast(String message, String accent);
        TicketPaymentView ticketView();
        boolean isShuttingDown();
    }

    private final ParkingGarage garage; private final ParkingService parking; private final TicketService tickets; private final UserService users; private final PersistenceStore persistence; private final User actor; private final OccupancyHost host; private final Consumer<Node> navigate; private final Function<BorderPane, Node> vehicleForm; private final Function<BorderPane, Node> ticketPage;
    private final Map<String, Button> spotButtons = new HashMap<>(); private final List<ParkingSpot> levelSpots = new ArrayList<>(); private final List<Animation> animations = new ArrayList<>();
    private BorderPane shell; private int currentLevel; private boolean listView; private ParkingSpot selected; private StackPane mapStack; private VBox detailPanel; private Region backdrop; private Label levelTitle, levelSubtitle, available, occupied, maintenance, occupancy; private ProgressBar occupancyProgress; private TextField search; private ComboBox<String> filter; private TilePane grid; private VBox list; private final List<Button> levelTabs = new ArrayList<>();

    public OccupancyMapView(ParkingGarage garage, ParkingService parking, TicketService tickets, UserService users, PersistenceStore persistence, User actor, OccupancyHost host, Consumer<Node> navigate, Function<BorderPane, Node> vehicleForm, Function<BorderPane, Node> ticketPage) { this.garage = Objects.requireNonNull(garage); this.parking = Objects.requireNonNull(parking); this.tickets = Objects.requireNonNull(tickets); this.users = Objects.requireNonNull(users); this.persistence = Objects.requireNonNull(persistence); this.actor = Objects.requireNonNull(actor); this.host = Objects.requireNonNull(host); this.navigate = Objects.requireNonNull(navigate); this.vehicleForm = Objects.requireNonNull(vehicleForm); this.ticketPage = Objects.requireNonNull(ticketPage); }

    public Node build(BorderPane shell) {
        this.shell = shell;
        BorderPane page = new BorderPane(); page.getStyleClass().add("page-root"); VBox main = new VBox(18); main.setPadding(new Insets(24));
        main.getChildren().addAll(new VBox(3, DesignTokens.text("Garage status", 24, TEXT, true), DesignTokens.text((garage.isOpen() ? "OPEN" : "CLOSED") + "  •  Real-time occupancy and spot operations", 14, MUTED, true)), toolbar(), stats());
        mapStack = new StackPane(); mapStack.setPrefHeight(520); mapStack.getStyleClass().add("map-shell"); backdrop = new Region(); backdrop.setManaged(false); backdrop.setVisible(false); backdrop.getStyleClass().add("detail-backdrop"); backdrop.setOnMouseClicked(e -> closeDetail()); mapStack.getChildren().add(backdrop); mapStack.getChildren().add(mapContent()); detailPanel = detailPanel(); detailPanel.setTranslateX(330); StackPane.setAlignment(detailPanel, Pos.CENTER_RIGHT); mapStack.getChildren().add(detailPanel); main.getChildren().add(mapStack); VBox.setVgrow(mapStack, Priority.ALWAYS);
        ScrollPane scroll = new ScrollPane(main); scroll.setFitToWidth(true); scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); scroll.getStyleClass().add("dark-scroll"); refreshLevel(0); return scroll;
    }
    private HBox toolbar() { HBox bar = new HBox(10); bar.setAlignment(Pos.CENTER_LEFT); search = new TextField(); search.setPromptText("Search spot, vehicle, customer..."); search.getStyleClass().add("toolbar-input"); HBox.setHgrow(search, Priority.ALWAYS); search.textProperty().addListener((o,a,b) -> applyFilters()); filter = new ComboBox<>(); filter.getItems().addAll("All Types", "Standard", "Compact", "Large", "EV Charging", "Handicapped"); filter.setValue("All Types"); filter.getStyleClass().add("dark-combo"); filter.valueProperty().addListener((o,a,b) -> applyFilters()); Button refresh = new Button("Refresh"); refresh.getStyleClass().add("outline-button"); refresh.setOnAction(e -> refreshLevel(currentLevel)); Button gridButton = new Button("Grid"); Button listButton = new Button("List"); gridButton.setOnAction(e -> { listView = false; render(); }); listButton.setOnAction(e -> { listView = true; render(); }); bar.getChildren().addAll(search, filter, refresh, gridButton, listButton); return bar; }
    private HBox stats() { HBox stats = new HBox(12); stats.setAlignment(Pos.CENTER_LEFT); available = DesignTokens.text("0", 22, GREEN, true); occupied = DesignTokens.text("0", 22, RED, true); maintenance = DesignTokens.text("0", 22, YELLOW, true); occupancy = DesignTokens.text("0%", 22, TEAL, true); occupancyProgress = new ProgressBar(); occupancyProgress.setPrefWidth(105); occupancyProgress.getStyleClass().add("teal-progress-small"); stats.getChildren().addAll(stat("AVAILABLE", available), stat("OCCUPIED", occupied), stat("MAINTENANCE", maintenance), new VBox(5, occupancy, occupancyProgress)); return stats; }
    private VBox stat(String title, Label value) { VBox box = new VBox(2, value, DesignTokens.text(title, 10, MUTED, true)); box.setAlignment(Pos.CENTER); box.setPrefWidth(130); box.setPrefHeight(70); box.getStyleClass().add("mini-stat"); return box; }
    private VBox mapContent() { VBox content = new VBox(14); HBox header = new HBox(); header.setAlignment(Pos.CENTER_LEFT); levelTitle = DesignTokens.text("Level 1 — Standard Zone", 20, TEXT, true); levelSubtitle = DesignTokens.text("0 spots available of 20", 13, MUTED, false); header.getChildren().add(new VBox(3, levelTitle, levelSubtitle)); HBox tabs = new HBox(); tabs.getStyleClass().add("level-tabs"); levelTabs.clear(); for (int i=0;i<3;i++) { final int level=i; Button tab = new Button("Level " + (i+1)); tab.getStyleClass().add(i==0?"level-tab-active":"level-tab"); tab.setOnAction(e -> refreshLevel(level)); levelTabs.add(tab); tabs.getChildren().add(tab); } grid = new TilePane(); grid.setPrefColumns(5); grid.setHgap(12); grid.setVgap(12); grid.getStyleClass().add("spot-grid"); list = new VBox(7); list.getStyleClass().add("spot-list"); content.getChildren().addAll(header, tabs, new StackPane(grid), legend()); return content; }
    private VBox legend() { HBox row = new HBox(18, item(GREEN,"Available"), item(RED,"Occupied"), item(YELLOW,"Maintenance"), item(BLUE,"Reserved"), item(TEAL,"EV Charging"), item(MUTED,"Out of Service")); VBox box = new VBox(6, DesignTokens.text("LEGEND", 10, MUTED, true), row); return box; }
    private HBox item(String color, String text) { return new HBox(7, new Circle(5, Color.web(color)), DesignTokens.text(text, 11, MUTED, false)); }
    private void refreshLevel(int level) { currentLevel=level; for (int i=0;i<levelTabs.size();i++) { Button tab=levelTabs.get(i); tab.getStyleClass().removeAll("level-tab", "level-tab-active"); tab.getStyleClass().add(i==level?"level-tab-active":"level-tab"); } levelSpots.clear(); if (garage.getLevels().get(level)!=null) levelSpots.addAll(garage.getLevels().get(level)); levelTitle.setText("Level " + (level+1) + " — " + zone(level)); render(); updateStats(); }
    private void render() { if (grid==null) return; grid.getChildren().clear(); list.getChildren().clear(); spotButtons.clear(); for (ParkingSpot spot : levelSpots) { Button tile = tile(spot, false); Button row = tile(spot, true); grid.getChildren().add(tile); list.getChildren().add(row); spotButtons.put(spot.getSpotId(), tile); } Node parent = grid.getParent(); if (parent instanceof StackPane stack) { stack.getChildren().setAll(listView ? list : grid); } applyFilters(); }
    private Button tile(ParkingSpot spot, boolean compact) { Button button = new Button(); button.setFocusTraversable(false); button.getStyleClass().add(compact ? "spot-list-row" : "spot-tile"); button.setPrefSize(compact ? 0 : 112, compact ? 54 : 82); String status=statusText(spot.getStatus()); if (compact) { HBox row = new HBox(14, new Circle(5.5, Color.web(statusColor(spot.getStatus()))), new VBox(2, DesignTokens.text(spot.getSpotId(),13,TEXT,true), DesignTokens.text(prettyType(spot.getSpotType())+"  •  "+UiFormat.money(spot.getHourlyRate())+"/hr",10,MUTED,false)), UiNodes.spacer(), DesignTokens.text(status,10,statusColor(spot.getStatus()),true)); row.setAlignment(Pos.CENTER_LEFT); button.setGraphic(row); } else { VBox inside = new VBox(2, DesignTokens.text(spot.getSpotId(),11,TEXT,true), DesignTokens.text(status.toUpperCase(),8,statusColor(spot.getStatus()),true), DesignTokens.text(prettyType(spot.getSpotType()),9,MUTED,false), DesignTokens.text(UiFormat.money(spot.getHourlyRate())+"/hr",9,MUTED,false)); inside.setAlignment(Pos.CENTER); button.setGraphic(inside); } applyStyle(button, spot.getStatus()); button.setTooltip(new Tooltip("Spot: " + spot.getSpotId() + "\nStatus: " + status)); button.setOnAction(e -> select(spot)); return button; }
    private void applyStyle(Button button, SpotStatus status) { button.getStyleClass().removeIf(value -> value.startsWith("spot-") && !value.equals("spot-tile")); button.getStyleClass().add("spot-" + status.name().toLowerCase(Locale.ROOT)); }
    private void applyFilters() { if (grid==null) return; String query=search==null?"":search.getText().trim().toLowerCase(Locale.ROOT); String type=filter==null?"All Types":filter.getValue(); for (Node node : (listView?list:grid).getChildren()) { ParkingSpot spot=spot(node); if (spot==null) continue; boolean match=("All Types".equals(type)||prettyType(spot.getSpotType()).equalsIgnoreCase(type)) && (query.isEmpty() || (spot.getSpotId()+" "+prettyType(spot.getSpotType())+" "+statusText(spot.getStatus())).toLowerCase(Locale.ROOT).contains(query)); node.setOpacity(match?1.0:0.30); } }
    private ParkingSpot spot(Node node) { if (!(node instanceof Button button) || button.getTooltip()==null) return null; String text=button.getTooltip().getText(); if (!text.startsWith("Spot: ")) return null; return garage.getSpotById(text.substring(6, text.indexOf('\n'))); }
    private VBox detailPanel() { VBox panel=new VBox(16); panel.getStyleClass().add("detail-panel"); panel.setPrefWidth(300); panel.setMaxWidth(300); panel.setPadding(new Insets(20)); return panel; }
    private void select(ParkingSpot spot) { selected=spot; showDetails(spot); }
    private void showDetails(ParkingSpot spot) { detailPanel.getChildren().clear(); HBox header=new HBox(DesignTokens.text(spot.getSpotId(),22,TEXT,true),UiNodes.spacer()); Button close=new Button("Close"); close.getStyleClass().add("detail-close"); close.setOnAction(e->closeDetail()); header.setAlignment(Pos.CENTER_LEFT); header.getChildren().add(close); detailPanel.getChildren().addAll(header, DesignTokens.text(statusText(spot.getStatus()),11,BG,true), row("TYPE",prettyType(spot.getSpotType())), row("STATUS",statusText(spot.getStatus()))); if (spot.getStatus()==SpotStatus.OCCUPIED) { Vehicle vehicle=spot.getVehicleId()==null?null:garage.getVehicle(spot.getVehicleId()); Ticket ticket=findTicket(spot); Button exit=new Button("Exit Vehicle"); exit.getStyleClass().add("exit-button"); exit.setMaxWidth(Double.MAX_VALUE); exit.setOnAction(e->performExit(spot,ticket)); detailPanel.getChildren().addAll(row("VEHICLE ID", vehicle==null?"—":vehicle.getVehicleId()), exit); } else if (spot.getStatus()==SpotStatus.UNDER_MAINTENANCE) { Button release=new Button("Release Spot"); release.getStyleClass().add("release-button"); release.setOnAction(e->{ spot.removeFromMaintenance(); garage.updateAvailability(); host.toast(spot.getSpotId()+" released from maintenance.",GREEN); showDetails(spot); updateStats(); }); detailPanel.getChildren().add(release); } else if (spot.isAvailable()) { if (actor instanceof Customer) { Button park=new Button("Park Vehicle"); park.getStyleClass().add("park-button"); park.setOnAction(e->performPark(spot)); detailPanel.getChildren().add(park); } else { Button maintain=new Button("Put Under Maintenance"); maintain.getStyleClass().add("release-button"); maintain.setOnAction(e->{ spot.setUnderMaintenance("Placed under maintenance by administrator"); garage.updateAvailability(); host.toast(spot.getSpotId()+" placed under maintenance.",YELLOW); showDetails(spot); updateStats(); });         detailPanel.getChildren().add(maintain); } } showReservationSection(spot); openDetail(); }
    private void showReservationSection(ParkingSpot spot) {
        ReservationService reservations = parking.getReservationService();
        if (reservations == null || host.isShuttingDown()) return;
        if (spot.getStatus() == SpotStatus.RESERVED) {
            String holder = spot.getReservationHolderUserId();
            boolean mine = holder != null && holder.equals(actor.getUserId());
            detailPanel.getChildren().add(row("RESERVED BY", mine ? "You" : (holder == null ? "—" : holder)));
            detailPanel.getChildren().add(row("EXPIRES", countdownText(spot.getReservationExpiry())));
            if (mine) {
                reservations.activeForSpot(spot.getSpotId()).ifPresent(hold -> {
                    if (actor instanceof Customer customer) {
                        Button parkHere = new Button("Park Here");
                        parkHere.getStyleClass().add("park-button");
                        parkHere.setMaxWidth(Double.MAX_VALUE);
                        parkHere.setOnAction(e -> showVehicleSelection(spot, customer, hold));
                        detailPanel.getChildren().add(parkHere);
                    }
                    Button cancel = new Button("Cancel Reservation");
                    cancel.getStyleClass().add("release-button");
                    cancel.setMaxWidth(Double.MAX_VALUE);
                    cancel.setOnAction(e -> cancelReservation(hold));
                    detailPanel.getChildren().add(cancel);
                });
            }
        } else if (spot.isAvailable() && actor.isActive()) {
            Button reserve = new Button("Reserve Spot");
            reserve.getStyleClass().add("park-button");
            reserve.setMaxWidth(Double.MAX_VALUE);
            reserve.setOnAction(e -> reserveSpot(spot));
            detailPanel.getChildren().add(reserve);
        }
    }
    private void reserveSpot(ParkingSpot spot) {
        try {
            Reservation hold = parking.getReservationService().reserve(actor, spot.getSpotId(), LocalDateTime.now());
            host.toast("Spot " + spot.getSpotId() + " reserved until " + hold.expiresAt().format(DateTimeFormatter.ofPattern("HH:mm")) + ".", GREEN);
            showDetails(spot); updateStats();
        } catch (RuntimeException ex) { host.toast("Reservation failed: " + ex.getMessage(), RED); }
    }
    private void cancelReservation(Reservation hold) {
        try {
            parking.getReservationService().cancel(actor, hold.reservationId(), LocalDateTime.now());
            host.toast("Reservation for spot " + hold.spotId() + " cancelled.", GREEN);
            ParkingSpot spot = garage.getSpotById(hold.spotId());
            if (spot != null) showDetails(spot);
            updateStats();
        } catch (RuntimeException ex) { host.toast("Cancellation failed: " + ex.getMessage(), RED); }
    }
    private String countdownText(LocalDateTime expiry) {
        if (expiry == null) return "—";
        long minutes = ChronoUnit.MINUTES.between(LocalDateTime.now(), expiry);
        String at = expiry.format(DateTimeFormatter.ofPattern("dd MMM HH:mm"));
        if (minutes <= 0) return at + " (expiring)";
        return at + " (in " + minutes + " min)";
    }
    private VBox row(String key,String value) { VBox row=new VBox(2,DesignTokens.text(key,10,MUTED,true),DesignTokens.text(value,13,TEXT,true)); row.getStyleClass().add("detail-row"); return row; }
    private Ticket findTicket(ParkingSpot spot) { for (Ticket ticket : tickets.getTicketsFor(actor)) if (spot.getSpotId().equals(ticket.getParkingSpotId()) && (ticket.getStatus()==TicketStatus.ACTIVE||ticket.getStatus()==TicketStatus.AWAITING_PAYMENT||ticket.getStatus()==TicketStatus.PAID)) return ticket; return null; }
    private void performPark(ParkingSpot spot) { if (!(actor instanceof Customer customer)) { host.toast("Only customer accounts can park vehicles.",RED); return; } if (spot.getStatus()==SpotStatus.RESERVED) { ReservationService reservations=parking.getReservationService(); Optional<Reservation> hold=reservations==null?Optional.empty():reservations.activeForSpot(spot.getSpotId()); if (hold.isPresent()&&actor.getUserId().equals(hold.get().userId())) { showVehicleSelection(spot,customer,hold.get()); return; } host.toast("This spot is reserved.",YELLOW); return; } if (!spot.isAvailable()) { host.toast("The selected spot is not available.",RED); return; } showVehicleSelection(spot,customer); }
    private void showVehicleSelection(ParkingSpot spot, Customer customer) { showVehicleSelection(spot, customer, null); }
    private void showVehicleSelection(ParkingSpot spot, Customer customer, Reservation hold) { List<Vehicle> eligible=new ArrayList<>(); for (String id: customer.getVehicleIds()==null?Collections.<String>emptyList():customer.getVehicleIds()) { Vehicle vehicle=garage.getRegisteredVehicle(id); if (vehicle!=null&&!vehicle.isParked()) eligible.add(vehicle); } Dialog<Vehicle> dialog=new Dialog<>(); dialog.setTitle("Select vehicle"); DialogPane pane=dialog.getDialogPane(); pane.getStyleClass().add("vehicle-selection-dialog"); pane.getButtonTypes().addAll(ButtonType.CANCEL,ButtonType.OK); ToggleGroup group=new ToggleGroup(); VBox content=new VBox(12,DesignTokens.text("Select vehicle",20,TEXT,true)); if (eligible.isEmpty()) { Button addVehicle=new Button("Add vehicle"); addVehicle.getStyleClass().add("primary-button"); addVehicle.setOnAction(e->{ dialog.close(); navigate.accept(vehicleForm.apply(shell)); }); content.getChildren().addAll(DesignTokens.text("No available vehicles",16,TEXT,true), addVehicle); } for(Vehicle vehicle:eligible){RadioButton choice=new RadioButton(safe(vehicle.getLicensePlate(),vehicle.getVehicleId())); choice.setToggleGroup(group); choice.setUserData(vehicle); content.getChildren().add(choice);} pane.setContent(content); dialog.setResultConverter(button->button==ButtonType.OK&&group.getSelectedToggle()!=null?(Vehicle)group.getSelectedToggle().getUserData():null); dialog.showAndWait().ifPresent(vehicle->parkSelectedVehicle(vehicle, hold)); }
    private void parkSelectedVehicle(Vehicle vehicle) { parkSelectedVehicle(vehicle, null); }
    private void parkSelectedVehicle(Vehicle vehicle, Reservation hold) { try { Ticket ticket=hold==null?parking.vehicleEntry(actor,vehicle):parking.vehicleEntry(actor,vehicle,hold.reservationId()); garage.updateAvailability(); showDetails(garage.getSpotById(ticket.getParkingSpotId())); showConfirmation(ticket,vehicle); } catch (SpotNotAvailableException ex) { host.toast("No suitable parking space available.",RED); } catch (VehicleAlreadyParkedException|InvalidTicketStatusException ex) { host.toast(ex.getMessage(),RED); } catch (RuntimeException ex) { host.toast("Parking failed: "+ex.getMessage(),RED); } }
    private void showConfirmation(Ticket ticket, Vehicle vehicle) { Dialog<ButtonType> dialog=new Dialog<>(); dialog.setTitle("Vehicle parked"); dialog.getDialogPane().getStyleClass().add("parking-confirmation-dialog"); ButtonType view=new ButtonType("View ticket"); dialog.getDialogPane().getButtonTypes().addAll(view,ButtonType.CLOSE); dialog.getDialogPane().setContent(new VBox(12,DesignTokens.text("Vehicle parked",22,TEXT,true),DesignTokens.text("Your parking session is now active.",13,MUTED,false),row("Vehicle",safe(vehicle.getLicensePlate(),vehicle.getVehicleId())),row("Parking spot",safe(ticket.getParkingSpotId(),"—")))); dialog.showAndWait().ifPresent(result->{if(result==view) navigate.accept(ticketPage.apply(shell));}); }
    private void performExit(ParkingSpot spot, Ticket ticket) { if (ticket==null) { host.toast("No active ticket exists for this occupied spot.",RED); return; } try { parking.vehicleExit(actor,ticket); host.toast("Vehicle exit recorded. Payment is required before release.",YELLOW); garage.updateAvailability(); showDetails(spot); updateStats(); } catch (TicketNotFoundException|InvalidTicketStatusException ex) { host.toast("Exit operation failed: "+ex.getMessage(),RED); } catch (RuntimeException ex) { host.toast("Exit operation failed: "+ex.getMessage(),RED); } }
    private void openDetail() { backdrop.setVisible(true); backdrop.setManaged(true); TranslateTransition transition=new TranslateTransition(Duration.millis(300),detailPanel); transition.setFromX(330); transition.setToX(0); transition.play(); }
    private void closeDetail() { if(detailPanel==null)return; TranslateTransition transition=new TranslateTransition(Duration.millis(300),detailPanel); transition.setFromX(detailPanel.getTranslateX()); transition.setToX(330); transition.setOnFinished(e->{backdrop.setVisible(false);backdrop.setManaged(false);}); transition.play(); selected=null; }
    private void updateStats() { int a=0,o=0,m=0; for(ParkingSpot spot:garage.getAllSpots()) switch(spot.getStatus()){case AVAILABLE->a++;case OCCUPIED->o++;case UNDER_MAINTENANCE,OUT_OF_SERVICE->m++;default->{} } available.setText(String.valueOf(a)); occupied.setText(String.valueOf(o)); maintenance.setText(String.valueOf(m)); double rate=garage.getOccupancyRate()*100; occupancy.setText(String.format(Locale.US,"%.0f%%",rate)); occupancyProgress.setProgress(rate/100); levelSubtitle.setText(a+" spots available of "+levelSpots.size()); }
    private String zone(int level){return level==0?"Standard Zone":level==1?"Mixed Vehicle Zone":"EV & Premium Zone";}
    private String prettyType(SpotType type){return UiFormat.prettyType(type);} private String statusText(SpotStatus status){return UiFormat.statusText(status);} private String statusColor(SpotStatus status){return switch(status){case AVAILABLE->GREEN;case OCCUPIED->RED;case UNDER_MAINTENANCE->YELLOW;case RESERVED->BLUE;case OUT_OF_SERVICE->MUTED;};} private String vehicleTypeLabel(VehicleType type){return type==null?"—":type.name();} private VehicleType vehicleTypeFor(SpotType type){return switch(type){case EV_CHARGING->VehicleType.ELECTRIC_VEHICLE;case HANDICAPPED->VehicleType.HANDICAPPED;case LARGE->VehicleType.SUV;default->VehicleType.CAR;};} private String safe(String value,String fallback){return value==null||value.isBlank()?fallback:value;}
}
