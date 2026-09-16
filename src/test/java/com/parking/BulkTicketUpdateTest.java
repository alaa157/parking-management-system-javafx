package com.parking;

import com.parking.enums.TicketStatus;
import com.parking.enums.UserRole;
import com.parking.enums.VehicleType;
import com.parking.exceptions.AuthorizationException;
import com.parking.model.Ticket;
import com.parking.model.User;
import com.parking.model.Vehicle;
import com.parking.services.BulkTicketUpdateResult;

import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BulkTicketUpdateTest extends TestFixtures {

    private User newAdmin() {
        return newUser("bulk-admin", "Admin@123!", "bulk-admin@example.com", UserRole.ADMIN);
    }

    private User newCustomer(String username) {
        return newUser(username, "Customer@123!", username + "@example.com", UserRole.CUSTOMER);
    }

    private Ticket newTicket(Vehicle vehicle, String spotId) {
        return ticketService.createTicket(vehicle, garage.getSpotById(spotId));
    }

    @Test
    void mixedSelectionReportsFailures() {
        User admin = newAdmin();
        Ticket ok = newTicket(testVehicle, "S-1");
        String badId = "TICKET-DOES-NOT-EXIST";

        BulkTicketUpdateResult result =
                ticketService.updateStatuses(admin, Set.of(ok.getTicketId(), badId), TicketStatus.CANCELLED);

        assertEquals(Set.of(ok.getTicketId()), result.succeeded());
        assertTrue(result.failures().containsKey(badId));
        assertEquals(TicketStatus.CANCELLED, ok.getStatus());
    }

    @Test
    void unauthorizedTicketIsReportedNotApplied() {
        User admin = newAdmin();
        User other = newCustomer("bulk-other");
        Vehicle otherVehicle = new Vehicle(
                "VEH-BULK-OTHER", "BULK-999", VehicleType.CAR,
                "Honda", "Civic", "Blue", 2021, other.getUserId());
        Ticket foreign = newTicket(otherVehicle, "S-2");

        BulkTicketUpdateResult result = ticketService.updateStatuses(
                testCustomer, Set.of(foreign.getTicketId()), TicketStatus.CANCELLED);

        assertTrue(result.succeeded().isEmpty());
        assertTrue(result.failures().containsKey(foreign.getTicketId()));
        assertEquals(TicketStatus.CREATED, foreign.getStatus());

        BulkTicketUpdateResult adminResult = ticketService.updateStatuses(
                admin, Set.of(foreign.getTicketId()), TicketStatus.CANCELLED);
        assertEquals(Set.of(foreign.getTicketId()), adminResult.succeeded());
    }

    @Test
    void inactiveActorIsRejected() {
        User admin = newAdmin();
        Ticket ticket = newTicket(testVehicle, "S-1");
        admin.setActive(false);

        assertThrows(AuthorizationException.class, () ->
                ticketService.updateStatuses(admin, Set.of(ticket.getTicketId()), TicketStatus.CANCELLED));
        assertEquals(TicketStatus.CREATED, ticket.getStatus());
    }

    @Test
    void invalidTransitionIsReportedAndLeavesStatusUnchanged() {
        User admin = newAdmin();
        Ticket ticket = newTicket(testVehicle, "S-1");

        BulkTicketUpdateResult result = ticketService.updateStatuses(
                admin, Set.of(ticket.getTicketId()), TicketStatus.CLOSED);

        assertTrue(result.succeeded().isEmpty());
        assertTrue(result.failures().containsKey(ticket.getTicketId()));
        assertTrue(result.failures().get(ticket.getTicketId()).contains("Invalid status transition"));
        assertEquals(TicketStatus.CREATED, ticket.getStatus());
    }

    @Test
    void emptySelectionReturnsEmptyResult() {
        User admin = newAdmin();

        BulkTicketUpdateResult result =
                ticketService.updateStatuses(admin, Set.of(), TicketStatus.CANCELLED);

        assertTrue(result.succeeded().isEmpty());
        assertTrue(result.failures().isEmpty());
    }

    @Test
    void persistenceFailureRollsBackWholeBatch() {
        User admin = newAdmin();
        Ticket first = newTicket(testVehicle, "S-1");
        Ticket second = newTicket(testVehicle, "S-2");

        persistence.failAfterWritesForTests(0);

        assertThrows(IllegalStateException.class, () -> ticketService.updateStatuses(
                admin, Set.of(first.getTicketId(), second.getTicketId()), TicketStatus.CANCELLED));

        assertEquals(TicketStatus.CREATED, first.getStatus());
        assertEquals(TicketStatus.CREATED, second.getStatus());
        assertTrue(persistence.loadTickets().stream()
                .filter(t -> t.getTicketId().equals(first.getTicketId())
                        || t.getTicketId().equals(second.getTicketId()))
                .allMatch(t -> t.getStatus() == TicketStatus.CREATED));
    }
}
