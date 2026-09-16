package com.parking.enums;

/**
 * Enum representing the status of a parking ticket throughout its lifecycle.
 */
public enum TicketStatus {
    CREATED,
    ACTIVE,
    CLOSED,
    CANCELLED,
    AWAITING_PAYMENT,
    PAID,
    REFUNDED;
    /**
     * Validates if a status transition is allowed.
     * Called from TicketService.updateTicketStatus().
     * 
     * @param currentStatus the current ticket status
     * @param newStatus the desired new status
     * @return true if the transition is valid, false otherwise
     */
    public static boolean isValidTransition(TicketStatus currentStatus, TicketStatus newStatus) {
        // CANCELLED and REFUNDED are terminal; CLOSED may be refunded within the payment refund window.
        // A ticket can only close after successful payment: PAID -> CLOSED.
        // A paid or closed ticket may be refunded while the payment is still refundable.
        switch (currentStatus) {
            case CREATED:
                return newStatus == ACTIVE || newStatus == CANCELLED;
            case ACTIVE:
                return newStatus == AWAITING_PAYMENT || newStatus == CANCELLED;
            case AWAITING_PAYMENT:
                return newStatus == PAID || newStatus == CANCELLED;
            case PAID:
                return newStatus == CLOSED || newStatus == REFUNDED;
            case CLOSED:
                return newStatus == REFUNDED;
            case CANCELLED:
            case REFUNDED:
            default:
                return false;
        }
    }
}