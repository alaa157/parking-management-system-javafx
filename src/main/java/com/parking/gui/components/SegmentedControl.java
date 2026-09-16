package com.parking.gui.components;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import java.util.function.Consumer;

/** Reusable segmented choice control. */
public final class SegmentedControl extends HBox {
    public SegmentedControl(String[] values, String selected, Consumer<String> callback) {
        super(2); setAlignment(Pos.CENTER_LEFT); getStyleClass().add("segmented-control");
        for (String value : values) {
            Button button = new Button(value); button.getStyleClass().add("segment-button");
            if (value.equals(selected)) button.getStyleClass().add("segment-selected");
            button.setOnAction(e -> { for (Node child : getChildren()) child.getStyleClass().remove("segment-selected"); button.getStyleClass().add("segment-selected"); callback.accept(value); });
            getChildren().add(button);
        }
    }
}
