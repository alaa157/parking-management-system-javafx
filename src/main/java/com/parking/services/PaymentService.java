package com.parking.services;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import com.parking.config.AppConfig;
import com.parking.enums.NotificationType;
import com.parking.enums.PaymentStatus;
import com.parking.exceptions.PaymentFailedException;
import com.parking.exceptions.InvalidTicketStatusException;
import com.parking.enums.TicketStatus;
import com.parking.enums.SpotStatus;
import com.parking.exceptions.TicketNotFoundException;
import com.parking.exceptions.AuthorizationException;
import com.parking.exceptions.GarageAccessException;
import com.parking.enums.UserRole;
import com.parking.model.*;
import com.parking.util.Money;
import com.parking.persistence.PersistenceStore;


/**
 * Service class responsible for handling payment processing,
 * payment method validation, and transaction management.
 */
public class PaymentService {

    private ParkingGarage garage;
    private TicketService ticketService;
    private UserService userService;
    private ParkingService parkingService;
    private NotificationService notificationService;
    private List<Payment> payments = new ArrayList<>();
    private final PersistenceStore persistence;
    public PaymentService(ParkingGarage garage, UserService userService, TicketService ticketService) {
        this(garage, userService, ticketService, ticketService.getPersistenceStore());
    }

    public PaymentService(ParkingGarage garage, UserService userService, TicketService ticketService, PersistenceStore persistence) {
        this(garage, userService, ticketService, persistence, null);
    }

    public PaymentService(ParkingGarage garage, UserService userService, TicketService ticketService,
                          PersistenceStore persistence, ParkingService parkingService) {
        this.garage = garage;
        this.userService = userService;
        this.ticketService = ticketService;
        this.parkingService = parkingService;
        this.persistence = persistence;
        this.payments.addAll(persistence.loadPayments());
    }

    public PersistenceStore getPersistenceStore() {
        return persistence;
    }

    public void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public NotificationService getNotificationService() {
        return notificationService;
    }

    /**
     * Processes a payment for a parking ticket.
     * Calculates the amount, processes the payment, and updates the ticket status.
     * 
     * @param ticket the ticket to pay for
     * @param paymentMethod the payment method to use (implements Payable)
     * @return Payment object with transaction details
     * @throws PaymentFailedException if payment processing fails
     */
    public synchronized Payment processPayment(Ticket ticket, Payment payment) throws PaymentFailedException {
        if (ticket == null || payment == null) {
            throw new PaymentFailedException("Ticket and payment method must not be null");
        }
        validateGarageConsistency(ticket, payment);
        boolean completedPaymentExists = payments.stream()
                .anyMatch(existing -> ticket.getTicketId() != null
                        && ticket.getTicketId().equals(existing.getTicketId())
                && existing.getStatus() == PaymentStatus.COMPLETED);
        if (payment.getPaymentId() == null || payment.getPaymentId().isBlank()) {
            throw new PaymentFailedException("Payment ID is required");
        }
        if (payments.stream().anyMatch(existing -> payment.getPaymentId().equals(existing.getPaymentId()))) {
            throw new PaymentFailedException("Payment request has already been processed");
        }
        if ((ticket.getStatus() == TicketStatus.PAID || ticket.getStatus() == TicketStatus.CLOSED)
                && completedPaymentExists) {
            throw new PaymentFailedException("Ticket has already paid payment");
        }
        if (ticket.getStatus() != TicketStatus.AWAITING_PAYMENT) {
            throw new PaymentFailedException("Ticket must be awaiting payment");
        }

        ParkingSpot parkingSpot = getSpotForTicket(ticket);
        if (parkingSpot == null) {
            throw new PaymentFailedException("Could not find parking spot for ticket");
        }

        TicketStatus oldTicketStatus = ticket.getStatus();
        String oldTicketPaymentId = ticket.getPaymentId();
        double oldTicketAmount = ticket.getAmount();
        double oldTicketFinalAmount = ticket.getFinalAmount();
        boolean oldTicketPaid = ticket.isPaid();
        Vehicle parkedVehicle = garage == null ? null : garage.getVehicle(ticket.getVehicleId());
        boolean oldVehicleParked = parkedVehicle != null && parkedVehicle.isParked();
        String oldVehicleSpotId = parkedVehicle == null ? null : parkedVehicle.getParkingSpotId();
        String oldSpotVehicleId = parkingSpot.getVehicleId();
        SpotStatus oldSpotStatus = parkingSpot.getStatus();
        LocalDateTime oldPaymentTime = payment.getPaymentTime();
        PaymentStatus oldPaymentStatus = payment.getStatus();
        double oldPaymentAmount = payment.getAmount();
        double oldPaymentTax = payment.getTaxAmount();
        double oldPaymentFinalAmount = payment.getFinalAmount();

        double baseAmount = ticket.calculateAmount(parkingSpot);
        baseAmount = Money.round(baseAmount);
        double taxAmount = Money.percentage(baseAmount, AppConfig.taxRate());
        double finalAmount = Money.round(baseAmount + taxAmount);
        // Set calculated amounts on the EXISTING concrete payment object
        payment.setAmount(baseAmount);
        payment.setTaxAmount(taxAmount);
        payment.setFinalAmount(finalAmount);
        // Process the actual payment (this sets subclass-specific fields like changeAmount, maskedCardNumber, etc.)
        boolean processed;
        try {
            processed = payment.processPayment(finalAmount);
        } catch (PaymentFailedException ex) {
            payment.setStatus(PaymentStatus.FAILED);
            payments.add(payment);
            persistence.savePayment(payment);
            throw ex;
        }
        if (!processed) {
            payment.setStatus(PaymentStatus.FAILED);
            payments.add(payment);
            persistence.savePayment(payment);
            throw new PaymentFailedException("Payment processing failed for ticket " + ticket.getTicketId());
        }
        /* The ticket, payment, close, and release writes are committed below
         * as one transaction. */
        try {
            persistence.inTransaction(() -> {
                if (!ticket.processPayment(payment.getPaymentId(), finalAmount)) {
                    throw new PaymentFailedException("Ticket payment failed for ticket " + ticket.getTicketId());
                }
                if (payment.getPaymentTime() == null) payment.setPaymentTime(LocalDateTime.now());
                payment.setStatus(PaymentStatus.COMPLETED);
                payments.add(payment);
                persistence.savePayment(payment);
                if (payment instanceof WalletPayment wallet && wallet.getCustomer() != null) {
                    persistence.saveUser(wallet.getCustomer());
                }
                ticketService.closeTicket(ticket);
                if (parkingService != null) parkingService.releaseAfterPayment(ticket);
                persistence.savePayment(payment);
                persistence.audit("PAYMENT_COMPLETED", payment.getPaymentId(), payment.getCustomerId(), true,
                        "ticket=" + payment.getTicketId() + ";method=" + payment.getPaymentType());
                publishPaymentNotification(payment, ticket);
                return null;
            });
        } catch (Exception failure) {
            payments.remove(payment);
            ticket.setPaymentId(oldTicketPaymentId);
            ticket.setAmount(oldTicketAmount);
            ticket.setFinalAmount(oldTicketFinalAmount);
            ticket.setStatus(oldTicketStatus);
            ticket.setPaid(oldTicketPaid);
            if (parkedVehicle != null) {
                parkedVehicle.setParked(oldVehicleParked);
                parkedVehicle.setParkingSpotId(oldVehicleSpotId);
                if (oldVehicleParked) garage.registerVehicle(parkedVehicle);
            }
            if (parkingSpot != null) {
                parkingSpot.setVehicleId(oldSpotVehicleId);
                parkingSpot.setStatus(oldSpotStatus);
                garage.updateAvailability();
            }
            payment.setPaymentTime(oldPaymentTime);
            payment.setStatus(oldPaymentStatus);
            payment.setAmount(oldPaymentAmount);
            payment.setTaxAmount(oldPaymentTax);
            payment.setFinalAmount(oldPaymentFinalAmount);
            if (failure instanceof PaymentFailedException paymentFailure) throw paymentFailure;
            if (failure instanceof RuntimeException runtime) {
                throw new PaymentFailedException("Could not complete payment: " + runtime.getMessage());
            }
            throw new PaymentFailedException("Could not complete payment: " + failure.getMessage());
        }
        return payment;
    }

    public Payment processPayment(User actor, Ticket ticket, Payment payment) throws PaymentFailedException {
        requirePaymentAccess(actor, ticket);
        return processPayment(ticket, payment);
    }

    private void validateGarageConsistency(Ticket ticket, Payment payment) throws PaymentFailedException {
        if (ticket.getGarageId() == null || ticket.getGarageId().isBlank()) {
            throw new PaymentFailedException("Ticket has no garage ownership");
        }
        if (payment.getGarageId() == null) payment.setGarageId(ticket.getGarageId());
        if (!ticket.getGarageId().equals(payment.getGarageId())) {
            throw new PaymentFailedException("Payment garage must match ticket garage");
        }
    }

    /**
     * Helper method to get the parking spot for a ticket
     * 
     * @param ticket the ticket to get the spot for
     * @return the ParkingSpot object, or null if not found
     */
    private ParkingSpot getSpotForTicket(Ticket ticket) {
        if (garage == null || ticket == null || ticket.getParkingSpotId() == null) {
            return null;
        }
        return garage.getSpotById(ticket.getParkingSpotId());
    }


    /**
     * Calculates the parking fee based on entry and exit times.
     * 
     * @param entryTime the time vehicle entered
     * @param exitTime the time vehicle exited
     * @param spot the ParkingSpot object
     * @return calculated amount
     */
    public double calculateParkingFee(LocalDateTime entryTime, LocalDateTime exitTime, ParkingSpot spot) {
        if (entryTime == null || exitTime == null) {
            return 0.0;
        }
        // Calculate duration in minutes
        long durationMinutes = ChronoUnit.MINUTES.between(entryTime, exitTime);
        // Convert to hours (round up for partial hours) 
        double durationHours = Math.ceil(durationMinutes / 60.0);
        // If duration is 0 or negative, charge minimum 1 hour
        if (durationHours <= 0) {
            durationHours = 1.0;
        }
        // Apply hourly rate
        double amount = durationHours * spot.getHourlyRate();
        // Return calculated amount
        return amount;
    }

    /**
     * Validates a card payment method.
     * Checks card number, expiry date, and CVV.
     * 
     * @param cardPayment the card payment to validate
     * @return true if card is valid, false otherwise
     */
    public boolean validateCardPayment(CardPayment cardPayment) {
        if (cardPayment == null) {
            return false;
        } 
        // Return validation result
        return cardPayment.validatePaymentDetails();
    }

    /**
     * Validates a wallet payment method.
     * Checks if wallet has sufficient balance.
     * 
     * @param walletPayment the wallet payment to validate
     * @return true if wallet is valid, false otherwise
     */
    public boolean validateWalletPayment(WalletPayment walletPayment) {
        if (walletPayment == null) {
            return false;
        }
        // Return validation result
        return walletPayment.validatePaymentDetails();
    }

    /**
     * Validates a cash payment method.
     * Ensures cash amount is sufficient.
     * 
     * @param cashPayment the cash payment to validate
     * @return true if cash is sufficient, false otherwise
     */
    public boolean validateCashPayment(CashPayment cashPayment) {
        if (cashPayment == null) {
            return false;
    } 
        // Return validation result
        return cashPayment.validatePaymentDetails();
    }

    /**
     * Refunds a payment transaction.
     * 
     * @param payment the payment to refund
     * @return true if refund is successful, false otherwise
     * @throws PaymentFailedException if refund processing fails
     */
    public boolean refundPayment(Payment payment) throws PaymentFailedException {
        if (payment == null) {
            throw new PaymentFailedException("Payment cannot be null");
        }
        // Check if payment.canBeRefunded() returns true
        if (!payment.canBeRefunded()) {
            throw new PaymentFailedException("Payment cannot be refunded");
        }
        // Process refund based on payment method type
        try {
            double capturedAmount = Money.round(payment.getFinalAmount());
            if (capturedAmount <= 0) {
                throw new PaymentFailedException("Captured amount must be greater than zero");
            }
            double refundAmount = capturedAmount;
            boolean refunded = false;
            // Try to cast to concrete payment types and call their processRefund
            if (payment instanceof CardPayment) {
                refunded = ((CardPayment) payment).processRefund(refundAmount);
            } 
            else if (payment instanceof CashPayment) {
                refunded = ((CashPayment) payment).processRefund(refundAmount);
            }
            else if (payment instanceof WalletPayment) {
                refunded = ((WalletPayment) payment).processRefund(refundAmount);
            }
            else {
                refunded = payment.refundPayment();
            }

            if (!refunded) {
                throw new PaymentFailedException("Refund processing failed");
            }
            payment.setStatus(PaymentStatus.REFUNDED);
            boolean persistedPayment = payments.contains(payment);
            if (persistedPayment) {
                persistence.savePayment(payment);
                persistence.audit("PAYMENT_REFUNDED", payment.getPaymentId(), payment.getCustomerId(), true,
                        "ticket=" + payment.getTicketId());
            }
            // Reverse the ticket's paid state
            if (ticketService != null) {
                try {
                    Ticket ticket = ticketService.getTicketById(payment.getTicketId());
                    if (!TicketStatus.isValidTransition(ticket.getStatus(), TicketStatus.REFUNDED)) {
                        throw new PaymentFailedException("Ticket cannot be refunded from status " + ticket.getStatus());
                    }
                    ticketService.updateTicketStatus(ticket, TicketStatus.REFUNDED);
                } catch (TicketNotFoundException e) {
                }
            }
            publishRefundNotification(payment);
            return true;
        } catch (Exception e) {
            throw new PaymentFailedException("Refund processing failed: " + e.getMessage());
        }
    }

    public boolean refundPayment(User actor, Payment payment) throws PaymentFailedException {
        if (payment == null) throw new PaymentFailedException("Payment cannot be null");
        if (actor == null || !actor.isActive()) {
            throw new AuthorizationException("An active authenticated user is required.");
        }
        if (actor.getRole() != UserRole.ADMIN && actor.getRole() != UserRole.ATTENDANT
                && !actor.getUserId().equals(payment.getCustomerId())) {
            throw new AuthorizationException("You are not authorized to refund this payment.");
        }
        return refundPayment(payment);
    }

    private void publishPaymentNotification(Payment payment, Ticket ticket) {
        if (notificationService == null) return;
        String recipientId = payment.getCustomerId();
        if (recipientId == null || recipientId.isBlank()) recipientId = ticket.getUserId();
        if (recipientId == null || recipientId.isBlank()) return;
        notificationService.publish(recipientId, NotificationType.PAYMENT_SUCCESS,
                "Payment " + payment.getPaymentId() + " completed for ticket " + payment.getTicketId()
                        + ": $" + String.format("%.2f", payment.getFinalAmount()) + ".");
    }

    private void publishRefundNotification(Payment payment) {
        if (notificationService == null) return;
        String recipientId = payment.getCustomerId();
        if (recipientId == null || recipientId.isBlank()) return;
        notificationService.publish(recipientId, NotificationType.PAYMENT_SUCCESS,
                "Payment " + payment.getPaymentId() + " refunded for ticket " + payment.getTicketId()
                        + ": $" + String.format("%.2f", payment.getFinalAmount()) + ".");
    }

    private void requirePaymentAccess(User actor, Ticket ticket) throws PaymentFailedException {
        if (actor == null || !actor.isActive()) {
            throw new AuthorizationException("An active authenticated user is required.");
        }
        if (ticket == null) throw new PaymentFailedException("Ticket cannot be null");
        if (ticket.getGarageId() == null || (actor.getRole() != UserRole.ADMIN
                && !persistence.hasGarageAccess(actor.getUserId(), ticket.getGarageId()))) {
            throw new GarageAccessException("User does not have access to ticket garage");
        }
        if (actor.getRole() != UserRole.ADMIN && actor.getRole() != UserRole.ATTENDANT
                && !actor.getUserId().equals(ticket.getUserId())) {
            throw new AuthorizationException("You are not authorized to pay this ticket.");
        }
    }

    /**
     * Credits the refunded amount to the paying customer's in-app wallet,
     * for payment methods (cash/card) that don't already manage a wallet balance.
     *
     * @param payment the payment being refunded
     * @param refundAmount the amount to credit back
     */
    /**
     * Generates a payment receipt for a completed transaction.
     * 
     * @param payment the completed payment
     * @return formatted receipt string
     */
    public String generateReceipt(Payment payment) {
        if (payment == null) {
            return "";
        }
 
        // Format payment details into receipt
        StringBuilder receipt = new StringBuilder();
        receipt.append("========================================\n");
        receipt.append("         PARKING PAYMENT RECEIPT\n");
        receipt.append("========================================\n\n");
        
        receipt.append("Payment ID:        ").append(payment.getPaymentId()).append("\n");
        receipt.append("Transaction ID:    ").append(payment.getTransactionId()).append("\n");
        receipt.append("Ticket ID:         ").append(payment.getTicketId()).append("\n");
        receipt.append("Customer ID:       ").append(payment.getCustomerId()).append("\n");
        receipt.append("\n");
        
        receipt.append("Payment Method:    ").append(payment.getPaymentMethod()).append("\n");
        receipt.append("Payment Time:      ").append(payment.getPaymentTime()).append("\n");
        receipt.append("Status:            ").append(payment.getStatus()).append("\n");
        receipt.append("\n");
        
        receipt.append("Amount:            $").append(String.format("%.2f", payment.getAmount())).append("\n");
        receipt.append("Tax (").append((int) (AppConfig.taxRate() * 100)).append("%):         $")
                .append(String.format("%.2f", payment.getTaxAmount())).append("\n");
        receipt.append("Final Amount:      $").append(String.format("%.2f", payment.getFinalAmount())).append("\n");
        receipt.append("\n");
        
        receipt.append("========================================\n");
        receipt.append("        Thank you for your visit!\n");
        receipt.append("========================================\n");
 
        // Return formatted receipt
        return receipt.toString();
    }

    /**
     * Gets the total revenue from all completed payments.
     * 
     * @return total revenue
     */
    public double getTotalRevenue() {
        double total = 0.0;
        // Iterate over payments and calculate total revenue  
        for (Payment payment : payments) {
            if (payment.getStatus() == PaymentStatus.COMPLETED) {
                total += payment.getFinalAmount();
            }
        }
        return total;
    }
    
    /**
     * Gets the payment status for a specific ticket.
     * 
     * @param ticket the ticket to check
     * @return PaymentStatus of the ticket
     */
    public PaymentStatus getPaymentStatus(Ticket ticket) {
        if (ticket == null) {
            return PaymentStatus.PENDING;
        }
 
        Payment latestAny = null;
        Payment latestCompleted = null;
        for (Payment payment : payments) {
            if (ticket.getTicketId() != null && ticket.getTicketId().equals(payment.getTicketId())) {
                latestAny = payment;
                if (payment.getStatus() == PaymentStatus.COMPLETED) {
                    latestCompleted = payment;
                }
            }
        }

        Payment latest = latestCompleted != null ? latestCompleted : latestAny;
        return latest == null ? PaymentStatus.PENDING : latest.getStatus();
    }

    /**
     * Gets all payments processed
     * 
     * @return list of all payments
     */
    public List<Payment> getAllPayments() {
        return new ArrayList<>(payments);
    }

    public List<Payment> getPaymentsFor(User actor) {
        if (actor == null || !actor.isActive()) {
            throw new AuthorizationException("An active authenticated user is required.");
        }
        if (actor.getRole() == UserRole.ADMIN || actor.getRole() == UserRole.ATTENDANT) {
            return getAllPayments();
        }
        List<Payment> result = new ArrayList<>();
        for (Payment payment : payments) {
            if (actor.getUserId().equals(payment.getCustomerId())) result.add(payment);
        }
        return result;
    }

    public List<Payment> findPayments(User actor, String garageId) {
        if (actor == null || !actor.isActive()) {
            throw new AuthorizationException("An active authenticated user is required.");
        }
        if (actor.getRole() == UserRole.ADMIN && garageId == null) return getAllPayments();
        if (garageId == null || !persistence.loadGarage(garageId).map(g -> !g.isArchived()).orElse(false)) {
            throw new GarageAccessException("Garage access is required");
        }
        if (actor.getRole() != UserRole.ADMIN && !persistence.hasGarageAccess(actor.getUserId(), garageId)) {
            throw new GarageAccessException("User does not have access to garage: " + garageId);
        }
        return getAllPayments().stream()
                .filter(payment -> garageId.equals(payment.getGarageId()))
                .filter(payment -> actor.getRole() == UserRole.ADMIN || actor.getRole() == UserRole.ATTENDANT
                        || actor.getUserId().equals(payment.getCustomerId()))
                .toList();
    }

    /**
     * Gets payment by payment ID
     * 
     * @param paymentId the payment ID to search for
     * @return Payment object or null if not found
     */
    public Payment getPaymentById(String paymentId) {
        for (Payment payment : payments) {
            if (payment.getPaymentId().equals(paymentId)) {
                return payment;
            }
        }
        return null;
    }
}
