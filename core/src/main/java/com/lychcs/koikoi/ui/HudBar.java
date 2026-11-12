package com.lychcs.koikoi.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;

/**
 * Flat Scene2D stat bar for the overworld HUD.
 *
 * <p>Draws a translucent dark backing, a one-pixel border and a coloured fill
 * from a shared 1x1 white pixel. Kept as a plain {@link Actor} so the HUD does
 * not depend on skin drawables and every bar shares the same terminal look.</p>
 */
public final class HudBar extends Actor {

    private static final Color BACKING = new Color(0.020f, 0.028f, 0.050f, 0.88f);
    private static final Color EDGE = new Color(0.42f, 0.50f, 0.52f, 0.90f);

    private final TextureRegion pixel;
    private final Color fill = new Color();
    private float ratio = 1f;

    public HudBar(TextureRegion pixel, Color fill) {
        if (pixel == null) {
            throw new IllegalArgumentException("pixel must not be null");
        }
        if (fill == null) {
            throw new IllegalArgumentException("fill must not be null");
        }
        this.pixel = pixel;
        this.fill.set(fill);
    }

    /** Fill fraction in {@code [0, 1]}; values outside are clamped. */
    public void setRatio(float value) {
        ratio = Math.max(0f, Math.min(1f, value));
    }

    public float getRatio() {
        return ratio;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        float alpha = getColor().a * parentAlpha;
        float x = getX();
        float y = getY();
        float width = getWidth();
        float height = getHeight();
        if (width <= 2f || height <= 2f) {
            return;
        }

        batch.setColor(BACKING.r, BACKING.g, BACKING.b, BACKING.a * alpha);
        batch.draw(pixel, x, y, width, height);

        batch.setColor(EDGE.r, EDGE.g, EDGE.b, EDGE.a * alpha);
        batch.draw(pixel, x, y, width, 1f);
        batch.draw(pixel, x, y + height - 1f, width, 1f);
        batch.draw(pixel, x, y, 1f, height);
        batch.draw(pixel, x + width - 1f, y, 1f, height);

        float inset = 2f;
        float fillWidth = (width - inset * 2f) * ratio;
        if (fillWidth > 0f) {
            batch.setColor(fill.r, fill.g, fill.b, fill.a * alpha);
            batch.draw(pixel, x + inset, y + inset, fillWidth, height - inset * 2f);
        }
        batch.setColor(1f, 1f, 1f, 1f);
    }
}
