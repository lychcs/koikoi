package com.lychcs.koikoi.render;

import com.badlogic.gdx.graphics.Color;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Static torii/fence/temple/rock vertices must be strictly opaque: the magic
 * ASCII post pass distinguishes spirit ink solely by a 0.5 alpha tag.
 */
class JapaneseSceneryOpaqueColorTest {

    @Test
    void sceneryVertexColorsAreAlwaysFullyOpaque() {
        Color color = new Color();
        float[][] samples = {
            {0.64f, 0.25f, 0.15f}, // torii vermillion
            {0.48f, 0.43f, 0.28f}, // bamboo fence
            {0.40f, 0.44f, 0.46f}, // slate stone
            {0.00f, 0.00f, 0.00f},
            {1.00f, 1.00f, 1.00f}
        };
        for (float[] sample : samples) {
            int packed = JapaneseScenery.opaqueColor(sample[0], sample[1], sample[2]);
            Color.rgba8888ToColor(color, packed);
            assertEquals(1f, color.a, 0.0001f,
                "scenery vertex alpha must never become a magic tag");
            assertEquals(sample[0], color.r, 1f / 255f);
            assertEquals(sample[1], color.g, 1f / 255f);
            assertEquals(sample[2], color.b, 1f / 255f);
        }
    }
}
