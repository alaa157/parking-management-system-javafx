package com.parking.gui;

import com.parking.model.Garage;
import com.parking.model.User;
import com.parking.services.GarageContext;
import com.parking.services.GarageService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.Objects;

import static com.parking.gui.DesignTokens.*;

/** Admin screen for garage lifecycle and configuration. */
public final class GarageManagementView {
    private final GarageService garages;
    private final GarageContext context;
    private final User actor;
    private final Runnable openAccess;
    private final Runnable back;
    private final TableView<Garage> table = new TableView<>();

    public GarageManagementView(GarageService garages, GarageContext context, User actor,
                                Runnable openAccess, Runnable back) {
        this.garages = Objects.requireNonNull(garages);
        this.context = Objects.requireNonNull(context);
        this.actor = Objects.requireNonNull(actor);
        this.openAccess = openAccess == null ? () -> {} : openAccess;
        this.back = back == null ? () -> {} : back;
    }

    public Node build() {
        if (actor.getRole() != com.parking.enums.UserRole.ADMIN) {
            return new VBox(10, label("Permissions denied", TYPE_H1, TEXT, true),
                    label("Garage management is available to administrators only.", TYPE_BODY, MUTED, false));
        }
        configureTable();
        refresh();

        Button dashboard = button("Dashboard", "ghost-button");
        dashboard.setOnAction(e -> back.run());
        Button create = button("+ Add garage", "primary-button");
        create.setOnAction(e -> showGarageDialog(null));
        Button edit = button("Edit", "outline-button");
        edit.setOnAction(e -> showGarageDialog(table.getSelectionModel().getSelectedItem()));
        Button lifecycle = button("Archive / reopen", "outline-button");
        lifecycle.setOnAction(e -> toggleLifecycle(table.getSelectionModel().getSelectedItem()));
        Button access = button("Manage access", "outline-button");
        access.setOnAction(e -> openAccess.run());

        HBox actions = new HBox(8, dashboard, new Region(), edit, lifecycle, access, create);
        HBox.setHgrow(actions.getChildren().get(1), Priority.ALWAYS);
        actions.setAlignment(Pos.CENTER_LEFT);
        VBox content = new VBox(18,
                new VBox(3, label("Garages", TYPE_H1, TEXT, true),
                        label("Create, configure, and control operational garage access.", TYPE_BODY, MUTED, false)),
                actions, table);
        content.setPadding(new Insets(24));
        VBox.setVgrow(table, Priority.ALWAYS);
        content.getStyleClass().add("page-root");
        return content;
    }

    public static String validateGarageInput(String id, String name, String address, String levels,
                                             String rate, String currency, String freeMinutes,
                                             String holdMinutes, String maxHours) {
        if (blank(id)) return "Garage ID is required.";
        if (blank(name)) return "Garage name is required.";
        if (blank(address)) return "Garage address is required.";
        if (blank(currency)) return "Currency is required.";
        if (positiveInt(levels) == null) return "Total levels must be a positive whole number.";
        if (nonNegativeDouble(rate) == null) return "Hourly rate must be a non-negative number.";
        if (nonNegativeInt(freeMinutes) == null) return "Free parking minutes must be a whole number.";
        if (positiveInt(holdMinutes) == null) return "Reservation hold minutes must be positive.";
        if (positiveInt(maxHours) == null) return "Maximum parking hours must be positive.";
        return null;
    }

    private void configureTable() {
        table.setPlaceholder(label("No garages configured yet.", TYPE_BODY, MUTED, false));
        table.getColumns().setAll(
                column("ID", g -> g.getGarageId()),
                column("Name", Garage::getName),
                column("Address", Garage::getAddress),
                column("Levels", g -> String.valueOf(g.getTotalLevels())),
                column("Rate", g -> String.format("%.2f %s", g.getBaseHourlyRate(), g.getCurrency())),
                column("Status", g -> g.isArchived() ? "Archived" : g.isOpen() ? "Open" : "Closed"));
    }

    private TableColumn<Garage, String> column(String title, java.util.function.Function<Garage, String> value) {
        TableColumn<Garage, String> column = new TableColumn<>(title);
        column.setCellValueFactory(data -> new SimpleStringProperty(value.apply(data.getValue())));
        column.setPrefWidth(150);
        return column;
    }

    private void refresh() { table.setItems(FXCollections.observableArrayList(garages.listGarages())); }

    private void toggleLifecycle(Garage garage) {
        if (garage == null) return;
        if (garage.isArchived()) garages.reopenGarage(garage.getGarageId());
        else garages.archiveGarage(garage.getGarageId());
        context.refresh();
        refresh();
    }

    private void showGarageDialog(Garage existing) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Add garage" : "Edit garage");
        ButtonType save = new ButtonType("Save changes", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        TextField id = field(existing == null ? "G-NEW" : existing.getGarageId());
        TextField name = field(existing == null ? "" : existing.getName());
        TextField address = field(existing == null ? "" : existing.getAddress());
        TextField levels = field(existing == null ? "1" : String.valueOf(existing.getTotalLevels()));
        TextField rate = field(existing == null ? "0" : String.valueOf(existing.getBaseHourlyRate()));
        TextField currency = field(existing == null ? "USD" : existing.getCurrency());
        TextField free = field(existing == null ? "0" : String.valueOf(existing.getFreeParkingMinutes()));
        TextField hold = field(existing == null ? "5" : String.valueOf(existing.getReservationHoldMinutes()));
        TextField max = field(existing == null ? "48" : String.valueOf(existing.getMaxParkHours()));
        GridPane form = new GridPane();
        form.setHgap(12); form.setVgap(10); form.setPadding(new Insets(16));
        String[] labels = {"Garage ID", "Name", "Address", "Levels", "Hourly rate", "Currency", "Free minutes", "Hold minutes", "Max hours"};
        TextField[] fields = {id, name, address, levels, rate, currency, free, hold, max};
        for (int i = 0; i < fields.length; i++) { form.add(label(labels[i], TYPE_BODY, TEXT, true), 0, i); form.add(fields[i], 1, i); }
        dialog.getDialogPane().setContent(form);
        Node saveButton = dialog.getDialogPane().lookupButton(save);
        saveButton.disableProperty().bind(id.textProperty().isEmpty().or(name.textProperty().isEmpty()).or(address.textProperty().isEmpty()));
        dialog.setResultConverter(button -> button == save ? save : null);
        dialog.showAndWait().ifPresent(result -> {
            String error = validateGarageInput(id.getText(), name.getText(), address.getText(), levels.getText(), rate.getText(), currency.getText(), free.getText(), hold.getText(), max.getText());
            if (error != null) return;
            Garage saved = existing == null
                    ? garages.createGarage(id.getText().trim(), name.getText().trim(), address.getText().trim(), Integer.parseInt(levels.getText()), Double.parseDouble(rate.getText()), currency.getText().trim(), Integer.parseInt(free.getText()), Integer.parseInt(hold.getText()), Integer.parseInt(max.getText()))
                    : existing;
            if (existing != null) {
                existing.setName(name.getText().trim()); existing.setAddress(address.getText().trim());
                existing.setTotalLevels(Integer.parseInt(levels.getText())); existing.setBaseHourlyRate(Double.parseDouble(rate.getText()));
                existing.setCurrency(currency.getText().trim()); existing.setFreeParkingMinutes(Integer.parseInt(free.getText()));
                existing.setReservationHoldMinutes(Integer.parseInt(hold.getText())); existing.setMaxParkHours(Integer.parseInt(max.getText()));
                garages.updateGarage(saved);
            }
            refresh();
        });
    }

    private static TextField field(String value) { TextField field = new TextField(value); field.setPrefWidth(260); return field; }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static Integer positiveInt(String value) { try { int n = Integer.parseInt(value); return n > 0 ? n : null; } catch (RuntimeException e) { return null; } }
    private static Integer nonNegativeInt(String value) { try { int n = Integer.parseInt(value); return n >= 0 ? n : null; } catch (RuntimeException e) { return null; } }
    private static Double nonNegativeDouble(String value) { try { double n = Double.parseDouble(value); return Double.isFinite(n) && n >= 0 ? n : null; } catch (RuntimeException e) { return null; } }
    private static Button button(String text, String style) { Button b = new Button(text); b.getStyleClass().add(style); return b; }
    private static Label label(String text, double size, String color, boolean bold) { Label l = new Label(text); l.setStyle("-fx-font-size: " + size + "px; -fx-text-fill: " + color + ";" + (bold ? " -fx-font-weight: 700;" : "")); return l; }
}
