package com.parking.gui;

import javafx.animation.Animation;
import javafx.animation.TranslateTransition;
import javafx.event.ActionEvent;
import javafx.scene.Node;

import java.util.List;

public final class UiMotion {
    private UiMotion() {
    }

    public static void shake(Node node) {
        TranslateTransition transition = new TranslateTransition(
                DesignTokens.motionDuration(DesignTokens.MOTION_FAST), node);
        transition.setFromX(0);
        transition.setToX(8);
        transition.setInterpolator(DesignTokens.motionAccelerate());
        transition.setAutoReverse(true);
        transition.setCycleCount(6);
        transition.play();
    }

    public static <T extends Animation> T own(List<Animation> running, T animation) {
        running.add(animation);
        javafx.event.EventHandler<ActionEvent> existing = animation.getOnFinished();
        animation.setOnFinished(event -> {
            if (existing != null) {
                existing.handle(event);
            }
            running.remove(animation);
        });
        return animation;
    }
}
