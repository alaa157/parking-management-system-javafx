package com.parking.exceptions;

/** Thrown when an actor is not allowed to perform an operation. */
public class AuthorizationException extends RuntimeException {
    public AuthorizationException(String message) {
        super(message);
    }
}
