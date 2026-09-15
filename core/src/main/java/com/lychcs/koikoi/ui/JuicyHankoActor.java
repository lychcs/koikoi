package com.lychcs.koikoi.ui;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.utils.Scaling;
import com.lychcs.koikoi.graphics.HankoShaderManager;
import com.lychcs.koikoi.model.hanko.HankoEffect;

public class JuicyHankoActor extends JuicyDraggableActor {

    public interface HankoListener {
        void onTap(JuicyHankoActor actor);
        void onDrop(JuicyHankoActor actor, Vector2 stagePos);
    }

    public final HankoEffect effect;
    private final TextureRegion region;
    private final HankoListener listener;
    private final ShaderImage mainImg;

    public JuicyHankoActor(HankoEffect effect, TextureRegion tex, float size, HankoListener listener) {
        super(size, size, tex);
        this.effect = effect;
        this.region = tex;
        this.listener = listener;

        mainImg = new ShaderImage(tex);
        mainImg.setSize(size, size);
        mainImg.setScaling(Scaling.fit);
        addActor(mainImg);
    }

    public JuicyHankoActor(HankoEffect effect, TextureRegion tex, HankoListener listener) {
        this(effect, tex, 64f, listener);
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

    /**
     * Eigene Image-Komponente, die den jeweiligen Hanko-Shader isoliert auf das Siegel zeichnet.
     */
    private class ShaderImage extends Image {
        public ShaderImage(TextureRegion region) {
            super(region);
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            ShaderProgram activeShader = null;

            switch (effect) {
                case POLYCHROME_SEAL -> {
                    activeShader = HankoShaderManager.getPolychromeShader();
                    batch.setShader(activeShader);

                    activeShader.setUniformf("u_time", HankoShaderManager.getTotalTime());

                    // Atlas-Koordinaten für den Holo-Verlauf
                    float u = region.getU();
                    float v = region.getV();
                    float uWidth = region.getU2() - u;
                    float vHeight = region.getV2() - v;
                    activeShader.setUniformf("u_region", u, v, uWidth, vHeight);
                }
                case GOLDEN_SEAL -> {
                    activeShader = HankoShaderManager.getPulseShader();
                    batch.setShader(activeShader);
                    activeShader.setUniformf("u_time", HankoShaderManager.getTotalTime());
                    activeShader.setUniformf("u_glowColor", 1.0f, 0.84f, 0.0f);
                }
                case VOID_SEAL -> {
                    activeShader = HankoShaderManager.getPulseShader();
                    batch.setShader(activeShader);
                    activeShader.setUniformf("u_time", HankoShaderManager.getTotalTime());
                    activeShader.setUniformf("u_glowColor", 0.65f, 0.0f, 0.95f);
                }
                case BLOOD_SEAL -> {
                    activeShader = HankoShaderManager.getPulseShader();
                    batch.setShader(activeShader);
                    activeShader.setUniformf("u_time", HankoShaderManager.getTotalTime());
                    activeShader.setUniformf("u_glowColor", 0.85f, 0.05f, 0.05f);
                }
                default -> {}
            }

            super.draw(batch, parentAlpha);

            if (activeShader != null) {
                batch.setShader(null);
            }
        }
    }
}
