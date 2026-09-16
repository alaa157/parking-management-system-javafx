package com.parking.gui;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import static com.parking.gui.DesignTokens.SKELETON_SHEEN;
import static com.parking.gui.DesignTokens.motionDuration;
import static com.parking.gui.DesignTokens.MOTION_SLOW;

/** Animated loading placeholders that can be placed over any view. */
public final class SkeletonView extends StackPane {
    private final Timeline shimmer;
    private PauseTransition completion;
    private boolean stopped;

    private SkeletonView(Node content) {
        getStyleClass().add("skeleton-overlay");
        getChildren().add(content);
        Rectangle sheen = new Rectangle(180, 900, DesignTokens.color(SKELETON_SHEEN));
        sheen.setRotate(12);
        sheen.setMouseTransparent(true);
        sheen.getStyleClass().add("skeleton-sheen");
        getChildren().add(sheen);
        // Phase 4: eased shimmer glide — decelerate out so the sweep settles softly.
        shimmer = new Timeline(
                new KeyFrame(javafx.util.Duration.ZERO, new KeyValue(sheen.translateXProperty(), -420, DesignTokens.motionDecelerate())),
                new KeyFrame(motionDuration(MOTION_SLOW).multiply(4), new KeyValue(sheen.translateXProperty(), 1100, DesignTokens.motionDecelerate())));
        shimmer.setCycleCount(Timeline.INDEFINITE);
        if (!ThemeManager.get().isReduceMotion()) shimmer.play();
    }

    public static SkeletonView table(int rows) {
        VBox box = new VBox(10);
        box.setPadding(new Insets(14));
        for (int i = 0; i < rows; i++) {
            HBox row = new HBox(12);
            for (int j = 0; j < 5; j++) {
                Region cell = block(j == 0 ? 42 : 120, 18);
                HBox.setHgrow(cell, javafx.scene.layout.Priority.ALWAYS);
                row.getChildren().add(cell);
            }
            box.getChildren().add(row);
        }
        return new SkeletonView(box);
    }

    public static SkeletonView cards(int count) {
        HBox box = new HBox(14);
        box.setPadding(new Insets(12));
        for (int i = 0; i < count; i++) {
            VBox card = new VBox(12, block(180, 18), block(120, 28), block(220, 12), block(160, 12));
            card.setPadding(new Insets(16));
            card.setPrefWidth(220);
            card.getStyleClass().add("skeleton-card");
            box.getChildren().add(card);
        }
        return new SkeletonView(box);
    }

    public static SkeletonView grid(int count) {
        javafx.scene.layout.TilePane grid = new javafx.scene.layout.TilePane();
        grid.setHgap(12); grid.setVgap(12); grid.setPadding(new Insets(12));
        for (int i = 0; i < count; i++) grid.getChildren().add(block(86, 56));
        return new SkeletonView(grid);
    }

    public static void show(StackPane host, SkeletonView skeleton, long millis) {
        host.getChildren().add(skeleton);
        skeleton.completion = new PauseTransition(Duration.millis(millis));
        skeleton.completion.setOnFinished(e -> {
            skeleton.stop();
            host.getChildren().remove(skeleton);
        });
        skeleton.parentProperty().addListener((obs, oldParent, newParent) -> {
            if (newParent == null) skeleton.stop();
        });
        skeleton.completion.play();
    }

    public void stop() {
        if (stopped) return;
        stopped = true;
        shimmer.stop();
        if (completion != null) completion.stop();
    }

    private static Region block(double width, double height) {
        Region r = new Region();
        r.setMinSize(width, height); r.setPrefSize(width, height);
        r.getStyleClass().add("skeleton-block");
        return r;
    }
}
