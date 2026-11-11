package com.lychcs.koikoi.graphics;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Validates the directional side profile and the two-frame leg toggle. */
class AsciiGlyphRendererTest {

    @Test
    void sideMatrixMirrorsOnlyWhenWalkingRight() {
        assertEquals(11, AsciiGlyphRenderer.spriteColumn(0, 12, AsciiFacing.RIGHT));
        assertEquals(0, AsciiGlyphRenderer.spriteColumn(11, 12, AsciiFacing.RIGHT));
        assertEquals(0, AsciiGlyphRenderer.spriteColumn(0, 12, AsciiFacing.LEFT));
        assertEquals(11, AsciiGlyphRenderer.spriteColumn(11, 12, AsciiFacing.LEFT));
        assertEquals(0, AsciiGlyphRenderer.spriteColumn(0, 12, AsciiFacing.DOWN));
    }

    @Test
    void frontLegsAlternateBetweenTwoFrames() {
        assertEquals('/',
            AsciiGlyphRenderer.animatedGlyph('|', 2, 0.9f, 13, false, 0f, true, true));
        assertEquals('\\',
            AsciiGlyphRenderer.animatedGlyph('|', 2, 0.9f, 13, false, 0f, true, false));
        assertEquals('\\',
            AsciiGlyphRenderer.animatedGlyph('|', 10, 0.9f, 13, false, 0f, true, true));
    }

    @Test
    void sideLegLeansWithTravelDirection() {
        assertEquals('/',
            AsciiGlyphRenderer.animatedGlyph('|', 4, 0.9f, 9, true, 1f, true, true));
        assertEquals('\\',
            AsciiGlyphRenderer.animatedGlyph('|', 4, 0.9f, 9, true, 1f, true, false));
        assertEquals('\\',
            AsciiGlyphRenderer.animatedGlyph('|', 4, 0.9f, 9, true, -1f, true, true));
        assertEquals('/',
            AsciiGlyphRenderer.animatedGlyph('|', 4, 0.9f, 9, true, -1f, true, false));
    }

    @Test
    void upperBodyAndIdleFramesKeepTheOriginalGlyph() {
        assertEquals('|',
            AsciiGlyphRenderer.animatedGlyph('|', 2, 0.3f, 13, false, 0f, true, true));
        assertEquals('|',
            AsciiGlyphRenderer.animatedGlyph('|', 2, 0.9f, 13, false, 0f, false, true));
    }

    @Test
    void everyInkedBlockStaysVisibleForDenseEntities() {
        float colon = AsciiGlyphStyle.density(':');
        assertTrue(AsciiGlyphRenderer.blockIsVisible(colon, 1, 2),
            "a single dither cell is still part of a dense entity contour");
        assertTrue(AsciiGlyphRenderer.blockIsVisible(colon, 2, 2));
        assertTrue(AsciiGlyphRenderer.blockIsVisible(AsciiGlyphStyle.density('\u2591'), 0, 2));
        assertFalse(AsciiGlyphRenderer.blockIsVisible(0f, 0, 2),
            "a block without any ink must stay transparent");
        assertTrue(AsciiGlyphRenderer.blockIsVisible(colon, 1, 1));
    }

    @Test
    void coreOnlyModeRejectsColonDitherButKeepsLightShades() {
        assertFalse(AsciiGlyphRenderer.isCoreInk(':'),
            "player core-only ink must ignore ':' background dither");
        assertTrue(AsciiGlyphRenderer.isCoreInk('\u2591'),
            "a light shade is the first solid body cell");
        assertFalse(AsciiGlyphRenderer.isCoreInk('.'));
        assertFalse(AsciiGlyphRenderer.isCoreInk(' '));
    }

    @Test
    void sourceDitherIsInkButEmptyCellsAreNot() {
        AsciiAsset matrix = new AsciiAsset("test_avatar", "player", "test", 5, 5,
            List.of(
                ".....",
                ".@:..",
                ".:...",
                "..:..",
                "....."),
            new int[25]);

        assertTrue(AsciiGlyphRenderer.isVisibleGlyph(matrix, 1, 1),
            "solid body cell must be visible");
        assertTrue(AsciiGlyphRenderer.isVisibleGlyph(matrix, 2, 1),
            "':' is source ink, though a sparse block may drop it later");
        assertFalse(AsciiGlyphRenderer.isVisibleGlyph(matrix, 0, 0),
            "empty '.' cells must stay transparent");
    }
}
