package com.parking;

import com.parking.enums.*;
import com.parking.exceptions.*;
import com.parking.model.*;
import com.parking.services.*;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TicketServiceTest extends TestFixtures {

    @Test
    void testCreateTicket() {
        ParkingSpot spot = garage.getSpotById("S-1");

        Ticket ticket = ticketService.createTicket(
                testVehicle,
                spot
        );

        assertNotNull(ticket);
        assertNotNull(ticket.getTicketId());

        assertTrue(
                ticket.getTicketId().startsWith("TICKET-")
        );

        assertEquals(
                testVehicle.getVehicleId(),
                ticket.getVehicleId()
        );

        assertEquals(
                spot.getSpotId(),
                ticket.getParkingSpotId()
        );

        assertEquals(
                TicketStatus.CREATED,
                ticket.getStatus()
        );

        assertNotNull(ticket.getEntryTime());
    }


    @Test
    void testTicketIdGeneration() {
        String id1 = ticketService.generateTicketId();
        String id2 = ticketService.generateTicketId();

        assertNotNull(id1);
        assertNotNull(id2);

        assertTrue(id1.startsWith("TICKET-"));
        assertTrue(id2.startsWith("TICKET-"));

        assertNotEquals(id1, id2);
    }


    @Test
    void testActivateTicket() {
        Ticket ticket = new Ticket(
                "TICKET-TEST",
                testVehicle.getVehicleId(),
                "S-1",
                testCustomer.getUserId()
        );

        ticket.activateTicket();

        assertEquals(
                TicketStatus.ACTIVE,
                ticket.getStatus()
        );
    }


    @Test
    void testCloseTicket() throws Exception {
        Ticket ticket = new Ticket(
                "TICKET-CLOSE",
                testVehicle.getVehicleId(),
                "S-1",
                testCustomer.getUserId()
        );

        ticket.setStatus(TicketStatus.ACTIVE);

        ticket.closeTicket();

        assertEquals(
                TicketStatus.ACTIVE,
                ticket.getStatus()
        );

        assertNull(ticket.getExitTime());
    }


    @Test
    void testActiveToClosedThrows() {
        Ticket ticket = new Ticket(
                "TICKET-ACTIVE-CLOSE",
                testVehicle.getVehicleId(),
                "S-1",
                testCustomer.getUserId()
        );

        ticket.setStatus(TicketStatus.ACTIVE);

        assertThrows(
                InvalidTicketStatusException.class,
                () -> ticketService.updateTicketStatus(
                        ticket,
                        TicketStatus.CLOSED
                )
        );
    }


    @Test
    void testPaidToClosedWorks() throws Exception {
        Ticket ticket = new Ticket(
                "TICKET-PAID-CLOSE",
                testVehicle.getVehicleId(),
                "S-1",
                testCustomer.getUserId()
        );

        ticket.setStatus(TicketStatus.PAID);
        ticketService.closeTicket(ticket);

        assertEquals(
                TicketStatus.CLOSED,
                ticket.getStatus()
        );
    }


    @Test
    void testPaidToRefundedWorks() throws Exception {
        Ticket ticket = new Ticket(
                "TICKET-PAID-REFUND",
                testVehicle.getVehicleId(),
                "S-1",
                testCustomer.getUserId()
        );

        ticket.setStatus(TicketStatus.PAID);
        ticket.setPaid(true);

        ticketService.updateTicketStatus(
                ticket,
                TicketStatus.REFUNDED
        );

        assertEquals(
                TicketStatus.REFUNDED,
                ticket.getStatus()
        );
        assertFalse(ticket.isPaid());
    }


    @Test
    void testTicketAmountCalculation() {
        Ticket ticket = new Ticket(
                "TICKET-AMOUNT",
                testVehicle.getVehicleId(),
                "S-1",
                testCustomer.getUserId()
        );

        LocalDateTime entry =
                LocalDateTime.now().minusMinutes(90);

        ticket.setEntryTime(entry);
        ticket.setExitTime(LocalDateTime.now());

        double amount = ticket.calculateAmount(5.0);

        // 90 minutes = 2 billable hours
        assertEquals(
                10.0,
                amount,
                0.001
        );
    }


    @Test
    void testTicketDuration() {
        Ticket ticket = new Ticket(
                "TICKET-DURATION",
                testVehicle.getVehicleId(),
                "S-1",
                testCustomer.getUserId()
        );

        ticket.setEntryTime(
                LocalDateTime.now().minusHours(2)
        );

        ticket.setExitTime(
                LocalDateTime.now()
        );

        assertTrue(
                ticket.getParkingDuration() >= 1.9
        );
    }


    @Test
    void testTicketExpiration() {
        Ticket ticket = new Ticket(
                "TICKET-EXP",
                testVehicle.getVehicleId(),
                "S-1",
                testCustomer.getUserId()
        );

        ticket.setStatus(TicketStatus.ACTIVE);

        ticket.setEntryTime(
                LocalDateTime.now().minusHours(5)
        );

        assertTrue(
                ticket.isExpired(4)
        );

        assertFalse(
                ticket.isExpired(10)
        );
    }


    @Test
    void testGetTicketById() throws Exception {
        ParkingSpot spot = garage.getSpotById("S-1");

        Ticket ticket = ticketService.createTicket(
                testVehicle,
                spot
        );

        Ticket found = ticketService.getTicketById(
                ticket.getTicketId()
        );

        assertNotNull(found);
        assertEquals(
                ticket.getTicketId(),
                found.getTicketId()
        );
    }


    @Test
    void testTicketNotFound() {
        assertThrows(
                TicketNotFoundException.class,
                () -> ticketService.getTicketById(
                        "DOES-NOT-EXIST"
                )
        );
    }


    @Test
    void testTicketStatusTransition() throws Exception {
        Ticket ticket = new Ticket(
                "TICKET-STATUS",
                testVehicle.getVehicleId(),
                "S-1",
                testCustomer.getUserId()
        );

        ticket.setStatus(TicketStatus.CREATED);

        ticketService.updateTicketStatus(
                ticket,
                TicketStatus.ACTIVE
        );

        assertEquals(
                TicketStatus.ACTIVE,
                ticket.getStatus()
        );
    }


    @Test
    void testInvalidTicketStatusTransition() {
        Ticket ticket = new Ticket(
                "TICKET-INVALID",
                testVehicle.getVehicleId(),
                "S-1",
                testCustomer.getUserId()
        );

        ticket.setStatus(TicketStatus.CLOSED);

        assertThrows(
                InvalidTicketStatusException.class,
                () -> ticketService.updateTicketStatus(
                        ticket,
                        TicketStatus.ACTIVE
                )
        );
    }


    @Test
    void testCloseTicketRejectsAwaitingPayment() throws Exception {
        // Enter and exit a vehicle without paying - ticket becomes AWAITING_PAYMENT
        Ticket ticket = parkingService.vehicleEntry(testVehicle);
        parkingService.vehicleExit(ticket);

        assertEquals(
                TicketStatus.AWAITING_PAYMENT,
                ticket.getStatus()
        );

        // Closing an unpaid ticket must be rejected, not silently succeed
        InvalidTicketStatusException ex = assertThrows(
                InvalidTicketStatusException.class,
                () -> ticketService.closeTicket(ticket)
        );
        assertTrue(ex.getMessage().contains("must be PAID"));

        // Ticket must remain AWAITING_PAYMENT, not be left in a partially-closed state
        assertEquals(
                TicketStatus.AWAITING_PAYMENT,
                ticket.getStatus()
        );
    }


}
