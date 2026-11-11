package com.lychcs.koikoi.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Validates the dense Caves-of-Qud-style terrain layer of the ASCII maps. */
class DenseTerrainTest {

    private static final String MAP = """
        !locationId test_glade
        !tileSize 64
        ########
        #......#
        #..@...#
        #..Y...#
        #..T.S.#
        #..H...#
        #......#
        ########
        """;

    @Test
    void denseLayerCoversEveryTileWithMeadowAndCanopyGlyphs() {
        AsciiMap map = AsciiMapLoader.parse(MAP, "test");
        AsciiMap dense = ProcGenMapSystem.densify(map, 7L);

        assertTrue(dense.hasDenseTerrain());
        assertEquals(map.getWidth() * AsciiMap.DETAIL_SCALE, dense.getDenseTerrainWidth());
        assertEquals(map.getHeight() * AsciiMap.DETAIL_SCALE, dense.getDenseTerrainHeight());

        boolean sawMeadow = false;
        boolean sawCanopy = false;
        for (int row = 0; row < dense.getDenseTerrainHeight(); row++) {
            for (int col = 0; col < dense.getDenseTerrainWidth(); col++) {
                char glyph = dense.denseTerrainAt(col, row);
                assertTrue(AsciiMap.isDenseTerrainGlyph(glyph),
                    "unexpected dense glyph '" + glyph + "' at (" + col + ", " + row + ")");
                sawMeadow |= AsciiMap.isMeadowGlyph(glyph);
                sawCanopy |= AsciiMap.isCanopyGlyph(glyph);
            }
        }

        assertTrue(sawMeadow, "the dense layer must contain meadow foliage");
        assertTrue(sawCanopy, "the dense layer must contain forest/rock clusters");
    }

    @Test
    void denseLayerIsDeterministicForTheSameSeed() {
        AsciiMap map = AsciiMapLoader.parse(MAP, "test");
        char[][] first = ProcGenMapSystem.generateDenseTerrain(map, 1234L);
        char[][] second = ProcGenMapSystem.generateDenseTerrain(map, 1234L);
        char[][] other = ProcGenMapSystem.generateDenseTerrain(map, 4321L);

        assertEquals(first.length, second.length);
        boolean differs = false;
        for (int row = 0; row < first.length; row++) {
            for (int col = 0; col < first[row].length; col++) {
                assertEquals(first[row][col], second[row][col], "dense terrain must be deterministic");
                differs |= first[row][col] != other[row][col];
            }
        }
        assertTrue(differs, "a different seed must grow a different terrain");
    }

    @Test
    void wallsBecomeCanopyClustersAndMarkersStayOnMeadow() {
        AsciiMap map = AsciiMapLoader.parse(MAP, "test");
        AsciiMap dense = ProcGenMapSystem.densify(map, 99L);

        for (int row = 0; row < map.getHeight(); row++) {
            for (int col = 0; col < map.getWidth(); col++) {
                char base = map.glyphAt(col, row);
                for (int subRow = 0; subRow < AsciiMap.DETAIL_SCALE; subRow++) {
                    for (int subCol = 0; subCol < AsciiMap.DETAIL_SCALE; subCol++) {
                        char glyph = dense.denseTerrainAt(
                            col * AsciiMap.DETAIL_SCALE + subCol,
                            row * AsciiMap.DETAIL_SCALE + subRow);

                        if (base == AsciiMap.WALL) {
                            assertTrue(AsciiMap.isCanopyGlyph(glyph),
                                "wall tile must be a canopy cluster but had '" + glyph + "'");
                        }
                        if (isMarker(base)) {
                            assertTrue(AsciiMap.isMeadowGlyph(glyph),
                                "marker tile must stay on meadow but had '" + glyph + "'");
                        }
                    }
                }
            }
        }
    }

    @Test
    void denseLayerNeverChangesTheGameplayGrid() {
        AsciiMap map = AsciiMapLoader.parse(MAP, "test");
        AsciiMap dense = ProcGenMapSystem.densify(map, 5L);

        assertEquals(map.getPlayerSpawn(), dense.getPlayerSpawn());
        assertEquals(map.getYokaiSpawns(), dense.getYokaiSpawns());
        for (int row = 0; row < map.getHeight(); row++) {
            for (int col = 0; col < map.getWidth(); col++) {
                assertEquals(map.glyphAt(col, row), dense.glyphAt(col, row));
                assertEquals(map.isWalkable(col, row), dense.isWalkable(col, row));
            }
        }
    }

    @Test
    void denseVocabularyAcceptsQuietPunctuationOnly() {
        for (char glyph : new char[] {'/', '\\', ',', '.', '\'', ';'}) {
            assertTrue(AsciiMap.isMeadowGlyph(glyph), "meadow glyph '" + glyph + "'");
        }
        for (char glyph : new char[] {'*'}) {
            assertTrue(AsciiMap.isBlossomGlyph(glyph), "blossom glyph '" + glyph + "'");
        }
        for (char glyph : new char[] {'/', '\\', ';'}) {
            assertTrue(AsciiMap.isBambooGlyph(glyph), "bamboo glyph '" + glyph + "'");
        }
        for (char glyph : new char[] {'-', '='}) {
            assertTrue(AsciiMap.isToriiGlyph(glyph), "torii glyph '" + glyph + "'");
        }
        assertTrue(AsciiMap.isSakuraGlyph(','), "sakura petal glyph");
        for (char glyph : new char[] {',', '.', '\'', ';', '/', '\\'}) {
            assertTrue(AsciiMap.isCanopyGlyph(glyph), "canopy glyph '" + glyph + "'");
        }

        for (char glyph : new char[] {'*', ',', '.', '\'', ';', '/', '\\', '-', '='}) {
            assertTrue(AsciiMap.isDenseTerrainGlyph(glyph), "dense glyph '" + glyph + "'");
        }
        for (char glyph : new char[] {'H', 'Y', 'Q', '@', '%', 'X', '#', '0', '~', 'x', '|', '!', 'T', '+', 'o'}) {
            assertFalse(AsciiMap.isDenseTerrainGlyph(glyph),
                "heavy glyph must not be part of the quiet terrain: '" + glyph + "'");
        }
        assertTrue(AsciiMap.isBendableGlyph(';'), "quiet blades must react to feet");
        assertTrue(AsciiMap.isBendableGlyph('/'), "slanted blades must react to feet");
        assertFalse(AsciiMap.isBendableGlyph(','), "ground dots must stay static");
        assertFalse(AsciiMap.isDenseTerrainGlyph('?'), "'?' must stay invalid");
    }

    @Test
    void withDenseTerrainRejectsWrongSizesAndGlyphs() {
        AsciiMap map = AsciiMapLoader.parse(MAP, "test");

        assertThrows(IllegalArgumentException.class,
            () -> map.withDenseTerrain(new char[3][3]));

        char[][] invalid = new char[map.getHeight() * AsciiMap.DETAIL_SCALE]
            [map.getWidth() * AsciiMap.DETAIL_SCALE];
        for (char[] row : invalid) {
            java.util.Arrays.fill(row, '?');
        }
        assertThrows(IllegalArgumentException.class, () -> map.withDenseTerrain(invalid));
    }

    @Test
    void emptyMapsRejectDenseTerrainQueries() {
        AsciiMap map = AsciiMapLoader.parse(MAP, "test");

        assertFalse(map.hasDenseTerrain());
        assertEquals(0, map.getDenseTerrainWidth());
        assertEquals(0, map.getDenseTerrainHeight());
        assertThrows(IllegalStateException.class, () -> map.denseTerrainAt(0, 0));
    }

    @Test
    void denseTerrainRejectsOutOfBoundsCells() {
        AsciiMap dense = ProcGenMapSystem.densify(AsciiMapLoader.parse(MAP, "test"), 3L);

        assertThrows(IndexOutOfBoundsException.class, () -> dense.denseTerrainAt(-1, 0));
        assertThrows(IndexOutOfBoundsException.class,
            () -> dense.denseTerrainAt(dense.getDenseTerrainWidth(), 0));
        assertThrows(IndexOutOfBoundsException.class,
            () -> dense.denseTerrainAt(0, dense.getDenseTerrainHeight()));
    }

    @Test
    void denseLayerMixesBladesBlossomsAndCanopy() {
        AsciiMap map = ProcGenMapSystem.generateOutdoor(20261111L);
        AsciiMap dense = ProcGenMapSystem.densify(map, 20261111L);

        java.util.Set<Character> meadow = new java.util.HashSet<>();
        boolean sawBlossom = false;
        boolean sawCanopy = false;
        boolean sawBamboo = false;

        for (int row = 0; row < dense.getDenseTerrainHeight(); row++) {
            for (int col = 0; col < dense.getDenseTerrainWidth(); col++) {
                char glyph = dense.denseTerrainAt(col, row);
                if (AsciiMap.isMeadowGlyph(glyph)) {
                    meadow.add(glyph);
                }
                sawBlossom |= AsciiMap.isBlossomGlyph(glyph);
                sawCanopy |= AsciiMap.isCanopyGlyph(glyph);
                sawBamboo |= AsciiMap.isBambooGlyph(glyph);
            }
        }

        assertTrue(meadow.size() >= 5,
            "the meadow must mix sparse dots and leafy blades, saw " + meadow);
        assertTrue(sawBlossom, "flower clusters must appear between the blades");
        assertTrue(sawCanopy, "forest/rock clusters must remain part of the carpet");
        assertTrue(sawBamboo, "bamboo groves must remain part of the carpet");
    }

    private static boolean isMarker(char glyph) {
        return glyph == AsciiMap.PLAYER_SPAWN || glyph == AsciiMap.YOKAI_SPAWN
            || glyph == AsciiMap.TAVERN_DOOR || glyph == AsciiMap.SHOP
            || glyph == AsciiMap.SHRINE;
    }

}
