package com.lychcs.koikoi.ui;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.lychcs.koikoi.model.Card;

public class JuicyCardActor extends JuicyDraggableActor {

    public interface CardListener {
        void onTap(JuicyCardActor actor);
        void onDrag(JuicyCardActor actor, Vector2 stagePos);
        void onDrop(JuicyCardActor actor, Vector2 stagePos);
    }

    public final Card card;
    private final CardListener listener;
    protected float baseX, baseY, baseRot;

    public JuicyCardActor(Card card, TextureRegion cardTex, TextureRegion shadowTex, CardListener listener) {
        super(72f, 128f, shadowTex);
        this.card = card;
        this.listener = listener;

        // Shadow-Offset für Karten leicht anpassen
        if (shadowImg != null) {
            shadowImg.setPosition(-4f, -6f);
        }

        Stack stack = new Stack();
        stack.setSize(getWidth(), getHeight());

        Image mainImg = new Image(cardTex);
        mainImg.setScaling(com.badlogic.gdx.utils.Scaling.fit);
        stack.add(mainImg);

        // Falls Hanko vorhanden, HankoActor auf der Karte platzieren
        if (card.hasHanko()) {
            // Hinweis: Falls du den HankoActor hier direkt nutzen möchtest,
            // kann die Textur übergeben oder geladen werden.
        }

        addActor(stack);
    }

    public void updateArc(float bx, float by, float brot, boolean isSelected) {
        this.baseX = bx;
        this.baseY = by;
        this.baseRot = brot;

        if (!isDragging) {
            targetX = bx;
            targetY = isSelected ? by + 25f : by; // CARD_SELECT_OFFSET_Y
            targetRot = isSelected ? 0f : brot;
        }
    }

    @Override
    protected void onDrag(InputEvent event, float x, float y, Vector2 stagePos) {
        targetRot = 0f;
        if (listener != null) {
            listener.onDrag(this, stagePos);
        }
    }

    @Override
    protected void onTap(InputEvent event, float x, float y) {
        punch();
        if (listener != null) {
            listener.onTap(this);
        }
    }

    @Override
    protected void onDrop(InputEvent event, float x, float y, Vector2 stagePos) {
        if (listener != null) {
            listener.onDrop(this, stagePos);
        }
    }
}
