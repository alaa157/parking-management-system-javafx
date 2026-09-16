package com.parking.gui;

import com.parking.enums.UserRole;
import com.parking.exceptions.GarageAccessException;
import com.parking.model.ParkingGarage;
import com.parking.model.Ticket;
import com.parking.model.User;
import com.parking.persistence.PersistenceStore;
import com.parking.services.GarageContext;
import com.parking.services.GarageService;
import com.parking.services.TicketService;
import com.parking.services.UserService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MultiGarageUiTest {

    @Test
    void operationalScreensExposeGarageOwnershipInRowsDetailsReceiptsAndReports() throws Exception {
        String tickets = Files.readString(Path.of("src/main/java/com/parking/gui/TicketPaymentView.java"));
        String reports = Files.readString(Path.of("src/main/java/com/parking/gui/AnalyticsReportingView.java"));
        assertTrue(tickets.contains("addCustomerColumn(\"Garage\""));
        assertTrue(tickets.contains("receiptLine(\"Garage\""));
        assertTrue(tickets.contains("addInfo(info, 3, \"Garage\""));
        assertTrue(reports.contains("col(\"Garage\""));
        assertTrue(reports.contains("Garage,Date,Entries"));
    }

    @Test
    void uiScopeReturnsSelectedGarageOrAllGaragesAndRejectsInaccessibleSelection() {
        try (PersistenceStore store = new PersistenceStore()) {
            User admin = new UserService(store).registerUser("ui-admin", "Admin@123", "ui-admin@example.com", UserRole.ADMIN);
            GarageService garages = new GarageService(store);
            garages.createGarage("G-1", "North Garage", "North", 2, 5.0, "USD", 0, 5, 48);
            garages.createGarage("G-2", "South Garage", "South", 2, 5.0, "USD", 0, 5, 48);
            GarageContext context = new GarageContext(garages, admin);
            TicketService tickets = new TicketService(store);

            context.select("G-1");
            assertEquals("G-1", GarageUiScope.garageQuery(context));
            assertEquals("North Garage · G-1", GarageUiScope.label(garages.getGarage("G-1")));

            context.selectAll();
            assertTrue(context.isAllGarages());
            assertNull(GarageUiScope.garageQuery(context));
            assertTrue(GarageUiScope.tickets(tickets, admin, context).isEmpty());

            User attendant = new UserService(store).registerUser("ui-attendant", "Attendant@123", "ui-attendant@example.com", UserRole.ATTENDANT);
            garages.grantAccess(attendant.getUserId(), "G-1", UserRole.ATTENDANT);
            GarageContext restricted = new GarageContext(garages, attendant);
            restricted.select("G-1");
            assertEquals("G-1", GarageUiScope.garageQuery(restricted));
            assertThrows(GarageAccessException.class, () -> restricted.select("G-2"));
        }
    }
}
