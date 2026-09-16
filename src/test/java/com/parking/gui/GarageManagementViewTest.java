package com.parking.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GarageManagementViewTest {
    @Test
    void validatesGarageFormValuesBeforePersistence() {
        assertNull(GarageManagementView.validateGarageInput("G-1", "North", "Address", "2", "5.0", "USD", "0", "5", "48"));
        assertEquals("Garage ID is required.", GarageManagementView.validateGarageInput("", "North", "Address", "2", "5.0", "USD", "0", "5", "48"));
        assertEquals("Total levels must be a positive whole number.", GarageManagementView.validateGarageInput("G-1", "North", "Address", "0", "5.0", "USD", "0", "5", "48"));
        assertEquals("Hourly rate must be a non-negative number.", GarageManagementView.validateGarageInput("G-1", "North", "Address", "2", "-1", "USD", "0", "5", "48"));
    }
}
