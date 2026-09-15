package com.lychcs.koikoi.ui;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.lychcs.koikoi.model.omamori.Omamori;

public class JuicyOmamoriActor extends JuicyDraggableActor {

    public interface OmamoriListener {
        void onTap(JuicyOmamoriActor actor);
        void onDrop(JuicyOmamoriActor actor, Vector2 stagePos);
    }

    public final Omamori omamori;
    private final OmamoriListener listener;

    public JuicyOmamoriActor(Omamori omamori, TextureRegion tex, OmamoriListener listener) {
        super(72f, 96f, tex);
        this.omamori = omamori;
        this.listener = listener;

        Image mainImg = new Image(tex);
        mainImg.setSize(getWidth(), getHeight());
        addActor(mainImg);
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
