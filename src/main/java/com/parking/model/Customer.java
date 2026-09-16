package com.parking.model;

import com.parking.enums.UserRole;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a customer in the parking management system.
 * Extends the User class with customer-specific attributes.
 */
public class Customer extends User {
    private String driverLicenseNumber;
    private double walletBalance;
    private List<String> vehicleIds;
    private List<String> ticketIds;

    public Customer() {
        super();
        this.vehicleIds = new ArrayList<>();
        this.ticketIds = new ArrayList<>();
        this.walletBalance = 0.0;
    }

    public Customer(String userId, String username, String password, String email, String fullName) {
        super(userId, username, password, email, UserRole.CUSTOMER, fullName);
        this.vehicleIds = new ArrayList<>();
        this.ticketIds = new ArrayList<>();
        this.walletBalance = 0.0;
    }

    public Customer(String userId, String username, String password, String email, String fullName,
                    boolean passwordAlreadyHashed) {
        super(userId, username, password, email, UserRole.CUSTOMER, fullName, passwordAlreadyHashed);
        this.vehicleIds = new ArrayList<>();
        this.ticketIds = new ArrayList<>();
        this.walletBalance = 0.0;
    }

    /**
     * Adds a vehicle to the customer's account.
     * 
     * @param vehicleId the vehicle ID to add
     */
    public void addVehicle(Vehicle vehicle) {
        if (vehicle != null && vehicle.getVehicleId() != null) {
            if (!vehicleIds.contains(vehicle.getVehicleId())) {
                vehicleIds.add(vehicle.getVehicleId());
            }
        }
    }

    /**
     * Removes a vehicle from the customer's account.
     * 
     * @param vehicleId the vehicle ID to remove
     */
    public void removeVehicle(String vehicleId) {
        if (vehicleId != null) {
            vehicleIds.remove(vehicleId);
        }
    }

    /**
     * Adds a ticket to the customer's history.
     * 
     * @param ticketId the ticket ID to add
     */
    public void addTicket(String ticketId) {
        if (ticketId != null && !ticketId.trim().isEmpty()) {
            if (!ticketIds.contains(ticketId)) {
                ticketIds.add(ticketId);
            }
        }
    }

    public void removeTicket(String ticketId) {
        if (ticketId != null) {
            ticketIds.remove(ticketId);
        }
    }

    /**
     * Adds money to the customer's wallet.
     * 
     * @param amount the amount to add
     */
    public void addWalletBalance(double amount) {
        if (amount > 0) {
            this.walletBalance += amount;
        }
    }

    /**
     * Deducts money from the customer's wallet.
     * 
     * @param amount the amount to deduct
     * @return true if deduction was successful, false if insufficient balance
     */
    public boolean deductWalletBalance(double amount) {
        if (amount > 0 && hasSufficientBalance(amount)) {
            this.walletBalance -= amount;
            return true;
        }
        return false;
    }

    /**
     * Checks if the wallet has sufficient balance for a transaction.
     * 
     * @param amount the amount to check
     * @return true if wallet balance is sufficient
     */
    public boolean hasSufficientBalance(double amount) {
        return amount > 0 && this.walletBalance >= amount;
    }

    // Getters and Setters
    public String getDriverLicenseNumber() { return driverLicenseNumber; }
    public void setDriverLicenseNumber(String driverLicenseNumber) { this.driverLicenseNumber = driverLicenseNumber; }
    
    public double getWalletBalance() { return walletBalance; }
    public void setWalletBalance(double walletBalance) { this.walletBalance = walletBalance; }
    
    public List<String> getVehicleIds() { return vehicleIds; }
    public void setVehicleIds(List<String> vehicleIds) { this.vehicleIds = vehicleIds; }
    
    public List<String> getTicketIds() { return ticketIds; }
    public void setTicketIds(List<String> ticketIds) { this.ticketIds = ticketIds; }

    @Override
    public String toString() {
        return "Customer{" +
                "customerId='" + super.getUserId() + '\'' +
                ", walletBalance=" + walletBalance +
                ", vehicleCount=" + vehicleIds.size() +
                ", ticketCount=" + ticketIds.size() +
                ", fullName='" + getFullName() + '\'' +
                ", email='" + getEmail() + '\'' +
                '}';
    }
}
