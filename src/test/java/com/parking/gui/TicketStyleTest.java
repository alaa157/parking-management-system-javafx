package com.parking.gui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TicketStyleTest {

    @Test
    void customerTicketTableUsesTheApplicationSurfaceInsteadOfDefaultWhite() throws IOException {
        String css = new String(
                TicketStyleTest.class.getResourceAsStream("/parkingos.css.template").readAllBytes(),
                StandardCharsets.UTF_8);

        assertTrue(css.contains(".data-table {\n"
                        + "    -fx-background-color: @BG@;\n"
                        + "    -fx-control-inner-background: @BG@;\n"
                        + "    -fx-table-cell-border-color: @BORDER@;"),
                "customer ticket tables must use the application surface colors");
        assertTrue(css.contains(".data-table .table-cell {\n"
                        + "    -fx-background-color: transparent;\n"
                        + "    -fx-text-fill: @TEXT@;"),
                "customer ticket cells must use the application text colors");
    }

    @Test
    void ticketContainersUseTheApplicationSurfaceInDarkMode() throws IOException {
        String css = new String(
                TicketStyleTest.class.getResourceAsStream("/parkingos.css.template").readAllBytes(),
                StandardCharsets.UTF_8);

        assertTrue(css.contains(".ticket-shell,")
                        && css.contains(".ticket-root,")
                        && css.contains(".ticket-host,"),
                "ticket roots must define an explicit dark background");
        assertTrue(css.contains(".ticket-split")
                        && css.contains(".transparent-scroll > .viewport"),
                "ticket split and scroll viewports must not expose JavaFX white defaults");
    }
}
