package com.parking.exceptions;

/**
 * Exception thrown when a requested ticket cannot be found in the system.
 */
public class TicketNotFoundException extends Exception {
    
    public TicketNotFoundException() {
        super("Ticket not found");
    }
    
    public TicketNotFoundException(String message) {
        super(message);
    }
}