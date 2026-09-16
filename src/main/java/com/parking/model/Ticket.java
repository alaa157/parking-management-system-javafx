package com.parking.model;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import com.parking.enums.TicketStatus;
import com.parking.config.AppConfig;

/**
 * Represents a parking ticket issued to a vehicle entering the garage.
 * Implements Trackable interface for location and status tracking.
 */
public class Ticket {
    private String ticketId;
    private String vehicleId;
    private String parkingSpotId;
    private String userId;
    private String garageId;
    private LocalDateTime entryTime;
    private LocalDateTime exitTime;
    private TicketStatus status;
    private double amount;
    private double finalAmount;
    private boolean isPaid;
    private String paymentId;

    public Ticket() {
        this.status = TicketStatus.CREATED;
        this.entryTime = LocalDateTime.now();
        this.isPaid = false;
        this.amount = 0.0;
    }

    public Ticket(String ticketId, String vehicleId, String parkingSpotId, String userId) {
        this.ticketId = ticketId;
        this.vehicleId = vehicleId;
        this.parkingSpotId = parkingSpotId;
        this.userId = userId;
        this.status = TicketStatus.CREATED;
        this.entryTime = LocalDateTime.now();
        this.isPaid = false;
        this.amount = 0.0;
    }

    public Ticket(String ticketId, String vehicleId, String parkingSpotId, String userId, String garageId) {
        this(ticketId, vehicleId, parkingSpotId, userId);
        setGarageId(garageId);
    }

    /**
     * Activates the ticket when the vehicle enters the garage.
     */
    public void activateTicket() {
        // Check if the ticket is already active or closed
        if (status != TicketStatus.CREATED) {
            return;
        }
        // Set status to active and update lastUpdatedTime
        status = TicketStatus.ACTIVE;
    }

    /**
     * Closes the ticket when the vehicle exits.
     */
    public void closeTicket() {
        // A ticket can only be closed after successful payment.
        if (status != TicketStatus.PAID) {
            return;
        }
        // Exit time is frozen at vehicle exit and must not be changed at close time.
        if (exitTime == null) {
            exitTime = LocalDateTime.now();
        }
        status = TicketStatus.CLOSED;
    }

    /**
     * Processes payment for the ticket.
     *
     * @param paymentId the ID of the payment
     * @param amountPaid the amount paid
     * @return true if payment was successful, false otherwise
     */
    public boolean processPayment(String paymentId, double amountPaid) {
        // Check if paymentId is null or empty
        if (paymentId == null || paymentId.isEmpty()) {
            return false;
        }
        // check if amountPaid is less than or equal to 0
        if (amountPaid <= 0) {
            return false;
        }
        // Check if ticket can accept payment
        // Only AWAITING_PAYMENT tickets can be paid
        if (status != TicketStatus.AWAITING_PAYMENT) {
            return false;
        }
        double expectedAmount = finalAmount > 0 ? finalAmount : this.amount;
        if (amountPaid < expectedAmount) {
            return false;
        }
        // Set paymentId, mark ticket as paid, update status to PAID, and update lastUpdatedTime
        this.paymentId = paymentId;
        this.finalAmount = amountPaid;
        isPaid = true;
        status = TicketStatus.PAID;
        return true;
    }

    /**
     * Calculates the total amount due for the ticket.
     *
     * @param hourlyRate the hourly rate to use for calculation
     * @return total amount due
     */
    public double calculateAmount(double hourlyRate) {
        if (hourlyRate <= 0) {
            return 0.0;
        }
        // Once exit time is set, the amount was frozen at vehicle exit.
        if (exitTime != null && amount > 0.0) {
            return amount;
        }

        // Apply the configured grace period before the one-hour minimum.
        long durationMinutes = entryTime == null ? 0 : ChronoUnit.MINUTES.between(entryTime,
                exitTime == null ? LocalDateTime.now() : exitTime);
        if (durationMinutes > 0 && durationMinutes <= AppConfig.freeParkingMinutes()) {
            this.amount = 0.0;
            return 0.0;
        }
        double duration = durationMinutes / 60.0;
        double roundedDuration = Math.max(1.0, Math.ceil(duration));
        double calculatedAmount = roundedDuration * hourlyRate;
        this.amount = calculatedAmount;
        return calculatedAmount;
    }

    /**
     * Calculate the total amount due for the ticket using the hourly rate of the parking spot it was parked in.
     *
     * @param spot the parking spot containing the hourly rate
     * @return total amount due
     */
    public double calculateAmount(ParkingSpot spot) {
        // If the parking spot is null, return a default amount of 0.0
        if (spot == null) {
            return 0.0;
        }
        // Calculate the total amount due using the hourly rate of the parking spot
        return calculateAmount(spot.getHourlyRate());
    }

    /**
     * Calculates the parking duration in hours.
     *
     * @return parking duration in hours
     */
    public double getParkingDurationInHoursRoundedup() {
        // If entry time is null, return 0.0 hours
        if (entryTime == null || (exitTime == null && status == TicketStatus.CLOSED)) {
            return 0.0;
        }
        // Calculate duration in minutes between entry time and exit time
        // If exit time is null, use current time
        LocalDateTime endTime = exitTime != null ? exitTime : LocalDateTime.now();
        long durationMinutes = ChronoUnit.MINUTES.between(entryTime, endTime);
        // Convert duration in minutes to hours and return
        double durationHours = Math.ceil(durationMinutes / 60.0);
        return durationHours;
    }

    /**
     * Checks if the ticket is expired.
     *
     * @param maxDurationHours maximum allowed duration in hours
     * @return true if ticket is expired, false otherwise
     */
    public boolean isExpired(int maxDurationHours) {
        if (maxDurationHours <= 0) {
            return false;
        }
        if (status != TicketStatus.ACTIVE) {
            return false;
        }
        double duration = getParkingDuration();
        return duration > maxDurationHours;
    }

    /**
     * Gets the total duration the vehicle has been parked.
     *
     * @return duration in hours
     */
    public double getParkingDuration() {
        if (entryTime == null) {
            return 0.0;
        }
        LocalDateTime endTime = exitTime != null ? exitTime : LocalDateTime.now();
        long durationMinutes = ChronoUnit.MINUTES.between(entryTime, endTime);
        return durationMinutes / 60.0;
    }

    /**
     * Reverses a completed payment on this ticket after a refund.
     * Marks the ticket as unpaid and transitions it to REFUNDED.
     */
    public void markRefunded() {
        if (!isPaid) {
            return; // nothing to refund
        }
        this.isPaid = false;
        this.status = TicketStatus.REFUNDED;
    }

    // Getters and Setters
    public String getTicketId() { return ticketId; }
    public void setTicketId(String ticketId) { this.ticketId = ticketId; }

    public String getVehicleId() { return vehicleId; }
    public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }

    public String getParkingSpotId() { return parkingSpotId; }
    public void setParkingSpotId(String parkingSpotId) { this.parkingSpotId = parkingSpotId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    /** The garage is assigned at creation and may only be restored to the same value. */
    public String getGarageId() { return garageId; }
    public void setGarageId(String garageId) {
        if (garageId == null || garageId.isBlank()) throw new IllegalArgumentException("garageId cannot be blank");
        if (this.garageId != null && !this.garageId.equals(garageId)) {
            throw new IllegalArgumentException("ticket garage cannot change");
        }
        this.garageId = garageId;
    }

    public LocalDateTime getEntryTime() { return entryTime; }
    public void setEntryTime(LocalDateTime entryTime) { this.entryTime = entryTime; }

    public LocalDateTime getExitTime() { return exitTime; }
    public void setExitTime(LocalDateTime exitTime) { this.exitTime = exitTime; }

    public TicketStatus getStatus() { return status; }
    public void setStatus(TicketStatus status) { this.status = status; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public double getFinalAmount() { return finalAmount; }
    public void setFinalAmount(double finalAmount) { this.finalAmount = finalAmount; }

    public boolean isPaid() { return isPaid; }
    public void setPaid(boolean paid) { isPaid = paid; }

    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }

    @Override
    public String toString() {
        return "Ticket{" +
                "ticketId='" + ticketId + '\'' +
                ", vehicleId='" + vehicleId + '\'' +
                ", status=" + status +
                ", entryTime=" + entryTime +
                ", amount=" + amount +
                ", isPaid=" + isPaid +
                '}';
    }
}
