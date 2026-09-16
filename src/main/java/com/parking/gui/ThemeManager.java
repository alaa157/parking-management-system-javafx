package com.parking.gui;

import javafx.scene.Scene;
import javafx.scene.Parent;

import java.util.prefs.Preferences;

/** Central in-memory/persisted theme preference for the desktop application. */
public final class ThemeManager {
    private static final ThemeManager INSTANCE = new ThemeManager();
    private static final String KEY = "lightTheme";
    private static final String CONTRAST_KEY = "highContrast";
    private static final String MOTION_KEY = "reduceMotion";
    private static final String DENSITY_KEY = "density";
    private static final String SIDEBAR_COLLAPSED_KEY = "sidebarCollapsed";
    private final Preferences prefs = Preferences.userNodeForPackage(ThemeManager.class);
    private boolean light;

    private ThemeManager() {
        light = prefs.getBoolean(KEY, false);
    }

    public static ThemeManager get() { return INSTANCE; }
    public boolean isLight() { return light; }
    public boolean isHighContrast() { return prefs.getBoolean(CONTRAST_KEY, false); }
    public boolean isReduceMotion() { return prefs.getBoolean(MOTION_KEY, false)
            || Boolean.getBoolean("parkingos.reduceMotion"); }
    public String getDensity() { return prefs.get(DENSITY_KEY, "Comfortable"); }
    public boolean isSidebarCollapsed() { return prefs.getBoolean(SIDEBAR_COLLAPSED_KEY, false); }
    public void setSidebarCollapsed(boolean value) { prefs.putBoolean(SIDEBAR_COLLAPSED_KEY, value); }

    public void setLight(boolean value) {
        light = value;
        prefs.putBoolean(KEY, value);
    }

    public void toggle() { setLight(!light); }

    public void setHighContrast(boolean value) { prefs.putBoolean(CONTRAST_KEY, value); }
    public void setReduceMotion(boolean value) { prefs.putBoolean(MOTION_KEY, value); }
    public void setDensity(String value) { prefs.put(DENSITY_KEY, value == null ? "Comfortable" : value); }

    public void apply(Scene scene) {
        if (scene == null || scene.getRoot() == null) return;
        apply(scene.getRoot());
    }

    public void apply(Parent root) {
        if (root == null) return;
        if (light) {
            if (!root.getStyleClass().contains("theme-light")) root.getStyleClass().add("theme-light");
            root.getStyleClass().remove("theme-dark");
        } else {
            if (!root.getStyleClass().contains("theme-dark")) root.getStyleClass().add("theme-dark");
            root.getStyleClass().remove("theme-light");
        }
        root.getStyleClass().removeAll("high-contrast", "reduce-motion", "density-comfortable", "density-compact", "density-dense");
        if (isHighContrast()) root.getStyleClass().add("high-contrast");
        if (isReduceMotion()) root.getStyleClass().add("reduce-motion");
        root.getStyleClass().add("density-" + getDensity().toLowerCase().replace(' ', '-'));
    }
}
