package com.parking.gui;

import com.parking.enums.UserRole;
import com.parking.model.DutySession;
import com.parking.model.User;
import com.parking.services.DutyService;
import com.parking.services.DutySummary;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.parking.gui.DesignTokens.*;

/** Attendant duty page and shift page with the admin summary. */
public final class DutyView {
    public enum Mode { DUTY, SHIFT }

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy • HH:mm");

    private final DutyService duty;
    private final User currentUser;
    private final ToastManager toasts;
    private final Mode mode;

    public DutyView(DutyService duty, User currentUser, ToastManager toasts, Mode mode) {
        this.duty = Objects.requireNonNull(duty);
        this.currentUser = Objects.requireNonNull(currentUser);
        this.toasts = Objects.requireNonNull(toasts);
        this.mode = Objects.requireNonNull(mode);
    }

    public Node build() {
        VBox page = new VBox(16);
        page.setPadding(new Insets(24, 28, 30, 28));
        page.getStyleClass().add("page-root");
        if (mode == Mode.DUTY) {
            page.getChildren().addAll(heading("Duty", "Start and end your current duty session."),
                    statusCard(), controlCard());
        } else {
            page.getChildren().addAll(heading("Shift", "Review your assigned shift and handoff details."),
                    statusCard(), historyCard());
            if (currentUser.getRole() == UserRole.ADMIN) page.getChildren().add(adminSummaryCard());
            else page.getChildren().add(handoffCard());
        }
        ScrollPane scroll = new ScrollPane(page);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("dark-scroll");
        return scroll;
    }

    private Node heading(String title, String subtitle) {
        return new VBox(3, DesignTokens.text(title, 24, TEXT, true), DesignTokens.text(subtitle, 14, MUTED, false));
    }

    private Node statusCard() {
        VBox card = card("Current duty");
        try {
            Optional<DutySession> open = duty.current(currentUser);
            if (open.isPresent()) {
                DutySession session = open.get();
                card.getChildren().add(DesignTokens.text("On duty: " + session.shift() + " • " + session.zone()
                        + " since " + session.startedAt().format(DATE_TIME), 14, GREEN, true));
            } else {
                card.getChildren().add(DesignTokens.text("Off duty — no open session.", 14, MUTED, false));
            }
        } catch (RuntimeException ex) {
            card.getChildren().add(DesignTokens.text("Duty status unavailable: " + ex.getMessage(), 13, RED, false));
        }
        return card;
    }

    private Node controlCard() {
        VBox card = card("Manage duty session");
        TextField shift = new TextField();
        shift.setPromptText("Shift (e.g. Morning)");
        shift.getStyleClass().add("dark-input");
        TextField zone = new TextField();
        zone.setPromptText("Zone (e.g. Zone A)");
        zone.getStyleClass().add("dark-input");
        Label feedback = DesignTokens.text("", 12, RED, false);
        Button start = new Button("Start duty");
        start.getStyleClass().add("primary-button");
        start.setOnAction(ignored -> {
            try {
                DutySession session = duty.start(currentUser, shift.getText(), zone.getText(), LocalDateTime.now());
                feedback.setTextFill(javafx.scene.paint.Color.web(GREEN));
                feedback.setText("On duty: " + session.shift() + " • " + session.zone() + ".");
                toasts.showSuccess("Duty started", session.shift() + " shift in " + session.zone() + ".");
            } catch (RuntimeException ex) {
                feedback.setTextFill(javafx.scene.paint.Color.web(RED));
                feedback.setText(ex.getMessage());
                toasts.showError("Cannot start duty", ex.getMessage());
            }
        });
        Button end = new Button("End duty");
        end.getStyleClass().add("outline-button");
        end.setOnAction(ignored -> {
            try {
                DutySession closed = duty.end(currentUser, LocalDateTime.now());
                feedback.setTextFill(javafx.scene.paint.Color.web(GREEN));
                feedback.setText("Duty ended: " + closed.shift() + " • " + closed.zone() + ".");
                toasts.showSuccess("Duty ended", "Your duty session was closed.");
            } catch (RuntimeException ex) {
                feedback.setTextFill(javafx.scene.paint.Color.web(RED));
                feedback.setText(ex.getMessage());
                toasts.showError("Cannot end duty", ex.getMessage());
            }
        });
        HBox actions = new HBox(10, start, end);
        actions.setAlignment(Pos.CENTER_LEFT);
        card.getChildren().addAll(DesignTokens.text("SHIFT", 11, MUTED, true), shift,
                DesignTokens.text("ZONE", 11, MUTED, true), zone, actions, feedback);
        return card;
    }

    private Node historyCard() {
        VBox card = card("Shift history");
        try {
            List<DutySession> sessions = duty.history(currentUser);
            if (sessions.isEmpty()) {
                card.getChildren().add(DesignTokens.text("No duty sessions recorded yet.", 13, MUTED, false));
            }
            for (DutySession session : sessions) {
                String line = session.shift() + " • " + session.zone() + " • "
                        + session.startedAt().format(DATE_TIME) + " → "
                        + (session.endedAt() == null ? "now" : session.endedAt().format(DATE_TIME))
                        + " • " + session.status();
                card.getChildren().add(DesignTokens.text(line, 13, TEXT, false));
            }
        } catch (RuntimeException ex) {
            card.getChildren().add(DesignTokens.text("Shift history unavailable: " + ex.getMessage(), 13, RED, false));
        }
        return card;
    }

    private Node handoffCard() {
        VBox card = card("Handoff details");
        try {
            Optional<DutySession> open = duty.current(currentUser);
            card.getChildren().add(DesignTokens.text(open.isPresent()
                    ? "Hand over " + open.get().zone() + " (" + open.get().shift() + " shift) at the next changeover."
                    : "Nothing to hand over — you are off duty.", 13, MUTED, false));
        } catch (RuntimeException ex) {
            card.getChildren().add(DesignTokens.text("Handoff unavailable: " + ex.getMessage(), 13, RED, false));
        }
        return card;
    }

    private Node adminSummaryCard() {
        VBox card = card("Team summary — last 7 days");
        try {
            LocalDateTime to = LocalDateTime.now();
            List<DutySummary> summaries = duty.summaries(currentUser, to.minusDays(7), to);
            if (summaries.isEmpty()) {
                card.getChildren().add(DesignTokens.text("No duty sessions in the last 7 days.", 13, MUTED, false));
            }
            for (DutySummary summary : summaries) {
                long minutes = summary.totalDuration().toMinutes();
                String line = summary.attendantName() + " — " + summary.completedTransactions()
                        + " transactions • $" + String.format("%.2f", summary.revenue())
                        + " • " + (minutes / 60) + "h " + (minutes % 60) + "m on duty";
                card.getChildren().add(DesignTokens.text(line, 13, TEXT, false));
            }
        } catch (RuntimeException ex) {
            card.getChildren().add(DesignTokens.text("Summary unavailable: " + ex.getMessage(), 13, RED, false));
        }
        return card;
    }

    private VBox card(String title) {
        VBox card = new VBox(10, DesignTokens.text(title, 15, TEXT, true));
        card.getStyleClass().add("settings-card");
        card.setPadding(new Insets(14));
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }
}
