package com.parking.security;

import com.parking.exceptions.PaymentFailedException;
import com.parking.interfaces.CardAuthorizationProvider;

/** Deterministic local provider for development and automated tests only. */
public final class LocalCardAuthorizationProvider implements CardAuthorizationProvider {
    @Override
    public boolean authorize(String cardNumber, String expiryDate, String cvv, double amount)
            throws PaymentFailedException {
        if (amount <= 0) {
            throw new PaymentFailedException("Amount must be greater than 0");
        }
        return true;
    }
}
