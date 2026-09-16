package com.parking.exceptions;

/**
 * Exception thrown when an operation attempts to perform an invalid status transition on a ticket.
 */
public class InvalidTicketStatusException extends Exception {
    
    public InvalidTicketStatusException() {
        super("Invalid ticket status operation");
    }
    
    public InvalidTicketStatusException(String message) {
        super(message);
    }
}