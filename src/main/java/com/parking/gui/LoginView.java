package com.parking.gui;

import com.parking.model.User;
import com.parking.services.UserService;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.control.Separator;

import java.util.Objects;
import java.util.function.Consumer;

import static com.parking.gui.DesignTokens.*;

/** Login screen seam; authentication remains owned by UserService. */
public final class LoginView {
    private final UserService users;
    private final String notice;
    private final Consumer<User> authenticated;
    private final Runnable register;

    public LoginView(UserService users, String notice, Consumer<User> authenticated, Runnable register) {
        this.users = Objects.requireNonNull(users);
        this.notice = notice;
        this.authenticated = Objects.requireNonNull(authenticated);
        this.register = Objects.requireNonNull(register);
    }

    public Parent build() {
        StackPane root = new StackPane();
        root.getStyleClass().add("auth-root");
        VBox card = new VBox(16);
        card.getStyleClass().addAll("login-card", "auth-card");
        card.setMaxWidth(440);
        card.setMaxHeight(620);
        card.setAlignment(javafx.geometry.Pos.TOP_CENTER);

        VBox brand = new VBox(2,
                new javafx.scene.layout.HBox(8, IconView.of(IconView.Name.PARKING, 24, TEAL), label("PARKINGOS", 28, TEXT, true)),
                label("Enterprise Platform", 14, MUTED, true));
        brand.setAlignment(javafx.geometry.Pos.CENTER);
        VBox welcome = new VBox(3, label("Welcome back", 20, TEXT, true),
                label("Sign in to manage your parking operation", 13, MUTED, false));
        welcome.setAlignment(javafx.geometry.Pos.CENTER);
        if (notice != null) welcome.getChildren().add(label(notice, 12, GREEN, true));

        TextField username = new TextField();
        username.setPromptText("Username"); username.getStyleClass().add("dark-input"); username.setPrefHeight(48);
        PasswordField password = new PasswordField();
        password.setPromptText("Password"); password.getStyleClass().add("dark-input"); password.setPrefHeight(48);
        Label error = label("", 11, RED, true); error.setVisible(false); error.setManaged(false);
        ProgressIndicator spinner = new ProgressIndicator(); spinner.setPrefSize(22, 22); spinner.setVisible(false);
        Button signIn = new Button(); signIn.getStyleClass().add("primary-button");
        signIn.setMaxWidth(Double.MAX_VALUE); signIn.setPrefHeight(50);
        signIn.setGraphic(new StackPane(label("Sign In", 15, WHITE, true), spinner));
        Label create = label("Create customer account", 13, TEAL, true); create.getStyleClass().add("link-label");

        Runnable submit = () -> {
            String name = username.getText().trim();
            if (name.isEmpty() || password.getText().isEmpty()) {
                error.setText(name.isEmpty() ? "Username is required." : "Password is required.");
                error.setVisible(true); error.setManaged(true); return;
            }
            try {
                User user = users.authenticateUser(name, password.getText());
                signIn.setDisable(true); username.setDisable(true); password.setDisable(true);
                spinner.setVisible(true);
                javafx.animation.PauseTransition delay = new javafx.animation.PauseTransition(javafx.util.Duration.millis(800));
                delay.setOnFinished(event -> authenticated.accept(user)); delay.play();
            } catch (RuntimeException failure) {
                error.setText(failure.getMessage() == null ? "Invalid username or password." : failure.getMessage());
                error.setVisible(true); error.setManaged(true); password.requestFocus();
            }
        };
        signIn.setOnAction(event -> submit.run());
        username.setOnAction(event -> submit.run());
        password.setOnAction(event -> submit.run());
        create.setOnMouseClicked(event -> register.run());
        card.getChildren().addAll(brand, separator(), welcome, formLabel("USERNAME"), username,
                formLabel("PASSWORD"), passwordWrap(password), error, signIn, create);
        root.getChildren().add(card);
        return root;
    }

    private static Label label(String value, double size, String color, boolean bold) {
        return DesignTokens.text(value, size, color, bold);
    }

    private static Label formLabel(String value) {
        Label label = UiNodes.formLabel(value);
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    private static Separator separator() { return UiNodes.separator(); }

    private static javafx.scene.layout.HBox passwordWrap(PasswordField password) {
        TextField visible = new TextField();
        visible.setPromptText("Password");
        visible.getStyleClass().add("dark-input");
        visible.setVisible(false); visible.setManaged(false);
        visible.textProperty().bindBidirectional(password.textProperty());
        javafx.scene.control.Button eye = new javafx.scene.control.Button(null,
                IconView.of(IconView.Name.EYE, 16, MUTED));
        eye.getStyleClass().add("password-toggle");
        eye.setFocusTraversable(false);
        eye.setOnAction(event -> {
            boolean show = visible.isVisible();
            visible.setVisible(!show); visible.setManaged(!show);
            password.setVisible(show); password.setManaged(show);
            eye.setGraphic(IconView.of(show ? IconView.Name.EYE : IconView.Name.EYE_OFF, 16, MUTED));
        });
        javafx.scene.layout.StackPane fields = new javafx.scene.layout.StackPane(password, visible);
        javafx.scene.layout.HBox wrapper = new javafx.scene.layout.HBox(10,
                IconView.of(IconView.Name.SECURITY, 16, MUTED), fields, eye);
        wrapper.getStyleClass().add("input-wrap");
        wrapper.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        javafx.scene.layout.HBox.setHgrow(fields, javafx.scene.layout.Priority.ALWAYS);
        return wrapper;
    }
}
