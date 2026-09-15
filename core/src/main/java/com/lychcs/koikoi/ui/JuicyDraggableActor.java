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
    protected float dragOffsetX, dragOffsetY;
    private float touchDownStageX, touchDownStageY;
    private static final float DRAG_THRESHOLD = 14f;

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
                dragOffsetX = x;
                dragOffsetY = y;
                touchDownStageX = event.getStageX();
                touchDownStageY = event.getStageY();

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

                // Drag-Position relativ zum Parent berechnen
                Vector2 stagePos = localToStageCoordinates(new Vector2(x, y));
                Vector2 parentPos = getParent().stageToLocalCoordinates(stagePos);
                targetX = parentPos.x - dragOffsetX;
                targetY = parentPos.y - dragOffsetY;

                onDrag(event, x, y, stagePos);
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                targetScale = 1.0f;
                targetShadow = 0f;

                if (!canInteract()) return;

                if (isDragging) {
                    isDragging = false;
                    Vector2 stagePos = localToStageCoordinates(new Vector2(x, y));
                    onDrop(event, x, y, stagePos);
                } else {
                    onTap(event, x, y);
                }
            }
        });
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        setX(MathUtils.lerp(getX(), targetX, posLerpSpeed * delta));
        setY(MathUtils.lerp(getY(), targetY, posLerpSpeed * delta));
        setRotation(MathUtils.lerp(getRotation(), targetRot, rotLerpSpeed * delta));

        float newScale = MathUtils.lerp(getScaleX(), targetScale, scaleLerpSpeed * delta);
        setScale(newScale, newScale);

        if (shadowImg != null) {
            currentShadow = MathUtils.lerp(currentShadow, targetShadow, shadowLerpSpeed * delta);
            shadowImg.setColor(0f, 0f, 0f, currentShadow);
        }
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
}
