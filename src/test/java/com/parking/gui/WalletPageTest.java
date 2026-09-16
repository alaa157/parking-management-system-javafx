package com.parking.gui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WalletPageTest {

    @Test
    void customerWalletPageOffersAnAddBalanceAction() throws IOException {
        String source = Files.readString(Path.of("src/main/java/com/parking/gui/WalletView.java"));

        assertTrue(source.contains("Button addBalance = new Button(\"Add balance\");"),
                "wallet page must expose an add balance action");
        assertTrue(source.contains("addBalance.setOnAction(e -> showAddBalanceDialog(shell));"),
                "add balance action must open the wallet top-up flow");
    }
}
