package com.parking.gui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;

/** Small dependency-free SVG icon set used instead of platform glyphs. */
public final class IconView extends StackPane {
    public enum Name {
        HOME, PARKING, TICKET, WALLET, USERS, CHART, SETTINGS, REFRESH, CLOSE,
        GRID, LIST, WARNING, CLOCK, EDIT, DELETE, CALENDAR, CARD, CASH, EYE,
        EYE_OFF, CHECK, ERROR, INFO, BELL, USER, SECURITY, NOTIFICATIONS,
        GARAGE, PALETTE, DESKTOP, MOBILE, BACK, SEARCH
    }

    private IconView(Name name, double size, String color) {
        setMinSize(size, size);
        setPrefSize(size, size);
        setMaxSize(size, size);
        setAlignment(Pos.CENTER);
        setAccessibleText(name.name().toLowerCase().replace('_', ' '));
        SVGPath path = new SVGPath();
        path.setContent(pathFor(name));
        path.setFill(Color.TRANSPARENT);
        path.setStroke(Color.web(color));
        path.setStrokeWidth(Math.max(1.4, size / 8));
        path.setStrokeLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        path.setStrokeLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);
        getChildren().add(path);
    }

    public static Node of(Name name, double size, String color) {
        return new IconView(name, size, color);
    }

    private static String pathFor(Name n) {
        return switch (n) {
            case HOME -> "M3 11L12 3L21 11V21H14V15H10V21H3Z";
            case PARKING -> "M4 4H20V20H4Z M8 17V7H12A3 3 0 0 1 12 13H8 M8 10H12";
            case TICKET -> "M4 6H20V10A2 2 0 0 0 20 14V18H4V14A2 2 0 0 0 4 10Z M9 9V15 M13 9V15";
            case WALLET -> "M3 6H19A2 2 0 0 1 21 8V18A2 2 0 0 1 19 20H5A2 2 0 0 1 3 18Z M3 9H21 M16 14H18";
            case USERS -> "M16 21V19A4 4 0 0 0 12 15H8A4 4 0 0 0 4 19V21 M10 11A4 4 0 1 0 10 3A4 4 0 0 0 10 11 M17 11A3 3 0 1 0 17 5";
            case CHART -> "M4 19V5 M4 19H21 M8 16V12 M12 16V8 M16 16V5 M20 16V10";
            case SETTINGS -> "M12 15.5A3.5 3.5 0 1 0 12 8.5A3.5 3.5 0 0 0 12 15.5Z M19.4 15A1.7 1.7 0 0 0 19.7 16.9L20 18L18 20L16.9 19.7A1.7 1.7 0 0 0 15 20.4L14.5 21H9.5L9 20.4A1.7 1.7 0 0 0 7.1 19.7L6 20L4 18L4.3 16.9A1.7 1.7 0 0 0 4 15L3 14.5V9.5L4 9A1.7 1.7 0 0 0 4.3 7.1L4 6L6 4L7.1 4.3A1.7 1.7 0 0 0 9 3.6L9.5 3H14.5L15 3.6A1.7 1.7 0 0 0 16.9 4.3L18 4L20 6L19.7 7.1A1.7 1.7 0 0 0 20 9L21 9.5V14.5Z";
            case REFRESH -> "M20 11A8 8 0 0 0 6 6L4 8 M4 4V8H8 M4 13A8 8 0 0 0 18 18L20 16 M20 20V16H16";
            case CLOSE -> "M5 5L19 19 M19 5L5 19";
            case GRID -> "M4 4H10V10H4Z M14 4H20V10H14Z M4 14H10V20H4Z M14 14H20V20H14Z";
            case LIST -> "M5 6H5.1 M9 6H20 M5 12H5.1 M9 12H20 M5 18H5.1 M9 18H20";
            case WARNING -> "M12 4L21 20H3Z M12 9V14 M12 17V17.1";
            case CLOCK -> "M12 21A9 9 0 1 0 12 3A9 9 0 0 0 12 21Z M12 7V12L15 14";
            case EDIT -> "M4 20H8L19 9L15 5L4 16Z M13 7L17 11";
            case DELETE -> "M5 7H19 M10 11V17 M14 11V17 M7 7L8 20H16L17 7 M9 7V4H15V7";
            case CALENDAR -> "M5 4V7 M19 4V7 M4 6H20V20H4Z M4 10H20";
            case CARD -> "M3 6H21V18H3Z M3 10H21";
            case CASH -> "M4 6H20V18H4Z M8 12A4 4 0 1 0 16 12A4 4 0 0 0 8 12Z";
            case EYE -> "M2 12S6 5 12 5S22 12 22 12S18 19 12 19S2 12 2 12Z M12 15A3 3 0 1 0 12 9A3 3 0 0 0 12 15Z";
            case EYE_OFF -> "M3 3L21 21 M10.6 10.6A2 2 0 0 0 13.4 13.4 M9.9 5.3A11 11 0 0 1 12 5C18 5 22 12 22 12A18 18 0 0 1 17.5 17.5 M6.5 6.5C3.7 8.5 2 12 2 12S6 19 12 19A10 10 0 0 0 14.1 18.7";
            case CHECK -> "M5 12L10 17L19 7";
            case ERROR -> "M6 6L18 18 M18 6L6 18";
            case INFO -> "M12 21A9 9 0 1 0 12 3A9 9 0 0 0 12 21Z M12 10V16 M12 7V7.1";
            case BELL -> "M18 8A6 6 0 0 0 6 8C6 15 3 15 3 17H21C21 15 18 15 18 8 M10 20H14";
            case USER -> "M20 21V19A4 4 0 0 0 16 15H8A4 4 0 0 0 4 19V21 M12 11A4 4 0 1 0 12 3A4 4 0 0 0 12 11Z";
            case SECURITY -> "M12 3L19 6V11C19 16 16 19 12 21C8 19 5 16 5 11V6Z M9 12L11 14L15 10";
            case NOTIFICATIONS -> "M18 8A6 6 0 0 0 6 8C6 15 4 15 4 17H20C20 15 18 15 18 8 M10 20H14";
            case GARAGE -> "M3 10L12 3L21 10V20H3Z M7 20V13H17V20 M9 16H15";
            case PALETTE -> "M12 3A9 9 0 0 0 12 21H15A2 2 0 0 0 15 17H14A2 2 0 0 1 14 13H18A3 3 0 0 0 18 7A9 9 0 0 0 12 3Z M8 9V9.1 M12 7V7.1 M16 9V9.1";
            case DESKTOP -> "M3 4H21V16H3Z M8 20H16 M12 16V20";
            case MOBILE -> "M7 3H17V21H7Z M11 18H13";
            case BACK -> "M19 12H5 M11 6L5 12L11 18";
            case SEARCH -> "M11 19A8 8 0 1 0 11 3A8 8 0 0 0 11 19Z M17 17L21 21";
        };
    }
}
