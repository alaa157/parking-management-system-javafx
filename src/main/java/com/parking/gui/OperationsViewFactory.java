package com.parking.gui;

import com.parking.model.ParkingGarage;
import com.parking.model.User;
import com.parking.services.*;
import javafx.scene.Node;

import java.util.Objects;
import java.util.function.Consumer;

/** Creates operational views while keeping service/context wiring out of the shell layout code. */
public final class OperationsViewFactory {
    private final TicketService tickets;
    private final PaymentService payments;
    private final ParkingService parking;
    private final UserService users;
    private final GarageService garages;
    private final ParkingGarage garage;
    private final User actor;
    private final GarageContext context;
    private final BackgroundTaskRunner backgroundTasks;
    private final Consumer<String> toast;
    private final Consumer<Void> back;
    private final Runnable dashboard;

    public OperationsViewFactory(TicketService tickets, PaymentService payments, ParkingService parking,
                                 UserService users, GarageService garages, ParkingGarage garage, User actor,
                                 GarageContext context, BackgroundTaskRunner backgroundTasks,
                                 Consumer<String> toast, Consumer<Void> back, Runnable dashboard) {
        this.tickets = Objects.requireNonNull(tickets); this.payments = Objects.requireNonNull(payments);
        this.parking = Objects.requireNonNull(parking); this.users = Objects.requireNonNull(users);
        this.garages = Objects.requireNonNull(garages); this.garage = Objects.requireNonNull(garage);
        this.actor = Objects.requireNonNull(actor); this.context = Objects.requireNonNull(context);
        this.backgroundTasks = Objects.requireNonNull(backgroundTasks); this.toast = Objects.requireNonNull(toast);
        this.back = Objects.requireNonNull(back); this.dashboard = Objects.requireNonNull(dashboard);
    }

    public Node ticketPayment() {
        return ticketPaymentView().build();
    }

    public TicketPaymentView ticketPaymentView() {
        return new TicketPaymentView(tickets, payments, parking, users, garage, actor, context, toast, back);
    }

    public Node garageManagement() {
        return garageManagement(() -> {}).build();
    }

    public GarageManagementView garageManagement(Runnable access) {
        return new GarageManagementView(garages, context, actor, access, dashboard);
    }

    public Node garageAccess() {
        return garageAccessView().build();
    }

    public GarageAccessView garageAccessView() {
        return new GarageAccessView(garages, users, actor, context, dashboard);
    }

    public Node analytics() {
        return analyticsView().build();
    }

    public AnalyticsReportingView analyticsView() {
        return new AnalyticsReportingView(payments, garage, tickets, actor, context, dashboard, backgroundTasks);
    }
}
