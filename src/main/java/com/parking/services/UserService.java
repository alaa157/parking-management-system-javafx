package com.parking.services;

import com.parking.model.User;
import com.parking.model.Customer;
import com.parking.model.Admin;
import com.parking.model.Attendant;
import com.parking.enums.UserRole;
import com.parking.model.Ticket;
import com.parking.enums.TicketStatus;
import com.parking.exceptions.TicketNotFoundException;
import com.parking.exceptions.AuthorizationException;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import com.parking.persistence.PersistenceStore;

/**
 * Service class responsible for user management including
 * registration, authentication, and authorization.
 */
public class UserService {

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;
    private static final Duration LOGIN_LOCKOUT_DURATION = Duration.ofMinutes(15);
    private static final String GENERIC_AUTHENTICATION_ERROR = "Invalid username or password";
    private static final String EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@(.+)$";
    private static final Pattern EMAIL_PATTERN = Pattern.compile(EMAIL_REGEX);

    // In-memory storage (replace with database in production)
    private Map<String, User> usersById = new HashMap<>();
    private Map<String, User> usersByUsername = new HashMap<>();
    private TicketService ticketService;
    private final PersistenceStore persistence;

    public UserService() {
        this(new PersistenceStore());
    }

    /**
     * Creates a user service backed by the supplied persistence boundary.
     *
     * <p>The GUI composition root uses one store for all services so related
     * ticket, user, and payment writes share the same SQLite connection and
     * transaction boundary. The no-argument constructor remains available
     * for the CLI and existing integrations.</p>
     */
    public UserService(PersistenceStore persistence) {
        if (persistence == null) {
            throw new IllegalArgumentException("persistence cannot be null");
        }
        this.persistence = persistence;
        for (User user : persistence.loadUsers()) {
            usersById.put(user.getUserId(), user);
            usersByUsername.put(normalizeUsername(user.getUsername()), user);
        }
    }

    public void setTicketService(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    public PersistenceStore getPersistenceStore() {
        return persistence;
    }

    /**
     * Registers a new user in the system.
     * 
     * @param username unique username for the user
     * @param password user's password
     * @param email    user's email address
     * @param role     the role to assign to the user
     * @return the created User object
     */
    public User registerUser(String username, String password, String email, UserRole role) {
        return registerUser(username, password, email, role, null, null);
    }

    /** Registers a user with optional profile fields before the first database write. */
    public User registerUser(String username, String password, String email, UserRole role,
                             String fullName, String phoneNumber) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or empty.");
        }
        String normalizedUsername = normalizeUsername(username);
        String normalizedFullName = fullName == null || fullName.trim().isEmpty()
                ? normalizedUsername : fullName.trim();
        // Validate username is unique
        if (isUsernameTaken(normalizedUsername)) {
            throw new RuntimeException("Username already exists");
        }
        // Validate email format
        if (!validateEmail(email)) {
            throw new RuntimeException("Invalid email format");
        }

        // Validate email uniqueness
        for (User existingUser : usersById.values()) {
            if (existingUser.getEmail() != null
                    && existingUser.getEmail().equalsIgnoreCase(email.trim())) {
                throw new RuntimeException("Email is already registered.");
            }
        }

        // Validate password strength
        if (!validatePasswordStrength(password)) {
            throw new RuntimeException("Password does not meet strength requirements");
        }

        // Create user based on role
        User user;
        String userId = UUID.randomUUID().toString();
        switch (role) {
            case CUSTOMER:
                user = new Customer(userId, normalizedUsername, password, email.trim(), normalizedFullName);
                break;
            case ADMIN:
                user = new Admin(userId, normalizedUsername, password, email.trim(), normalizedFullName, "");
                break;
            case ATTENDANT:
                user = new Attendant(userId, normalizedUsername, password, email.trim(), normalizedFullName, "");
                break;
            default:
                user = new User(userId, normalizedUsername, password, email.trim(), role, normalizedFullName);
        }
        user.setPhoneNumber(phoneNumber);
        // Persist first so a database failure cannot leave a phantom in-memory user.
        persistence.saveUser(user);
        usersById.put(userId, user);
        usersByUsername.put(normalizedUsername, user);
        // Return created user
        return user;
    }

    /**
     * Authenticates a user with username and password.
     * 
     * @param username the username to authenticate
     * @param password the password to validate
     * @return authenticated User object
     * @throws RuntimeException if authentication fails
     */
    public User authenticateUser(String username, String password) throws RuntimeException {
        String normalizedUsername = username == null ? "" : username.trim();
        User user = usersByUsername.get(normalizedUsername);
        LocalDateTime now = LocalDateTime.now();

        if (user == null || user.isLoginLocked(now) || !user.isActive()) {
            throw new RuntimeException(GENERIC_AUTHENTICATION_ERROR);
        }

        if (!user.verifyPassword(password)) {
            UserState before = snapshot(user);
            user.recordFailedLogin(MAX_FAILED_LOGIN_ATTEMPTS, LOGIN_LOCKOUT_DURATION, now);
            try {
                persistence.saveUser(user);
            } catch (RuntimeException failure) {
                restore(user, before);
                throw new IllegalStateException("Could not persist authentication state", failure);
            }
            usersById.put(user.getUserId(), user);
            throw new RuntimeException(GENERIC_AUTHENTICATION_ERROR);
        }

        UserState before = snapshot(user);
        user.resetLoginFailures();
        // Update last login
        user.setLastLogin(now);
        // Update in storage
        try {
            persistence.saveUser(user);
        } catch (RuntimeException failure) {
            restore(user, before);
            throw new IllegalStateException("Could not persist login state", failure);
        }
        usersById.put(user.getUserId(), user);
        // Return authenticated user
        return user;
    }

    /**
     * Checks if a username is already taken.
     * 
     * @param username the username to check
     * @return true if username exists, false otherwise
     */
    public boolean isUsernameTaken(String username) {
        // Search for user with username
        return username != null && usersByUsername.containsKey(normalizeUsername(username));
    }

    /**
     * Validates user's email address format.
     * 
     * @param email the email to validate
     * @return true if email format is valid, false otherwise
     */
    public boolean validateEmail(String email) {
        if (email == null || email.isEmpty()) {
            return false;
        }

        // Check email contains @ and .
        if (!email.contains("@") || !email.contains(".")) {
            return false;
        }

        // Validate email format using regex
        return EMAIL_PATTERN.matcher(email).matches();
    }

    /**
     * Validates password strength and complexity.
     * 
     * @param password the password to validate
     * @return true if password is strong, false otherwise
     */
    public boolean validatePasswordStrength(String password) {
        if (password == null) {
            return false;
        }

        // Check minimum length (8 characters)
        if (password.length() < MIN_PASSWORD_LENGTH) {
            return false;
        }

        // Check for uppercase letter
        boolean hasUppercase = password.matches(".*[A-Z].*");

        // Check for lowercase letter
        boolean hasLowercase = password.matches(".*[a-z].*");

        // Check for digit
        boolean hasDigit = password.matches(".*[0-9].*");

        // Check for special character
        boolean hasSpecialChar = password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};:'\",.<>?/].*");

        return hasUppercase
                && hasLowercase
                && hasDigit
                && hasSpecialChar;
    }

    /**
     * Updates user profile information.
     * 
     * @param user        the user to update
     * @param newEmail    new email address
     * @param newPassword new password (optional)
     * @return updated User object
     */
    public User updateUserProfile(User user, String newEmail, String newPassword) {
        if (user == null) {
            throw new RuntimeException("User not found");
        }
        UserState before = snapshot(user);
        // Validate new email if provided
        if (newEmail != null && !newEmail.isEmpty()) {

            if (!validateEmail(newEmail)) {
                throw new RuntimeException("Invalid email format");
            }

            // Check whether another user already uses this email
            for (User existingUser : usersById.values()) {
                if (!existingUser.getUserId().equals(user.getUserId())
                        && existingUser.getEmail() != null
                        && existingUser.getEmail().equalsIgnoreCase(newEmail)) {

                    throw new RuntimeException(
                            "Email is already registered to another user.");
                }
            }

        }
        // Validate new password if provided
        if (newPassword != null && !newPassword.isEmpty()) {
            if (!validatePasswordStrength(newPassword)) {
                throw new RuntimeException("Password does not meet strength requirements");
            }
        }
        // Apply changes only after all validation and uniqueness checks succeed.
        if (newEmail != null && !newEmail.isEmpty()) {
            user.setEmail(newEmail.trim());
        }
        if (newPassword != null && !newPassword.isEmpty()) {
            user.changePassword(newPassword);
        }
        try {
            persistence.saveUser(user);
            usersById.put(user.getUserId(), user);
        } catch (RuntimeException failure) {
            restore(user, before);
            throw new IllegalStateException("Could not persist user profile update", failure);
        }
        // Return updated user
        return user;
    }

    /**
     * Changes a password only after verifying the current password through the
     * authentication service. The stored credential is never returned to UI code.
     */
    public User changePassword(User user, String currentPassword, String newPassword) {
        if (user == null || !user.verifyPassword(currentPassword)) {
            throw new RuntimeException("Current password is incorrect.");
        }
        if (!validatePasswordStrength(newPassword)) {
            throw new RuntimeException("Password does not meet strength requirements");
        }
        UserState before = snapshot(user);
        user.changePassword(newPassword);
        try {
            persistence.saveUser(user);
            usersById.put(user.getUserId(), user);
        } catch (RuntimeException failure) {
            restore(user, before);
            throw new IllegalStateException("Could not persist password change", failure);
        }
        return user;
    }

    /**
     * Updates user profile information, changing their role if needed.
     * Only an administrator can use this method.
     * 
     * @param user        the user to update
     * @param fullName    new full name
     * @param username    new username
     * @param email        new email address
     * @param newPassword new password (optional)
     * @param newRole     new role for the user
     * @param active    whether the user should be active or not
     * @return updated User object
     */
    public User adminUpdateUser(
            User user,
            String fullName,
            String username,
            String email,
            String newPassword,
            UserRole newRole,
            boolean active) {
        if (user == null) {
            throw new RuntimeException("User not found");
        }
        if (fullName == null || fullName.trim().isEmpty()) {
            throw new IllegalArgumentException("Full name cannot be empty.");
        }
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be empty.");
        }
        if (!validateEmail(email)) {
            throw new RuntimeException("Invalid email format");
        }
        if (newRole == null) {
            throw new IllegalArgumentException("Role is required.");
        }

        String normalizedUsername = username.trim();
        User existingUsernameOwner = usersByUsername.get(normalizedUsername);
        if (existingUsernameOwner != null && !existingUsernameOwner.getUserId().equals(user.getUserId())) {
            throw new RuntimeException("Username already exists");
        }

        for (User existing : usersById.values()) {
            if (!existing.getUserId().equals(user.getUserId())
                    && existing.getEmail() != null
                    && existing.getEmail().equalsIgnoreCase(email.trim())) {
                throw new RuntimeException("Email is already registered to another user.");
            }
        }

        if (newPassword != null && !newPassword.isEmpty() && !validatePasswordStrength(newPassword)) {
            throw new RuntimeException("Password does not meet strength requirements");
        }

        String oldUsername = normalizeUsername(user.getUsername());
        UserState before = snapshot(user);
        User updated = user;

        if (user.getRole() != newRole) {
            String password = newPassword == null || newPassword.isEmpty()
                    ? UUID.randomUUID().toString()
                    : newPassword;
            switch (newRole) {
                case CUSTOMER:
                    updated = new Customer(user.getUserId(), normalizedUsername, password, email.trim(),
                            fullName.trim(), newPassword == null || newPassword.isEmpty());
                    break;
                case ATTENDANT:
                    updated = new Attendant(user.getUserId(), normalizedUsername, password, email.trim(),
                            fullName.trim(), "", newPassword == null || newPassword.isEmpty());
                    break;
                case ADMIN:
                    updated = new Admin(user.getUserId(), normalizedUsername, password, email.trim(), fullName.trim(),
                            "", newPassword == null || newPassword.isEmpty());
                    break;
                default:
                    updated = new User(user.getUserId(), normalizedUsername, password, email.trim(), newRole,
                            fullName.trim(), newPassword == null || newPassword.isEmpty());
                    break;
            }
            if (newPassword == null || newPassword.isEmpty()) {
                updated.copyPasswordHashFrom(user);
            }
            updated.setPhoneNumber(user.getPhoneNumber());
            updated.setCreatedAt(user.getCreatedAt());
            updated.setLastLogin(user.getLastLogin());
        } else {
            updated.setFullName(fullName.trim());
            updated.setUsername(normalizedUsername);
            updated.setEmail(email.trim());
            if (newPassword != null && !newPassword.isEmpty()) {
                updated.changePassword(newPassword);
            }
            updated.setRole(newRole);
        }

        updated.setActive(active);
        try {
            persistence.saveUser(updated);
        } catch (RuntimeException failure) {
            if (updated == user) restore(user, before);
            throw new IllegalStateException("Could not persist administrative user update", failure);
        }
        usersById.put(updated.getUserId(), updated);
        if (!oldUsername.equals(normalizedUsername)) usersByUsername.remove(oldUsername);
        usersByUsername.put(normalizedUsername, updated);
        return updated;
    }

    public User adminUpdateUser(User actor, User user, String fullName, String username,
                                String email, String newPassword, UserRole newRole, boolean active) {
        requireAdmin(actor);
        if (user != null && user.getRole() == UserRole.ADMIN
                && (newRole != UserRole.ADMIN || !active)
                && countActiveAdmins() <= 1) {
            throw new AuthorizationException("The last active administrator cannot be demoted or deactivated.");
        }
        return adminUpdateUser(user, fullName, username, email, newPassword, newRole, active);
    }

    /**
     * Deletes a user account from the system.
     * 
     * @param userId the ID of the user to delete
     * @return true if deletion was successful
     * @throws RuntimeException if user cannot be deleted
     */
    public boolean deleteUser(String userId) throws RuntimeException {
        // Check if user exists
        User user = usersById.get(userId);
        if (user == null) {
            throw new RuntimeException("User not found with ID: " + userId);
        }

        // Prevent deletion of admin users
        if (user.getRole() == UserRole.ADMIN) {
            throw new RuntimeException("Cannot delete admin users");
        }

        // Validate user can be deleted (no active tickets for customers)
        if (user.getRole() == UserRole.CUSTOMER) {
            Customer customer = (Customer) user;

            if (ticketService != null && customer.getTicketIds() != null) {
                int blockingTickets = 0;

                for (String ticketId : customer.getTicketIds()) {
                    try {
                        Ticket ticket = ticketService.getTicketById(ticketId);

                        TicketStatus status = ticket.getStatus();

                        if (status == TicketStatus.ACTIVE ||
                                status == TicketStatus.AWAITING_PAYMENT) {

                            blockingTickets++;
                        }

                    } catch (TicketNotFoundException e) {
                        // Historical ticket no longer exists; don't block deletion.
                    }
                }

                if (blockingTickets > 0) {
                    throw new RuntimeException(
                            "Cannot delete customer with active or unpaid parking tickets. " +
                                    "Blocking tickets: " + blockingTickets);
                }
            }
        }

        // Validate attendant can be deleted (no outstanding transactions)
        if (user.getRole() == UserRole.ATTENDANT) {
            Attendant attendant = (Attendant) user;

            // Check if attendant has processed any tickets
            if (attendant.getProcessedTicketIds() != null && !attendant.getProcessedTicketIds().isEmpty()) {
                throw new RuntimeException("Cannot delete attendant with processed transactions. " +
                        "Processed transactions: " + attendant.getProcessedTicketIds().size());
            }
        }

        // Delete from SQLite first; keep both indexes untouched if persistence fails.
        persistence.deleteUserById(userId);
        usersById.remove(userId);
        usersByUsername.remove(normalizeUsername(user.getUsername()));

        // Return deletion status
        return true;
    }

    public boolean deleteUser(User actor, String userId) throws RuntimeException {
        requireAdmin(actor);
        User target = getUserById(userId);
        if (target.getUserId().equals(actor.getUserId())) {
            throw new AuthorizationException("Administrators cannot delete their own account.");
        }
        if (target.getRole() == UserRole.ADMIN && countActiveAdmins() <= 1) {
            throw new AuthorizationException("The last active administrator cannot be deleted.");
        }
        return deleteUser(userId);
    }

    private void requireAdmin(User actor) {
        if (actor == null || actor.getRole() != UserRole.ADMIN || !actor.isActive()) {
            throw new AuthorizationException("Administrator privileges are required.");
        }
    }

    private int countActiveAdmins() {
        int count = 0;
        for (User user : usersById.values()) {
            if (user.getRole() == UserRole.ADMIN && user.isActive()) count++;
        }
        return count;
    }

    /**
     * Retrieves a user by their ID.
     * 
     * @param userId the ID to search for
     * @return the User object
     * @throws RuntimeException if user is not found
     */
    public User getUserById(String userId) throws RuntimeException {
        // Search for user by ID
        User user = usersById.get(userId);

        // Throw exception if not found
        if (user == null) {
            throw new RuntimeException("User not found with ID: " + userId);
        }

        // Return user
        return user;
    }

    /**
     * Retrieves a user by their username.
     * 
     * @param username the username to search for
     * @return the User object
     * @throws RuntimeException if user is not found
     */
    public User getUserByUsername(String username) throws RuntimeException {
        // Search for user by username
        User user = usersByUsername.get(normalizeUsername(username));

        // Throw exception if not found
        if (user == null) {
            throw new RuntimeException("User not found with username: " + username);
        }

        // Return user
        return user;
    }

    /**
     * Gets all registered users in the system.
     * 
     * @return list of all users
     */
    public List<User> getAllUsers() {
        // Retrieve all users from storage
        return new ArrayList<>(usersById.values());
    }

    /**
     * Checks if a user has admin privileges.
     * 
     * @param user the user to check
     * @return true if user is admin, false otherwise
     */
    public boolean isAdmin(User user) {
        if (user == null) {
            return false;
        }

        // Check user role is ADMIN
        return user.getRole() == UserRole.ADMIN;
    }

    /**
     * Performs a simple role check for a user.
     * Admin has all permissions (true for all roles).
     * 
     * @param user         the user to check
     * @param requiredRole the role required for the operation
     * @return true if user has required permissions, false otherwise
     */
    public boolean hasPermission(User user, UserRole requiredRole) {
        if (user == null || requiredRole == null || user.getRole() == null) {
            return false;
        }

        UserRole userRole = user.getRole();

        return userRole == requiredRole;
    }

    /**
     * Logs out a user from the system.
     * Clears session data or authentication tokens.
     * 
     * @param user the user to logout
     */
    public void logoutUser(User user) {
        if (user == null) {
            return;
        }
        // Update in storage
        usersById.put(user.getUserId(), user);
    }

    private static String normalizeUsername(String username) {
        return username == null ? "" : username.trim();
    }

    private static UserState snapshot(User user) {
        return new UserState(user.getUsername(), user.getEmail(), user.getStoredPasswordHash(), user.getRole(),
                user.getFullName(), user.getPhoneNumber(), user.getCreatedAt(), user.getLastLogin(), user.isActive(),
                user.getFailedLoginAttempts(), user.getLockedUntil());
    }

    private static void restore(User user, UserState state) {
        user.setUsername(state.username());
        user.setEmail(state.email());
        user.copyPasswordHashFrom(new HashCarrier(state.passwordHash()));
        user.setRole(state.role());
        user.setFullName(state.fullName());
        user.setPhoneNumber(state.phoneNumber());
        user.setCreatedAt(state.createdAt());
        user.setLastLogin(state.lastLogin());
        user.setActive(state.active());
        user.restoreLoginState(state.failedAttempts(), state.lockedUntil());
    }

    /** Minimal User subtype used only to restore an opaque hash after a failed write. */
    private static final class HashCarrier extends User {
        private HashCarrier(String hash) {
            super("rollback", "rollback", hash, "rollback@example.com", UserRole.CUSTOMER, "Rollback", true);
        }
    }

    private record UserState(String username, String email, String passwordHash, UserRole role, String fullName,
                             String phoneNumber, LocalDateTime createdAt, LocalDateTime lastLogin, boolean active,
                             int failedAttempts, LocalDateTime lockedUntil) { }

}
