package com.parking.gui;

import com.parking.enums.SpotStatus;
import com.parking.model.ParkingGarage;
import com.parking.model.ParkingSpot;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.beans.property.SimpleStringProperty;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import static com.parking.gui.DesignTokens.*;

/** Maintenance queue page for administrators. */
public final class MaintenanceView {
    private final ParkingGarage garage;
    private final Consumer<String> toast;

    public MaintenanceView(ParkingGarage garage, Consumer<String> toast) { this.garage = Objects.requireNonNull(garage); this.toast = Objects.requireNonNull(toast); }

    public Node build() {
        VBox content = new VBox(18); content.setPadding(new Insets(24, 28, 30, 28)); HBox heading = new HBox(12); heading.setAlignment(Pos.CENTER_LEFT);
        VBox title = new VBox(3, DesignTokens.text("Maintenance", 24, TEXT, true), DesignTokens.text("Track spots requiring service and resolve completed work", 14, MUTED, false)); Region push = new Region(); HBox.setHgrow(push, Priority.ALWAYS); Button add = new Button("Put under maintenance"); add.getStyleClass().add("primary-button"); heading.getChildren().addAll(title, push, add);
        TableView<ParkingSpot> table = new TableView<>(); table.getStyleClass().add("data-table"); table.setPlaceholder(DesignTokens.text("No spots are currently under maintenance.", 13, MUTED, false)); table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        TableColumn<ParkingSpot, String> spot = column("Spot", value -> value.getSpotId()), level = column("Level", this::levelFor), status = column("Status", value -> UiFormat.statusText(value.getStatus())), reason = column("Reason", value -> value.getMaintenanceReason() == null || value.getMaintenanceReason().isBlank() ? "—" : value.getMaintenanceReason());
        TableColumn<ParkingSpot, String> action = new TableColumn<>("Action"); action.setCellFactory(column -> new TableCell<>() { private final Button resolve = new Button("Resolve"); { resolve.getStyleClass().add("outline-button"); resolve.setOnAction(e -> { ParkingSpot selected = getTableView().getItems().get(getIndex()); selected.removeFromMaintenance(); garage.updateAvailability(); refresh(table); toast.accept(selected.getSpotId() + " released from maintenance."); }); } @Override protected void updateItem(String item, boolean empty) { super.updateItem(item, empty); setGraphic(empty ? null : resolve); } });
        table.getColumns().addAll(spot, level, status, reason, action); refresh(table); VBox.setVgrow(table, Priority.ALWAYS); add.setOnAction(e -> showDialog(table)); content.getChildren().addAll(heading, table); return content;
    }
    private TableColumn<ParkingSpot, String> column(String title, java.util.function.Function<ParkingSpot, String> value) { TableColumn<ParkingSpot, String> column = new TableColumn<>(title); column.setCellValueFactory(data -> new SimpleStringProperty(value.apply(data.getValue()))); return column; }
    private String levelFor(ParkingSpot spot) { for (Map.Entry<Integer, List<ParkingSpot>> entry : garage.getLevels().entrySet()) if (entry.getValue().contains(spot)) return String.valueOf(entry.getKey() + 1); return "—"; }
    private void refresh(TableView<ParkingSpot> table) { table.setItems(FXCollections.observableArrayList(garage.getAllSpots().stream().filter(s -> s.getStatus() == SpotStatus.UNDER_MAINTENANCE).toList())); }
    private void showDialog(TableView<ParkingSpot> table) { List<ParkingSpot> candidates = garage.getAllSpots().stream().filter(s -> s.getStatus() == SpotStatus.AVAILABLE).toList(); if (candidates.isEmpty()) { toast.accept("No available spots can be placed under maintenance."); return; }
        ComboBox<ParkingSpot> spot = new ComboBox<>(FXCollections.observableArrayList(candidates)); spot.setConverter(new javafx.util.StringConverter<>() { @Override public String toString(ParkingSpot value) { return value == null ? "" : value.getSpotId(); } @Override public ParkingSpot fromString(String value) { return null; } }); spot.getSelectionModel().selectFirst(); TextField reason = new TextField(); reason.setPromptText("Reason"); reason.getStyleClass().add("dark-input"); Dialog<ButtonType> dialog = new Dialog<>(); dialog.setTitle("Place spot under maintenance"); dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK); dialog.getDialogPane().setContent(new VBox(12, DesignTokens.text("Place spot under maintenance", 18, TEXT, true), DesignTokens.text("Spot", 11, MUTED, true), spot, DesignTokens.text("Reason", 11, MUTED, true), reason)); Optional<ButtonType> result = dialog.showAndWait(); if (result.isEmpty() || result.get() != ButtonType.OK) return; if (reason.getText().trim().isEmpty()) { toast.accept("Enter a maintenance reason."); return; } ParkingSpot selected = spot.getValue(); selected.setUnderMaintenance(reason.getText().trim()); garage.updateAvailability(); refresh(table); toast.accept(selected.getSpotId() + " placed under maintenance."); }
}
