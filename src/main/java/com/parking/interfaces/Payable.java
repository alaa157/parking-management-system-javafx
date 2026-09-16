package com.parking.interfaces;

import com.parking.exceptions.PaymentFailedException;

/**
 * Interface for different payment methods.
 * Implemented by CardPayment, CashPayment, WalletPayment, etc.
 */
public interface Payable {
    
    /**
     * Processes a payment transaction using this payment method.
     * 
     * @param amount the amount to be paid
     * @return true if payment is successful, false otherwise
     * @throws PaymentFailedException if payment processing fails
     */
    boolean processPayment(double amount) throws PaymentFailedException;
    
    /**
     * Validates the payment details before processing.
     * 
     * @return true if payment details are valid, false otherwise
     */
    boolean validatePaymentDetails();
    
    /**
     * Gets the type of payment method.
     * 
     * @return the payment method type as a string
     */
    String getPaymentType();
    
    /**
     * Processes a refund for a previous payment.
     * 
     * @param amount the amount to refund
     * @return true if refund is successful, false otherwise
     * @throws PaymentFailedException if refund processing fails
     */
    boolean processRefund(double amount) throws PaymentFailedException;
}