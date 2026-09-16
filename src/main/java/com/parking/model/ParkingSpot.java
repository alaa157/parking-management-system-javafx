package com.parking.model;

import com.parking.enums.SpotStatus;
import com.parking.enums.SpotType;
import java.time.LocalDateTime;

/**
 * Represents a parking spot in the parking garage.
 * Implements Trackable interface for location and status tracking.
 */
public class ParkingSpot {
    private String spotId;
    private SpotType spotType;
    private SpotStatus status;
    private String location;
    private String vehicleId;
    private double hourlyRate;
    private boolean isUnderMaintenance;
    private String maintenanceReason;
    private String reservationHolderUserId;
    private LocalDateTime reservationExpiry;

    public ParkingSpot() {
        this.status = SpotStatus.AVAILABLE;
        this.isUnderMaintenance = false;
    }

    public ParkingSpot(String spotId, SpotType spotType, String location, double hourlyRate) {
        this.spotId = spotId;
        this.spotType = spotType;
        this.location = location;
        this.hourlyRate = hourlyRate;
        this.status = SpotStatus.AVAILABLE;
        this.isUnderMaintenance = false;
    }

    /**
     * Occupies the parking spot with a vehicle.
     *
     * @param vehicleId the ID of the vehicle occupying the spot
     * @return true if spot was successfully occupied, false otherwise
     */
    public boolean occupySpot(String vehicleId) {
        if (vehicleId == null || vehicleId.isEmpty()) {
            return false;
        }
        // A spot must be genuinely available, including maintenance state.
        if (!isAvailable()) {
            return false;
        }
        this.vehicleId = vehicleId;
        status = SpotStatus.OCCUPIED;
        return true;
    }

    /**
     * Reserves this parking spot for a user.
     *
     * @param holderUserId the user holding the reservation
     * @return true if the reservation was created
     */
    public boolean reserveSpot(String holderUserId) {
        if (holderUserId == null || holderUserId.trim().isEmpty()) {
            return false;
        }
        if (!isAvailable()) {
            return false;
        }

        reservationHolderUserId = holderUserId;
        reservationExpiry = null;
        status = SpotStatus.RESERVED;
        return true;
    }

    /**
     * Clears an active reservation and makes the spot available again.
     */
    public void clearReservation() {
        reservationHolderUserId = null;
        reservationExpiry = null;
        if (status == SpotStatus.RESERVED) {
            status = SpotStatus.AVAILABLE;
        }
    }

    public boolean clearReservationIfExpired(LocalDateTime now) {
        if (now == null) {
            return false;
        }
        if (status == SpotStatus.RESERVED
                && reservationExpiry != null
                && !now.isBefore(reservationExpiry)) {
            clearReservation();
            return true;
        }
        return false;
    }

    /**
     * Frees the parking spot when a vehicle exits.
     */
    public void freeSpot() {
        vehicleId = null;
        if (isUnderMaintenance) {
            status = SpotStatus.UNDER_MAINTENANCE;
        } else if (status != SpotStatus.OUT_OF_SERVICE) {
            status = SpotStatus.AVAILABLE;
        }
    }

    /**
     * Checks if the spot is currently available for parking.
     *
     * @return true if spot is available, false otherwise
     */
    public boolean isAvailable() {
        return status == SpotStatus.AVAILABLE
                && !isUnderMaintenance;
    }

    /**
     * Puts the spot under maintenance.
     * @param reason the reason for maintenance
     */
    public void setUnderMaintenance(String reason) {
        // Check if the spot is currently occupied or reserved
        if (status == SpotStatus.OCCUPIED || status == SpotStatus.RESERVED) {
            return;
        }
        // Set maintenance details
        isUnderMaintenance = true;
        maintenanceReason = reason;
        status = SpotStatus.UNDER_MAINTENANCE;
    }

    /**
     * Removes the spot from maintenance.
     */
    public void removeFromMaintenance() {
        isUnderMaintenance = false;
        maintenanceReason = null;
        status = SpotStatus.AVAILABLE;
    }

    /**
     * Removes this spot from normal service.
     */
    public void setOutOfService() {
        if (status == SpotStatus.OCCUPIED || status == SpotStatus.RESERVED) {
            return;
        }
        isUnderMaintenance = false;
        status = SpotStatus.OUT_OF_SERVICE;
    }

    /**
     * Restores an out-of-service spot to availability.
     */
    public void restoreToAvailable() {
        if (status == SpotStatus.OUT_OF_SERVICE) {
            status = SpotStatus.AVAILABLE;
        }
    }

    // Getters and Setters
    public String getSpotId() { return spotId; }
    public void setSpotId(String spotId) { this.spotId = spotId; }

    public SpotType getSpotType() { return spotType; }
    public void setSpotType(SpotType spotType) { this.spotType = spotType; }

    public SpotStatus getStatus() { return status; }
    public void setStatus(SpotStatus status) { this.status = status; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getVehicleId() { return vehicleId; }
    public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }

    public double getHourlyRate() { return hourlyRate; }
    public void setHourlyRate(double hourlyRate) {
        if (hourlyRate > 0) {
            this.hourlyRate = hourlyRate;
        }
     }

    public boolean isUnderMaintenance() { return isUnderMaintenance; }

    public String getMaintenanceReason() { return maintenanceReason; }
    public void setMaintenanceReason(String maintenanceReason) { this.maintenanceReason = maintenanceReason; }

    public String getReservationHolderUserId() {
        return reservationHolderUserId;
    }

    public void setReservationHolderUserId(String reservationHolderUserId) {
        this.reservationHolderUserId = reservationHolderUserId;
    }

    public LocalDateTime getReservationExpiry() {
        return reservationExpiry;
    }

    public void setReservationExpiry(LocalDateTime reservationExpiry) {
        this.reservationExpiry = reservationExpiry;
    }

    @Override
    public String toString() {
        return "ParkingSpot{" +
                "spotId='" + spotId + '\'' +
                ", spotType=" + spotType +
                ", status=" + status +
                ", location='" + location + '\'' +
                ", vehicleId='" + vehicleId + '\'' +
                '}';
    }
}
