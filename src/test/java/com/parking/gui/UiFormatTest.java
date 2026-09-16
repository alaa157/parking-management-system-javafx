package com.parking.gui;

import com.parking.enums.SpotStatus;
import com.parking.enums.SpotType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UiFormatTest {

    @Test
    void moneyUsesUsCurrencyFormat() {
        assertEquals("$12.50", UiFormat.money(12.5));
    }

    @Test
    void initialsUseFirstAndLastNames() {
        assertEquals("AS", UiFormat.initials("alaa saad"));
    }

    @Test
    void spotTypeUsesReadableName() {
        assertEquals("EV Charging", UiFormat.prettyType(SpotType.EV_CHARGING));
    }

    @Test
    void spotStatusUsesReadableName() {
        assertEquals("Out of Service", UiFormat.statusText(SpotStatus.OUT_OF_SERVICE));
    }
}
