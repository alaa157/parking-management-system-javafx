package com.parking.gui;

import com.parking.gui.components.StyleManager;

import javafx.animation.ScaleTransition;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.effect.GaussianBlur;
import javafx.util.Duration;
import javafx.event.EventHandler;

import static com.parking.gui.DesignTokens.MODAL_BACKDROP;

/** Reusable in-app modal with backdrop, blur, animation, and Escape handling. */
public final class ModalDialog extends StackPane {
    private final StackPane host;
    private final Node blurredNode;
    private final javafx.scene.effect.Effect previousEffect;
    private final javafx.scene.effect.Effect appliedEffect;
    private final EventHandler<KeyEvent> escapeHandler = this::handleEscape;
    private final ChangeListener<Scene> sceneListener = (obs, oldScene, newScene) -> {
        removeEscape(oldScene);
        installEscape(newScene);
    };
    private Scene escapeScene;
    private ParallelTransition entranceTransition;
    private boolean closed;

    private ModalDialog(StackPane host, Node content) {
        this.host = host;
        this.blurredNode = host.getChildren().isEmpty() ? null : host.getChildren().get(0);
        this.previousEffect = blurredNode == null ? null : blurredNode.getEffect();
        setAlignment(Pos.CENTER);
        setFocusTraversable(true);
        getStyleClass().add("modal-layer");
        StyleManager.styleLayer(this, 10);

        Region backdrop = new Region();
        backdrop.getStyleClass().add("modal-backdrop");
        backdrop.setEffect(new GaussianBlur(5));
        backdrop.setOnMouseClicked(e -> close());
        getChildren().add(backdrop);

        StackPane card = new StackPane(content);
        card.getStyleClass().add("modal-card");
        card.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        card.setOnMouseClicked(e -> e.consume());
        getChildren().add(card);
        setOpacity(0);
        card.setScaleX(.95); card.setScaleY(.95);
        appliedEffect = blurredNode == null ? null : new GaussianBlur(3);
        if (blurredNode != null) blurredNode.setEffect(appliedEffect);
        sceneProperty().addListener(sceneListener);
        requestFocus();
        // Phase 4: enter decelerates in (soft landing), exit would accelerate out.
        ScaleTransition scale = new ScaleTransition(DesignTokens.motionDuration(DesignTokens.MOTION_NORMAL), card);
        scale.setToX(1); scale.setToY(1);
        scale.setInterpolator(DesignTokens.motionDecelerate());
        FadeTransition fade = new FadeTransition(DesignTokens.motionDuration(DesignTokens.MOTION_NORMAL), this);
        fade.setToValue(1);
        fade.setInterpolator(DesignTokens.motionDecelerate());
        entranceTransition = new ParallelTransition(scale, fade);
        entranceTransition.setOnFinished(e -> entranceTransition = null);
        entranceTransition.play();
    }

    public static ModalDialog show(StackPane host, Node content) {
        ModalDialog modal = new ModalDialog(host, content);
        host.getChildren().add(modal);
        modal.toFront();
        return modal;
    }

    public void close() {
        if (closed) return;
        closed = true;
        removeEscape(escapeScene);
        sceneProperty().removeListener(sceneListener);
        if (entranceTransition != null) {
            entranceTransition.stop();
            entranceTransition = null;
        }

        // Restore the effect only when this modal still owns it. This also
        // handles overlays whose parent is removed before close is called.
        if (blurredNode != null && blurredNode.getEffect() == appliedEffect) {
            blurredNode.setEffect(previousEffect);
        }
        host.getChildren().remove(this);
    }

    private void installEscape(Scene scene) {
        if (closed || scene == null) return;
        scene.addEventFilter(KeyEvent.KEY_PRESSED, escapeHandler);
        escapeScene = scene;
    }

    private void removeEscape(Scene scene) {
        if (scene != null) scene.removeEventFilter(KeyEvent.KEY_PRESSED, escapeHandler);
        if (escapeScene == scene) escapeScene = null;
    }

    private void handleEscape(KeyEvent event) {
        if (event.getCode() == KeyCode.ESCAPE && isActiveModal()) {
            event.consume();
            close();
        }
    }

    private boolean isActiveModal() {
        if (closed || getParent() != host) return false;
        for (int i = host.getChildren().size() - 1; i >= 0; i--) {
            Node child = host.getChildren().get(i);
            if (child instanceof ModalDialog) return child == this;
        }
        return false;
    }
}
