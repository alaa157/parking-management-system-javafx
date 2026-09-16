package com.parking.gui;

import com.parking.enums.UserRole;
import com.parking.model.User;
import com.parking.services.UserService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.parking.gui.DesignTokens.*;

/** Customer registration page; authentication and persistence remain in UserService. */
public final class RegistrationView {
    private final UserService users;
    private final Runnable showLogin;
    private final Consumer<User> onAuthenticated;

    public RegistrationView(UserService users, Runnable showLogin, Consumer<User> onAuthenticated) {
        this.users = Objects.requireNonNull(users);
        this.showLogin = Objects.requireNonNull(showLogin);
        this.onAuthenticated = Objects.requireNonNull(onAuthenticated);
    }

    public Parent build() {
        StackPane root = new StackPane();
        root.getStyleClass().add("auth-root");
        VBox card = new VBox(16);
        card.getStyleClass().addAll("login-card", "auth-card");
        card.setMaxWidth(520);
        card.setMaxHeight(720);

        VBox brand = new VBox(2,
                new HBox(8, IconView.of(IconView.Name.PARKING, 22, TEAL), label("PARKINGOS", 24, TEXT, true)),
                label("Customer access", 12, MUTED, true));
        brand.setAlignment(Pos.CENTER);
        VBox heading = new VBox(3, label("Create customer account", 20, TEXT, true),
                label("Enter your details to get started.", 13, MUTED, false));
        heading.setAlignment(Pos.CENTER);

        TextField fullName = field("Full name");
        TextField username = field("Username");
        TextField email = field("name@example.com");
        TextField phone = field("Phone number");
        PasswordField password = password("Password");
        PasswordField confirm = password("Confirm password");
        fullName.setAccessibleText("Full name");
        username.setAccessibleText("Username");
        email.setAccessibleText("Email address");
        phone.setAccessibleText("Phone number");
        password.setAccessibleText("Password");
        confirm.setAccessibleText("Confirm password");

        Label fullNameError = error();
        Label usernameError = error();
        Label emailError = error();
        Label phoneError = error();
        Label passwordError = error();
        Label confirmError = error();
        bind(fullName, fullNameError, value -> value.trim().isEmpty() ? "Full name is required." : null);
        bind(username, usernameError, this::usernameError);
        bind(email, emailError, value -> value.trim().isEmpty() ? "Email is required."
                : users.validateEmail(value.trim()) ? null : "Enter a valid email address.");
        bind(phone, phoneError, value -> value.trim().isEmpty() ? "Phone is required."
                : value.trim().matches("\\+?[0-9][0-9 ()-]{6,19}") ? null : "Enter a valid phone number.");
        bind(password, passwordError, value -> value.isEmpty() ? "Password is required."
                : users.validatePasswordStrength(value) ? null
                : "Use 8+ characters with upper/lowercase, a number and a special character.");
        bind(confirm, confirmError, value -> value.isEmpty() ? "Confirm your password."
                : value.equals(password.getText()) ? null : "Passwords do not match.");
        password.textProperty().addListener((obs, oldValue, newValue) -> {
            if (confirm.isFocused() || !confirm.getText().isEmpty()) {
                validate(confirm, confirmError, value -> value.isEmpty() ? "Confirm your password."
                        : value.equals(password.getText()) ? null : "Passwords do not match.");
            }
        });

        GridPane form = new GridPane();
        form.setHgap(16);
        form.setVgap(7);
        form.getColumnConstraints().addAll(new ColumnConstraints(132), new ColumnConstraints(280));
        row(form, 0, "Full name", fullName, fullNameError);
        row(form, 1, "Username", username, usernameError);
        row(form, 2, "Email", email, emailError);
        row(form, 3, "Phone", phone, phoneError);
        row(form, 4, "Password", passwordWrap(password), passwordError);
        row(form, 5, "Confirm password", passwordWrap(confirm), confirmError);

        Button create = new Button("Create account");
        create.getStyleClass().add("primary-button");
        create.setMaxWidth(Double.MAX_VALUE);
        create.setPrefHeight(50);
        Button cancel = new Button("Cancel");
        cancel.getStyleClass().add("ghost-button");
        cancel.setAccessibleText("Cancel registration");
        cancel.setOnAction(e -> showLogin.run());
        HBox actions = new HBox(12, cancel, create);
        actions.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(create, Priority.ALWAYS);
        Label signIn = label("Already have an account? Sign in", 13, TEAL, true);
        signIn.getStyleClass().add("link-label");
        signIn.setOnMouseClicked(e -> showLogin.run());
        card.getChildren().addAll(brand, UiNodes.separator(), heading, form, actions, signIn);
        root.getChildren().add(card);

        Runnable submit = () -> register(fullName, username, email, phone, password, confirm,
                fullNameError, usernameError, emailError, phoneError, passwordError, confirmError, create);
        create.setOnAction(e -> submit.run());
        root.sceneProperty().addListener((obs, oldScene, scene) -> {
            if (scene != null) {
                scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
                    if (e.getCode() == javafx.scene.input.KeyCode.ENTER && !create.isDisabled()) {
                        submit.run();
                        e.consume();
                    }
                });
            }
        });
        Platform.runLater(fullName::requestFocus);
        return root;
    }

    private void register(TextField fullName, TextField username, TextField email, TextField phone,
                          PasswordField password, PasswordField confirm, Label fullNameError,
                          Label usernameError, Label emailError, Label phoneError, Label passwordError,
                          Label confirmError, Button create) {
        boolean valid = validate(fullName, fullNameError, value -> value.trim().isEmpty() ? "Full name is required." : null);
        valid &= validate(username, usernameError, this::usernameError);
        valid &= validate(email, emailError, value -> value.trim().isEmpty() ? "Email is required."
                : users.validateEmail(value.trim()) ? null : "Enter a valid email address.");
        valid &= validate(phone, phoneError, value -> value.trim().isEmpty() ? "Phone is required."
                : value.trim().matches("\\+?[0-9][0-9 ()-]{6,19}") ? null : "Enter a valid phone number.");
        valid &= validate(password, passwordError, value -> value.isEmpty() ? "Password is required."
                : users.validatePasswordStrength(value) ? null : "Use 8+ characters with upper/lowercase, a number and a special character.");
        valid &= validate(confirm, confirmError, value -> value.isEmpty() ? "Confirm your password."
                : value.equals(password.getText()) ? null : "Passwords do not match.");
        if (!valid) return;
        create.setDisable(true);
        try {
            User user = users.registerUser(username.getText().trim(), password.getText(), email.getText().trim(),
                    UserRole.CUSTOMER, fullName.getText().trim(), phone.getText().trim());
            onAuthenticated.accept(user);
        } catch (RuntimeException ex) {
            create.setDisable(false);
            Label target = ex.getMessage() != null && ex.getMessage().toLowerCase(Locale.ROOT).contains("email")
                    ? emailError : usernameError;
            showError(target, ex.getMessage());
        }
    }

    private TextField field(String prompt) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.getStyleClass().add("dark-input");
        field.setPrefHeight(40);
        return field;
    }

    private PasswordField password(String prompt) {
        PasswordField field = new PasswordField();
        field.setPromptText(prompt);
        field.getStyleClass().add("dark-input");
        field.setPrefHeight(40);
        return field;
    }

    private void row(GridPane form, int row, String name, Node field, Label error) {
        Label label = UiNodes.formLabel(name.toUpperCase(Locale.ROOT));
        VBox input = new VBox(3, field, error);
        form.add(label, 0, row);
        form.add(input, 1, row);
        GridPane.setValignment(label, javafx.geometry.VPos.TOP);
        GridPane.setMargin(label, new Insets(12, 0, 0, 0));
        GridPane.setHgrow(input, Priority.ALWAYS);
    }

    private Label error() {
        Label error = label("", 11, RED, true);
        error.setWrapText(true);
        error.setMinHeight(16);
        error.setPrefHeight(16);
        error.getStyleClass().add("validation-error");
        error.setVisible(false);
        return error;
    }

    private void bind(TextInputControl field, Label error, Function<String, String> rule) {
        field.focusedProperty().addListener((obs, oldValue, focused) -> {
            if (!focused) validate(field, error, rule);
        });
    }

    private boolean validate(TextInputControl field, Label error, Function<String, String> rule) {
        String message = rule.apply(field.getText());
        if (message == null) {
            error.setText("");
            error.setVisible(false);
            field.getStyleClass().remove("field-invalid");
            return true;
        }
        showError(error, message);
        field.getStyleClass().add("field-invalid");
        return false;
    }

    private void showError(Label error, String message) {
        error.setText(message == null || message.isBlank() ? "Could not create account." : message);
        error.setVisible(true);
    }

    private String usernameError(String value) {
        if (value == null || value.trim().isEmpty()) return "Username is required.";
        if (!value.equals(value.trim())) return "Username cannot start or end with spaces.";
        if (value.length() < 3 || value.length() > 32) return "Username must be 3–32 characters.";
        return null;
    }

    private HBox passwordWrap(PasswordField password) {
        TextField visible = new TextField();
        visible.setPromptText(password.getPromptText());
        visible.getStyleClass().add("dark-input");
        visible.setVisible(false);
        visible.setManaged(false);
        visible.textProperty().bindBidirectional(password.textProperty());
        Button eye = new Button(null, IconView.of(IconView.Name.EYE, 16, MUTED));
        eye.getStyleClass().add("password-toggle");
        eye.setOnAction(e -> {
            boolean show = visible.isVisible();
            visible.setVisible(!show);
            visible.setManaged(!show);
            password.setVisible(show);
            password.setManaged(show);
            eye.setGraphic(IconView.of(show ? IconView.Name.EYE : IconView.Name.EYE_OFF, 16, MUTED));
        });
        StackPane fields = new StackPane(password, visible);
        HBox wrapper = new HBox(10, IconView.of(IconView.Name.SECURITY, 16, MUTED), fields, eye);
        wrapper.getStyleClass().add("input-wrap");
        wrapper.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(fields, Priority.ALWAYS);
        return wrapper;
    }

    private static Label label(String value, double size, String color, boolean bold) {
        return DesignTokens.text(value, size, color, bold);
    }
}
