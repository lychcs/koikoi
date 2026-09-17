package com.lychcs.koikoi.ui;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Scaling;
import com.lychcs.koikoi.model.Card;

public class JuicyCardActor extends JuicyDraggableActor {

    /*
     * Die Inventarkarte ist 60 Pixel breit und verwendet einen
     * 20-Pixel-Hanko. Die Kampfkarte ist 72 Pixel breit, deshalb
     * wird der Hanko proportional auf 24 Pixel skaliert.
     */
    private static final float HANKO_BADGE_SIZE = 24f;
    private static final float HANKO_BADGE_PADDING = 2f;

    public interface CardListener {
        void onTap(JuicyCardActor actor);

        void onDrag(JuicyCardActor actor, Vector2 stagePos);

        void onDrop(JuicyCardActor actor, Vector2 stagePos);
    }

    public final Card card;

    private final CardListener listener;

    protected float baseX;
    protected float baseY;
    protected float baseRot;

    public JuicyCardActor(
        Card card,
        TextureRegion cardTex,
        TextureRegion hankoBadgeTex,
        TextureRegion shadowTex,
        CardListener listener
    ) {
        super(72f, 128f, shadowTex);

        this.card = card;
        this.listener = listener;

        if (shadowImg != null) {
            shadowImg.setPosition(-4f, -6f);
        }

        /*
         * Wie im InventoryOverlay werden Kartenbild und Hanko in
         * demselben Stack angeordnet. Dadurch übernimmt der Hanko
         * automatisch Position, Rotation, Skalierung und Alpha der Karte.
         */
        Stack cardStack = new Stack();
        cardStack.setSize(getWidth(), getHeight());
        cardStack.setTouchable(Touchable.disabled);

        Image mainImg = new Image(cardTex);
        mainImg.setScaling(Scaling.fit);
        mainImg.setTouchable(Touchable.disabled);
        cardStack.add(mainImg);

        if (card.hasHanko() && hankoBadgeTex != null) {
            Image badge = new Image(hankoBadgeTex);
            badge.setScaling(Scaling.fit);
            badge.setTouchable(Touchable.disabled);

            /*
             * Entspricht dem InventoryOverlay:
             *
             * badgeContainer.top().right();
             * badgeContainer.add(...).size(...).pad(...);
             */
            Table badgeContainer = new Table();
            badgeContainer.top().right();
            badgeContainer.setTouchable(Touchable.disabled);
            badgeContainer.add(badge)
                .size(HANKO_BADGE_SIZE)
                .pad(HANKO_BADGE_PADDING);

            cardStack.add(badgeContainer);
        }

        addActor(cardStack);
    }

    public void updateArc(
        float bx,
        float by,
        float brot,
        boolean isSelected
    ) {
        this.baseX = bx;
        this.baseY = by;
        this.baseRot = brot;

        if (!isDragging) {
            targetX = bx;
            targetY = isSelected ? by + 25f : by;
            targetRot = isSelected ? 0f : brot;
        }
    }

    @Override
    protected void onDrag(
        InputEvent event,
        float x,
        float y,
        Vector2 stagePos
    ) {
        targetRot = 0f;

        if (listener != null) {
            listener.onDrag(this, stagePos);
        }
    }

    @Override
    protected void onTap(
        InputEvent event,
        float x,
        float y
    ) {
        punch();

        if (listener != null) {
            listener.onTap(this);
        }
    }

    @Override
    protected void onDrop(
        InputEvent event,
        float x,
        float y,
        Vector2 stagePos
    ) {
        if (listener != null) {
            listener.onDrop(this, stagePos);
        }
    }
}
