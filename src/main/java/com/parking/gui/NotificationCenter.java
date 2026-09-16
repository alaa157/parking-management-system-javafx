package com.parking.gui;

import com.parking.enums.NotificationType;
import com.parking.gui.components.StyleManager;
import com.parking.model.User;
import com.parking.services.NotificationService;

import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.prefs.Preferences;
import java.time.format.DateTimeFormatter;

/** Notification dropdown content/popup for the ParkingOS shell. */
public final class NotificationCenter {
    public record Notice(String text, String time, String type, boolean unread) {}

    private final VBox panel = new VBox();
    private final List<Notice> notices = new ArrayList<>();
    private final Runnable onChange;
    private final NotificationService service;
    private final Supplier<User> currentUser;
    private static final Preferences SOUND_PREFS = Preferences.userNodeForPackage(NotificationCenter.class);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    public NotificationCenter(Runnable onChange) {
        this(null, null, onChange);
    }

    /** Service-backed center rendering the current user's persisted unread notifications. */
    public NotificationCenter(NotificationService service, Supplier<User> currentUser, Runnable onChange) {
        this.service = service;
        this.currentUser = currentUser;
        this.onChange = onChange;
        loadNotices();
        build();
    }

    private boolean isBound() { return service != null && currentUser != null; }

    /** Reloads the rendered list from persisted unread notifications. */
    public void refresh() {
        loadNotices();
        build();
        if (onChange != null) onChange.run();
    }

    private void loadNotices() {
        notices.clear();
        if (!isBound()) return;
        User user = currentUser.get();
        if (user == null) return;
        var unread = service.listUnread(user);
        for (int i = unread.size() - 1; i >= 0; i--) {
            var notification = unread.get(i);
            notices.add(new Notice(notification.message(), notification.createdAt().format(TIME),
                    displayType(notification.type()), true));
        }
    }

    public Node getPanel() { return panel; }
    public int unreadCount() { return (int) notices.stream().filter(Notice::unread).count(); }

    public void markAllRead() {
        if (isBound()) {
            User user = currentUser.get();
            if (user != null) service.markAllRead(user);
            refresh();
            return;
        }
        for (int i = 0; i < notices.size(); i++) {
            Notice n = notices.get(i);
            notices.set(i, new Notice(n.text(), n.time(), n.type(), false));
        }
        build();
        if (onChange != null) onChange.run();
    }

    public void clearAll() {
        if (isBound()) {
            User user = currentUser.get();
            if (user != null) service.clear(user);
            refresh();
            return;
        }
        notices.clear();
        build();
        if (onChange != null) onChange.run();
    }

    public void add(String text, String time, String type) {
        if (isBound()) {
            User user = currentUser.get();
            if (user != null && text != null && !text.isBlank()) {
                service.publish(user.getUserId(), mapType(type), text);
            }
            playSound(type);
            refresh();
            return;
        }
        notices.add(0, new Notice(text, time, type, true));
        playSound(type);
        build();
        if (onChange != null) onChange.run();
    }

    public static boolean isSoundEnabled(String type) { return SOUND_PREFS.getBoolean("sound." + type.toUpperCase(), true); }
    public static void setSoundEnabled(String type, boolean enabled) { SOUND_PREFS.putBoolean("sound." + type.toUpperCase(), enabled); }
    public static void playSound(String type) {
        if (!isSoundEnabled(type) || ThemeManager.get().isReduceMotion()) return;
        try { java.awt.Toolkit.getDefaultToolkit().beep(); } catch (RuntimeException ignored) { }
    }

    public void toggleAt(Node anchor) {
        if (panel.getParent() == null) return;
        boolean opening = !panel.isVisible();
        panel.setVisible(true);
        panel.setManaged(true);
        if (opening) {
            panel.setTranslateY(-12);
            panel.setOpacity(0);
            TranslateTransition t = new TranslateTransition(Duration.millis(300), panel);
            t.setToY(0);
            FadeTransition f = new FadeTransition(Duration.millis(300), panel);
            f.setToValue(1);
            t.play(); f.play();
        } else {
            panel.setVisible(false);
            panel.setManaged(false);
        }
    }

    public void hide() {
        panel.setVisible(false);
        panel.setManaged(false);
    }

    private void build() {
        panel.getChildren().clear();
        panel.setPrefWidth(360);
        panel.setMaxWidth(360);
        panel.setPadding(new Insets(14));
        panel.getStyleClass().setAll("notification-panel");
        StyleManager.styleLayer(panel, 30);

        HBox header = new HBox(10,
                text("Notifications", 18, DesignTokens.TEXT, true),
                spacer(),
                linkButton("Mark all as read", this::markAllRead));
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 0, 10, 0));
        panel.getChildren().add(header);

        VBox list = new VBox(0);
        if (notices.isEmpty()) {
            VBox empty = new VBox(6,
                    IconView.of(IconView.Name.BELL, 30, DesignTokens.MUTED),
                    text("No notifications", 13, DesignTokens.TEXT, true),
                    text("You're all caught up.", 12, DesignTokens.MUTED, false));
            empty.setAlignment(Pos.CENTER);
            empty.setPadding(new Insets(30));
            list.getChildren().add(empty);
        } else {
            for (Notice n : notices) list.getChildren().add(item(n));
        }
        VBox.setVgrow(list, javafx.scene.layout.Priority.ALWAYS);
        panel.getChildren().add(list);

        Button clear = new Button("Clear all");
        clear.getStyleClass().add("notification-clear");
        clear.setOnAction(e -> clearAll());
        HBox foot = new HBox(clear);
        foot.setAlignment(Pos.CENTER_RIGHT);
        foot.setPadding(new Insets(10, 0, 0, 0));
        panel.getChildren().add(foot);
    }

    private Node item(Notice n) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.TOP_LEFT);
        row.setPadding(new Insets(11, 10, 11, 10));
        row.getStyleClass().add("notification-item");
        if (n.unread()) row.getStyleClass().add("notification-unread");

        Label dot = new Label();
        dot.setGraphic(IconView.of(IconView.Name.INFO, 12, DesignTokens.TEAL));
        dot.getStyleClass().add("notification-dot-" + n.type());

        VBox copy = new VBox(4,
                text(n.text(), 13, DesignTokens.TEXT, n.unread()),
                text(n.time(), 11, DesignTokens.MUTED, false));
        HBox.setHgrow(copy, javafx.scene.layout.Priority.ALWAYS);
        row.getChildren().addAll(dot, copy);
        return row;
    }

    private Button linkButton(String title, Runnable r) {
        Button b = new Button(title);
        b.getStyleClass().add("notification-link");
        b.setOnAction(e -> r.run());
        return b;
    }

    private static javafx.scene.layout.Region spacer() {
        javafx.scene.layout.Region r = new javafx.scene.layout.Region();
        HBox.setHgrow(r, javafx.scene.layout.Priority.ALWAYS);
        return r;
    }

    private static Label text(String s, double size, String color, boolean bold) {
        return DesignTokens.text(s, size, color, bold);
    }

    /** Maps persisted notification types onto the existing notice display kinds. */
    private static String displayType(NotificationType type) {
        if (type == null) return "INFO";
        return switch (type) {
            case PAYMENT_SUCCESS -> "SUCCESS";
            case MAINTENANCE -> "WARNING";
            case ENTRY_EXIT, WEEKLY_SUMMARY -> "INFO";
        };
    }

    /** Maps legacy notice kinds onto persisted notification types. */
    private static NotificationType mapType(String type) {
        if (type == null) return NotificationType.MAINTENANCE;
        return switch (type.toUpperCase()) {
            case "SUCCESS" -> NotificationType.PAYMENT_SUCCESS;
            case "INFO" -> NotificationType.ENTRY_EXIT;
            case "ERROR", "WARNING" -> NotificationType.MAINTENANCE;
            default -> NotificationType.MAINTENANCE;
        };
    }
}
