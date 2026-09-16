package com.parking.gui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LightThemeTest {

    @Test
    void semanticTextColorsCanSwitchAwayFromDarkInlineColors() throws IOException {
        String source = Files.readString(Path.of("src/main/java/com/parking/gui/DesignTokens.java"));
        String css = new String(
                LightThemeTest.class.getResourceAsStream("/parkingos.css.template").readAllBytes(),
                StandardCharsets.UTF_8);

        assertTrue(source.contains("theme-text"), "primary labels need a theme-aware semantic class");
        assertTrue(source.contains("theme-muted"), "muted labels need a theme-aware semantic class");
        assertTrue(css.contains(".theme-light .theme-text { -fx-text-fill: @LIGHT_TEXT@; }"),
                "light mode must recolor primary labels");
        assertTrue(css.contains(".theme-light .theme-muted { -fx-text-fill: @LIGHT_MUTED@; }"),
                "light mode must recolor muted labels");
    }

    @Test
    void sharedDarkSurfacesHaveLightThemeOverrides() throws IOException {
        String css = new String(
                LightThemeTest.class.getResourceAsStream("/parkingos.css.template").readAllBytes(),
                StandardCharsets.UTF_8);

        assertTrue(css.contains(".theme-light .dark-scroll > .viewport"),
                "scrollable pages must use the light background");
        assertTrue(css.contains(".theme-light .map-shell"),
                "garage pages must use the light background");
        assertTrue(css.contains(".theme-light .settings-card"),
                "settings cards must use light surfaces");
        assertTrue(css.contains(".theme-light .ticket-card"),
                "ticket cards must use light surfaces");
        assertTrue(css.contains(".theme-light .analytics-kpi"),
                "analytics cards must use light surfaces");
        assertTrue(css.contains(".theme-light .login-card"),
                "login and registration cards must use light surfaces");
        assertTrue(css.contains(".theme-light .ticket-panel"),
                "ticket panels must use light surfaces");
    }

    @Test
    void themeToggleForcesTheLiveSceneToRefreshItsStyles() throws IOException {
        String source = Files.readString(Path.of("src/main/java/com/parking/gui/ParkingApplication.java"));

        assertTrue(source.contains("private void applyThemeChanges()"),
                "theme changes need a shared runtime refresh path");
        assertTrue(source.contains("getRoot().applyCss();"),
                "theme changes must apply CSS immediately to the live scene");
        assertTrue(source.contains("light -> applyThemeChanges()"),
                "settings theme clicks must use the runtime refresh path");
    }

    @Test
    void standaloneLoginRootAndCredentialsUseLightThemeTokens() throws IOException {
        String css = new String(
                LightThemeTest.class.getResourceAsStream("/parkingos.css.template").readAllBytes(),
                StandardCharsets.UTF_8);
        String source = Files.readString(Path.of("src/main/java/com/parking/gui/ParkingApplication.java"));

        assertTrue(css.contains(".theme-light.app-root"),
                "standalone login and registration roots need a direct light selector");
        assertTrue(css.contains(".theme-light.app-root .login-card"),
                "login cards must inherit the standalone light root");
        assertTrue(css.contains(".theme-light.app-root .form-label"),
                "username and password labels must be readable in light mode");
        assertTrue(css.contains(".theme-light.app-root .dark-input"),
                "username and password fields must use light input colors");
        assertTrue(source.contains("LightDesignTokens.LIGHT_BG"),
                "the Scene fill must use the active theme background");
    }
}
