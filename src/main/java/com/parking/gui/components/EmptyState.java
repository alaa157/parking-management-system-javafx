package com.parking.gui.components;

import com.parking.gui.DesignTokens;
import com.parking.gui.IconView;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;

/** Component-library empty state with optional action. */
public final class EmptyState extends VBox {
    public EmptyState(IconView.Name icon, String title, String message, String actionText, Runnable action) {
        super(10); setAlignment(Pos.CENTER); getStyleClass().addAll("empty-state", "z-1");
        getChildren().addAll(IconView.of(icon, 52, DesignTokens.TEAL), DesignTokens.text(title, 18, DesignTokens.TEXT, true), DesignTokens.text(message, 13, DesignTokens.MUTED, false));
        if (actionText != null && action != null) { Button b = new Button(actionText); StyleManager.styleButton(b, StyleManager.ButtonType.PRIMARY); b.setOnAction(e -> action.run()); getChildren().add(b); }
    }
}
