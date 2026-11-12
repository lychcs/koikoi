package com.lychcs.koikoi.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Validates the density-ordered 7x11 terminal glyph atlas vocabulary. */
class AsciiGlyphAtlasTest {

    @Test
    void glyphOrderMatchesTheDensityRamp() {
        assertEquals(" .,'\":;-/\\+omM#@", new String(AsciiGlyphAtlas.GLYPHS));
        assertEquals(16, AsciiGlyphAtlas.glyphCount());
    }

    @Test
    void inkCoverageIsNonDecreasing() {
        int previous = -1;
        for (int glyph = 0; glyph < AsciiGlyphAtlas.glyphCount(); glyph++) {
            int ink = AsciiGlyphAtlas.inkPixels(glyph);
            assertTrue(ink >= previous,
                "glyph '" + AsciiGlyphAtlas.glyphAt(glyph)
                    + "' must not be lighter than the previous glyph");
            previous = ink;
        }
        assertEquals(0, AsciiGlyphAtlas.inkPixels(0), "space carries no ink");
        assertTrue(AsciiGlyphAtlas.inkPixels(15) >= 45,
            "'@' must be the densest glyph");
    }

    @Test
    void everyGlyphFitsTheSevenByElevenCell() {
        assertEquals(7, AsciiGlyphAtlas.CELL_WIDTH);
        assertEquals(11, AsciiGlyphAtlas.CELL_HEIGHT);
        for (int glyph = 0; glyph < AsciiGlyphAtlas.glyphCount(); glyph++) {
            int counted = 0;
            for (int y = 0; y < AsciiGlyphAtlas.CELL_HEIGHT; y++) {
                for (int x = 0; x < AsciiGlyphAtlas.CELL_WIDTH; x++) {
                    if (AsciiGlyphAtlas.isInk(glyph, x, y)) {
                        counted++;
                    }
                }
            }
            assertEquals(AsciiGlyphAtlas.inkPixels(glyph), counted);
            assertFalse(AsciiGlyphAtlas.isInk(glyph, -1, 0));
            assertFalse(AsciiGlyphAtlas.isInk(glyph, 0, -1));
            assertFalse(AsciiGlyphAtlas.isInk(glyph, AsciiGlyphAtlas.CELL_WIDTH, 0));
            assertFalse(AsciiGlyphAtlas.isInk(glyph, 0, AsciiGlyphAtlas.CELL_HEIGHT));
        }
    }

    @Test
    void toneMappingIsMonotonicAndClamped() {
        assertEquals(0, AsciiGlyphAtlas.indexForTone(-1f));
        assertEquals(0, AsciiGlyphAtlas.indexForTone(0f));
        assertEquals(15, AsciiGlyphAtlas.indexForTone(2f));
        assertEquals(15, AsciiGlyphAtlas.indexForTone(1f));

        int previous = -1;
        for (int step = 0; step <= 100; step++) {
            int index = AsciiGlyphAtlas.indexForTone(step / 100f);
            assertTrue(index >= previous, "tone mapping must never step backwards");
            previous = index;
        }
    }

    @Test
    void glyphLookupClampsOutOfRangeIndices() {
        assertEquals(' ', AsciiGlyphAtlas.glyphAt(-3));
        assertEquals('@', AsciiGlyphAtlas.glyphAt(99));
    }
}
