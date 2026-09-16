package com.parking.model;

import com.parking.enums.SpotType;
import com.parking.enums.SpotStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents the live operational view of a parking garage, managing its
 * parking spots, levels, vehicles, capacity, and occupancy operations. This
 * class is intentionally separate from {@code Garage}: it handles in-memory
 * runtime activity, while {@code Garage} holds durable identity, configuration,
 * and administrative lifecycle state rather than replacing this operational
 * responsibility.
 */
public class ParkingGarage {
    private String garageId;
    private String name;
    private String address;
    private int totalLevels;
    private Map<Integer, List<ParkingSpot>> levels;
    private Map<String, Vehicle> vehicles = new HashMap<>();
    private Map<String, Vehicle> registeredVehicles = new HashMap<>();
    private List<ParkingSpot> allSpots;
    private double baseHourlyRate;
    private int totalCapacity;
    private int availableSpots;
    private boolean isOpen;

    public ParkingGarage() {
        this.allSpots = new ArrayList<>();
        this.levels = new HashMap<>();
        this.isOpen = false;
        this.availableSpots = 0;
        this.totalCapacity = 0;
    }

    public ParkingGarage(String garageId, String name, String address, int totalLevels, double baseHourlyRate) {
        this.garageId = garageId;
        this.name = name;
        this.address = address;
        this.totalLevels = totalLevels;
        this.baseHourlyRate = baseHourlyRate;
        this.isOpen = true;
        this.availableSpots = 0;
        this.totalCapacity = 0;
        this.allSpots = new ArrayList<>();
        this.levels = new HashMap<>();
        for (int i = 0; i < totalLevels; i++) {
            levels.put(i, new ArrayList<>());
        }
    }

    /**
     * Adds a new parking spot to the garage.
     * 
     * @param spot the parking spot to add
     * @param level the level number to add the spot to
     * @return true if spot was successfully added, false otherwise
     */
    public boolean addParkingSpot(ParkingSpot spot, int level) {
        // Check if spot is null or level is out of bounds
        if (spot == null) {
           throw new IllegalArgumentException("Parking spot cannot be null.");
        }
        if (level < 0 || level >= totalLevels) {
            throw new IllegalArgumentException("Level " + level + " is out of bounds for this garage.");
        }
        // Check if spot already exists in allSpots
        for (ParkingSpot s : allSpots) {
            if (s.getSpotId().equals(spot.getSpotId())) {
                return false;
            }
        }
        // Add spot to allSpots and levels map
        allSpots.add(spot);
        levels.get(level).add(spot);
        totalCapacity++;
        // Update availableSpots if the spot is available
        if (spot.isAvailable()) {
            availableSpots++;
        }
        return true;
    }

    /**
     * Removes a parking spot from the garage.
     * 
     * @param spotId the ID of the spot to remove
     * @return true if spot was successfully removed, false otherwise
     */
    public boolean removeParkingSpot(String spotId) {
        // Check if spotId is null or empty
        if (spotId == null || spotId.isEmpty()) {
            return false;
        }
        // Find the spot to remove
        ParkingSpot spotToRemove = null;
        for (ParkingSpot spot : allSpots) {
            if (spot.getSpotId().equals(spotId)) {
                spotToRemove = spot;
                break;
            }
        }
        if (spotToRemove == null) {
            return false;
        }
        if (spotToRemove.getStatus() == SpotStatus.OCCUPIED) {
            return false;
        }
        // Remove spot from allSpots and levels
        allSpots.remove(spotToRemove);
        for (List<ParkingSpot> level : levels.values()) {
            level.remove(spotToRemove);
        }
        // Update availableSpots and totalCapacity
        totalCapacity--;
        if (spotToRemove.isAvailable()) {
            availableSpots--;
        }
        return true;
    }

    /**
     * Registers a vehicle when it enters.
     * 
     * @param vehicle the vehicle to register
     */
    public void registerVehicle(Vehicle vehicle) {
        // Add vehicle to vehicles map
        vehicles.put(vehicle.getVehicleId(), vehicle);
    }
    
    /**
     * Gets a vehicle by ID.
     * 
     * @param vehicleId the ID of the vehicle to get
     * @return the vehicle, or null if not found
     */
    public Vehicle getVehicle(String vehicleId) {
        // Get vehicle from vehicles map
        return vehicles.get(vehicleId);
    }

    /**
     * Unregisters a vehicle when it exits.
     * 
     * @param vehicleId the ID of the vehicle to unregister
     */
    public void unregisterVehicle(String vehicleId) {
        // Remove vehicle from vehicles map
        vehicles.remove(vehicleId);
    }

    /**
     * Registers a vehicle when it enters the parking garage.
     * 
     * @param vehicle the vehicle to register
     */
    public void registerVehicleDetails(Vehicle vehicle) {
        if (vehicle == null || vehicle.getVehicleId() == null) {
            throw new IllegalArgumentException("Vehicle cannot be null or have a null ID.");
        }
        Vehicle existing = registeredVehicles.get(vehicle.getVehicleId());
        if (existing != null && existing != vehicle) {
            throw new IllegalArgumentException("Vehicle ID is already registered: " + vehicle.getVehicleId());
        }
        if (vehicle.getLicensePlate() != null) {
            for (Vehicle candidate : registeredVehicles.values()) {
                if (!candidate.getVehicleId().equals(vehicle.getVehicleId())
                        && vehicle.getLicensePlate().equalsIgnoreCase(candidate.getLicensePlate())) {
                    throw new IllegalArgumentException("License plate is already registered: " + vehicle.getLicensePlate());
                }
            }
        }
        registeredVehicles.put(vehicle.getVehicleId(), vehicle);
    }

    public Map<String, Vehicle> getRegisteredVehicles() {
        return registeredVehicles;
    }
    
    /**
     * Gets a registered vehicle by ID.
     *
     * @param vehicleId the ID of the vehicle to get
     * @return the registered vehicle, or null if not found
     */

    public Vehicle getRegisteredVehicle(String vehicleId) {
        if (vehicleId == null || vehicleId.isEmpty()) {
            return null;
        }
    
        return registeredVehicles.get(vehicleId);
    }
    
    /**
     * Finds an available parking spot for a vehicle type.
     * 
     * @param spotType the type of spot needed
     * @return an available ParkingSpot, or null if none available
     */
    public ParkingSpot findAvailableSpot(SpotType spotType) {
        if (spotType == null) {
            return null;
        }
        // Iterate through allSpots and find an available spot
        for (ParkingSpot spot : allSpots) {
            if (spot.isAvailable() && spot.getSpotType() == spotType) {
                return spot;
            }
        }
        return null;
    }

    /**
     * Finds a parking spot by its ID.
     * 
     * @param spotId the spot ID to search for
     * @return the ParkingSpot, or null if not found
     */
    public ParkingSpot getSpotById(String spotId) {
        // Iterate through allSpots and find the spot
        for (ParkingSpot spot : allSpots) {
            if (spot.getSpotId().equals(spotId)) {
                return spot;
            }
        }
        return null;
    }

    /**
     * Gets all available spots for a specific spot type.
     * 
     * @param spotType the type of spots to search for
     * @return list of available spots of the specified type
     */
    public List<ParkingSpot> getAvailableSpotsByType(SpotType spotType) {
        // Iterate through allSpots and find available spots
        List<ParkingSpot> results = new ArrayList<>();
        // Check if spotType is null and return
        if (spotType == null) {
            return results;
        }
        // Iterate through allSpots and find available spots and add to results
        for (ParkingSpot spot : allSpots) {
            if (spot.isAvailable() && spot.getSpotType() == spotType) {
                results.add(spot);
            }
        }
        return results;
    }

    /**
     * Updates the availability count of the garage.
     */
    public void updateAvailability() {
        // Count the number of available spots
        int counter = 0;
        for (ParkingSpot spot : allSpots) {
            if (spot.isAvailable()) {
                counter++;
            }
        }
        // Update availableSpots
        availableSpots = counter;
    }

    /**
     * Opens the parking garage.
     */
    public void openGarage() {
        isOpen = true;
    }

    /**
     * Closes the parking garage.
     */
    public void closeGarage() {
        // Check if there are any occupied spots
        for (ParkingSpot spot : allSpots) {
            if (spot.getStatus() == SpotStatus.OCCUPIED) {
                throw new IllegalStateException("Cannot close garage with occupied spots");
            }
        }
        isOpen = false;
    }

    /**
     * Gets the occupancy rate of the garage.
     * 
     * @return occupancy percentage (0.0 to 1.0)
     */
    public double getOccupancyRate() {
        if (totalCapacity == 0) {
            return 0.0;
        }
        int occupiedCount = 0;
        for (ParkingSpot spot : allSpots) {
            if (spot.getStatus() == SpotStatus.OCCUPIED) {
                occupiedCount++;
            }
        }
        return (double) occupiedCount / totalCapacity;
    }

    // Getters and Setters
    public String getGarageId() { return garageId; }
    public void setGarageId(String garageId) { this.garageId = garageId; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    
    public int getTotalLevels() { return totalLevels; }
    public void setTotalLevels(int totalLevels) { this.totalLevels = totalLevels; }
    
    public Map<Integer, List<ParkingSpot>> getLevels() { return levels; }
    public void setLevels(Map<Integer, List<ParkingSpot>> levels) { this.levels = levels; }
    
    public List<ParkingSpot> getAllSpots() { return allSpots; }
    public void setAllSpots(List<ParkingSpot> allSpots) { this.allSpots = allSpots; }
    
    public double getBaseHourlyRate() { return baseHourlyRate; }
    public void setBaseHourlyRate(double baseHourlyRate) { this.baseHourlyRate = baseHourlyRate; }
    
    public int getTotalCapacity() { return totalCapacity; }
    public void setTotalCapacity(int totalCapacity) { this.totalCapacity = totalCapacity; }
    
    public int getAvailableSpots() { return availableSpots; }
    public void setAvailableSpots(int availableSpots) { this.availableSpots = availableSpots; }
    
    public boolean isOpen() { return isOpen; }
    public void setOpen(boolean open) { isOpen = open; }

    @Override
    public String toString() {
        return "ParkingGarage{" +
                "garageId='" + garageId + '\'' +
                ", name='" + name + '\'' +
                ", address='" + address + '\'' +
                ", totalCapacity=" + totalCapacity +
                ", availableSpots=" + availableSpots +
                ", isOpen=" + isOpen +
                ", occupancyRate=" + getOccupancyRate() +
                '}';
    }
}
