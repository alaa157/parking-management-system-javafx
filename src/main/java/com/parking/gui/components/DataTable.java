package com.parking.gui.components;

import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableView;

/** TableView with consistent project styling and multi-selection defaults. */
public class DataTable<T> extends TableView<T> {
    public DataTable() {
        getStyleClass().add("data-table");
        StyleManager.styleLayer(this, 1);
        getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        setColumnResizePolicy(CONSTRAINED_RESIZE_POLICY);
    }
}
