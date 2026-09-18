package com.lychcs.koikoi.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.lychcs.koikoi.graphics.HankoShaderManager;

public class AltarFlameActor extends Actor {

    private final TextureRegion flameRegion;
    private boolean active = false;

    public AltarFlameActor(TextureRegion flameRegion) {
        this.flameRegion = flameRegion;
        getColor().a = 0f; // Startet unsichtbar
    }

    public void ignite() {
        active = true;
        clearActions();
        addAction(Actions.fadeIn(0.25f));
    }

    public void extinguish() {
        clearActions();
        addAction(Actions.sequence(
            Actions.fadeOut(0.4f),
            Actions.run(() -> active = false)
        ));
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        if (getColor().a <= 0.01f || flameRegion == null) return;

        ShaderProgram flameShader = HankoShaderManager.getVoidFlameShader();
        if (flameShader == null || !flameShader.isCompiled()) return;

        // Eigenen Shader setzen und den vorher aktiven danach wiederherstellen.
        ShaderProgram previousShader = batch.getShader();
        batch.setShader(flameShader);
        flameShader.setUniformf("u_time", HankoShaderManager.getTotalTime());

        Color c = getColor();
        batch.setColor(c.r, c.g, c.b, c.a * parentAlpha);

        float flameW = 48f;
        float flameH = 110f;
        float yOffset = getY() + (getHeight() - flameH) / 2f + 10f;

        // Flamme Links hinter dem Altar
        batch.draw(flameRegion, getX() - flameW + 8f, yOffset, flameW, flameH);

        // Flamme Rechts hinter dem Altar (horizontal gespiegelt)
        batch.draw(flameRegion, getX() + getWidth() - 8f + flameW, yOffset, -flameW, flameH);

        batch.setColor(Color.WHITE);
        batch.setShader(previousShader);
    }
}
