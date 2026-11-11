package com.lychcs.koikoi.graphics;

import com.badlogic.gdx.graphics.Color;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Validates the terminal glyph vocabulary and the entity foreground lift. */
class AsciiGlyphStyleTest {

    private static float luma(Color color) {
        return 0.299f * color.r + 0.587f * color.g + 0.114f * color.b;
    }

    @Test
    void densityOrdersTheShadeRamp() {
        assertTrue(AsciiGlyphStyle.density(':') < AsciiGlyphStyle.density('\u2591'));
        assertTrue(AsciiGlyphStyle.density('\u2591') < AsciiGlyphStyle.density('\u2592'));
        assertTrue(AsciiGlyphStyle.density('\u2592') < AsciiGlyphStyle.density('\u2593'));
        assertTrue(AsciiGlyphStyle.density('\u2593') < AsciiGlyphStyle.density('\u2588'));
        assertTrue(AsciiGlyphStyle.density('\u2588') < AsciiGlyphStyle.density('@'));
    }

    @Test
    void emptyCellsNeverProduceInk() {
        assertEquals(AsciiGlyphStyle.EMPTY, AsciiGlyphStyle.glyphFor('.', 1, 1));
        assertEquals(AsciiGlyphStyle.EMPTY, AsciiGlyphStyle.glyphFor(' ', 1, 1));
        assertFalse(AsciiGlyphStyle.isInk('.'));
        assertFalse(AsciiGlyphStyle.isInk(' '));
    }

    @Test
    void lightShadesBecomeSubtleDitherGlyphs() {
        for (int i = 0; i < 16; i++) {
            char glyph = AsciiGlyphStyle.glyphFor('\u2591', i, 3);
            assertTrue(glyph == ':' || glyph == '+', "unexpected light glyph '" + glyph + "'");
        }
    }

    @Test
    void mediumShadesBecomeVariedMaterialGlyphs() {
        Set<Character> seen = new HashSet<>();
        for (int i = 0; i < 32; i++) {
            char glyph = AsciiGlyphStyle.glyphFor('\u2592', i, 7);
            assertTrue("/\\|o".indexOf(glyph) >= 0, "unexpected medium glyph '" + glyph + "'");
            seen.add(glyph);
        }
        assertTrue(seen.size() >= 2, "neighbouring shade cells must vary");
        assertEquals(AsciiGlyphStyle.glyphFor('\u2592', 5, 9),
            AsciiGlyphStyle.glyphFor('\u2592', 5, 9),
            "the material choice must be deterministic per cell");
    }

    @Test
    void denseShadesBecomeLeafAndSparkGlyphs() {
        Set<Character> seen = new HashSet<>();
        for (int i = 0; i < 32; i++) {
            char glyph = AsciiGlyphStyle.glyphFor('\u2593', i, 5);
            assertTrue("*%#Q".indexOf(glyph) >= 0, "unexpected dense glyph '" + glyph + "'");
            seen.add(glyph);
        }
        assertTrue(seen.size() >= 2);
        assertEquals('@', AsciiGlyphStyle.glyphFor('@', 1, 2),
            "authored ASCII ink must survive the mapping");
    }

    @Test
    void playerInkIsHighLuminanceParchment() {
        Color dark = new Color(0.05f, 0.08f, 0.05f, 1f);
        Color body = AsciiGlyphStyle.playerForeground(dark, 0.32f, 0.5f, new Color());
        Color head = AsciiGlyphStyle.playerForeground(dark, 0.32f, 0f, new Color());

        assertTrue(luma(body) >= 0.70f, "player ink must pop from the dark terrain");
        assertTrue(luma(head) > luma(body), "the head must catch the warm rim light");
        assertTrue(body.r >= body.b, "player parchment stays warm");
        assertEquals(1f, body.a, 0.0001f);
    }

    @Test
    void foregroundLiftsDarkPaletteEntriesAndSetsAlpha() {
        Color dark = new Color(0.02f, 0.03f, 0.02f, 1f);
        Color out = AsciiGlyphStyle.foreground(dark, 0.4f, 0.5f, new Color());

        assertTrue(luma(out) >= 0.44f, "glyph ink must stay readable over the dark terrain");
        assertEquals(1f, out.a, 0.0001f);
    }

    @Test
    void upperBodyReceivesTheWarmRimLight() {
        Color source = new Color(0.36f, 0.38f, 0.32f, 1f);
        Color top = AsciiGlyphStyle.foreground(source, 0.5f, 0f, new Color());
        Color feet = AsciiGlyphStyle.foreground(source, 0.5f, 1f, new Color());

        assertTrue(luma(top) > luma(feet), "the head must catch more lantern light");
        assertTrue(top.r >= top.b, "the rim light stays warm/parchment");
    }
}
