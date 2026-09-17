package com.lychcs.koikoi.ui;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;

public abstract class JuicyDraggableActor extends Group {

    protected Image shadowImg;

    // Transformations-Ziele für die Lerp-Physik
    protected float targetX, targetY, targetRot;
    protected float targetScale = 1f;
    protected float targetShadow = 0f;
    protected float currentShadow = 0f;

    // Drag-Handling
    protected boolean isDragging = false;
    /**
     * Abstand zwischen Griffpunkt (Finger) und Actor-Position, ausgedrueckt in
     * Parent-Koordinaten. Actor-lokale Event-Koordinaten und Stage-Koordinaten
     * sind KEINE Parent-Koordinaten und duerfen nicht direkt verwendet werden.
     */
    private float grabOffsetX, grabOffsetY;
    /** Parent, in dem der Griffpunkt gemessen wurde (fuer Reparenting waehrend des Drags). */
    private Group dragParent;
    private float touchDownStageX, touchDownStageY;
    private static final float DRAG_THRESHOLD = 14f;

    // Wiederverwendete Vektoren: keine Allokation pro Drag-Event.
    private final Vector2 tmpStage = new Vector2();
    private final Vector2 tmpParent = new Vector2();
    private final Vector2 lastStagePosition = new Vector2();
    private boolean lastStagePositionValid = false;

    // Positions-Tracking: erkennt, wenn ein Layout-System (z. B. Table) die Position setzt.
    private boolean positionTracked = false;
    private float lastAppliedX, lastAppliedY;

    // Konfigurierbare Lerp-Geschwindigkeiten
    protected float posLerpSpeed = 22f;
    protected float rotLerpSpeed = 18f;
    protected float scaleLerpSpeed = 35f;
    protected float shadowLerpSpeed = 35f;

    public JuicyDraggableActor(float width, float height, TextureRegion shadowRegion) {
        setSize(width, height);
        setOrigin(width / 2f, height / 2f);

        if (shadowRegion != null) {
            shadowImg = new Image(shadowRegion);
            shadowImg.setSize(width, height);
            shadowImg.setColor(0f, 0f, 0f, 0f);
            shadowImg.setPosition(-4f, -6f);
            addActor(shadowImg);
        }

        initDragListener();
    }

    private void initDragListener() {
        addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if (!canInteract()) return false;

                isDragging = false;
                touchDownStageX = event.getStageX();
                touchDownStageY = event.getStageY();
                captureGrabOffset(x, y);

                targetScale = 1.05f;
                targetShadow = 0.3f;

                onPress(event, x, y);
                return true;
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer) {
                if (!canInteract()) return;

                if (!isDragging) {
                    float dist = Vector2.dst(touchDownStageX, touchDownStageY, event.getStageX(), event.getStageY());
                    if (dist < DRAG_THRESHOLD) return;

                    isDragging = true;
                    targetRot = 0f;
                    targetShadow = 0.6f;
                    toFront();
                    onDragStart(event);
                }

                Group parent = getParent();
                if (parent == null) return;

                // Fingerposition in Stage-Koordinaten ermitteln (Event-Koordinaten sind Actor-lokal).
                tmpStage.set(x, y);
                localToStageCoordinates(tmpStage);

                // Stage-Koordinaten in das Koordinatensystem des aktuellen Parents umrechnen.
                tmpParent.set(tmpStage);
                parent.stageToLocalCoordinates(tmpParent);

                if (parent != dragParent) {
                    // Reparenting waehrend des Drags: die zuletzt sichtbare Stage-Position
                    // im neuen Parent wiederherstellen, damit die Karte nicht springt.
                    dragParent = parent;
                    if (lastStagePositionValid) {
                        tmpParent.set(lastStagePosition);
                        parent.stageToLocalCoordinates(tmpParent);
                        setPosition(tmpParent.x, tmpParent.y);
                        tmpParent.set(tmpStage);
                        parent.stageToLocalCoordinates(tmpParent);
                    }
                    grabOffsetX = tmpParent.x - getX();
                    grabOffsetY = tmpParent.y - getY();
                }

                targetX = tmpParent.x - grabOffsetX;
                targetY = tmpParent.y - grabOffsetY;

                // Sichtbare Position des Actors fuer ein moegliches Reparenting merken.
                lastStagePosition.set(0f, 0f);
                localToStageCoordinates(lastStagePosition);
                lastStagePositionValid = true;

                // tmpStage enthaelt weiterhin die Stage-Position des Fingers.
                onDrag(event, x, y, tmpStage);
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                targetScale = 1.0f;
                targetShadow = 0f;

                if (!canInteract()) return;

                if (isDragging) {
                    // Snap-back-Ziele setzen erst nach dem Loslassen (isDragging == false).
                    isDragging = false;
                    tmpStage.set(x, y);
                    localToStageCoordinates(tmpStage);
                    onDrop(event, x, y, tmpStage);
                } else {
                    onTap(event, x, y);
                }
            }
        });
    }

    /**
     * Merkt sich den Griffpunkt (Finger) relativ zur Actor-Position im
     * Koordinatensystem des aktuellen Parents.
     */
    private void captureGrabOffset(float localX, float localY) {
        Group parent = getParent();
        dragParent = parent;
        lastStagePositionValid = false;

        if (parent == null) {
            grabOffsetX = localX;
            grabOffsetY = localY;
            return;
        }

        tmpStage.set(localX, localY);
        localToStageCoordinates(tmpStage);
        tmpParent.set(tmpStage);
        parent.stageToLocalCoordinates(tmpParent);

        grabOffsetX = tmpParent.x - getX();
        grabOffsetY = tmpParent.y - getY();

        lastStagePosition.set(0f, 0f);
        localToStageCoordinates(lastStagePosition);
        lastStagePositionValid = true;
    }

    @Override
    public void act(float delta) {
        super.act(delta);

        // Wurde die Position von aussen gesetzt (z. B. durch ein Table-Layout), wird sie
        // als neues Ziel uebernommen, damit die Lerp-Physik das Layout nicht ueberschreibt.
        // Der erste Frame registriert nur die Startposition, damit ein bereits gesetztes
        // Ziel (z. B. der Kartenbogen der Hand) erhalten bleibt.
        if (!isDragging && positionTracked && (getX() != lastAppliedX || getY() != lastAppliedY)) {
            targetX = getX();
            targetY = getY();
        }
        positionTracked = true;

        setX(MathUtils.lerp(getX(), targetX, posLerpSpeed * delta));
        setY(MathUtils.lerp(getY(), targetY, posLerpSpeed * delta));
        setRotation(MathUtils.lerp(getRotation(), targetRot, rotLerpSpeed * delta));

        float newScale = MathUtils.lerp(getScaleX(), targetScale, scaleLerpSpeed * delta);
        setScale(newScale, newScale);

        if (shadowImg != null) {
            currentShadow = MathUtils.lerp(currentShadow, targetShadow, shadowLerpSpeed * delta);
            shadowImg.setColor(0f, 0f, 0f, currentShadow);
        }

        // Merken, welche Position die Physik selbst gesetzt hat.
        lastAppliedX = getX();
        lastAppliedY = getY();
    }

    public void punch() {
        clearActions();
        addAction(Actions.sequence(
            Actions.parallel(Actions.scaleTo(1.2f, 1.2f, 0.08f, Interpolation.fastSlow), Actions.rotateBy(4f, 0.08f)),
            Actions.parallel(Actions.scaleTo(1.0f, 1.0f, 0.15f, Interpolation.bounceOut), Actions.rotateTo(0f, 0.15f))
        ));
    }

    // --- Lifecycle-Hooks für Subklassen ---
    protected boolean canInteract() { return true; }
    protected void onPress(InputEvent event, float x, float y) {}
    protected void onDragStart(InputEvent event) {}
    protected void onDrag(InputEvent event, float x, float y, Vector2 stagePos) {}
    protected void onDrop(InputEvent event, float x, float y, Vector2 stagePos) {}
    protected abstract void onTap(InputEvent event, float x, float y);
    public boolean isDragging() { return isDragging; }
}
