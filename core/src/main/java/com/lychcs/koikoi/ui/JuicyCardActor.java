package com.lychcs.koikoi.ui;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.utils.Scaling;
import com.lychcs.koikoi.model.Card;

public class JuicyCardActor extends JuicyDraggableActor {

    /** Kantenlaenge des Hanko-Abzeichens auf der Karte. */
    private static final float HANKO_BADGE_SIZE = 26f;

    public interface CardListener {
        void onTap(JuicyCardActor actor);
        void onDrag(JuicyCardActor actor, Vector2 stagePos);
        void onDrop(JuicyCardActor actor, Vector2 stagePos);
    }

    public final Card card;
    private final CardListener listener;
    protected float baseX, baseY, baseRot;

    /**
     * @param cardTex      Atlas-Region des Kartenbildes.
     * @param hankoBadgeTex Atlas-Region des Hanko-Abzeichens oder {@code null},
     *                      wenn die Karte keinen Stempel traegt.
     * @param shadowTex    Region fuer den Schatten.
     * @param listener     Callback fuer Tap/Drag/Drop.
     */
    public JuicyCardActor(Card card, TextureRegion cardTex, TextureRegion hankoBadgeTex, TextureRegion shadowTex, CardListener listener) {
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

        addActor(stack);

        // Hanko-Abzeichen oberhalb des Kartenbildes. Als Kind des Actors bewegt,
        // skaliert, rotiert und fadet es automatisch mit der Karte mit.
        if (card.hasHanko() && hankoBadgeTex != null) {
            Image badge = new Image(hankoBadgeTex);
            badge.setScaling(Scaling.fit);
            badge.setSize(HANKO_BADGE_SIZE, HANKO_BADGE_SIZE);
            badge.setPosition(
                (getWidth() - HANKO_BADGE_SIZE) / 2f,
                getHeight() - HANKO_BADGE_SIZE / 2f
            );
            // Das Abzeichen darf keine Eingabe auf die Karte blockieren.
            badge.setTouchable(Touchable.disabled);
            addActor(badge);
        }
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
