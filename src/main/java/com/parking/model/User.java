package com.parking.model;

import com.parking.enums.UserRole;
import com.parking.security.PasswordHasher;
import java.time.LocalDateTime;
import java.time.Duration;

/**
 * Represents a user in the parking management system.
 * This is the base class for all user types in the system.
 */
public class User {
    private String userId;
    private String username;
    private String password;
    private String email;
    private UserRole role;
    private String fullName;
    private String phoneNumber;
    private LocalDateTime createdAt;
    private LocalDateTime lastLogin;
    private boolean isActive;
    private int failedLoginAttempts;
    private LocalDateTime lockedUntil;

    public User() {
        this.createdAt = LocalDateTime.now();
        this.isActive = true;
        this.failedLoginAttempts = 0;
    }

    public User(String userId, String username, String password, String email, UserRole role, String fullName) {
        this(userId, username, password, email, role, fullName, false);
    }

    /** Internal constructor used when a role change must preserve an existing hash. */
    public User(String userId, String username, String password, String email, UserRole role,
                String fullName, boolean passwordAlreadyHashed) {
        this.userId = userId;
        this.username = username;
        if (passwordAlreadyHashed) {
            if (password == null || password.trim().isEmpty()) {
                throw new IllegalArgumentException("Password hash cannot be null or empty.");
            }
            this.password = password;
        } else {
            changePassword(password);
        }
        this.email = email;
        this.role = role;
        this.fullName = fullName;
        this.createdAt = LocalDateTime.now();
        this.isActive = true;
        this.failedLoginAttempts = 0;
    }

    // Getters and Setters
    public String getUserId() { return userId; }
    public void setUserId(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty.");
        }
        this.userId = userId;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or empty.");
        }
        this.username = username;
    }
    
    /** Changes the password by hashing the supplied raw password. */
    public void setPassword(String password) {
        changePassword(password);
    }

    public void changePassword(String password) {
        if (password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty.");
        }
        this.password = PasswordHasher.hashPassword(password);
    }

    /** Used by UserService; the stored hash is never exposed to callers. */
    public boolean verifyPassword(String rawPassword) {
        return PasswordHasher.verifyPassword(rawPassword, password);
    }

    /** Internal persistence hook; exposes only the one-way hash, never plaintext. */
    public String getStoredPasswordHash() { return password; }

    /** Copies a previously stored hash without exposing it. */
    public void copyPasswordHashFrom(User source) {
        if (source == null || source.password == null || source.password.trim().isEmpty()) {
            throw new IllegalArgumentException("Source password hash is unavailable.");
        }
        this.password = source.password;
    }

    public boolean isLoginLocked(LocalDateTime now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    public void recordFailedLogin(int lockoutThreshold, Duration lockoutDuration, LocalDateTime now) {
        failedLoginAttempts++;
        if (failedLoginAttempts >= lockoutThreshold) {
            lockedUntil = now.plus(lockoutDuration);
            failedLoginAttempts = 0;
        }
    }

    public void resetLoginFailures() {
        failedLoginAttempts = 0;
        lockedUntil = null;
    }

    /** Restores persisted authentication lock state without exposing the password hash. */
    public void restoreLoginState(int failedAttempts, LocalDateTime lockedUntil) {
        this.failedLoginAttempts = Math.max(0, failedAttempts);
        this.lockedUntil = lockedUntil;
    }

    public int getFailedLoginAttempts() { return failedLoginAttempts; }
    public LocalDateTime getLockedUntil() { return lockedUntil; }

    public String getEmail() { return email; }
    public void setEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email cannot be null or empty.");
        }
        this.email = email;
    }
    
    public UserRole getRole() { return role; }
    public void setRole(UserRole role) {
        if (role == null) {
            throw new IllegalArgumentException("UserRole cannot be null.");
        }
        this.role = role;
    }
    
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) {
            throw new IllegalArgumentException("Full name cannot be null or empty.");
        }
        this.fullName = fullName;
    }
    
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
   
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getLastLogin() { return lastLogin; }
    public void setLastLogin(LocalDateTime lastLogin) { this.lastLogin = lastLogin; }
    
    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    @Override
    public String toString() {
        return "User{" +
                "userId='" + userId + '\'' +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", role=" + role +
                ", fullName='" + fullName + '\'' +
                ", isActive=" + isActive +
                '}';
    }
}
