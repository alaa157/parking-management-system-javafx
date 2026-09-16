package com.parking.gui;

import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import javafx.animation.Interpolator;

/** Shared visual language for every JavaFX view. */
public final class DesignTokens {
    private DesignTokens() { }

    public static final String BG = "#0A0E27";
    public static final String CARD = "#141B2D";
    public static final String HOVER = "#1A2332";
    public static final String TEAL = "#00D4AA";
    public static final String TEAL_DARK = "#00B894";
    public static final String ORANGE = "#FF6B35";
    public static final String YELLOW = "#FFB800";
    public static final String RED = "#FF4757";
    public static final String GREEN = "#2ED573";
    public static final String BLUE = "#3498DB";
    public static final String PURPLE = "#9B59B6";
    public static final String TEXT = "#E8ECF1";
    public static final String WHITE = "#FFFFFF";
    public static final String BLACK = "#000000";
    public static final String MUTED = "#7B8BA3";
    public static final String BORDER = "#2A3548";

    // Named one-off palette values used by the native JavaFX stylesheet.
    public static final String SURFACE_HEADER = "#0F152A";
    public static final String SURFACE_COMMAND = "#202B3D";
    public static final String SKELETON_BLOCK = "#202A40";
    public static final String HEADER_MUTED = "#AAB6C8";
    public static final String TEAL_BRIGHT = "#2DE2BC";
    public static final String ORANGE_DARK = "#E5503B";
    public static final String SPOT_AVAILABLE_BG = "#1A3A2A";
    public static final String SPOT_OCCUPIED_BG = "#2A1A1A";
    public static final String SPOT_MAINTENANCE_BG = "#3A2A0A";
    public static final String SPOT_RESERVED_BG = "#1A1A3A";
    public static final String SPOT_EV_BG = "#0A2A2A";
    public static final String SPOT_OUT_OF_SERVICE_BG = "#252A35";
    public static final String SPOT_AVAILABLE_DARK_BG = "#18382B";
    public static final String SPOT_EV_DARK_BG = "#143B3A";
    public static final String SKELETON_SHEEN = "rgba(255,255,255,0.07)";
    public static final String PRINT_BORDER = "#CBD5E1";

    // Alpha colors are palette tokens too; keeping them named prevents CSS-only drift.
    public static final String SHADOW_BLACK_28 = "rgba(0,0,0,0.28)";
    public static final String SHADOW_BLACK_30 = "rgba(0,0,0,0.30)";
    public static final String SHADOW_BLACK_35 = "rgba(0,0,0,0.35)";
    public static final String SHADOW_BLACK_40 = "rgba(0,0,0,0.40)";
    public static final String SHADOW_BLACK_42 = "rgba(0,0,0,0.42)";
    public static final String SHADOW_BLACK_45 = "rgba(0,0,0,0.45)";
    public static final String SHADOW_BLACK_48 = "rgba(0,0,0,0.48)";
    public static final String SHADOW_BLACK_50 = "rgba(0,0,0,0.50)";
    public static final String SHADOW_BLACK_5 = "rgba(0,0,0,0.5)";
    public static final String SHADOW_BLACK_4 = "rgba(0,0,0,0.4)";
    public static final String SHADOW_BLACK_55 = "rgba(0,0,0,0.55)";
    public static final String SHADOW_BLACK_68 = "rgba(0,0,0,0.68)";
    public static final String SHADOW_DARK_SURFACE_45 = "rgba(20,27,45,0.45)";
    public static final String OVERLAY_DARK = "rgba(10,14,39,0.96)";
    public static final String OVERLAY_SURFACE = "rgba(20,27,45,0.96)";
    public static final String GLOW_TEAL_03 = "rgba(0,212,170,0.03)";
    public static final String GLOW_TEAL_20 = "rgba(0,212,170,0.20)";
    public static final String GLOW_TEAL_26 = "rgba(0,212,170,0.26)";
    public static final String GLOW_TEAL_35 = "rgba(0,212,170,0.35)";
    public static final String GLOW_ORANGE_35 = "rgba(255,107,53,0.35)";
    public static final String GLOW_YELLOW_16 = "rgba(255,184,0,.16)";
    public static final String GLOW_RED_16 = "rgba(255,71,87,.16)";
    public static final String GLOW_GREEN_16 = "rgba(46,213,115,.16)";
    public static final String GLOW_BLUE_16 = "rgba(52,152,219,.16)";
    public static final String GLOW_TEAL_10 = "rgba(0,212,170,0.10)";
    public static final String GLOW_TEAL_12 = "rgba(0,212,170,0.12)";
    public static final String GLOW_TEAL_13 = "rgba(0,212,170,0.13)";
    public static final String GLOW_BLUE_10 = "rgba(52,152,219,0.10)";
    public static final String GLOW_BLUE_13 = "rgba(52,152,219,0.13)";
    public static final String GLOW_ORANGE_10 = "rgba(255,107,53,0.10)";
    public static final String GLOW_ORANGE_13 = "rgba(255,107,53,0.13)";
    public static final String GLOW_RED_13 = "rgba(255,71,87,0.13)";
    public static final String MODAL_BACKDROP = "rgba(5,8,24,0.72)";
    public static final String USER_MODAL_BACKDROP = "rgba(5,8,27,0.78)";

    public static final double TYPE_DISPLAY = 30;
    public static final double TYPE_H1 = 24;
    public static final double TYPE_H2 = 18;
    public static final double TYPE_BODY = 14;
    public static final double TYPE_CAPTION = 12;
    public static final double TYPE_MICRO = 10;

    // Phase 2: typography system — single source of truth for font treatment.
    public static final String FONT_FAMILY = "\"Segoe UI\", \"Arial\"";
    public static final double LINE_HEIGHT_TIGHT = 1.15;
    public static final double LINE_HEIGHT_NORMAL = 1.45;
    public static final double LINE_HEIGHT_LOOSE = 1.7;
    public static final double LETTER_SPACING_TIGHT = -0.02;
    public static final double LETTER_SPACING_NORMAL = 0.0;
    public static final double LETTER_SPACING_WIDE = 0.08;
    public static final String FONT_WEIGHT_REGULAR = "400";
    public static final String FONT_WEIGHT_MEDIUM = "600";
    public static final String FONT_WEIGHT_BOLD = "700";

    // Phase 2: spacing scale — 4px base unit, every padding/gap snaps to these.
    public static final double SPACE_1 = 4;
    public static final double SPACE_2 = 8;
    public static final double SPACE_3 = 12;
    public static final double SPACE_4 = 16;
    public static final double SPACE_5 = 20;
    public static final double SPACE_6 = 24;
    public static final double SPACE_8 = 32;
    public static final double SPACE_10 = 40;

    // Phase 2: shape + elevation scale.
    public static final double RADIUS_SM = 8;
    public static final double RADIUS_MD = 12;
    public static final double RADIUS_LG = 16;
    public static final double RADIUS_XL = 24;
    public static final double RADIUS_PILL = 999;
    public static final DurationSpec MOTION_FAST = new DurationSpec(150);
    public static final DurationSpec MOTION_NORMAL = new DurationSpec(250);
    public static final DurationSpec MOTION_SLOW = new DurationSpec(350);

    public record DurationSpec(double millis) { }

    // Phase 4: purposeful motion — one easing language, three named curves.
    public static Interpolator motionInterpolator() { return Interpolator.SPLINE(.4, 0, .2, 1); }
    public static Interpolator motionAccelerate() { return Interpolator.SPLINE(.4, 0, 1, 1); }
    public static Interpolator motionDecelerate() { return Interpolator.SPLINE(0, 0, .2, 1); }
    public static double motionMillis(double millis) { return ThemeManager.get().isReduceMotion() ? 1 : millis; }
    public static javafx.util.Duration motionDuration(DurationSpec spec) {
        return javafx.util.Duration.millis(motionMillis(spec.millis()));
    }

    /** Maps legacy one-off sizes to the shared type scale. */
    public static double typeSize(double requested) {
        if (requested >= 28) return TYPE_DISPLAY;
        if (requested >= 21) return TYPE_H1;
        if (requested >= 16) return TYPE_H2;
        if (requested >= 13) return TYPE_BODY;
        if (requested >= 11) return TYPE_CAPTION;
        return TYPE_MICRO;
    }

    public static String typeClass(double requested) {
        if (requested >= 28) return "type-display";
        if (requested >= 21) return "type-h1";
        if (requested >= 16) return "type-h2";
        if (requested >= 13) return "type-body";
        if (requested >= 11) return "type-caption";
        return "type-micro";
    }

    public static Label text(String value, double requested, String color, boolean bold) {
        Label label = new Label(value == null ? "" : value);
        label.getStyleClass().add(typeClass(requested));
        boolean primaryText = TEXT.equals(color);
        boolean mutedText = MUTED.equals(color);
        if (primaryText) label.getStyleClass().add("theme-text");
        if (mutedText) label.getStyleClass().add("theme-muted");
        String textStyle = primaryText || mutedText ? "" : "-fx-text-fill:" + color + ";";
        label.setStyle("-fx-font-size:" + typeSize(requested) + "px;" + textStyle
                + "-fx-font-weight:" + (bold ? "700" : "400") + ";");
        label.setWrapText(true);
        return label;
    }

    public static Color color(String value) { return Color.web(value); }
}
