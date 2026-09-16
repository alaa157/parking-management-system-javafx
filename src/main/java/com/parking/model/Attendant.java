package com.parking.model;

import com.parking.enums.UserRole;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a parking attendant in the parking management system.
 * Attendants assist with parking operations and customer service.
 */
public class Attendant extends User {
    private String shift;
    private String assignedZone;
    private List<String> processedTicketIds;
    private int totalTransactions;
    private double totalRevenue;
    private boolean isOnDuty;


    public Attendant() {
        super();
        this.processedTicketIds = new ArrayList<>();
        this.totalTransactions = 0;
        this.totalRevenue = 0.0;
        this.isOnDuty = false;

    }

    public Attendant(String userId, String username, String password, String email, String fullName, String shift) {
        super(userId, username, password, email, UserRole.ATTENDANT, fullName);
        this.shift = shift;
        this.processedTicketIds = new ArrayList<>();
        this.totalTransactions = 0;
        this.totalRevenue = 0.0;
        this.isOnDuty = false;

    }

    public Attendant(String userId, String username, String password, String email, String fullName, String shift,
                     boolean passwordAlreadyHashed) {
        super(userId, username, password, email, UserRole.ATTENDANT, fullName, passwordAlreadyHashed);
        this.shift = shift;
        this.processedTicketIds = new ArrayList<>();
        this.totalTransactions = 0;
        this.totalRevenue = 0.0;
        this.isOnDuty = false;
    }

    /**
     * Processes a ticket transaction for a customer.
     *
     * @param ticketId the ticket being processed
     * @param amount the amount collected
     * @return true if transaction was successful, false otherwise
     */
    public boolean processTransaction(String ticketId, double amount) {
        if (ticketId == null || ticketId.trim().isEmpty() || amount < 0) {
            return false;
        }
        if (processedTicketIds.contains(ticketId)) {
            return false;
        }
            processedTicketIds.add(ticketId);
            totalTransactions++;
            totalRevenue += amount;
            return true;
    }

    /**
     * Changes the attendant's shift.
     *
     * @param newShift the new shift
     */
    public void changeShift(String newShift) {
        if (newShift != null && !newShift.trim().isEmpty()) {
            this.shift = newShift;
        }
    }

    /**
     * Assigns the attendant to a specific zone.
     *
     * @param zone the zone to assign
     */
    public void assignToZone(String zone) {
        if (zone != null && !zone.trim().isEmpty()) {
            this.assignedZone = zone;
        }
    }

    /**
     * Starts the attendant's duty.
     */
    public void startDuty() {
        this.isOnDuty = true;
    }

    /**
     * Ends the attendant's duty.
     */
    public void endDuty() {
        this.isOnDuty = false;
    }

    // Getters and Setters
    public String getShift() { return shift; }
    public void setShift(String shift) { this.shift = shift; }

    public String getAssignedZone() { return assignedZone; }
    public void setAssignedZone(String assignedZone) { this.assignedZone = assignedZone; }

    public List<String> getProcessedTicketIds() { return processedTicketIds; }
    public void setProcessedTicketIds(List<String> processedTicketIds) { this.processedTicketIds = processedTicketIds; }

    public int getTotalTransactions() { return totalTransactions; }
    public void setTotalTransactions(int totalTransactions) { this.totalTransactions = totalTransactions; }

    public double getTotalRevenue() { return totalRevenue; }
    public void setTotalRevenue(double totalRevenue) { this.totalRevenue = totalRevenue; }

    public boolean isOnDuty() { return isOnDuty; }
    public void setOnDuty(boolean onDuty) { isOnDuty = onDuty; }

    @Override
    public String toString() {
        return "Attendant{" +
                "attendantId='" + super.getUserId() + '\'' +
                ", shift='" + shift + '\'' +
                ", assignedZone='" + assignedZone + '\'' +
                ", isOnDuty=" + isOnDuty +
                ", totalTransactions=" + totalTransactions +
                ", totalRevenue=" + totalRevenue +
                ", fullName='" + getFullName() + '\'' +
                ", email='" + getEmail() + '\'' +
                '}';
    }
}
