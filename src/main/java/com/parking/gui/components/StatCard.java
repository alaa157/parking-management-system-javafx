package com.parking.gui.components;

import com.parking.gui.DesignTokens;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Reusable KPI/statistic card. */
public final class StatCard extends VBox {
    private final Label value;
    public StatCard(String title, String initialValue, String subtitle, String trend, String accent) {
        super(8);
        setPadding(new javafx.geometry.Insets(18)); setMinHeight(145);
        StyleManager.styleCard(this);
        HBox header = new HBox(DesignTokens.text(title, 11, DesignTokens.MUTED, true), spacer(), DesignTokens.text(trend, 10, accent, true));
        header.setAlignment(Pos.CENTER_LEFT);
        value = DesignTokens.text(initialValue, 32, DesignTokens.TEAL, true);
        value.setAccessibleText(title + " value");
        getChildren().addAll(header, value, DesignTokens.text(subtitle, 12, DesignTokens.MUTED, false));
    }
    public Label valueLabel() { return value; }
    private Region spacer() { return com.parking.gui.UiNodes.spacer(); }
}
