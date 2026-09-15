package com.lychcs.koikoi.ui;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Scaling;
import com.lychcs.koikoi.model.yokai.Yokai;

public class JuicyYokaiActor extends JuicyDraggableActor {

    public interface YokaiListener {
        void onTap(JuicyYokaiActor actor);
        void onDrop(JuicyYokaiActor actor, Vector2 stagePos);
    }

    public final Yokai yokai;
    public final boolean isAltar;
    private final YokaiListener listener;

    public JuicyYokaiActor(Yokai yokai, TextureRegion tex, boolean isAltar, Skin skin, YokaiListener listener) {
        super(108f, 192f, tex);
        this.yokai = yokai;
        this.isAltar = isAltar;
        this.listener = listener;

        // Shadow-Offset für Yokai leicht anpassen
        if (shadowImg != null) {
            shadowImg.setPosition(-6f, -8f);
        }

        Stack stack = new Stack();
        stack.setSize(getWidth(), getHeight());

        Image mainImg = new Image(tex);
        mainImg.setScaling(Scaling.fit);
        stack.add(mainImg);

        if (yokai.isExhausted()) {
            mainImg.setColor(0.35f, 0.35f, 0.35f, 0.6f);
            Label exLabel = new Label("Rastet", skin);
            exLabel.setFontScale(0.75f);
            Table t = new Table();
            t.center().add(exLabel);
            stack.add(t);
        } else {
            Label stageLabel = new Label(yokai.getName(), skin);
            stageLabel.setFontScale(0.8f);
            Table labelTable = new Table();
            labelTable.bottom().padBottom(4);
            labelTable.add(stageLabel);
            stack.add(labelTable);
        }

        addActor(stack);
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
