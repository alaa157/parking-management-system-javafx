package com.parking.gui;

import com.parking.enums.UserRole;
import com.parking.model.User;
import com.parking.services.UserService;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.Objects;
import java.util.function.Consumer;

import static com.parking.gui.DesignTokens.*;

/** First-run administrator setup page for a new local database. */
public final class FirstRunSetupView {
    private final UserService users;
    private final Runnable showLogin;
    private final Consumer<User> onAuthenticated;

    public FirstRunSetupView(UserService users, Runnable showLogin, Consumer<User> onAuthenticated) {
        this.users = Objects.requireNonNull(users);
        this.showLogin = Objects.requireNonNull(showLogin);
        this.onAuthenticated = Objects.requireNonNull(onAuthenticated);
    }

    public Parent build() {
        StackPane root = new StackPane();
        root.getStyleClass().add("app-root");
        VBox card = new VBox(14);
        card.getStyleClass().add("login-card");
        card.setAlignment(Pos.TOP_CENTER);
        card.setMaxWidth(480);

        VBox heading = new VBox(3,
                new HBox(8, IconView.of(IconView.Name.PARKING, 24, TEAL), label("PARKINGOS", 28, TEXT, true)),
                label("Create the first administrator account", 14, MUTED, true));
        heading.setAlignment(Pos.CENTER);
        TextField fullName = field("Full name");
        TextField username = field("Username");
        TextField email = field("Email");
        PasswordField password = password("Password");
        PasswordField confirmation = password("Confirm password");
        Label error = error();
        Button create = new Button("Create administrator");
        create.getStyleClass().add("primary-button");
        create.setMaxWidth(Double.MAX_VALUE);
        create.setPrefHeight(50);
        create.setOnAction(event -> {
            String name = fullName.getText().trim();
            String user = username.getText().trim();
            String mail = email.getText().trim();
            if (name.isEmpty() || user.isEmpty() || mail.isEmpty()
                    || password.getText().isEmpty() || confirmation.getText().isEmpty()) {
                showError(error, "Complete every field to continue.");
                return;
            }
            if (!password.getText().equals(confirmation.getText())) {
                showError(error, "Passwords do not match.");
                return;
            }
            try {
                User created = users.registerUser(user, password.getText(), mail, UserRole.ADMIN, name, null);
                onAuthenticated.accept(created);
            } catch (RuntimeException failure) {
                showError(error, failure.getMessage() == null
                        ? "Could not create administrator account." : failure.getMessage());
            }
        });
        card.getChildren().addAll(heading, UiNodes.separator(),
                label("This is required only once for a new database.", 13, MUTED, false),
                UiNodes.formLabel("FULL NAME"), fullName,
                UiNodes.formLabel("USERNAME"), username,
                UiNodes.formLabel("EMAIL"), email,
                UiNodes.formLabel("PASSWORD"), password,
                UiNodes.formLabel("CONFIRM PASSWORD"), confirmation,
                error, create);
        root.getChildren().add(card);
        Platform.runLater(fullName::requestFocus);
        return root;
    }

    private TextField field(String prompt) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.getStyleClass().add("dark-input");
        field.setPrefHeight(46);
        return field;
    }

    private PasswordField password(String prompt) {
        PasswordField field = new PasswordField();
        field.setPromptText(prompt);
        field.getStyleClass().add("dark-input");
        field.setPrefHeight(46);
        return field;
    }

    private Label error() {
        Label error = label("", 11, RED, true);
        error.setVisible(false);
        error.setManaged(false);
        return error;
    }

    private void showError(Label error, String message) {
        error.setText(message);
        error.setVisible(true);
        error.setManaged(true);
    }

    private static Label label(String value, double size, String color, boolean bold) {
        return DesignTokens.text(value, size, color, bold);
    }
}
