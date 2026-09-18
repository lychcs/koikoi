package com.lychcs.koikoi.ui;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.lychcs.koikoi.graphics.HankoShaderManager;
import com.lychcs.koikoi.model.hanko.HankoEffect;

public class HankoActor extends Image {

    private final HankoEffect effect;

    public HankoActor(HankoEffect effect, TextureRegion region) {
        super(region);
        this.effect = effect;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        ShaderProgram activeShader = null;
        // Vorher aktiven Shader merken, damit er nach dem Zeichnen exakt
        // wiederhergestellt wird (kein blindes setShader(null)).
        ShaderProgram previousShader = batch.getShader();

        switch (effect) {
            case POLYCHROME_SEAL -> {
                activeShader = HankoShaderManager.getPolychromeShader();
                batch.setShader(activeShader);
                activeShader.setUniformf("u_time", HankoShaderManager.getTotalTime());
            }
            case GOLDEN_SEAL -> {
                activeShader = HankoShaderManager.getPulseShader();
                batch.setShader(activeShader);
                activeShader.setUniformf("u_time", HankoShaderManager.getTotalTime());
                activeShader.setUniformf("u_glowColor", 1.0f, 0.84f, 0.0f); // Warmes Gold
            }
            case VOID_SEAL -> {
                activeShader = HankoShaderManager.getPulseShader();
                batch.setShader(activeShader);
                activeShader.setUniformf("u_time", HankoShaderManager.getTotalTime());
                activeShader.setUniformf("u_glowColor", 0.65f, 0.0f, 0.95f); // Lila Void
            }
            case BLOOD_SEAL -> {
                activeShader = HankoShaderManager.getPulseShader();
                batch.setShader(activeShader);
                activeShader.setUniformf("u_time", HankoShaderManager.getTotalTime());
                activeShader.setUniformf("u_glowColor", 0.85f, 0.05f, 0.05f); // Blutrot
            }
            default -> {
                // WHITE_SEAL, BLACK_SEAL, STONE_SEAL nutzen keinen Shader
            }
        }

        super.draw(batch, parentAlpha);

        if (activeShader != null) {
            batch.setShader(previousShader);
        }
    }
}
