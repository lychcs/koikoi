package com.lychcs.koikoi.graphics;

import com.badlogic.gdx.graphics.Color;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Validates the muted earth-tone palette mapping used by the avatar and sprites. */
class EarthTonePaletteTest {

    @Test
    void brightColoursAreMutedAndNeverBecomeWhite() {
        Color white = EarthTonePalette.transform(new Color(1f, 1f, 1f, 1f));

        assertEquals(1f, white.a, 0.0001f);
        assertTrue(Math.max(white.r, Math.max(white.g, white.b)) <= 0.8001f,
            "highlights must stay in the pastel range");
        assertTrue(white.r >= white.g && white.g >= white.b,
            "the warm parchment bias should survive the cap");
    }

    @Test
    void darkEarthColoursKeepTheirWarmHue() {
        Color brown = EarthTonePalette.transform(new Color(0.12f, 0.09f, 0.07f, 1f));

        assertTrue(brown.r > brown.g && brown.g > brown.b,
            "burnt umber should stay brown after mapping");
        assertTrue(brown.r > 0.12f, "the warm lift should make the entry readable");
        assertEquals(1f, brown.a, 0.0001f);
    }

    @Test
    void cyanLosesItsNeonSaturation() {
        Color cyan = new Color(0.05f, 0.80f, 1.00f, 1f);
        Color mapped = EarthTonePalette.transform(cyan);

        assertTrue(mapped.b - mapped.r < cyan.b - cyan.r,
            "mapped cyan must be visibly less saturated");
        assertEquals(1f, mapped.a, 0.0001f);
    }
}
