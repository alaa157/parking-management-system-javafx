package com.parking.model;

import com.parking.exceptions.PaymentFailedException;
import com.parking.enums.PaymentStatus;

import java.util.UUID;
import com.parking.util.Money;

/**
 * Represents a digital wallet payment method.
 * Implements Payable interface for payment processing.
 */
public class WalletPayment extends Payment {
    private String walletId;
    private String walletProvider; // PayPal, Google Pay, Apple Pay, etc.
    private String walletEmail;
    private boolean isWalletActive;
    private Customer customer;

    public WalletPayment() {
        super();
        this.isWalletActive = true;
    }

    public WalletPayment(String paymentId, String ticketId, String customerId, double amount,
                         String walletId, String walletProvider, String walletEmail) {
        super(paymentId, ticketId, customerId, amount, "WALLET");
        this.walletId = walletId;
        this.walletProvider = walletProvider;
        this.walletEmail = walletEmail;
        this.isWalletActive = true;
    }

    public WalletPayment(String paymentId, String ticketId, String customerId, double amount,
                         String walletId, String walletProvider, String walletEmail, Customer customer) {
        super(paymentId, ticketId, customerId, amount, "WALLET");
        this.walletId = walletId;
        this.walletProvider = walletProvider;
        this.walletEmail = walletEmail;
        this.isWalletActive = true;
        this.customer = customer;
    }

    /**
     * Processes a payment using the wallet payment method.
     * 
     * @param amount the amount to be paid
     * @return true if payment is successful, false otherwise
     * @throws PaymentFailedException if payment processing fails
     */
    @Override
    public boolean processPayment(double amount) throws PaymentFailedException {
        // Check if wallet is active
        if (!isWalletActive) {
            throw new PaymentFailedException("Wallet is not active");
        }
        // Validate wallet details
        if (!validatePaymentDetails()) {
            throw new PaymentFailedException("Invalid wallet details");
        }
        // Validate customer wallet
        if (customer == null) {
            throw new PaymentFailedException("Customer not linked to wallet");
        }
        // Check if customer has sufficient balance
        if (!customer.hasSufficientBalance(amount)) {
            throw new PaymentFailedException("Insufficient wallet balance. Current balance: $" + 
                                            String.format("%.2f", customer.getWalletBalance()));
        }
        // Deduct funds from customer wallet
        if (!customer.deductWalletBalance(amount)) {
            throw new PaymentFailedException("Failed to deduct from customer wallet");
        }
        // Set payment status to COMPLETED and generate transaction ID
        setStatus(PaymentStatus.COMPLETED);
        setTransactionId("WALLET-" + UUID.randomUUID().toString().substring(0, 8));
        return true;
    }

    /**
     * Validates the wallet payment details..
     * 
     * @return true if payment details are valid, false otherwise
     */
    @Override
    public boolean validatePaymentDetails() {
        // Check if wallet ID is not empty
        if (walletId == null || walletId.isEmpty()) {
            return false;
        }
        // Check if wallet provider is not empty
        if (walletProvider == null || walletProvider.isEmpty()) {
            return false;
        }
        // Check if wallet email is not empty
        if (walletEmail == null || walletEmail.isEmpty()) {
            return false;
        }
        if (customer == null) {
            return false;
        }
        // Check if wallet balance is greater than or equal to 0
        return isWalletActive;
    }
 
    /**
     * Gets the type of payment method.
     * 
     * @return the payment method type as a string
     */
    @Override
    public String getPaymentType() {
        return "Wallet";
    }
 
    /**
     * Processes a refund for the payment transaction.
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
        if (customer == null) {
            throw new PaymentFailedException("Customer not linked to wallet");
        }
        if (amount <= 0 || amount > getFinalAmount() || !Money.same(amount, getFinalAmount())) {
            throw new PaymentFailedException(
                    "Only full refunds are allowed"
            );
        }
        // Add the refund to customer's wallet
        customer.addWalletBalance(amount);
        // Update the payment status
        setStatus(PaymentStatus.REFUNDED);
        return true;
    }
 
    /**
     * Deactivates the wallet.
     */
    public void deactivateWallet() {
        isWalletActive = false;
    }

    /**
     * Activates the wallet.
     */
    public void activateWallet() {
        isWalletActive = true;
    }

    // Getters and Setters
    public String getWalletId() { return walletId; }
    public void setWalletId(String walletId) { this.walletId = walletId; }
    
    public String getWalletProvider() { return walletProvider; }
    public void setWalletProvider(String walletProvider) { this.walletProvider = walletProvider; }
    
    public String getWalletEmail() { return walletEmail; }
    public void setWalletEmail(String walletEmail) { this.walletEmail = walletEmail; }
    
    public boolean isWalletActive() { return isWalletActive; }
    public void setWalletActive(boolean walletActive) { isWalletActive = walletActive; }

    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }

    @Override
    public String toString() {
        return "WalletPayment{" +
                "paymentId='" + getPaymentId() + '\'' +
                ", amount=" + getAmount() +
                ", walletProvider='" + walletProvider + '\'' +
                ", walletEmail='" + walletEmail + '\'' +
                ", isWalletActive=" + isWalletActive +
                '}';
    }
}
