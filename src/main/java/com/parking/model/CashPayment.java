package com.parking.model;

import com.parking.enums.PaymentStatus;

import java.util.UUID;

import com.parking.exceptions.PaymentFailedException;
import com.parking.util.Money;

/**
 * Represents a cash payment method.
 * Implements Payable interface for payment processing.
 */
public class CashPayment extends Payment {
    private double cashReceived;
    private double changeAmount;
    private String currency;
    private String receivedBy;

    public CashPayment() {
        super();
        this.currency = "USD";
        this.cashReceived = 0.0;
        this.changeAmount = 0.0;
    }

    public CashPayment(String paymentId, String ticketId, String customerId, double amount, 
                       double cashReceived, String receivedBy) {
        super(paymentId, ticketId, customerId, amount, "CASH");
        this.cashReceived = cashReceived;
        this.receivedBy = receivedBy;
        this.currency = "USD";
        this.changeAmount = 0.0;
    }

    /**
     * Processes a cash payment.
     * 
     * @param amount the amount to pay
     * @return true if payment is successful, false otherwise
     * @throws PaymentFailedException if payment processing fails
     */
    @Override
    public boolean processPayment(double amount) throws PaymentFailedException {
        // Validate cash amount is sufficient
        if (!validatePaymentDetails()) {
            throw new PaymentFailedException("Insufficient cash provided");
        }
        // Calculate change amount
        double change = calculateChange();
        // Update change amount
        changeAmount = change;
        // Update payment status
        setStatus(com.parking.enums.PaymentStatus.COMPLETED);
        // Generate transaction ID
        setTransactionId("CASH-" + UUID.randomUUID().toString().substring(0, 8));
        return true;
    }

    /**
     * Checks if the cash received is sufficient.
     * 
     * @return true if payment details are valid, false otherwise
     */
    @Override
    public boolean validatePaymentDetails() {
        // Check if cash is sufficient
        return isCashSufficient();
    }

    /**
     * Gets the type of payment method.
     * 
     * @return the payment method type as a string
     */
    @Override
    public String getPaymentType() {
        return "Cash";
    }

    /**
     * Refunds a cash payment.
     * 
     * @param amount the amount to refund
     * @return true if refund is successful, false otherwise
     * @throws PaymentFailedException if refund processing fails
     */
    @Override
    public boolean processRefund(double amount) throws PaymentFailedException {
        if (!canBeRefunded()) {
            throw new PaymentFailedException("Payment cannot be refunded");
        }
        if (amount <= 0 || amount > getFinalAmount() || !Money.same(amount, getFinalAmount())) {
            throw new PaymentFailedException("Only full refunds are allowed");
        }
        // Set payment status to REFUNDED
        setStatus(PaymentStatus.REFUNDED);
        return true;
    }


    /**
     * Calculates the change amount to return to the customer.
     * 
     * @return the change amount, or 0 if the customer paid with exact change
     */
    public double calculateChange() {
        // Calculate the difference between the cash received and the final amount due
        double change = cashReceived - getFinalAmount();
        // Ensure the change amount is not negative (i.e. exact change was provided)
        return change >= 0 ? change : 0;
    }

    /**
     * Checks if the customer paid with exact change.
     * 
     * @return true if exact change was provided
     */
    public boolean isExactChange() {
        return Math.abs(cashReceived - getFinalAmount()) < 0.01;
    }

    /**
     * Validates if the cash received is sufficient.
     * 
     * @return true if cashReceived >= amount due
     */
    public boolean isCashSufficient() {
        return Money.round(cashReceived) >= getFinalAmount();
    }

    // Getters and Setters
    public double getCashReceived() { return cashReceived; }
    public void setCashReceived(double cashReceived) { this.cashReceived = cashReceived; }
    
    public double getChangeAmount() { return changeAmount; }
    public void setChangeAmount(double changeAmount) { this.changeAmount = changeAmount; }
    
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    
    public String getReceivedBy() { return receivedBy; }
    public void setReceivedBy(String receivedBy) { this.receivedBy = receivedBy; }
    
    @Override
    public String toString() {
        return "CashPayment{" +
                "paymentId='" + getPaymentId() + '\'' +
                ", amount=" + getAmount() +
                ", cashReceived=" + cashReceived +
                ", changeAmount=" + changeAmount +
                ", receivedBy='" + receivedBy + '\'' +
                ", isExactChange=" + isExactChange() +
                '}';
    }
}
