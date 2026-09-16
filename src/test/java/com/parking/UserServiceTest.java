package com.parking;

import com.parking.enums.*;
import com.parking.exceptions.*;
import com.parking.model.*;
import com.parking.services.*;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class UserServiceTest extends TestFixtures {

    @Test
    void testRegisterUser() {
        User user = userService.registerUser(
                "john",
                "Password1!",
                "john@example.com",
                UserRole.CUSTOMER
        );

        assertNotNull(user);
        assertEquals("john", user.getUsername());
        assertEquals("john@example.com", user.getEmail());
        assertEquals(UserRole.CUSTOMER, user.getRole());
        assertTrue(userService.isUsernameTaken("john"));
    }


    @Test
    void testDuplicateUsername() {
        assertThrows(
                RuntimeException.class,
                () -> userService.registerUser(
                        "testuser",
                        "Another@123",
                        "another@example.com",
                        UserRole.CUSTOMER
                )
        );
    }


    @Test
    void testInvalidEmail() {
        assertFalse(userService.validateEmail("invalid-email"));
        assertTrue(userService.validateEmail("valid@example.com"));
    }


    @Test
    void testPasswordValidation() {
        assertFalse(
                userService.validatePasswordStrength("Password1")
        );

        assertTrue(
                userService.validatePasswordStrength("Password1!")
        );

        assertFalse(
                userService.validatePasswordStrength("abc")
        );
    }


    @Test
    void testDuplicateEmailIsRejected() {
        assertThrows(
                RuntimeException.class,
                () -> userService.registerUser(
                        "anotheruser",
                        "Password1!",
                        "TEST@EXAMPLE.COM",
                        UserRole.CUSTOMER
                )
        );
    }


    @Test
    void testAuthenticateUser() {
        User authenticated = userService.authenticateUser(
                "testuser",
                "Test@123"
        );

        assertNotNull(authenticated);
        assertEquals(
                testCustomer.getUserId(),
                authenticated.getUserId()
        );
    }


    @Test
    void testInvalidAuthentication() {
        assertThrows(
                RuntimeException.class,
                () -> userService.authenticateUser(
                        "testuser",
                        "WrongPassword"
                )
        );
    }


    @Test
    void testRepeatedAuthenticationFailuresLockTheAccount() {
        for (int i = 0; i < 5; i++) {
            assertThrows(
                    RuntimeException.class,
                    () -> userService.authenticateUser("testuser", "WrongPassword")
            );
        }

        assertThrows(
                RuntimeException.class,
                () -> userService.authenticateUser("testuser", "Test@123")
        );
    }


    @Test
    void testUpdateUserProfile() {
        User updated = userService.updateUserProfile(
                testCustomer,
                "newemail@example.com",
                "NewPass1!"
        );

        assertEquals(
                "newemail@example.com",
                updated.getEmail()
        );

        assertNotNull(
                userService.authenticateUser(
                        "testuser",
                        "NewPass1!"
                )
        );
    }


    @Test
    void testAdminDoesNotImpersonateOtherRoles() {
        User admin = userService.registerUser(
                "testadmin",
                "Admin@123!",
                "admin2@example.com",
                UserRole.ADMIN
        );

        assertTrue(
                userService.hasPermission(admin, UserRole.ADMIN)
        );

        assertFalse(
                userService.hasPermission(admin, UserRole.CUSTOMER)
        );

        assertFalse(
                userService.hasPermission(admin, UserRole.ATTENDANT)
        );
    }


    @Test
    void testCustomerCannotAccessAnotherCustomersTicket() throws Exception {
        Customer otherCustomer = (Customer) userService.registerUser(
                "othercustomer", "Other@123!", "other@example.com", UserRole.CUSTOMER);
        Vehicle otherVehicle = new Vehicle(
                "VEH-OTHER", "OTHER-123", VehicleType.CAR,
                "Honda", "Civic", "Blue", 2021, otherCustomer.getUserId());
        Ticket otherTicket = parkingService.vehicleEntry(otherVehicle);

        assertThrows(
                com.parking.exceptions.AuthorizationException.class,
                () -> ticketService.getTicketById(testCustomer, otherTicket.getTicketId())
        );
    }


    @Test
    void testNonAdminCannotManageUsersThroughSecuredApi() {
        assertThrows(
                com.parking.exceptions.AuthorizationException.class,
                () -> userService.deleteUser(testCustomer, "DOES-NOT-EXIST")
        );
    }


    @Test
    void testTicketServiceDefaultsToNull() {
        UserService isolatedUserService = new UserService();

        assertThrows(
                RuntimeException.class,
                () -> isolatedUserService.deleteUser("DOES-NOT-EXIST")
        );
    }


    @Test
    void testCustomerAddVehicle() {
        assertEquals(1, testCustomer.getVehicleIds().size());
        assertTrue(
                testCustomer.getVehicleIds().contains("VEH-TEST")
        );

        // Adding same vehicle again should not duplicate it
        testCustomer.addVehicle(testVehicle);

        assertEquals(1, testCustomer.getVehicleIds().size());
    }


    @Test
    void testCustomerRemoveVehicle() {
        testCustomer.removeVehicle("VEH-TEST");

        assertFalse(
                testCustomer.getVehicleIds().contains("VEH-TEST")
        );
    }


    @Test
    void testCustomerWallet() {
        assertEquals(
                0.0,
                testCustomer.getWalletBalance(),
                0.001
        );

        testCustomer.addWalletBalance(100.0);

        assertEquals(
                100.0,
                testCustomer.getWalletBalance(),
                0.001
        );

        assertTrue(
                testCustomer.hasSufficientBalance(50.0)
        );

        assertTrue(
                testCustomer.deductWalletBalance(50.0)
        );

        assertEquals(
                50.0,
                testCustomer.getWalletBalance(),
                0.001
        );

        assertFalse(
                testCustomer.deductWalletBalance(100.0)
        );
    }


    @Test
    void testInvalidUsernameSetter() {
        assertThrows(
                IllegalArgumentException.class,
                () -> testCustomer.setUsername("")
        );
    }


    @Test
    void testInvalidPasswordSetter() {
        assertThrows(
                IllegalArgumentException.class,
                () -> testCustomer.setPassword("")
        );
    }


    @Test
    void testInvalidEmailSetter() {
        assertThrows(
                IllegalArgumentException.class,
                () -> testCustomer.setEmail("")
        );
    }


    @Test
    void testAdminPermissions() {
        Admin admin = new Admin(
                "ADMIN-1",
                "admin",
                "Admin@123",
                "admin@test.com",
                "Admin User",
                "IT"
        );

        assertTrue(
                admin.hasPermission("VIEW_REPORTS")
        );

        assertTrue(
                admin.hasPermission("MANAGE_USERS")
        );

        admin.addPermission("TEST_PERMISSION");

        assertTrue(
                admin.hasPermission("TEST_PERMISSION")
        );

        admin.removePermission("TEST_PERMISSION");

        assertFalse(
                admin.hasPermission("TEST_PERMISSION")
        );
    }


    @Test
    void testAttendantTransactions() {
        Attendant attendant = new Attendant(
                "ATT-1",
                "attendant",
                "Attendant@123",
                "attendant@test.com",
                "Attendant User",
                "MORNING"
        );

        assertTrue(
                attendant.processTransaction(
                        "TICKET-1",
                        50.0
                )
        );

        assertFalse(
                attendant.processTransaction(
                        "TICKET-1",
                        50.0
                )
        );

        assertEquals(
                1,
                attendant.getTotalTransactions()
        );

        assertEquals(
                50.0,
                attendant.getTotalRevenue(),
                0.001
        );
    }


    @Test
    void testAttendantDuty() {
        Attendant attendant = new Attendant(
                "ATT-2",
                "attendant2",
                "Attendant@123",
                "attendant2@test.com",
                "Attendant User",
                "MORNING"
        );

        assertFalse(
                attendant.isOnDuty()
        );

        attendant.startDuty();

        assertTrue(
                attendant.isOnDuty()
        );

        attendant.endDuty();

        assertFalse(
                attendant.isOnDuty()
        );
    }


}
