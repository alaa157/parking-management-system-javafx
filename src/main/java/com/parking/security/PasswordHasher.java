package com.parking.security;

import org.mindrot.jbcrypt.BCrypt;

public final class PasswordHasher {

    private static final int WORK_FACTOR = 12;

    private PasswordHasher() {
    }

    public static String hashPassword(String password) {
        if (password == null) {
            throw new IllegalArgumentException("Password cannot be null.");
        }

        return BCrypt.hashpw(password, BCrypt.gensalt(WORK_FACTOR));
    }

    public static boolean verifyPassword(String password, String storedPassword) {
        if (password == null || storedPassword == null) {
            return false;
        }

        if (!storedPassword.startsWith("$2a$")
                && !storedPassword.startsWith("$2b$")
                && !storedPassword.startsWith("$2y$")) {
            return false;
        }

        try {
            return BCrypt.checkpw(password, storedPassword);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return false;
        }
    }
}
