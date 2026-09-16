package com.parking.model;

import com.parking.exceptions.PaymentFailedException;
import com.parking.enums.PaymentStatus;
import com.parking.interfaces.CardAuthorizationProvider;
import com.parking.security.LocalCardAuthorizationProvider;

import java.time.LocalDateTime;


/**
 * Represents a credit/debit card payment method.
 * Implements Payable interface for payment processing.
 */
public class CardPayment extends Payment {
    private String cardNumber;
    private String cardHolderName;
    private String expiryDate;
    private String cvv;
    private String cardType; // VISA, MASTERCARD, AMEX, etc.
    private boolean isVerified;
    private CardAuthorizationProvider authorizationProvider;

    public CardPayment() {
        super();
        this.isVerified = false;
        this.authorizationProvider = new LocalCardAuthorizationProvider();
    }

    public CardPayment(String paymentId, String ticketId, String customerId, double amount, 
                       String cardNumber, String cardHolderName, String expiryDate, String cvv) {
        super(paymentId, ticketId, customerId, amount, "CARD");
        this.cardNumber = cardNumber;
        this.cardHolderName = cardHolderName;
        this.expiryDate = expiryDate;
        this.cvv = cvv;
        this.isVerified = false;
        this.authorizationProvider = new LocalCardAuthorizationProvider();
    }

    /**
     * Processes a payment using the credit/debit card details.
     * 
     * @param amount the amount to be paid
     * @return true if payment is successful, false otherwise
     * @throws PaymentFailedException if payment processing fails
     */
    @Override
    public boolean processPayment(double amount) throws PaymentFailedException {
        // Validate card details before processing payment
        // Validate card details before processing payment
        if (!validatePaymentDetails()) {
            throw new PaymentFailedException("Invalid card details");
        }
        // Check if the amount is valid
        if (amount <= 0) {
            throw new PaymentFailedException("Amount must be greater than 0");
        }
        // Verify the card before processing payment
        if (!verifyCard(amount)) {
            throw new PaymentFailedException("Card verification failed");
        }
        // Keep only a masked PAN after successful verification.
        if (cardNumber != null && cardNumber.length() >= 4) {
            String last4 = cardNumber.substring(cardNumber.length() - 4);
            cardNumber = "**** **** **** " + last4;
        }
        // Set payment status to COMPLETED and generate transaction ID
        setStatus(com.parking.enums.PaymentStatus.COMPLETED);
        setTransactionId("CARD-" + System.currentTimeMillis());
        setCvv(null);
        return true;
    }

    /**
     * Validates the card payment details.
     * Checks if card number, expiry date, CVV, and card holder name are valid.
     * 
     * @return true if payment details are valid, false otherwise
     */
    @Override
    public boolean validatePaymentDetails() {
        // Check card number length and digits only (13-19 digits)
        if (cardNumber == null || !cardNumber.matches("\\d{13,19}")) {
            return false;
        }
        // Check Luhn checksum
        if (!passesLuhnCheck(cardNumber)) {
            return false;
        }
        // Check CVV format (3-4 digits)
        if (cvv == null || !cvv.matches("\\d{3,4}")) {
            return false;
        }
        // Check expiry date format (MM/YY)
        if (expiryDate == null || !expiryDate.matches("\\d{2}/\\d{2}")) {
            return false;
        }
        // Check expiry date is not in the past
        String[] parts = expiryDate.split("/");
        int month = Integer.parseInt(parts[0]);
        int year = Integer.parseInt(parts[1]);
        // Check month is valid (1-12)
        if (month < 1 || month > 12) return false;
        if (year < LocalDateTime.now().getYear() % 100 || (year == LocalDateTime.now().getYear() % 100 && month < LocalDateTime.now().getMonthValue())) {
            return false;
        }
        // Check card holder name is not empty
        if (cardHolderName == null || cardHolderName.isEmpty()) {
            return false;
        }
        return true;
    }

    private boolean passesLuhnCheck(String number) {
        int sum = 0;
        boolean doubleDigit = false;
        for (int i = number.length() - 1; i >= 0; i--) {
            int digit = number.charAt(i) - '0';
            if (doubleDigit) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubleDigit = !doubleDigit;
        }
        return sum % 10 == 0;
    }

    @Override
    public String getPaymentType() {
        return "Card";
    }

    /**
     * Processes a refund for the card payment.
     * 
     * @param amount the amount to be refunded
     * @return true if refund is successful, false otherwise
     * @throws PaymentFailedException if refund processing fails
     */
    @Override
    public boolean processRefund(double amount) throws PaymentFailedException {
        if (!canBeRefunded()) {
            throw new PaymentFailedException("Payment cannot be refunded");
        }
        if (amount != getFinalAmount()) {
            throw new PaymentFailedException("Only full refunds are allowed");
        }
        // Set payment status to REFUNDED
        setStatus(PaymentStatus.REFUNDED);
        return true;
    }

    /**
     * Verifies the card with the bank to ensure the card details are valid.
     * 
     * @return true if card is verified, false otherwise
     */
    public boolean verifyCard() {
        return verifyCard(getFinalAmount());
    }

    public boolean verifyCard(double amount) {
        // Validate card details before attempting to verify the card
        if (!validatePaymentDetails()) {
            return false;
        }
        // Detect the card type from the card number
        detectCardType();
        try {
            if (authorizationProvider == null
                    || !authorizationProvider.authorize(cardNumber, expiryDate, cvv, amount)) {
                return false;
            }
        } catch (PaymentFailedException e) {
            return false;
        }
        // Set flag to indicate that the card has been verified
        isVerified = true;
        return true;
    }

    /**
     * Masks the card number for display purposes, only showing the last 4 digits..
     * 
     * @return the masked card number (e.g., **** **** **** 1234)
     */
    public String getMaskedCardNumber() {
        if (cardNumber == null || cardNumber.length() < 4) {
            return "****";
        }
        // Get the last 4 digits of the card number
        String last4 = cardNumber.substring(cardNumber.length() - 4);
        // Return the masked card number
        return "**** **** **** " + last4;
    }

    /**
     * Detects the card type based on the card number.
     * 
     * @return the card type (VISA, MASTERCARD, AMEX, etc.)
     */
    public String detectCardType() {
        // Check if the card number is null or empty
        if (cardNumber == null || cardNumber.isEmpty()) {
            return null;
        }
        // Check if the card number starts with a 4
        // If so, it is a VISA card
        if (cardNumber.startsWith("4")) {
            cardType = "VISA";
        } 
        // Check if the card number starts with a 5
        // If so, it is a MASTERCARD card
        else if (cardNumber.startsWith("5")) {
            cardType = "MASTERCARD";
        } 
        // Check if the card number starts with a 3
        // If so, it is an AMEX card
        else if (cardNumber.startsWith("3")) {
            cardType = "AMEX";
        } 
        // If none of the above conditions are met, it is an OTHER card type
        else {
            cardType = "OTHER";
        }
        // Return the detected card type
        return cardType;
    }

    // Getters and Setters
    public void setCardNumber(String cardNumber) { this.cardNumber = cardNumber; }
    
    public String getCardHolderName() { return cardHolderName; }
    public void setCardHolderName(String cardHolderName) { this.cardHolderName = cardHolderName; }
    
    public String getExpiryDate() { return expiryDate; }
    public void setExpiryDate(String expiryDate) { this.expiryDate = expiryDate; }
    
    public void setCvv(String cvv) { this.cvv = cvv; }

    public void setAuthorizationProvider(CardAuthorizationProvider authorizationProvider) {
        this.authorizationProvider = authorizationProvider;
    }
    
    public String getCardType() { return cardType; }
    public void setCardType(String cardType) { this.cardType = cardType; }
    
    public boolean isVerified() { return isVerified; }
    public void setVerified(boolean verified) { isVerified = verified; }

    @Override
    public String toString() {
        return "CardPayment{" +
                "paymentId='" + getPaymentId() + '\'' +
                ", amount=" + getAmount() +
                ", cardType='" + cardType + '\'' +
                ", cardHolderName='" + cardHolderName + '\'' +
                ", maskedCardNumber='" + getMaskedCardNumber() + '\'' +
                ", isVerified=" + isVerified +
                '}';
    }
}
