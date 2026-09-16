package com.parking.exceptions;

/**
 * Exception thrown when a payment processing operation fails.
 */
public class PaymentFailedException extends Exception {
    
    public PaymentFailedException() {
        super("Payment processing failed");
    }
    
    public PaymentFailedException(String message) {
        super(message);
    }
}