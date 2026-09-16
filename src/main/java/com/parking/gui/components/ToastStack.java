package com.parking.gui.components;

import javafx.geometry.Pos;
import javafx.scene.layout.VBox;

/** Shared toast host component; ToastManager owns its lifecycle and content. */
public class ToastStack extends VBox {
    public ToastStack() { setAlignment(Pos.BOTTOM_RIGHT); setSpacing(10); setPickOnBounds(false); getStyleClass().add("toast-stack"); StyleManager.styleLayer(this, 20); }
}
