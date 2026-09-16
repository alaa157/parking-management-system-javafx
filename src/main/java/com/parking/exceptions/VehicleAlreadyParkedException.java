package com.parking.exceptions;

/**
 * Exception thrown when attempting to park a vehicle that is already parked in the garage.
 */
public class VehicleAlreadyParkedException extends Exception {
    private final String ticketId;
    private final String garageId;
    private final String spotId;
    private final java.time.LocalDateTime entryTime;
    
    public VehicleAlreadyParkedException() {
        this("Vehicle is already parked", null, null, null, null);
    }
    
    public VehicleAlreadyParkedException(String message) {
        this(message, null, null, null, null);
    }

    public VehicleAlreadyParkedException(String ticketId, String garageId, String spotId,
                                         java.time.LocalDateTime entryTime) {
        this("Vehicle is already parked in garage " + garageId + " (ticket=" + ticketId
                + ", spot=" + spotId + ", entry=" + entryTime + ")",
                ticketId, garageId, spotId, entryTime);
    }

    private VehicleAlreadyParkedException(String message, String ticketId, String garageId,
                                          String spotId, java.time.LocalDateTime entryTime) {
        super(message);
        this.ticketId = ticketId;
        this.garageId = garageId;
        this.spotId = spotId;
        this.entryTime = entryTime;
    }

    public String getTicketId() { return ticketId; }
    public String getGarageId() { return garageId; }
    public String getSpotId() { return spotId; }
    public java.time.LocalDateTime getEntryTime() { return entryTime; }
}
