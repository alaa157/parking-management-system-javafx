package com.parking.gui.components;

import com.parking.gui.DesignTokens;
import com.parking.gui.ThemeManager;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;

/** Central styling API for shared JavaFX components and application layers. */
public final class StyleManager {
    public enum ButtonType { PRIMARY, OUTLINE, GHOST, DANGER, ICON }
    public static final String COLOR_BG = DesignTokens.BG;
    public static final String COLOR_CARD = DesignTokens.CARD;
    public static final String COLOR_TEAL = DesignTokens.TEAL;
    public static final String COLOR_TEXT = DesignTokens.TEXT;
    public static final String COLOR_MUTED = DesignTokens.MUTED;
    public static final double DURATION_FAST = DesignTokens.MOTION_FAST.millis();
    public static final double DURATION_NORMAL = DesignTokens.MOTION_NORMAL.millis();
    public static final double DURATION_SLOW = DesignTokens.MOTION_SLOW.millis();

    private StyleManager() { }

    public static void injectThemeStylesheets(Scene scene) {
        if (scene == null) return;
        String css = StyleManager.class.getResource("/parkingos.css").toExternalForm();
        if (!scene.getStylesheets().contains(css)) scene.getStylesheets().add(css);
        ThemeManager.get().apply(scene);
    }

    public static void styleCard(Node node) { add(node, "card", "z-2"); }
    public static void styleLayer(Node node, int z) { add(node, z >= 30 ? "z-30" : z >= 20 ? "z-20" : z >= 10 ? "z-10" : z >= 5 ? "z-5" : z >= 2 ? "z-2" : z == 1 ? "z-1" : "z-0"); node.setViewOrder(-z); }
    public static void styleButton(Node node, ButtonType type) {
        String cls = switch (type) { case PRIMARY -> "primary-button"; case OUTLINE -> "outline-button"; case GHOST -> "ghost-button"; case DANGER -> "danger-button"; case ICON -> "icon-button"; };
        add(node, cls, "motion-normal", "z-2");
    }
    public static void apply(Node root) { if (root instanceof Parent p) ThemeManager.get().apply(p); }
    private static void add(Node node, String... classes) { if (node != null) for (String cls : classes) if (!node.getStyleClass().contains(cls)) node.getStyleClass().add(cls); }
}
