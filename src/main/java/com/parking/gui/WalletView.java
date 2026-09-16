package com.parking.gui;

import com.parking.enums.PaymentStatus;
import com.parking.model.Customer;
import com.parking.model.Payment;
import com.parking.model.ParkingGarage;
import com.parking.services.PaymentService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.parking.gui.DesignTokens.*;

/** Customer wallet balance and payment history page. */
public final class WalletView {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy  •  HH:mm");
    private final PaymentService payments;
    private final Customer customer;
    private final Consumer<Node> navigate;
    private final Function<BorderPane, Node> ticketPage;
    private final Consumer<String> toast;

    public WalletView(PaymentService payments, Customer customer, Consumer<Node> navigate,
                      Function<BorderPane, Node> ticketPage, Consumer<String> toast) {
        this.payments = Objects.requireNonNull(payments); this.customer = Objects.requireNonNull(customer);
        this.navigate = Objects.requireNonNull(navigate); this.ticketPage = Objects.requireNonNull(ticketPage); this.toast = Objects.requireNonNull(toast);
    }

    public Node build(BorderPane shell) {
        VBox content = new VBox(18); content.setPadding(new Insets(26, 30, 32, 30)); content.getStyleClass().add("page-root");
        Button addBalance = new Button("Add balance"); addBalance.getStyleClass().add("primary-button"); addBalance.setOnAction(e -> showAddBalanceDialog(shell));
        HBox header = new HBox(12, DesignTokens.text("Wallet", 26, TEXT, true), UiNodes.spacer(), addBalance); header.setAlignment(Pos.CENTER_LEFT);
        content.getChildren().addAll(header, DesignTokens.text("Review your balance and payment history.", 14, MUTED, false));
        HBox balance = new HBox(14, summary("AVAILABLE BALANCE", UiFormat.money(customer.getWalletBalance()), "Parking wallet"), summary("PAYMENTS", String.valueOf(payments.getPaymentsFor(customer).size()), "Recorded transactions")); balance.getChildren().forEach(child -> HBox.setHgrow(child, Priority.ALWAYS)); content.getChildren().add(balance);
        VBox history = new VBox(8); history.getStyleClass().add("card"); history.setPadding(new Insets(18)); history.getChildren().addAll(DesignTokens.text("Payment history", 17, TEXT, true), DesignTokens.text("Your completed and attempted parking payments", 12, MUTED, false));
        var records = payments.getPaymentsFor(customer); if (records.isEmpty()) history.getChildren().add(DesignTokens.text("No payments recorded yet.", 13, MUTED, false));
        for (Payment payment : records) { String when = payment.getPaymentTime() == null ? "—" : payment.getPaymentTime().format(DATE_TIME); HBox row = new HBox(12, DesignTokens.text(safe(payment.getPaymentId(), "—"), 12, TEXT, true), DesignTokens.text("Ticket " + safe(payment.getTicketId(), "—"), 12, MUTED, false), UiNodes.spacer(), DesignTokens.text(UiFormat.money(payment.getFinalAmount()), 12, TEXT, true), DesignTokens.text(payment.getStatus().name(), 11, payment.getStatus() == PaymentStatus.COMPLETED ? TEAL : MUTED, true), DesignTokens.text(when, 11, MUTED, false)); row.setAlignment(Pos.CENTER_LEFT); row.setMinHeight(34); history.getChildren().add(row); }
        content.getChildren().add(history); ScrollPane scroll = new ScrollPane(content); scroll.setFitToWidth(true); scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); scroll.getStyleClass().add("dark-scroll"); return scroll;
    }

    private void showAddBalanceDialog(BorderPane shell) {
        TextField amount = new TextField(); amount.setPromptText("Amount"); amount.getStyleClass().add("dark-input"); Dialog<ButtonType> dialog = new Dialog<>(); dialog.setTitle("Add balance"); dialog.getDialogPane().getStyleClass().add("vehicle-selection-dialog"); dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK); dialog.getDialogPane().setContent(new VBox(10, DesignTokens.text("Add balance to wallet", 18, TEXT, true), DesignTokens.text("Enter the amount to add.", 12, MUTED, false), amount));
        Optional<ButtonType> result = dialog.showAndWait(); if (result.isEmpty() || result.get() != ButtonType.OK) return;
        try { double value = Double.parseDouble(amount.getText().trim()); if (value <= 0) throw new NumberFormatException(); customer.addWalletBalance(value); payments.getPersistenceStore().saveUser(customer); toast.accept("Wallet balance updated."); navigate.accept(build(shell)); }
        catch (NumberFormatException ex) { toast.accept("Enter a valid amount greater than zero."); }
    }
    private VBox summary(String title, String value, String detail) { VBox box = new VBox(5, DesignTokens.text(title, 10, MUTED, true), DesignTokens.text(value, 21, TEAL, true), DesignTokens.text(detail, 11, MUTED, false)); box.getStyleClass().add("card"); box.setPadding(new Insets(15)); return box; }
    private String safe(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
}
