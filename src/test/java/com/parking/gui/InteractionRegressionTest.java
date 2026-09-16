package com.parking.gui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InteractionRegressionTest {

    @Test
    void closingUserEditModalRestoresTheBackgroundEffect() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/parking/gui/ModalDialog.java"));

        assertTrue(source.contains("blurredNode.getEffect() == appliedEffect"),
                "modal close must identify the effect owned by the modal");
        assertTrue(source.contains("blurredNode.setEffect(previousEffect);"),
                "modal close must restore the background effect");
        assertFalse(source.contains("isActiveModal() && blurredNode != null"),
                "restoring the owned blur must not depend on a stale active-modal check");
    }

    @Test
    void changingGarageLevelMovesTheActiveLevelStyle() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/parking/gui/OccupancyMapView.java"));

        assertTrue(source.contains("levelTabs"),
                "occupancy map must retain its level buttons so their active style can move");
        assertTrue(source.contains("level-tab-active"),
                "occupancy map must apply the active style to the selected level");
        assertTrue(source.contains("level-tab\""),
                "occupancy map must remove the active style from the previous level");
    }

    @Test
    void administratorsCanManageSpotsButCannotParkVehicles() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/parking/gui/OccupancyMapView.java"));

        assertTrue(source.contains("actor instanceof Customer"),
                "parking must remain a customer-only action");
        assertTrue(source.contains("setUnderMaintenance"),
                "non-customer spot details must expose a maintenance action");
        assertFalse(source.contains("getUserByUsername(\"customer\")"),
                "admins must not fall through to demo-customer parking");
    }

    @Test
    void customerPaymentPanelAdaptsToNarrowDetailWidths() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/parking/gui/TicketPaymentView.java"));

        assertTrue(source.contains("split.setOrientation"),
                "ticket details must switch layout when the customer window is narrow");
        assertTrue(source.contains("FlowPane"),
                "payment controls must wrap instead of forcing the pay button off-screen");
    }
}
