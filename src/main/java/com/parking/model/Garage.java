package com.parking.model;

import java.time.LocalDateTime;

/**
 * Durable configuration and lifecycle state for one parking garage. This class
 * is intentionally separate from {@link ParkingGarage}: it represents the
 * persisted garage identity, configuration, and administrative open/archive
 * lifecycle, while {@link ParkingGarage} owns the live operational collections
 * and spot-level behavior used during parking operations.
 */
public final class Garage {
    private final String garageId;
    private String name;
    private String address;
    private int totalLevels;
    private double baseHourlyRate;
    private String currency;
    private int freeParkingMinutes;
    private int reservationHoldMinutes;
    private int maxParkHours;
    private boolean open = true;
    private boolean archived;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Garage(String garageId, String name, String address, int totalLevels,
                  double baseHourlyRate, String currency, int freeParkingMinutes,
                  int reservationHoldMinutes, int maxParkHours) {
        this(garageId, name, address, totalLevels, baseHourlyRate, currency,
                freeParkingMinutes, reservationHoldMinutes, maxParkHours,
                true, false, LocalDateTime.now(), LocalDateTime.now());
    }

    public Garage(String garageId, String name, String address, int totalLevels,
                  double baseHourlyRate, String currency, int freeParkingMinutes,
                  int reservationHoldMinutes, int maxParkHours, boolean open,
                  boolean archived, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.garageId = required(garageId, "garageId");
        this.createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
        this.updatedAt = updatedAt == null ? this.createdAt : updatedAt;
        setName(name);
        setAddress(address);
        setTotalLevels(totalLevels);
        setBaseHourlyRate(baseHourlyRate);
        setCurrency(currency);
        setFreeParkingMinutes(freeParkingMinutes);
        setReservationHoldMinutes(reservationHoldMinutes);
        setMaxParkHours(maxParkHours);
        this.open = open;
        this.archived = archived;
    }

    public String getGarageId() { return garageId; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public int getTotalLevels() { return totalLevels; }
    public double getBaseHourlyRate() { return baseHourlyRate; }
    public String getCurrency() { return currency; }
    public int getFreeParkingMinutes() { return freeParkingMinutes; }
    public int getReservationHoldMinutes() { return reservationHoldMinutes; }
    public int getMaxParkHours() { return maxParkHours; }
    public boolean isOpen() { return open; }
    public boolean isArchived() { return archived; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setName(String name) { this.name = required(name, "name"); touch(); }
    public void setAddress(String address) { this.address = required(address, "address"); touch(); }

    public void setTotalLevels(int totalLevels) {
        if (totalLevels <= 0) throw new IllegalArgumentException("totalLevels must be greater than 0");
        this.totalLevels = totalLevels;
        touch();
    }

    public void setBaseHourlyRate(double baseHourlyRate) {
        if (!Double.isFinite(baseHourlyRate) || baseHourlyRate < 0) {
            throw new IllegalArgumentException("baseHourlyRate must be finite and non-negative");
        }
        this.baseHourlyRate = baseHourlyRate;
        touch();
    }

    public void setCurrency(String currency) { this.currency = required(currency, "currency").toUpperCase(); touch(); }

    public void setFreeParkingMinutes(int minutes) {
        if (minutes < 0) throw new IllegalArgumentException("freeParkingMinutes cannot be negative");
        freeParkingMinutes = minutes;
        touch();
    }

    public void setReservationHoldMinutes(int minutes) {
        if (minutes <= 0) throw new IllegalArgumentException("reservationHoldMinutes must be greater than 0");
        reservationHoldMinutes = minutes;
        touch();
    }

    public void setMaxParkHours(int hours) {
        if (hours <= 0) throw new IllegalArgumentException("maxParkHours must be greater than 0");
        maxParkHours = hours;
        touch();
    }

    public void setOpen(boolean open) { this.open = open; touch(); }
    public void archive() { archived = true; open = false; touch(); }
    public void reopen() { archived = false; open = true; touch(); }

    private void touch() { updatedAt = LocalDateTime.now(); }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " cannot be blank");
        return value.trim();
    }
}
