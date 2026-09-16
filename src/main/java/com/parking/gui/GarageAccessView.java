package com.parking.gui;

import com.parking.enums.UserRole;
import com.parking.model.Garage;
import com.parking.model.User;
import com.parking.services.GarageContext;
import com.parking.services.GarageService;
import com.parking.services.UserService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.Objects;

import static com.parking.gui.DesignTokens.*;

/** Admin controls for assigning and revoking user access to garages. */
public final class GarageAccessView {
    private final GarageService garages;
    private final UserService users;
    private final User actor;
    private final GarageContext context;
    private final Runnable back;

    public GarageAccessView(GarageService garages, UserService users, User actor,
                            GarageContext context, Runnable back) {
        this.garages = Objects.requireNonNull(garages);
        this.users = Objects.requireNonNull(users);
        this.actor = Objects.requireNonNull(actor);
        this.context = Objects.requireNonNull(context);
        this.back = back == null ? () -> {} : back;
    }

    public Node build() {
        if (actor.getRole() != UserRole.ADMIN) {
            return new VBox(10, label("Permissions denied", 24, TEXT, true),
                    label("Only administrators can assign garage access.", 14, MUTED, false));
        }
        ComboBox<User> user = new ComboBox<>();
        user.getItems().setAll(users.getAllUsers());
        user.setConverter(new javafx.util.StringConverter<>() {
            public String toString(User value) { return value == null ? "" : value.getFullName() + "  ·  @" + value.getUsername(); }
            public User fromString(String value) { return null; }
        });
        ComboBox<Garage> garage = new ComboBox<>();
        garage.getItems().setAll(garages.listGarages());
        garage.setConverter(new javafx.util.StringConverter<>() {
            public String toString(Garage value) { return value == null ? "" : value.getName() + "  ·  " + value.getGarageId(); }
            public Garage fromString(String value) { return null; }
        });
        ComboBox<UserRole> role = new ComboBox<>();
        role.getItems().setAll(UserRole.values());
        role.setValue(UserRole.ATTENDANT);
        Label feedback = label("Select a user and garage.", 13, MUTED, false);
        Button grant = button("Grant access", "primary-button");
        grant.setOnAction(e -> {
            if (user.getValue() == null || garage.getValue() == null) { feedback.setText("Select both a user and garage."); return; }
            try { garages.grantAccess(user.getValue().getUserId(), garage.getValue().getGarageId(), role.getValue()); feedback.setText("Access granted."); context.refresh(); }
            catch (RuntimeException ex) { feedback.setText(ex.getMessage()); }
        });
        Button revoke = button("Revoke access", "danger-button");
        revoke.setOnAction(e -> {
            if (user.getValue() == null || garage.getValue() == null) { feedback.setText("Select both a user and garage."); return; }
            try { garages.revokeAccess(user.getValue().getUserId(), garage.getValue().getGarageId()); feedback.setText("Access revoked."); context.refresh(); }
            catch (RuntimeException ex) { feedback.setText(ex.getMessage()); }
        });
        Button dashboard = button("Back", "ghost-button");
        dashboard.setOnAction(e -> back.run());

        VBox form = new VBox(14,
                new VBox(3, label("Garage access", 24, TEXT, true), label("Assign active operational scope to users.", 14, MUTED, false)),
                field("User", user), field("Garage", garage), field("Role", role),
                new HBox(8, grant, revoke), feedback, dashboard);
        form.setPadding(new Insets(28));
        form.setMaxWidth(620);
        form.setAlignment(Pos.TOP_LEFT);
        form.getStyleClass().add("page-root");
        return form;
    }

    private static VBox field(String title, Control control) {
        control.setMaxWidth(Double.MAX_VALUE);
        return new VBox(5, label(title, 12, MUTED, true), control);
    }

    private static Button button(String text, String style) { Button b = new Button(text); b.getStyleClass().add(style); return b; }
    private static Label label(String text, double size, String color, boolean bold) { Label l = new Label(text); l.setWrapText(true); l.setStyle("-fx-font-size: " + size + "px; -fx-text-fill: " + color + ";" + (bold ? " -fx-font-weight: 700;" : "")); return l; }
}
