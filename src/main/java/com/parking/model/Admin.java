package com.parking.model;

import com.parking.enums.UserRole;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents an administrator in the parking management system.
 * Admin users have full system access and management capabilities.
 */
public class Admin extends User {
    private String department;
    private List<String> permissions;
    private boolean isSuperAdmin;
    private LocalDateTime lastPasswordChange;

    public Admin() {
        super();
        this.permissions = new ArrayList<>();
        this.isSuperAdmin = false;
        this.lastPasswordChange = LocalDateTime.now();
    }

    public Admin(String userId, String username, String password, String email, String fullName, String department) {
        super(userId, username, password, email, UserRole.ADMIN, fullName);
        this.department = department;
        this.permissions = new ArrayList<>();
        this.isSuperAdmin = false;
        this.lastPasswordChange = LocalDateTime.now();
        this.permissions.add("VIEW_REPORTS");
        this.permissions.add("MANAGE_USERS");
        this.permissions.add("MANAGE_SPOTS");
        this.permissions.add("VIEW_ANALYTICS");
    }

    public Admin(String userId, String username, String password, String email, String fullName, String department,
                 boolean passwordAlreadyHashed) {
        super(userId, username, password, email, UserRole.ADMIN, fullName, passwordAlreadyHashed);
        this.department = department;
        this.permissions = new ArrayList<>();
        this.isSuperAdmin = false;
        this.lastPasswordChange = LocalDateTime.now();
        this.permissions.add("VIEW_REPORTS");
        this.permissions.add("MANAGE_USERS");
        this.permissions.add("MANAGE_SPOTS");
        this.permissions.add("VIEW_ANALYTICS");
    }

    /**
     * Adds a permission to the admin's permission list.
     * 
     * @param permission the permission to add
     */
    public void addPermission(String permission) {
        if (permission != null && !permission.trim().isEmpty() && !permissions.contains(permission)) {
            permissions.add(permission);
        }
    }

    /**
     * Removes a permission from the admin's permission list.
     * 
     * @param permission the permission to remove
     */
    public void removePermission(String permission) {
        if (permission != null) {
            permissions.remove(permission);
        }
    }

    /**
     * Checks if the admin has a specific permission.
     * 
     * @param permission the permission to check
     * @return true if the admin has the permission, false otherwise
     */
    public boolean hasPermission(String permission) {
        if (isSuperAdmin) {
            return true;
        }
        return permission != null && permissions.contains(permission);
    }

    // Getters and Setters
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    
    public List<String> getPermissions() { return permissions; }
    public void setPermissions(List<String> permissions) { this.permissions = permissions; }
    
    public boolean isSuperAdmin() { return isSuperAdmin; }
    public void setSuperAdmin(boolean superAdmin) { isSuperAdmin = superAdmin; }
    
    public LocalDateTime getLastPasswordChange() { return lastPasswordChange; }
    public void setLastPasswordChange(LocalDateTime lastPasswordChange) { this.lastPasswordChange = lastPasswordChange; }

    @Override
    public String toString() {
        return "Admin{" +
                "adminId='" + super.getUserId() + '\'' +
                ", department='" + department + '\'' +
                ", isSuperAdmin=" + isSuperAdmin +
                ", permissions=" + permissions.size() +
                ", fullName='" + getFullName() + '\'' +
                ", email='" + getEmail() + '\'' +
                '}';
    }
}
