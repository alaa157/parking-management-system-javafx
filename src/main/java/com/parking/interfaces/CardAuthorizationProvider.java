package com.parking.interfaces;

import com.parking.exceptions.PaymentFailedException;

/** Boundary for card authorization; real gateways can replace the local provider. */
public interface CardAuthorizationProvider {
    boolean authorize(String cardNumber, String expiryDate, String cvv, double amount)
            throws PaymentFailedException;
}
