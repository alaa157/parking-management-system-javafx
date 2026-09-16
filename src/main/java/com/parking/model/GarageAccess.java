package com.parking.model;

import com.parking.enums.UserRole;
import java.time.LocalDateTime;

/** A user's active or revoked assignment to one garage. */
public final class GarageAccess {
    private final String userId;
    private final String garageId;
    private final UserRole role;
    private boolean active;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public GarageAccess(String userId, String garageId, UserRole role) {
        this(userId, garageId, role, true, LocalDateTime.now(), LocalDateTime.now());
    }

    public GarageAccess(String userId, String garageId, UserRole role, boolean active,
                        LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.userId = required(userId, "userId");
        this.garageId = required(garageId, "garageId");
        if (role == null) throw new IllegalArgumentException("role cannot be null");
        this.role = role;
        this.active = active;
        this.createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
        this.updatedAt = updatedAt == null ? this.createdAt : updatedAt;
    }

    public String getUserId() { return userId; }
    public String getGarageId() { return garageId; }
    public UserRole getRole() { return role; }
    public boolean isActive() { return active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setActive(boolean active) { this.active = active; updatedAt = LocalDateTime.now(); }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " cannot be blank");
        return value.trim();
    }
}
