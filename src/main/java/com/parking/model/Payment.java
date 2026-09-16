package com.parking.model;

import com.parking.enums.PaymentStatus;
import com.parking.exceptions.PaymentFailedException;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import com.parking.interfaces.Payable;
import com.parking.util.Money;

/**
 * Represents a payment transaction in the parking management system.
 * This is the base class for all payment types.
 */
public class Payment implements Payable {
    private String paymentId;
    private String ticketId;
    private String customerId;
    private String garageId;
    private double amount;
    private LocalDateTime paymentTime;
    private PaymentStatus status;
    private String paymentMethod;
    private String transactionId;
    private double taxAmount;
    private double finalAmount;

    public Payment() {
        this.paymentTime = LocalDateTime.now();
        this.status = PaymentStatus.PENDING;
        this.amount = 0.0;
        this.taxAmount = 0.0;
        this.finalAmount = 0.0;
    }

    public Payment(String paymentId, String ticketId, String customerId, double amount, String paymentMethod) {
        this.paymentId = paymentId;
        this.ticketId = ticketId;
        this.customerId = customerId;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.paymentTime = LocalDateTime.now();
        this.status = PaymentStatus.PENDING;
        this.taxAmount = 0.0;
        this.finalAmount = amount;
    }

    /**
     * Processes the payment transaction.
     * 
     * @return true if payment was successful, false otherwise
     */
    public boolean processPayment() {
        try {
            return processPayment(getAmount());
        } catch (PaymentFailedException e) {
            return false;
        }
    }
    
    @Override
    public boolean processPayment(double amount) throws PaymentFailedException {
        if (amount <= 0) {
            throw new PaymentFailedException("Payment amount must be greater than 0");
        }

        // Keep the amount synchronized with the payment object.
        setAmount(amount);

        // Calculate final amount only if needed.
        calculateFinalAmount();

        setStatus(PaymentStatus.COMPLETED);

        if (transactionId == null || transactionId.isEmpty()) {
            transactionId = "TXN-" + System.currentTimeMillis();
        }

        return true;
    }

    @Override
    public boolean validatePaymentDetails() {
        // Generic/base Payment has no method-specific details to validate.
        return amount >= 0;
    }

    @Override
    public String getPaymentType() {
        return paymentMethod != null && !paymentMethod.isEmpty()
                ? paymentMethod
                : "GENERIC";
    }

    @Override
    public boolean processRefund(double amount) throws PaymentFailedException {
        if (!canBeRefunded()) {
            throw new PaymentFailedException("Payment cannot be refunded");
        }

        if (amount <= 0 || amount > getFinalAmount() || !Money.same(amount, getFinalAmount())) {
            throw new PaymentFailedException(
                "Only full refunds are allowed"
            );
        }

        setStatus(PaymentStatus.REFUNDED);
        return true;
    }

    /**
     * Refunds the payment transaction.
     * 
     * @return true if refund was successful, false otherwise
     */
    public boolean refundPayment() {
        if (!canBeRefunded()) {
            return false;
        }
        // Update the payment status and notes
        status = PaymentStatus.REFUNDED;
        return true;
    }

    /**
     * Calculates the final amount including tax and discounts.
     * If the final amount is negative, it is set to 0.
     * 
     * @return the final amount after adjustments
     */
    public double calculateFinalAmount() {
        // Validate the original amount
        if (amount <= 0) {
            return 0.0;
        }
        // Validate the tax amount
        if (taxAmount < 0) {
            taxAmount = 0;
        }
        // Calculate the final amount
        finalAmount = Money.round(amount + taxAmount);
        // Ensure the final amount is not negative
        if (finalAmount < 0) {
            finalAmount = 0;
        }
        
        return finalAmount;
    }

    /**
     * Checks if the payment is completed successfully.
     * 
     * @return true if payment status is COMPLETED
     */
    public boolean isSuccessful() {
        return status == PaymentStatus.COMPLETED;
    }

    /**
     * Checks if the payment can be refunded.
     * The payment status is COMPLETED or made within the last 14 days
     * 
     * @return true if payment can be refunded, false otherwise
     */
    public boolean canBeRefunded() {
        // Check if the payment status is COMPLETED
        if (status != PaymentStatus.COMPLETED) {
            return false;
        }
        if (paymentTime == null) {
            return false;
        }
        // Calculate the number of days since the payment was made
        long daysSincePayment = ChronoUnit.DAYS.between(paymentTime, LocalDateTime.now());
        // Check if the payment was made within the last 14 days
        return daysSincePayment >= 0 && daysSincePayment <= 14;
    }

    // Getters and Setters with validation
    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) {
        if (paymentId != null && !paymentId.isEmpty()) {
            this.paymentId = paymentId;
        }
    }
    
    public String getTicketId() { return ticketId; }
    public void setTicketId(String ticketId) {
        if (ticketId != null && !ticketId.isEmpty()) {
            this.ticketId = ticketId;
        }
    }
    
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) {
        if (customerId != null && !customerId.isEmpty()) {
            this.customerId = customerId;
        }
    }

    public String getGarageId() { return garageId; }
    public void setGarageId(String garageId) {
        if (garageId != null && !garageId.isBlank()) this.garageId = garageId;
    }
    
    public double getAmount() { return amount; }
    public void setAmount(double amount) {
        if (amount >= 0) {
            this.amount = amount;
        }
    }
    
    public LocalDateTime getPaymentTime() { return paymentTime; }
    public void setPaymentTime(LocalDateTime paymentTime) { this.paymentTime = paymentTime; }
    
    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }
    
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) {
        // Validate payment method not null or empty
        if (paymentMethod != null && !paymentMethod.isEmpty()) {
            this.paymentMethod = paymentMethod;
        }
    }
    
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    
    public double getTaxAmount() { return taxAmount; }
    public void setTaxAmount(double taxAmount) {
        // Validate tax amount is non-negative
        if (taxAmount >= 0) {
            this.taxAmount = taxAmount;
        }
    }
    
    public double getFinalAmount() { return finalAmount; }
    public void setFinalAmount(double finalAmount) {
        // Validate final amount is non-negative
        if (finalAmount >= 0) {
            this.finalAmount = finalAmount;
        }
    }

    @Override
    public String toString() {
        return "Payment{" +
                "paymentId='" + paymentId + '\'' +
                ", ticketId='" + ticketId + '\'' +
                ", amount=" + amount +
                ", status=" + status +
                ", paymentMethod='" + paymentMethod + '\'' +
                ", finalAmount=" + finalAmount +
                '}';
    }
}
