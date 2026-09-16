package com.parking.model;

import com.parking.enums.VehicleType;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Represents a vehicle in the parking management system.
 * Implements Trackable interface for location and status tracking.
 */
public class Vehicle {
    private String vehicleId;
    private String licensePlate;
    private VehicleType vehicleType;
    private String make;
    private String model;
    private String color;
    private int year;
    private String userId;

    private LocalDateTime entryTime;
    private String parkingSpotId;
    private boolean isParked;

    public Vehicle() {
        this.isParked = false;
    }

    public Vehicle(String vehicleId, String licensePlate, VehicleType vehicleType, 
                   String make, String model, String color, int year, String userId) {
        this.vehicleId = vehicleId;
        this.licensePlate = licensePlate;
        this.vehicleType = vehicleType;
        this.make = make;
        this.model = model;
        this.color = color;
        this.year = year;
        this.userId = userId;
        this.isParked = false;
    }
    
    /**
     * Parks the vehicle in a specific parking spot.
     * 
     * @param parkingSpotId the ID of the parking spot
     */
    public void park(String parkingSpotId) {
        if (parkingSpotId == null || parkingSpotId.trim().isEmpty()) {
            throw new IllegalArgumentException("Parking spot ID cannot be null or empty.");
        }
        if (this.isParked) {
            return;
        }
        this.parkingSpotId = parkingSpotId;
        this.entryTime = LocalDateTime.now();
        this.isParked = true;
    }

    /**
     * Removes the vehicle from its parking spot.
     */
    public void unpark() {
        if (!this.isParked) {
            return;
        }
        this.parkingSpotId = null;
        this.isParked = false;
    }

    /**
     * Calculates the parking duration.
     * 
     * @return duration in hours, or 0 if not parked
     */
    public double getParkingDuration() {
        if (!isParked || entryTime == null) {
            return 0.0;
        }
        long minutes = ChronoUnit.MINUTES.between(entryTime, LocalDateTime.now());
        return minutes / 60.0;
    }

    // Getters and Setters
    public String getVehicleId() { return vehicleId; }
    public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }
    
    public String getLicensePlate() { return licensePlate; }
    public void setLicensePlate(String licensePlate) { this.licensePlate = licensePlate; }
    
    public VehicleType getVehicleType() { return vehicleType; }
    public void setVehicleType(VehicleType vehicleType) { this.vehicleType = vehicleType; }
    
    public String getMake() { return make; }
    public void setMake(String make) { this.make = make; }
    
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    
    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public LocalDateTime getEntryTime() { return entryTime; }
    public void setEntryTime(LocalDateTime entryTime) { this.entryTime = entryTime; }
    
    public String getParkingSpotId() { return parkingSpotId; }
    public void setParkingSpotId(String parkingSpotId) { this.parkingSpotId = parkingSpotId; }
    
    public boolean isParked() { return isParked; }
    public void setParked(boolean parked) { isParked = parked; }

    @Override
    public String toString() {
        return "Vehicle{" +
                "vehicleId='" + vehicleId + '\'' +
                ", licensePlate='" + licensePlate + '\'' +
                ", make='" + make + '\'' +
                ", model='" + model + '\'' +
                ", color='" + color + '\'' +
                ", isParked=" + isParked +
                '}';
    }
}