package com.parking.gui;

import com.parking.enums.VehicleType;
import com.parking.model.Customer;
import com.parking.model.ParkingGarage;
import com.parking.model.Vehicle;
import com.parking.persistence.PersistenceStore;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Collections;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.parking.gui.DesignTokens.*;

/** Customer vehicle list and registration page. */
public final class CustomerVehiclesView {
    private final PersistenceStore persistence;
    private final ParkingGarage garage;
    private final Customer customer;
    private final Consumer<Node> navigate;
    private final Function<BorderPane, Node> parkingPage;
    private final Function<BorderPane, Node> ticketPage;
    private final Consumer<String> toast;

    public CustomerVehiclesView(PersistenceStore persistence, ParkingGarage garage, Customer customer,
                                Consumer<Node> navigate, Function<BorderPane, Node> parkingPage,
                                Function<BorderPane, Node> ticketPage, Consumer<String> toast) {
        this.persistence = Objects.requireNonNull(persistence); this.garage = Objects.requireNonNull(garage);
        this.customer = Objects.requireNonNull(customer); this.navigate = Objects.requireNonNull(navigate);
        this.parkingPage = Objects.requireNonNull(parkingPage); this.ticketPage = Objects.requireNonNull(ticketPage);
        this.toast = Objects.requireNonNull(toast);
    }

    public Node build(BorderPane shell) {
        VBox content = new VBox(18); content.setPadding(new Insets(26, 30, 32, 30));
        HBox heading = new HBox(14, new VBox(3, DesignTokens.text("My Vehicles", 25, TEXT, true),
                DesignTokens.text("Manage the vehicles available for your parking sessions.", 13, MUTED, false)), UiNodes.spacer());
        Button add = new Button("Add vehicle", IconView.of(IconView.Name.PARKING, 15, BG)); add.getStyleClass().add("primary-button");
        add.setOnAction(e -> navigate.accept(buildForm(shell))); heading.getChildren().add(add); heading.setAlignment(Pos.CENTER_LEFT);
        VBox table = new VBox(0); table.getStyleClass().add("card"); table.setPadding(new Insets(6, 18, 12, 18));
        table.getChildren().add(row("VEHICLE", "TYPE", "PLATE", "STATUS", DesignTokens.text("ACTION", 10, MUTED, true), true));
        var ids = customer.getVehicleIds() == null ? Collections.<String>emptyList() : customer.getVehicleIds();
        if (ids.isEmpty()) table.getChildren().add(new VBox(8, DesignTokens.text("No vehicles added yet", 16, TEXT, true), DesignTokens.text("Add a vehicle to make parking faster.", 12, MUTED, false)) {{ setAlignment(Pos.CENTER); setPadding(new Insets(40)); }});
        for (String id : ids) {
            Vehicle vehicle = garage.getRegisteredVehicle(id); if (vehicle == null) vehicle = garage.getVehicle(id); if (vehicle == null) continue;
            Vehicle selected = vehicle; boolean parked = vehicle.isParked(); Button action = new Button(parked ? "View" : "Park"); action.getStyleClass().add("table-action");
            action.setOnAction(e -> navigate.accept((parked ? ticketPage : parkingPage).apply(shell)));
            table.getChildren().add(row(safe(selected.getVehicleId(), "—"), vehicleTypeLabel(selected.getVehicleType()), safe(selected.getLicensePlate(), "—"), parked ? "Parked" : "Available", action, false));
        }
        content.getChildren().addAll(heading, table); ScrollPane scroll = new ScrollPane(content); scroll.setFitToWidth(true); scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); scroll.getStyleClass().add("dark-scroll"); return scroll;
    }

    private Node buildForm(BorderPane shell) {
        VBox content = new VBox(18); content.setPadding(new Insets(26, 30, 32, 30)); content.setMaxWidth(650);
        VBox heading = new VBox(3, DesignTokens.text("Add vehicle", 25, TEXT, true), DesignTokens.text("Add the vehicle details you use for parking.", 13, MUTED, false));
        TextField plate = field("ABC-123"); ComboBox<VehicleType> type = new ComboBox<>(); type.getItems().addAll(VehicleType.values()); type.setValue(VehicleType.CAR); type.setMaxWidth(Double.MAX_VALUE); type.getStyleClass().add("dark-combo");
        type.setButtonCell(cell()); type.setCellFactory(list -> cell()); TextField make = field("Make (optional)"), model = field("Model (optional)"), color = field("Color (optional)"), year = field("Year (optional)"); Label error = DesignTokens.text("", 11, RED, true); error.setVisible(false); error.setManaged(false);
        GridPane form = new GridPane(); form.setHgap(18); form.setVgap(10); form.getColumnConstraints().addAll(new ColumnConstraints(140), new ColumnConstraints(340));
        add(form, 0, "License plate", plate); add(form, 1, "Vehicle type", type); add(form, 2, "Make", make); add(form, 3, "Model", model); add(form, 4, "Color", color); add(form, 5, "Year", year); form.add(error, 1, 6);
        Button cancel = new Button("Cancel"); cancel.getStyleClass().add("ghost-button"); cancel.setOnAction(e -> navigate.accept(build(shell)));
        Button save = new Button("Save vehicle"); save.getStyleClass().add("primary-button"); save.setOnAction(e -> {
            error.setText(""); error.setVisible(false); error.setManaged(false); if (plate.getText().trim().isEmpty()) { show(error, "License plate is required."); return; }
            try { Vehicle vehicle = new Vehicle("VEH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT), plate.getText().trim(), type.getValue(), make.getText().trim(), model.getText().trim(), color.getText().trim(), parseYear(year.getText()), customer.getUserId()); garage.registerVehicleDetails(vehicle); persistence.saveVehicle(vehicle); customer.addVehicle(vehicle); navigate.accept(build(shell)); toast.accept("Vehicle added successfully."); }
            catch (RuntimeException ex) { show(error, ex.getMessage()); }
        });
        HBox actions = new HBox(12, cancel, save); actions.setAlignment(Pos.CENTER_RIGHT); content.getChildren().addAll(heading, form, actions); return content;
    }
    private void add(GridPane form, int row, String title, Node field) { form.add(DesignTokens.text(title, 11, MUTED, true), 0, row); form.add(field, 1, row); }
    private TextField field(String prompt) { TextField field = new TextField(); field.setPromptText(prompt); field.getStyleClass().add("dark-input"); return field; }
    private ListCell<VehicleType> cell() { return new ListCell<>() { @Override protected void updateItem(VehicleType item, boolean empty) { super.updateItem(item, empty); setText(empty || item == null ? null : vehicleTypeLabel(item)); } }; }
    private int parseYear(String text) { if (text == null || text.isBlank()) return 0; try { return Integer.parseInt(text.trim()); } catch (NumberFormatException ex) { throw new IllegalArgumentException("Year must be a number."); } }
    private void show(Label error, String message) { error.setText(message == null ? "Unable to save vehicle." : message); error.setVisible(true); error.setManaged(true); }
    private GridPane row(String vehicle, String type, String plate, String status, Node action, boolean header) { GridPane row = new GridPane(); row.setHgap(12); row.setVgap(4); row.setPadding(new Insets(12, 0, 12, 0)); double[] widths = {25, 18, 20, 19, 18}; String[] values = {vehicle, type, plate, status}; for (int i = 0; i < widths.length; i++) { ColumnConstraints c = new ColumnConstraints(); c.setPercentWidth(widths[i]); row.getColumnConstraints().add(c); if (i < 4) row.add(i == 3 && !header ? statusBadge(status) : DesignTokens.text(values[i], header ? 10 : 12, header ? MUTED : TEXT, header), i, 0); else row.add(action, i, 0); } if (!header) row.getStyleClass().add("activity-row"); return row; }
    private Label statusBadge(String status) { boolean parked = "Parked".equalsIgnoreCase(status); Label label = DesignTokens.text(parked ? "Parked" : "Available", 11, parked ? GREEN : TEAL, true); label.getStyleClass().addAll("status-badge", parked ? "status-badge-parked" : "status-badge-available"); return label; }
    private String vehicleTypeLabel(VehicleType type) { if (type == null) return "—"; return switch (type) { case COMPACT_CAR -> "Compact car"; case ELECTRIC_VEHICLE -> "Electric vehicle"; case HANDICAPPED -> "Accessible"; default -> { String value = type.name().toLowerCase(Locale.ROOT).replace('_', ' '); yield Character.toUpperCase(value.charAt(0)) + value.substring(1); } }; }
    private String safe(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
}
