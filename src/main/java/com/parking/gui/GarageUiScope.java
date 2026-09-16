package com.parking.gui;

import com.parking.model.Garage;
import com.parking.model.Payment;
import com.parking.model.Ticket;
import com.parking.model.User;
import com.parking.services.GarageContext;
import com.parking.services.PaymentService;
import com.parking.services.TicketService;

import java.util.List;

/** Shared UI adapter for applying the authenticated user's active garage context. */
public final class GarageUiScope {
    private GarageUiScope() { }

    /** Null means all garages, which is the service API's global-admin scope. */
    public static String garageQuery(GarageContext context) {
        return context == null || context.isAllGarages() ? null : context.getSelectedGarageId();
    }

    public static List<Ticket> tickets(TicketService service, User actor, GarageContext context) {
        return service.findTickets(actor, garageQuery(context));
    }

    public static List<Payment> payments(PaymentService service, User actor, GarageContext context) {
        return service.findPayments(actor, garageQuery(context));
    }

    public static String label(Garage garage) {
        if (garage == null) return "Unknown garage";
        String name = garage.getName() == null || garage.getName().isBlank()
                ? garage.getGarageId() : garage.getName();
        return name + " · " + garage.getGarageId();
    }

    public static String label(String garageId) {
        return garageId == null || garageId.isBlank() ? "Unknown garage" : "Garage · " + garageId;
    }
}
