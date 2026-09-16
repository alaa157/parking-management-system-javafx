package com.parking.gui;

import com.parking.enums.UserRole;
import com.parking.model.Admin;
import com.parking.model.Attendant;
import com.parking.model.Customer;
import com.parking.model.User;
import com.parking.services.PaymentService;
import com.parking.services.TicketService;
import com.parking.services.UserService;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.parking.gui.DesignTokens.*;
import com.parking.gui.components.DataTable;
import com.parking.gui.components.EmptyState;

/**
 * Phase 4 - Admin User Management.
 * Center content only: the application's existing shared sidebar remains in ParkingApplication.
 */
public final class UserManagementView {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy  •  HH:mm");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final UserService userService;
    private final TicketService ticketService;
    private final PaymentService paymentService;
    private final User currentUser;
    private final Runnable backToDashboard;
    private final Runnable openGarageAccess;

    private final ObservableList<User> allUsers = FXCollections.observableArrayList();
    private final ObservableList<User> visibleUsers = FXCollections.observableArrayList();

    private final StackPane host = new StackPane();
    private final DataTable<User> table = new DataTable<>();
    private final java.util.LinkedHashMap<String, TableColumn<User, ?>> columns = new java.util.LinkedHashMap<>();
    private final TextField searchField = new TextField();
    private final ComboBox<String> roleFilter = new ComboBox<>();
    private final ComboBox<String> statusFilter = new ComboBox<>();
    private DatePicker createdFrom;
    private DatePicker createdTo;
    private ComboBox<String> presetBox;
    private final Map<String, FilterPreset> filterPresets = new LinkedHashMap<>();
    private final Label totalUsersValue = statValue();
    private final Label customersValue = statValue();
    private final Label attendantsValue = statValue();
    private final Label adminsValue = statValue();

    private VBox detailPanel;
    private Node detailBackdrop;
    private User selectedUser;
    private PauseTransition searchDebounce;
    private long toastGeneration;

    public UserManagementView(
            UserService userService,
            TicketService ticketService,
            PaymentService paymentService,
            User currentUser,
            Runnable backToDashboard) {
        this(userService, ticketService, paymentService, currentUser, backToDashboard, () -> {});
    }

    public UserManagementView(
            UserService userService,
            TicketService ticketService,
            PaymentService paymentService,
            User currentUser,
            Runnable backToDashboard,
            Runnable openGarageAccess) {
        this.userService = userService;
        this.ticketService = ticketService;
        this.paymentService = paymentService;
        this.currentUser = currentUser;
        this.backToDashboard = backToDashboard;
        this.openGarageAccess = openGarageAccess == null ? () -> {} : openGarageAccess;
    }

    public Node build() {
        host.getStyleClass().add("user-management-host");

        if (currentUser == null || !userService.isAdmin(currentUser)) {
            host.getChildren().setAll(buildPermissionDenied());
            return host;
        }

        reloadUsers();

        BorderPane page = new BorderPane();
        page.getStyleClass().add("page-root");

        VBox content = new VBox(18);
        content.setPadding(new Insets(24, 24, 24, 24));

        setupTable();
        content.getChildren().addAll(buildHeader(), buildStats(), buildFilters(), table);
        VBox.setVgrow(table, Priority.ALWAYS);

        page.setCenter(content);
        host.getChildren().setAll(page);
        SkeletonView.show(host, SkeletonView.table(6), 420);

        installFiltering();
        refreshVisibleUsers();
        refreshStats();

        return host;
    }

    private Node buildHeader() {
        Label subtitle = label("Manage all system users and roles", 14, MUTED, false);
        subtitle.setWrapText(true);
        VBox titleBlock = new VBox(3,
                label("User Management", 24, TEXT, true),
                subtitle);
        titleBlock.setMinWidth(220);

        Button add = button("+ Add User", "primary-button");
        add.setPrefHeight(40);
        add.setOnAction(e -> openUserModal(null));

        Button access = button("Garage access", "outline-button");
        access.setOnAction(e -> openGarageAccess.run());

        Button dashboard = new Button("Dashboard", IconView.of(IconView.Name.BACK, 16, MUTED));
        dashboard.getStyleClass().add("ghost-button");
        dashboard.setPrefHeight(40);
        dashboard.setOnAction(e -> {
            closeDetailPanel();
            backToDashboard.run();
        });

        Button bulkDelete = button("Delete selected", "danger-button");
        bulkDelete.setDisable(true);
        bulkDelete.setOnAction(e -> deleteSelectedUsers());
        table.getSelectionModel().getSelectedItems().addListener((javafx.collections.ListChangeListener<User>) c ->
                bulkDelete.setDisable(table.getSelectionModel().getSelectedItems().isEmpty()));

        MenuButton export = new MenuButton("Export");
        export.getStyleClass().add("outline-button");
        MenuItem csv = new MenuItem("Export CSV");
        MenuItem pdf = new MenuItem("Export PDF");
        csv.setOnAction(e -> exportUsers("csv"));
        pdf.setOnAction(e -> exportUsers("pdf"));
        export.getItems().addAll(csv, pdf);

        Button activate = button("Activate", "outline-button");
        activate.setOnAction(e -> setSelectedStatus(true));
        Button deactivate = button("Deactivate", "outline-button");
        deactivate.setOnAction(e -> setSelectedStatus(false));
        FlowPane actions = new FlowPane(8, 8, dashboard, columnVisibilityMenu(), export,
                activate, deactivate, bulkDelete, access, add);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setPrefWrapLength(720);

        VBox header = new VBox(12, titleBlock, actions);
        header.setFillWidth(true);
        return header;
    }

    private Node buildStats() {
        HBox row = new HBox(20,
                statCard("TOTAL USERS", totalUsersValue, "All registered accounts"),
                statCard("ACTIVE CUSTOMERS", customersValue, "Customer accounts"),
                statCard("ACTIVE ATTENDANTS", attendantsValue, "Operations staff"),
                statCard("ADMINS", adminsValue, "Full-access accounts"));
        for (Node n : row.getChildren()) HBox.setHgrow(n, Priority.ALWAYS);
        return row;
    }

    private Node buildFilters() {
        searchField.setPromptText("Search name, username or email...");
        searchField.setPrefHeight(40);
        searchField.getStyleClass().add("dark-input");
        HBox.setHgrow(searchField, Priority.ALWAYS);

        roleFilter.setItems(FXCollections.observableArrayList("All Roles", "Customer", "Attendant", "Admin"));
        roleFilter.setValue("All Roles");
        roleFilter.setPrefHeight(40);
        roleFilter.getStyleClass().add("dark-combo");

        statusFilter.setItems(FXCollections.observableArrayList("All Status", "Active", "Inactive"));
        statusFilter.setValue("All Status");
        statusFilter.setPrefHeight(40);
        statusFilter.getStyleClass().add("dark-combo");

        FlowPane filters = new FlowPane(10, 8,
                searchField,
                labeledFilter("ROLE", roleFilter),
                labeledFilter("STATUS", statusFilter));
        filters.setAlignment(Pos.BOTTOM_LEFT);
        filters.setPrefWrapLength(900);
        ToggleButton advanced = new ToggleButton("Advanced filters");
        advanced.getStyleClass().add("outline-button");
        createdFrom = new DatePicker(); createdTo = new DatePicker();
        createdFrom.setPromptText("Created from"); createdTo.setPromptText("Created to");
        presetBox = new ComboBox<>(); presetBox.setPromptText("Saved preset");
        Button savePreset = button("Save preset", "outline-button");
        savePreset.setOnAction(e -> saveFilterPreset());
        HBox advancedPanel = new HBox(8, createdFrom, createdTo, presetBox, savePreset);
        advancedPanel.setVisible(false); advancedPanel.setManaged(false);
        advanced.selectedProperty().addListener((obs, old, open) -> { advancedPanel.setVisible(open); advancedPanel.setManaged(open); });
        createdFrom.valueProperty().addListener((obs, old, value) -> refreshVisibleUsers());
        createdTo.valueProperty().addListener((obs, old, value) -> refreshVisibleUsers());
        presetBox.setOnAction(e -> applyFilterPreset());
        return new VBox(8, filters, advanced, advancedPanel);
    }

    private VBox labeledFilter(String title, ComboBox<String> box) {
        VBox v = new VBox(4, formLabel(title), box);
        v.setPrefWidth(150);
        return v;
    }

    private void setupTable() {
        table.getStyleClass().add("user-table");
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new EmptyState(IconView.Name.USERS, "No users found",
                "Create a user to start managing roles and access.", "Add User", () -> openUserModal(null)));
        table.setRowFactory(tv -> {
            TableRow<User> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (!row.isEmpty() && e.getClickCount() == 1) {
                    showUserDetails(row.getItem());
                }
            });
            return row;
        });

        TableColumn<User, Boolean> selected = new TableColumn<>("SELECT");
        selected.setPrefWidth(70);
        selected.setCellValueFactory(c -> new javafx.beans.property.ReadOnlyBooleanWrapper(table.getSelectionModel().getSelectedItems().contains(c.getValue())));
        selected.setCellFactory(c -> new TableCell<>() {
            private final CheckBox check = new CheckBox();
            { check.setOnAction(e -> { User user = getTableView().getItems().get(getIndex()); if (check.isSelected()) table.getSelectionModel().select(user); else table.getSelectionModel().clearSelection(table.getItems().indexOf(user)); }); }
            @Override protected void updateItem(Boolean value, boolean empty) { super.updateItem(value, empty); if (empty) setGraphic(null); else { check.setSelected(value != null && value); setGraphic(check); } }
        });

        TableColumn<User, String> avatar = new TableColumn<>("AVATAR");
        avatar.setPrefWidth(76);
        avatar.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getFullName()));
        avatar.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) { setGraphic(null); return; }
                setGraphic(avatarGraphic(getIndex() >= 0 ? table.getItems().get(getIndex()) : null, 32));
                setAlignment(Pos.CENTER_LEFT);
            }
        });

        TableColumn<User, String> name = new TableColumn<>("NAME");
        name.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getFullName()));
        name.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) { setGraphic(null); return; }
                User u = getTableView().getItems().get(getIndex());
                VBox box = new VBox(2,
                        label(value, 13, TEXT, true),
                        label("@" + safe(u.getUsername()), 11, MUTED, false));
                setGraphic(box);
                setPadding(new Insets(6, 10, 6, 4));
            }
        });

        TableColumn<User, String> email = new TableColumn<>("EMAIL");
        email.setCellValueFactory(c -> new SimpleStringProperty(safe(c.getValue().getEmail())));
        email.setCellFactory(c -> textCell(MUTED));

        TableColumn<User, UserRole> role = new TableColumn<>("ROLE");
        role.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getRole()));
        role.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(UserRole value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) { setGraphic(null); return; }
                setGraphic(roleBadge(value));
                setAlignment(Pos.CENTER_LEFT);
            }
        });

        TableColumn<User, String> status = new TableColumn<>("STATUS");
        status.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().isActive() ? "Active" : "Inactive"));
        status.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) { setGraphic(null); return; }
                boolean active = "Active".equals(value);
                Circle dot = new Circle(4, DesignTokens.color(active ? GREEN : MUTED));
                setGraphic(new HBox(8, dot, label(value, 13, active ? GREEN : MUTED, true)));
            }
        });

        TableColumn<User, Integer> tickets = new TableColumn<>("TICKETS");
        tickets.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(ticketCount(c.getValue())));
        tickets.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(Integer value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : String.valueOf(value));
                setTextFill(DesignTokens.color(TEXT));
                getStyleClass().add("table-count-cell");
            }
        });

        TableColumn<User, String> actions = new TableColumn<>("ACTIONS");
        actions.setPrefWidth(110);
        actions.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); return; }
                User u = getTableView().getItems().get(getIndex());
                Button edit = iconButton(IconView.Name.EDIT);
                Button delete = iconButton(IconView.Name.DELETE);
                edit.setOnAction(e -> openUserModal(u));
                delete.setOnAction(e -> openDeleteConfirm(u));
                boolean self = currentUser.getUserId().equals(u.getUserId());
                boolean admin = u.getRole() == UserRole.ADMIN;
                delete.setDisable(self || admin);
                HBox box = new HBox(6, edit, delete);
                box.setAlignment(Pos.CENTER_LEFT);
                setGraphic(box);
            }
        });

        table.getColumns().setAll(selected, avatar, name, email, role, status, tickets, actions);
        columns.clear();
        columns.put("Select", selected);
        columns.put("Avatar", avatar);
        columns.put("Name", name);
        columns.put("Email", email);
        columns.put("Role", role);
        columns.put("Status", status);
        columns.put("Tickets", tickets);
        columns.put("Actions", actions);
        table.getSortOrder().clear();
        name.setSortable(true);
        email.setSortable(true);
        role.setSortable(true);
        status.setSortable(true);
        tickets.setSortable(true);
    }

    private MenuButton columnVisibilityMenu() {
        MenuButton menu = new MenuButton("Columns");
        menu.getStyleClass().add("outline-button");
        for (var entry : columns.entrySet()) {
            CheckMenuItem item = new CheckMenuItem(entry.getKey());
            item.setSelected(true);
            item.selectedProperty().addListener((obs, old, visible) -> entry.getValue().setVisible(visible));
            menu.getItems().add(item);
        }
        return menu;
    }

    private TableCell<User, String> textCell(String color) {
        return new TableCell<>() {
            @Override protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty ? null : value);
                setTextFill(empty ? null : DesignTokens.color(color));
                getStyleClass().add("table-text-cell");
            }
        };
    }

    private void installFiltering() {
        searchDebounce = new PauseTransition(Duration.millis(200));
        searchDebounce.setOnFinished(e -> refreshVisibleUsers());
        searchField.textProperty().addListener((obs, old, now) -> {
            searchDebounce.stop();
            searchDebounce.playFromStart();
        });
        roleFilter.valueProperty().addListener((obs, old, now) -> refreshVisibleUsers());
        statusFilter.valueProperty().addListener((obs, old, now) -> refreshVisibleUsers());
    }

    private void reloadUsers() {
        allUsers.setAll(userService.getAllUsers());
        allUsers.sort(Comparator.comparing(u -> safe(u.getFullName()).toLowerCase(Locale.ROOT)));
    }

    private void refreshVisibleUsers() {
        String query = safe(searchField.getText()).trim().toLowerCase(Locale.ROOT);
        String role = roleFilter.getValue();
        String status = statusFilter.getValue();
        LocalDate from = createdFrom == null ? null : createdFrom.getValue();
        LocalDate to = createdTo == null ? null : createdTo.getValue();

        visibleUsers.setAll(allUsers.stream()
                .filter(u -> query.isEmpty()
                        || safe(u.getFullName()).toLowerCase(Locale.ROOT).contains(query)
                        || safe(u.getUsername()).toLowerCase(Locale.ROOT).contains(query)
                        || safe(u.getEmail()).toLowerCase(Locale.ROOT).contains(query))
                .filter(u -> role == null || "All Roles".equals(role) || matchesRole(u, role))
                .filter(u -> status == null || "All Status".equals(status)
                        || ("Active".equals(status) && u.isActive())
                        || ("Inactive".equals(status) && !u.isActive()))
                .filter(u -> from == null || (u.getCreatedAt() != null && !u.getCreatedAt().toLocalDate().isBefore(from)))
                .filter(u -> to == null || (u.getCreatedAt() != null && !u.getCreatedAt().toLocalDate().isAfter(to)))
                .collect(Collectors.toList()));
        table.setItems(visibleUsers);
        refreshStats();
    }

    private void setSelectedStatus(boolean active) {
        int changed = 0;
        for (User user : new ArrayList<>(table.getSelectionModel().getSelectedItems())) {
            if (user == null || user.getRole() == UserRole.ADMIN || currentUser.getUserId().equals(user.getUserId())) continue;
            user.setActive(active); changed++;
        }
        refreshVisibleUsers();
        showToast(changed + " user(s) marked " + (active ? "active" : "inactive"), GREEN);
    }

    private void saveFilterPreset() {
        String name = "Preset " + (filterPresets.size() + 1);
        FilterPreset preset = new FilterPreset(searchField.getText(), roleFilter.getValue(), statusFilter.getValue(),
                createdFrom == null ? null : createdFrom.getValue(), createdTo == null ? null : createdTo.getValue());
        filterPresets.put(name, preset);
        presetBox.getItems().setAll(filterPresets.keySet());
        presetBox.setValue(name);
        showToast("Filter preset saved", GREEN);
    }

    private void applyFilterPreset() {
        FilterPreset preset = filterPresets.get(presetBox.getValue());
        if (preset == null) return;
        searchField.setText(preset.query()); roleFilter.setValue(preset.role()); statusFilter.setValue(preset.status());
        createdFrom.setValue(preset.from()); createdTo.setValue(preset.to());
    }

    private record FilterPreset(String query, String role, String status, LocalDate from, LocalDate to) { }

    private boolean matchesRole(User u, String value) {
        return ("Customer".equals(value) && u.getRole() == UserRole.CUSTOMER)
                || ("Attendant".equals(value) && u.getRole() == UserRole.ATTENDANT)
                || ("Admin".equals(value) && u.getRole() == UserRole.ADMIN);
    }

    private void refreshStats() {
        long customers = allUsers.stream().filter(u -> u.getRole() == UserRole.CUSTOMER && u.isActive()).count();
        long attendants = allUsers.stream().filter(u -> u.getRole() == UserRole.ATTENDANT && u.isActive()).count();
        long admins = allUsers.stream().filter(u -> u.getRole() == UserRole.ADMIN).count();
        totalUsersValue.setText(String.valueOf(allUsers.size()));
        customersValue.setText(String.valueOf(customers));
        attendantsValue.setText(String.valueOf(attendants));
        adminsValue.setText(String.valueOf(admins));
    }

    private void deleteSelectedUsers() {
        List<User> selected = new ArrayList<>(table.getSelectionModel().getSelectedItems());
        int deleted = 0;
        for (User user : selected) {
            if (user == null || currentUser.getUserId().equals(user.getUserId()) || user.getRole() == UserRole.ADMIN) continue;
            try {
                userService.deleteUser(currentUser, user.getUserId());
                deleted++;
            } catch (RuntimeException ignored) { }
        }
        table.getSelectionModel().clearSelection();
        reloadUsers();
        refreshVisibleUsers();
        showToast(deleted == 0 ? "No eligible users selected" : deleted + " user(s) deleted", deleted == 0 ? RED : GREEN);
    }

    private void exportUsers(String format) {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Export users as " + format.toUpperCase(Locale.ROOT));
        chooser.setInitialFileName("parkingos-users." + format);
        javafx.stage.FileChooser.ExtensionFilter filter = new javafx.stage.FileChooser.ExtensionFilter(
                format.equals("csv") ? "CSV files (*.csv)" : "PDF files (*.pdf)", "*." + format);
        chooser.getExtensionFilters().add(filter);
        java.io.File file = chooser.showSaveDialog(host.getScene() == null ? null : host.getScene().getWindow());
        if (file == null) return;
        try {
            if (format.equals("csv")) writeCsv(file.toPath());
            else writePdf(file.toPath());
            showToast("Users exported successfully", GREEN);
        } catch (IOException ex) {
            showToast("Export failed: " + ex.getMessage(), RED);
        }
    }

    private void writeCsv(Path path) throws IOException {
        try (BufferedWriter out = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            out.write("Name,Username,Email,Role,Status,Tickets\n");
            for (User user : visibleUsers) {
                out.write(String.join(",", csv(user.getFullName()), csv(user.getUsername()), csv(user.getEmail()),
                        csv(user.getRole() == null ? "" : user.getRole().name()), csv(user.isActive() ? "Active" : "Inactive"),
                        String.valueOf(ticketCount(user))));
                out.write('\n');
            }
        }
    }

    private void writePdf(Path path) throws IOException {
        StringBuilder body = new StringBuilder("BT /F1 10 Tf 40 760 Td (ParkingOS User Export) Tj 0 -18 Td ");
        for (User user : visibleUsers) {
            String line = safe(user.getFullName()) + " | " + safe(user.getUsername()) + " | " + safe(user.getEmail())
                    + " | " + (user.getRole() == null ? "" : user.getRole().name()) + " | " + (user.isActive() ? "Active" : "Inactive");
            body.append('(').append(pdfEscape(line)).append(") Tj 0 -14 Td ");
        }
        body.append("ET");
        String[] objects = {
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
                "<< /Length " + body.length() + " >>\nstream\n" + body + "\nendstream"
        };
        StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.length; i++) {
            offsets.add(pdf.length());
            pdf.append(i + 1).append(" 0 obj\n").append(objects[i]).append("\nendobj\n");
        }
        int xref = pdf.length();
        pdf.append("xref\n0 ").append(objects.length + 1).append("\n0000000000 65535 f \n");
        for (int offset : offsets) pdf.append(String.format(Locale.ROOT, "%010d 00000 n \n", offset));
        pdf.append("trailer\n<< /Size ").append(objects.length + 1).append(" /Root 1 0 R >>\nstartxref\n")
                .append(xref).append("\n%%EOF");
        Files.writeString(path, pdf.toString(), StandardCharsets.ISO_8859_1);
    }

    private String pdfEscape(String value) {
        return value.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)").replaceAll("[^\\x20-\\x7E]", "?");
    }

    private String csv(String value) {
        String safe = safe(value).replace("\"", "\"\"");
        return '"' + safe + '"';
    }

    private void openUserModal(User existing) {
        boolean edit = existing != null;

        TextField fullName = input("Full name");
        TextField username = input("Username");
        TextField email = input("Email");
        PasswordField password = new PasswordField();
        password.setPromptText(edit ? "Leave blank to keep current password" : "Password");
        password.getStyleClass().add("dark-input");
        password.setPrefHeight(42);

        Label fullNameError = errorLabel();
        Label usernameError = errorLabel();
        Label emailError = errorLabel();
        Label passwordError = errorLabel();

        SegmentedRole roles = new SegmentedRole();
        ToggleButton activeToggle = new ToggleButton();
        activeToggle.getStyleClass().add("toggle-switch");
        activeToggle.setSelected(existing == null || existing.isActive());
        activeToggle.setText(activeToggle.isSelected() ? "Active" : "Inactive");
        activeToggle.setOnAction(e -> activeToggle.setText(activeToggle.isSelected() ? "Active" : "Inactive"));

        StrengthIndicator strength = new StrengthIndicator();
        password.textProperty().addListener((obs, old, now) -> strength.update(now));

        bindValidation(fullName, fullNameError, value -> !value.trim().isEmpty(), "Full name is required.");
        bindValidation(username, usernameError, value -> {
            String candidate = value.trim();
            return !candidate.isEmpty() && (edit && candidate.equalsIgnoreCase(safe(existing.getUsername()).trim())
                    || !userService.isUsernameTaken(candidate));
        }, "Username is required or already taken.");
        bindValidation(email, emailError,
                value -> value.trim().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"),
                "Enter a valid email address.");
        password.textProperty().addListener((obs, old, now) -> {
            boolean optional = edit && now.isEmpty();
            boolean valid = optional || userService.validatePasswordStrength(now);
            password.getStyleClass().removeAll("field-invalid", "field-valid");
            if (!now.isEmpty()) password.getStyleClass().add(valid ? "field-valid" : "field-invalid");
            setValidationMessage(passwordError, valid, now.isEmpty() ? "" : "Use 8+ characters with upper/lowercase and a number.");
        });

        if (edit) {
            fullName.setText(safe(existing.getFullName()));
            username.setText(safe(existing.getUsername()));
            email.setText(safe(existing.getEmail()));
            roles.setRole(existing.getRole());
            strength.update("");
            if (currentUser.getUserId().equals(existing.getUserId())) {
                roles.root.setDisable(true);
                activeToggle.setDisable(true);
            }
        } else {
            roles.setRole(UserRole.CUSTOMER);
        }

        VBox form = new VBox(10,
                formLabel("FULL NAME *"), fullName, fullNameError,
                formLabel("USERNAME *"), username, usernameError,
                formLabel("EMAIL *"), email, emailError,
                formLabel("PASSWORD *"), password, strength.root, passwordError,
                formLabel("ROLE"), roles.root,
                formLabel("STATUS"), activeToggle);
        form.setMaxWidth(520);

        Button cancel = button("Cancel", "outline-button");
        Button save = button(edit ? "Save Changes" : "Create User", "primary-button");

        HBox footer = new HBox(10, spacer(), cancel, save);
        footer.setAlignment(Pos.CENTER_RIGHT);

        VBox card = new VBox(18,
                label(edit ? "Edit User" : "Add User", 22, TEXT, true),
                label(edit ? "Update account details and role access." : "Create a new system account.", 13, MUTED, false),
                separator(),
                form,
                footer);
        card.setPadding(new Insets(24));
        card.setMaxWidth(570);
        card.getStyleClass().add("phase4-modal-card");

        ModalDialog overlay = ModalDialog.show(host, card);

        cancel.setOnAction(e -> closeOverlay(overlay));
        save.setOnAction(e -> {
            clearErrors(fullNameError, usernameError, emailError, passwordError);
            boolean valid = validateUserForm(fullName, username, email, password, edit ? existing : null,
                    fullNameError, usernameError, emailError, passwordError);
            if (!valid) return;

            try {
                User saved;
                if (edit) {
                    saved = userService.adminUpdateUser(currentUser, existing,
                            fullName.getText().trim(),
                            username.getText().trim(),
                            email.getText().trim(),
                            password.getText(),
                            roles.getRole(),
                            activeToggle.isSelected());
                    if (existing.getUserId().equals(currentUser.getUserId())) {
                        selectedUser = saved;
                    }
                    showToast("User updated successfully", GREEN);
                } else {
                    saved = userService.registerUser(username.getText().trim(), password.getText(),
                            email.getText().trim(), roles.getRole());
                    saved.setFullName(fullName.getText().trim());
                    saved.setActive(activeToggle.isSelected());
                    showToast("User created successfully", GREEN);
                }

                reloadUsers();
                refreshVisibleUsers();
                refreshStats();
                if (selectedUser != null && selectedUser.getUserId().equals(saved.getUserId())) {
                    showUserDetails(saved);
                }
                closeOverlay(overlay);
            } catch (RuntimeException ex) {
                usernameError.setText(ex.getMessage() == null ? "Unable to save user." : ex.getMessage());
                usernameError.setVisible(true);
                usernameError.setManaged(true);
                shake(card);
            }
        });
    }

    private boolean validateUserForm(
            TextField fullName,
            TextField username,
            TextField email,
            PasswordField password,
            User editing,
            Label fullNameError,
            Label usernameError,
            Label emailError,
            Label passwordError) {

        boolean ok = true;
        if (safe(fullName.getText()).trim().isEmpty()) {
            setError(fullNameError, "Full name is required."); ok = false;
        }
        if (safe(username.getText()).trim().isEmpty()) {
            setError(usernameError, "Username is required."); ok = false;
        } else if (!safe(username.getText()).trim().equalsIgnoreCase(safe(editing == null ? "" : editing.getUsername()).trim())
                && userService.isUsernameTaken(username.getText().trim())) {
            setError(usernameError, "Username is already taken."); ok = false;
        }
        if (!userService.validateEmail(email.getText().trim())) {
            setError(emailError, "Enter a valid email address."); ok = false;
        }
        if (editing == null && !userService.validatePasswordStrength(password.getText())) {
            setError(passwordError, "Password must be at least 8 characters and meet complexity requirements."); ok = false;
        }
        if (editing != null && !password.getText().isEmpty() && !userService.validatePasswordStrength(password.getText())) {
            setError(passwordError, "New password is not strong enough."); ok = false;
        }
        return ok;
    }

    private void openDeleteConfirm(User user) {
        if (user == null || currentUser.getUserId().equals(user.getUserId()) || user.getRole() == UserRole.ADMIN) {
            return;
        }

        Button cancel = button("Cancel", "outline-button");
        Button delete = button("Delete User", "danger-button");

        VBox card = new VBox(15,
                label("Delete User", 22, TEXT, true),
                label("This action cannot be undone.", 13, MUTED, false),
                separator(),
                label("Delete \"" + safe(user.getFullName()) + "\" (@" + safe(user.getUsername()) + ")?", 14, TEXT, false),
                label("The account will be removed from the in-memory user store. Domain rules may reject deletion when active or processed transactions exist.", 12, MUTED, false),
                new HBox(10, spacer(), cancel, delete));
        card.setPadding(new Insets(24));
        card.setMaxWidth(480);
        card.getStyleClass().addAll("phase4-modal-card", "danger-modal");

        ModalDialog overlay = ModalDialog.show(host, card);

        cancel.setOnAction(e -> closeOverlay(overlay));
        delete.setOnAction(e -> {
            try {
                userService.deleteUser(currentUser, user.getUserId());
                if (selectedUser != null && selectedUser.getUserId().equals(user.getUserId())) closeDetailPanel();
                reloadUsers();
                refreshVisibleUsers();
                refreshStats();
                showToast("User deleted successfully", GREEN);
                closeOverlay(overlay);
            } catch (RuntimeException ex) {
                showToast(ex.getMessage() == null ? "Unable to delete user." : ex.getMessage(), RED);
                shake(card);
            }
        });
    }

    private Node buildPermissionDenied() {
        VBox card = new VBox(12,
                label("Permissions Denied", 26, TEXT, true),
                label("User Management is available to administrators only.", 14, MUTED, false));
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(36));
        card.setMaxWidth(520);
        card.getStyleClass().add("card");

        Button back = new Button("Back to Dashboard", IconView.of(IconView.Name.BACK, 16, MUTED));
        back.getStyleClass().add("outline-button");
        back.setOnAction(e -> backToDashboard.run());
        card.getChildren().add(back);

        StackPane box = new StackPane(card);
        box.setPadding(new Insets(40));
        return box;
    }

    private void showUserDetails(User user) {
        if (user == null) return;
        selectedUser = user;
        closeDetailPanel();

        detailBackdrop = new Region();
        detailBackdrop.getStyleClass().add("phase6-modal-overlay");
        detailBackdrop.setOnMouseClicked(e -> closeDetailPanel());
        StackPane.setAlignment(detailBackdrop, Pos.CENTER);
        host.getChildren().add(detailBackdrop);

        detailPanel = new VBox(16);
        detailPanel.setPrefWidth(340);
        detailPanel.setMaxWidth(340);
        detailPanel.setPadding(new Insets(22));
        detailPanel.getStyleClass().add("detail-panel");
        StackPane.setAlignment(detailPanel, Pos.CENTER_RIGHT);

        Button close = iconButton(IconView.Name.CLOSE);
        close.setOnAction(e -> closeDetailPanel());

        HBox title = new HBox(label("User Details", 16, TEXT, true), spacer(), close);
        title.setAlignment(Pos.CENTER_LEFT);

        StackPane avatar = avatarGraphic(user, 64);
        avatar.getStyleClass().add("avatar-ring");

        VBox identity = new VBox(4,
                label(safe(user.getFullName()), 20, TEXT, true),
                label(safe(user.getEmail()), 13, MUTED, false),
                roleBadge(user.getRole()));
        identity.setAlignment(Pos.CENTER);

        VBox stats = new VBox(10,
                detailStat("Tickets Handled", String.valueOf(ticketCount(user))),
                detailStat("Revenue Processed", money(revenueProcessed(user))),
                detailStat("Last Active", lastActive(user)));

        VBox timeline = new VBox(12);
        timeline.getChildren().add(label("Recent Activity", 15, TEXT, true));
        buildTimeline(user, timeline);

        ScrollPane scroll = new ScrollPane(timeline);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("dark-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        detailPanel.getChildren().addAll(title, avatar, identity, separator(), stats, separator(), scroll);
        host.getChildren().add(detailPanel);

        detailPanel.setTranslateX(340);
        TranslateTransition in = new TranslateTransition(Duration.millis(240), detailPanel);
        in.setToX(0);
        in.play();
    }

    private void buildTimeline(User user, VBox timeline) {
        List<Activity> activities = new ArrayList<>();
        if (user.getLastLogin() != null) {
            activities.add(new Activity(user.getLastLogin(), "User logged in"));
        }
        if (user.getRole() == UserRole.ATTENDANT) {
            int count = ((Attendant) user).getProcessedTicketIds() == null ? 0 : ((Attendant) user).getProcessedTicketIds().size();
            if (count > 0) activities.add(new Activity(LocalDateTime.now().minusMinutes(12), count + " ticket transaction(s) processed"));
        } else if (user.getRole() == UserRole.CUSTOMER) {
            int count = ((Customer) user).getTicketIds() == null ? 0 : ((Customer) user).getTicketIds().size();
            if (count > 0) activities.add(new Activity(LocalDateTime.now().minusMinutes(22), count + " parking ticket(s) created"));
        }
        activities.add(new Activity(user.getCreatedAt(), "Account created"));

        for (int i = 0; i < activities.size(); i++) {
            Activity a = activities.get(i);
            Circle dot = new Circle(4, DesignTokens.color(TEAL));
            VBox text = new VBox(2,
                    label(a.time.format(DATE_TIME), 11, MUTED, true),
                    label(a.description, 13, TEXT, false));
            HBox row = new HBox(12, dot, text);
            row.setAlignment(Pos.TOP_LEFT);
            timeline.getChildren().add(row);
            if (i < activities.size() - 1) {
                Line line = new Line(0, 0, 0, 16);
                line.setStroke(DesignTokens.color(BORDER));
                timeline.getChildren().add(line);
            }
        }
    }

    private void closeDetailPanel() {
        if (detailPanel != null) {
            host.getChildren().remove(detailPanel);
            detailPanel = null;
        }
        if (detailBackdrop != null) {
            host.getChildren().remove(detailBackdrop);
            detailBackdrop = null;
        }
        selectedUser = null;
    }

    private StackPane modalOverlay(Node card) {
        Region shade = new Region();
        shade.getStyleClass().add("phase6-modal-overlay");
        shade.setOnMouseClicked(e -> { /* modal intentionally blocks background clicks */ });

        StackPane overlay = new StackPane(shade, card);
        overlay.getStyleClass().add("modal-overlay");
        StackPane.setAlignment(card, Pos.CENTER);
        return overlay;
    }

    private void animateModalIn(Node card) {
        card.setOpacity(0);
        card.setScaleX(.9);
        card.setScaleY(.9);
        FadeTransition fade = new FadeTransition(Duration.millis(200), card);
        fade.setToValue(1);
        ScaleTransition scale = new ScaleTransition(Duration.millis(200), card);
        scale.setToX(1);
        scale.setToY(1);
        fade.play();
        scale.play();
    }

    private void closeOverlay(Node overlay) {
        if (overlay instanceof ModalDialog modal) {
            modal.close();
            return;
        }
        FadeTransition fade = new FadeTransition(Duration.millis(150), overlay);
        fade.setToValue(0);
        fade.setOnFinished(e -> host.getChildren().remove(overlay));
        fade.play();
    }

    private int ticketCount(User user) {
        if (user instanceof Customer) {
            Customer c = (Customer) user;
            if (c.getTicketIds() != null) return c.getTicketIds().size();
        }
        if (user instanceof Attendant) {
            Attendant a = (Attendant) user;
            if (a.getProcessedTicketIds() != null) return a.getProcessedTicketIds().size();
        }
        if (ticketService != null) {
            try {
                return (int) ticketService.getAllTickets().stream()
                        .filter(t -> user.getUserId().equals(t.getUserId()))
                        .count();
            } catch (RuntimeException ignored) { }
        }
        return 0;
    }

    private double revenueProcessed(User user) {
        if (user instanceof Attendant) return ((Attendant) user).getTotalRevenue();
        if (paymentService == null) return 0;
        try {
            return paymentService.getAllPayments().stream()
                    .filter(p -> user.getUserId().equals(p.getCustomerId()))
                    .mapToDouble(p -> p.getFinalAmount() > 0 ? p.getFinalAmount() : p.getAmount())
                    .sum();
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private String lastActive(User user) {
        if (user.getLastLogin() == null) return "Never";
        return user.getLastLogin().format(TIME);
    }

    private VBox detailStat(String title, String value) {
        VBox v = new VBox(2,
                label(title.toUpperCase(Locale.ROOT), 10, MUTED, true),
                label(value, 15, TEXT, true));
        return v;
    }

    private StackPane avatarGraphic(User user, double size) {
        String initials = initials(user == null ? "" : user.getFullName());
        Circle circle = new Circle(size / 2.0);
        circle.setFill(DesignTokens.color(GLOW_TEAL_12));
        circle.setStroke(DesignTokens.color(BORDER));
        Label l = label(initials, Math.max(11, size * .34), TEAL, true);
        StackPane pane = new StackPane(circle, l);
        pane.setMinSize(size, size);
        pane.setPrefSize(size, size);
        pane.setMaxSize(size, size);
        return pane;
    }

    private Label roleBadge(UserRole role) {
        String text = role == UserRole.CUSTOMER ? "CUSTOMER" : role == UserRole.ATTENDANT ? "ATTENDANT" : "ADMIN";
        String color = role == UserRole.CUSTOMER ? BLUE : role == UserRole.ATTENDANT ? ORANGE : RED;
        String cls = role == UserRole.CUSTOMER ? "role-badge-customer" : role == UserRole.ATTENDANT ? "role-badge-attendant" : "role-badge-admin";
        Label badge = label(text, 11, color, true);
        badge.setPadding(new Insets(5, 9, 5, 9));
        badge.getStyleClass().add(cls);
        return badge;
    }

    private VBox statCard(String title, Label value, String subtitle) {
        VBox v = new VBox(5,
                label(title, 10, MUTED, true),
                value,
                label(subtitle, 11, MUTED, false));
        v.setPadding(new Insets(16));
        v.setMinHeight(104);
        v.getStyleClass().add("card");
        return v;
    }

    private static Label statValue() {
        Label l = new Label("0");
        l.setTextFill(DesignTokens.color(TEAL));
        l.getStyleClass().add("stat-value");
        return l;
    }

    private TextField input(String prompt) {
        TextField f = new TextField();
        f.setPromptText(prompt);
        f.setPrefHeight(42);
        f.getStyleClass().add("dark-input");
        return f;
    }

    private Button iconButton(IconView.Name icon) {
        return UiNodes.iconButton(icon);
    }

    private Button button(String text, String style) {
        Button b = new Button(text);
        b.getStyleClass().add(style);
        b.setFocusTraversable(false);
        return b;
    }

    private Separator separator() {
        return UiNodes.separator();
    }

    private Region spacer() {
        return UiNodes.spacer();
    }

    private Label formLabel(String text) {
        return UiNodes.formLabel(text);
    }

    private Label errorLabel() {
        Label l = label("", 11, RED, false);
        l.setVisible(false);
        l.setManaged(false);
        return l;
    }

    private void clearErrors(Label... labels) {
        for (Label l : labels) {
            l.setText("");
            l.setGraphic(null);
            l.setVisible(false);
            l.setManaged(false);
        }
    }

    private void setError(Label label, String message) {
        label.setText(message);
        label.setVisible(true);
        label.setManaged(true);
    }

    private void bindValidation(TextInputControl field, Label message, Predicate<String> validator, String error) {
        field.textProperty().addListener((obs, old, now) -> setValidationMessage(message, validator.test(now), error));
    }

    private void setValidationMessage(Label message, boolean valid, String error) {
        message.setText(valid ? "" : error);
        boolean show = !valid && !error.isEmpty();
        message.setGraphic(valid && !error.isEmpty() ? IconView.of(IconView.Name.CHECK, 12, GREEN) : null);
        message.setVisible(show || message.getGraphic() != null);
        message.setManaged(show || message.getGraphic() != null);
    }

    private void showToast(String message, String accent) {
        long token = ++toastGeneration;
        Label toast = label(message, 13, WHITE, true);
        toast.setPadding(new Insets(12, 16, 12, 16));
        toast.getStyleClass().add(GREEN.equals(accent) ? "user-toast-success" : RED.equals(accent) ? "user-toast-error" : "user-toast-info");
        toast.setEffect(new DropShadow(16, DesignTokens.color(SHADOW_BLACK_35)));
        StackPane.setAlignment(toast, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(toast, new Insets(0, 24, 24, 0));
        host.getChildren().add(toast);
        toast.setTranslateX(40);
        toast.setOpacity(0);

        TranslateTransition move = new TranslateTransition(Duration.millis(220), toast);
        move.setToX(0);
        FadeTransition fade = new FadeTransition(Duration.millis(220), toast);
        fade.setToValue(1);
        move.play();
        fade.play();

        PauseTransition hold = new PauseTransition(Duration.seconds(3));
        hold.setOnFinished(e -> {
            if (token != toastGeneration) return;
            FadeTransition out = new FadeTransition(Duration.millis(160), toast);
            out.setToValue(0);
            out.setOnFinished(x -> host.getChildren().remove(toast));
            out.play();
        });
        hold.play();
    }

    private void shake(Node n) {
        UiMotion.shake(n);
    }

    private String money(double value) { return UiFormat.money(value); }
    private String initials(String name) {
        return UiFormat.initials(name);
    }
    private String safe(String s) { return s == null ? "" : s; }

    private Label label(String text, double size, String color, boolean bold) {
        return DesignTokens.text(text, size, color, bold);
    }

    private static final class Activity {
        final LocalDateTime time;
        final String description;
        Activity(LocalDateTime time, String description) {
            this.time = time == null ? LocalDateTime.now() : time;
            this.description = description;
        }
    }

    private static final class SegmentedRole {
        final HBox root = new HBox(0);
        private final ToggleGroup group = new ToggleGroup();
        private final ToggleButton customer = roleButton("Customer", UserRole.CUSTOMER);
        private final ToggleButton attendant = roleButton("Attendant", UserRole.ATTENDANT);
        private final ToggleButton admin = roleButton("Admin", UserRole.ADMIN);

        SegmentedRole() {
            root.getStyleClass().add("segmented-role");
            root.getChildren().addAll(customer, attendant, admin);
        }
        void setRole(UserRole role) {
            if (role == UserRole.ATTENDANT) group.selectToggle(attendant);
            else if (role == UserRole.ADMIN) group.selectToggle(admin);
            else group.selectToggle(customer);
        }
        UserRole getRole() {
            Toggle selected = group.getSelectedToggle();
            if (selected == attendant) return UserRole.ATTENDANT;
            if (selected == admin) return UserRole.ADMIN;
            return UserRole.CUSTOMER;
        }
        private ToggleButton roleButton(String text, UserRole role) {
            ToggleButton b = new ToggleButton(text);
            b.setToggleGroup(group);
            b.setPrefHeight(38);
            b.setUserData(role);
            b.getStyleClass().add("role-segment");
            HBox.setHgrow(b, Priority.ALWAYS);
            return b;
        }
    }

    private static final class StrengthIndicator {
        final HBox root = new HBox(5);
        private final Region[] segments = {new Region(), new Region(), new Region()};
        StrengthIndicator() {
            root.getStyleClass().add("strength-bar");
            for (Region r : segments) {
                r.setPrefHeight(5);
                HBox.setHgrow(r, Priority.ALWAYS);
                r.getStyleClass().add("strength-segment");
                root.getChildren().add(r);
            }
        }
        void update(String password) {
            int score = 0;
            if (password != null) {
                if (password.length() >= 8) score++;
                if (password.matches(".*[A-Z].*") && password.matches(".*[a-z].*")) score++;
                if (password.matches(".*[0-9].*") || password.matches(".*[^A-Za-z0-9].*")) score++;
            }
            String[] colors = {RED, YELLOW, GREEN};
            for (int i = 0; i < segments.length; i++) {
                segments[i].getStyleClass().removeAll("strength-off", "strength-weak", "strength-medium", "strength-strong");
                segments[i].getStyleClass().add(i < score ? (score == 1 ? "strength-weak" : score == 2 ? "strength-medium" : "strength-strong") : "strength-off");
            }
        }
    }
}
