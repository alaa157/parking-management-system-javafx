package com.parking.gui;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import com.parking.gui.components.ToastStack;

import java.util.HashMap;
import java.util.Map;

/** Reusable animated toast stack for ParkingOS. */
public final class ToastManager {
    public enum Type {
        SUCCESS, ERROR, WARNING, INFO
    }

    private final ToastStack stack = new ToastStack();
    private final Map<Node, ToastLifecycle> activeToasts = new HashMap<>();

    public ToastManager() {
        stack.setPadding(new Insets(16));
        stack.setPickOnBounds(false);
    }

    public Node getOverlay() {
        return stack;
    }

    public void showSuccess(String title, String message) {
        show(Type.SUCCESS, title, message);
    }

    public void showError(String title, String message) {
        show(Type.ERROR, title, message);
    }

    public void showWarning(String title, String message) {
        show(Type.WARNING, title, message);
    }

    public void showInfo(String title, String message) {
        show(Type.INFO, title, message);
    }

    public void show(Type type, String title, String message) {
        NotificationCenter.playSound(type.name());
        Node icon = IconView.of(iconFor(type), 26, colorFor(type));
        icon.getStyleClass().add("toast-icon-" + type.name().toLowerCase());

        VBox copy = new VBox(2,
                text(title, 14, DesignTokens.TEXT, true),
                text(message, 13, DesignTokens.MUTED, false));
        copy.setMaxWidth(270);

        Button close = new Button(null, IconView.of(IconView.Name.CLOSE, 14, DesignTokens.MUTED));
        close.setAccessibleText("Dismiss notification");
        close.getStyleClass().add("toast-close");

        HBox toast = new HBox(10, icon, copy, close);
        close.setOnAction(e -> dismiss(toast));
        toast.setAlignment(Pos.CENTER_LEFT);
        toast.setPadding(new Insets(12, 12, 12, 12));
        toast.setMaxWidth(390);
        toast.getStyleClass().add("toast-card");
        toast.getStyleClass().add("toast-" + type.name().toLowerCase());
        // Phase 4: icon pop — overshoot scale sells the arrival without shouting.
        icon.setScaleX(0.4); icon.setScaleY(0.4);
        javafx.animation.RotateTransition iconSpin = new javafx.animation.RotateTransition(DesignTokens.motionDuration(DesignTokens.MOTION_NORMAL), icon);
        iconSpin.setFromAngle(-18); iconSpin.setToAngle(0);
        iconSpin.setInterpolator(DesignTokens.motionDecelerate());
        javafx.animation.ScaleTransition iconGrow = new javafx.animation.ScaleTransition(DesignTokens.motionDuration(DesignTokens.MOTION_NORMAL), icon);
        iconGrow.setToX(1); iconGrow.setToY(1);
        iconGrow.setInterpolator(DesignTokens.motionDecelerate());
        toast.getProperties().put("toastIconPop", (Runnable) () -> { iconSpin.play(); iconGrow.play(); });

        stack.getChildren().add(toast);
        toast.setTranslateX(430);
        toast.setOpacity(0);
        // Phase 4: toast progress bar — 4s lifetime is visible, pause on hover.
        javafx.scene.layout.Region progress = new javafx.scene.layout.Region();
        progress.getStyleClass().add("toast-progress");
        progress.setMaxWidth(390);
        ((VBox) copy).getChildren().add(progress);
        javafx.animation.Timeline life = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.ZERO,
                        new javafx.animation.KeyValue(progress.scaleXProperty(), 1, DesignTokens.motionInterpolator())),
                new javafx.animation.KeyFrame(javafx.util.Duration.seconds(4),
                        new javafx.animation.KeyValue(progress.scaleXProperty(), 0, DesignTokens.motionInterpolator())));
        toast.setOnMouseEntered(e -> { life.pause(); });
        toast.setOnMouseExited(e -> { life.play(); });

        TranslateTransition inX = new TranslateTransition(DesignTokens.motionDuration(DesignTokens.MOTION_NORMAL), toast);
        inX.setFromX(430);
        inX.setToX(0);
        inX.setInterpolator(DesignTokens.motionDecelerate());
        FadeTransition inFade = new FadeTransition(DesignTokens.motionDuration(DesignTokens.MOTION_NORMAL), toast);
        inFade.setFromValue(0);
        inFade.setToValue(1);
        inFade.setInterpolator(DesignTokens.motionDecelerate());
        ToastLifecycle lifecycle = new ToastLifecycle(new ParallelTransition(inX, inFade));
        activeToasts.put(toast, lifecycle);
        lifecycle.enter.play();
        Object iconPop = toast.getProperties().get("toastIconPop");
        if (iconPop instanceof Runnable r) r.run();
        life.play();
        lifecycle.progress = life;

        javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(4));
        lifecycle.pause = pause;
        pause.setOnFinished(e -> dismiss(toast));
        pause.play();
    }

    private void dismiss(Node toast) {
        ToastLifecycle lifecycle = activeToasts.remove(toast);
        if (lifecycle == null || !stack.getChildren().contains(toast))
            return;
        lifecycle.stop();
        TranslateTransition outX = new TranslateTransition(DesignTokens.motionDuration(DesignTokens.MOTION_FAST), toast);
        outX.setToX(430);
        outX.setInterpolator(DesignTokens.motionAccelerate());
        FadeTransition outFade = new FadeTransition(DesignTokens.motionDuration(DesignTokens.MOTION_FAST), toast);
        outFade.setToValue(0);
        outFade.setInterpolator(DesignTokens.motionAccelerate());
        ParallelTransition out = new ParallelTransition(outX, outFade);
        lifecycle.exit = out;
        out.setOnFinished(e -> {
            stack.getChildren().remove(toast);
            activeToasts.remove(toast);
        });
        out.play();
    }

    /** Stops all toast timers/transitions before the owning application closes. */
    public void clear() {
        for (ToastLifecycle lifecycle : activeToasts.values()) lifecycle.stop();
        activeToasts.clear();
        stack.getChildren().clear();
    }

    private static final class ToastLifecycle {
        private final ParallelTransition enter;
        private javafx.animation.PauseTransition pause;
        private javafx.animation.Timeline progress;
        private ParallelTransition exit;

        private ToastLifecycle(ParallelTransition enter) { this.enter = enter; }

        private void stop() {
            enter.stop();
            if (pause != null) pause.stop();
            if (progress != null) progress.stop();
            if (exit != null) exit.stop();
        }
    }

    private static IconView.Name iconFor(Type type) {
        return switch (type) {
            case SUCCESS -> IconView.Name.CHECK;
            case ERROR -> IconView.Name.ERROR;
            case WARNING -> IconView.Name.WARNING;
            case INFO -> IconView.Name.INFO;
        };
    }

    private static String colorFor(Type type) {
        return switch (type) {
            case SUCCESS -> DesignTokens.GREEN;
            case ERROR -> DesignTokens.RED;
            case WARNING -> DesignTokens.YELLOW;
            case INFO -> DesignTokens.BLUE;
        };
    }

    private static Label text(String value, double size, String color, boolean bold) {
        return DesignTokens.text(value, size, color, bold);
    }

}
