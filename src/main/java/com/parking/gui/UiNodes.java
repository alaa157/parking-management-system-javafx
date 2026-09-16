package com.parking.gui;

import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

import static com.parking.gui.DesignTokens.BG;
import com.parking.gui.components.StyleManager;

import static com.parking.gui.DesignTokens.MUTED;

public final class UiNodes {
    private UiNodes() {
    }

    public static Region spacer() {
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }

    public static Separator separator() {
        Separator separator = new Separator();
        separator.getStyleClass().add("separator-line");
        return separator;
    }

    public static Label formLabel(String text) {
        Label label = DesignTokens.text(text, 10, MUTED, true);
        label.getStyleClass().add("form-label");
        return label;
    }

    public static Label label(String text, double size, String color, boolean bold) {
        return DesignTokens.text(text, size, color, bold);
    }

    public static Scene themedScene(Parent root, double width, double height) {
        Scene scene = new Scene(root, width, height);
        scene.setFill(Color.web(ThemeManager.get().isLight() ? LightDesignTokens.LIGHT_BG : BG));
        try {
            StyleManager.injectThemeStylesheets(scene);
        } catch (Exception ignored) {
            // Inline styles keep the UI usable without resources.
        }
        return scene;
    }

    public static Button iconButton(IconView.Name icon) {
        Button button = new Button(null, IconView.of(icon, 18, MUTED));
        button.setAccessibleText(icon.name().toLowerCase(java.util.Locale.ROOT) + " button");
        button.getStyleClass().add("icon-button");
        button.setPrefSize(32, 32);
        button.setMinSize(32, 32);
        button.setMaxSize(32, 32);
        button.setFocusTraversable(false);
        return button;
    }
}
