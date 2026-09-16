package com.parking.exceptions;

/**
 * Exception thrown when a requested parking spot is not available.
 */
public class SpotNotAvailableException extends Exception {
    
    public SpotNotAvailableException() {
        super("Parking spot is not available");
    }
    
    public SpotNotAvailableException(String message) {
        super(message);
    }
}