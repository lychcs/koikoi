package com.lychcs.koikoi.world;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Validates cellular-automata outdoor generation and its map contract. */
class ProcGenMapSystemTest {

    @Test
    void generationIsDeterministicForTheSameSeed() {
        String first = ProcGenMapSystem.generateOutdoorText(42L, 32, 24, 48, "spirit_glade", "SUMMER");
        String second = ProcGenMapSystem.generateOutdoorText(42L, 32, 24, 48, "spirit_glade", "SUMMER");
        String other = ProcGenMapSystem.generateOutdoorText(43L, 32, 24, 48, "spirit_glade", "SUMMER");

        assertEquals(first, second);
        assertNotEquals(first, other);
    }

    @Test
    void generatedTextRoundTripsThroughTheLoader() {
        String text = ProcGenMapSystem.generateOutdoorText(7L, 20, 16, 48, "bamboo_forest", "AUTUMN");
        AsciiMap map = AsciiMapLoader.parse(text, "procgen:7");

        assertEquals("bamboo_forest", map.getLocationId());
        assertEquals("AUTUMN", map.getSeasonName());
        assertEquals(48, map.getTileSize());
        assertEquals(20, map.getWidth());
        assertEquals(16, map.getHeight());
    }

    @Test
    void generatedMapsAreSealedAndSpawnOnFloor() {
        for (long seed = 0; seed < 20; seed++) {
            AsciiMap map = ProcGenMapSystem.generateOutdoor(seed);
            String context = "seed " + seed;

            assertEquals(ProcGenMapSystem.DEFAULT_WIDTH, map.getWidth(), context);
            assertEquals(ProcGenMapSystem.DEFAULT_HEIGHT, map.getHeight(), context);

            for (int col = 0; col < map.getWidth(); col++) {
                assertTrue(map.isSolid(col, 0), context + ": north border");
                assertTrue(map.isSolid(col, map.getHeight() - 1), context + ": south border");
            }
            for (int row = 0; row < map.getHeight(); row++) {
                assertTrue(map.isSolid(0, row), context + ": west border");
                assertTrue(map.isSolid(map.getWidth() - 1, row), context + ": east border");
            }

            AsciiMap.Tile player = map.getPlayerSpawn();
            assertNotNull(player, context);
            assertTrue(map.isWalkable(player.col(), player.row()), context + ": player spawn");

            List<AsciiMap.YokaiSpawn> yokaiSpawns = map.getYokaiSpawns();
            assertEquals(2, yokaiSpawns.size(), context);
            for (AsciiMap.YokaiSpawn spawn : yokaiSpawns) {
                assertTrue(map.isWalkable(spawn.col(), spawn.row()), context + ": yokai spawn");
                assertEquals(ProcGenMapSystem.DEFAULT_YOKAI_ENCOUNTER_ID, spawn.encounterId(), context);
            }

            assertEquals(1, map.tilesOf(AsciiMap.TAVERN_DOOR).size(), context);
            assertEquals(1, map.tilesOf(AsciiMap.SHOP).size(), context);
            assertEquals(1, map.tilesOf(AsciiMap.SHRINE).size(), context);
        }
    }

    @Test
    void everyWalkableTileIsReachableFromThePlayerSpawn() {
        for (long seed = 0; seed < 10; seed++) {
            AsciiMap map = ProcGenMapSystem.generateOutdoor(seed, 32, 24, 32, "SPRING");
            int walkable = countWalkableTiles(map);
            int reachable = countReachableTiles(map);

            assertEquals(walkable, reachable,
                "seed " + seed + ": unreachable floor tiles break the overworld");
        }
    }

    @Test
    void generatedMapsOfferTheMinimumFloorRatio() {
        for (long seed = 0; seed < 10; seed++) {
            AsciiMap map = ProcGenMapSystem.generateOutdoor(seed, 24, 20, 64, "WINTER");
            int walkable = countWalkableTiles(map);
            int required = 24 * 20 * ProcGenMapSystem.MIN_FLOOR_RATIO_PERCENT / 100;

            assertTrue(walkable >= required, "seed " + seed + " has only " + walkable
                + " walkable tiles (required " + required + ")");
        }
    }

    @Test
    void rejectsTooSmallMaps() {
        assertThrows(IllegalArgumentException.class, () ->
            ProcGenMapSystem.generateOutdoor(1L, 8, 8, 64, "SPRING"));
    }

    @Test
    void rejectsNonPositiveTileSize() {
        assertThrows(IllegalArgumentException.class, () ->
            ProcGenMapSystem.generateOutdoor(1L, 16, 16, 0, "SPRING"));
    }

    @Test
    void generatedGlyphsUseOnlyTheDocumentedLegend() {
        AsciiMap map = ProcGenMapSystem.generateOutdoor(11L, 20, 16, 64, "SPRING");
        for (int row = 0; row < map.getHeight(); row++) {
            for (int col = 0; col < map.getWidth(); col++) {
                char glyph = map.glyphAt(col, row);
                assertTrue(glyph == AsciiMap.WALL
                        || glyph == AsciiMap.FLOOR
                        || glyph == AsciiMap.PLAYER_SPAWN
                        || glyph == AsciiMap.YOKAI_SPAWN
                        || glyph == AsciiMap.TAVERN_DOOR
                        || glyph == AsciiMap.SHOP
                        || glyph == AsciiMap.SHRINE,
                    "unexpected glyph '" + glyph + "' at (" + col + ", " + row + ")");
            }
        }
    }

    @Test
    void floorMatrixIsDeterministicAndUsesOnlyTerrainGlyphs() {
        char[][] first = ProcGenMapSystem.generateFloorMatrix(7L, 48, 32);
        char[][] second = ProcGenMapSystem.generateFloorMatrix(7L, 48, 32);

        assertEquals(32, first.length);
        assertEquals(48, first[0].length);

        boolean sawComma = false;
        boolean sawDot = false;
        for (int row = 0; row < first.length; row++) {
            assertEquals(48, first[row].length);
            for (int col = 0; col < first[row].length; col++) {
                char glyph = first[row][col];
                assertTrue(glyph == ProcGenMapSystem.TERRAIN_COMMA
                        || glyph == ProcGenMapSystem.TERRAIN_DOT
                        || glyph == ProcGenMapSystem.TERRAIN_TUFT,
                    "unexpected terrain glyph '" + glyph + "'");
                assertEquals(glyph, second[row][col], "floor matrix must be deterministic");

                sawComma |= glyph == ProcGenMapSystem.TERRAIN_COMMA;
                sawDot |= glyph == ProcGenMapSystem.TERRAIN_DOT;
            }
        }

        assertTrue(sawComma, "the backdrop must contain ',' terrain");
        assertTrue(sawDot, "the backdrop must contain '.' terrain");
    }

    @Test
    void floorMatrixRejectsInvalidSizes() {
        assertThrows(IllegalArgumentException.class, () ->
            ProcGenMapSystem.generateFloorMatrix(1L, 0, 10));
        assertThrows(IllegalArgumentException.class, () ->
            ProcGenMapSystem.generateFloorMatrix(1L, 10, -1));
    }

    // ------------------------------------------------------------------
    // Test helpers
    // ------------------------------------------------------------------

    private static int countWalkableTiles(AsciiMap map) {
        int walkable = 0;
        for (int row = 0; row < map.getHeight(); row++) {
            for (int col = 0; col < map.getWidth(); col++) {
                if (map.isWalkable(col, row)) {
                    walkable++;
                }
            }
        }
        return walkable;
    }

    private static int countReachableTiles(AsciiMap map) {
        boolean[][] visited = new boolean[map.getHeight()][map.getWidth()];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        AsciiMap.Tile spawn = map.getPlayerSpawn();

        visited[spawn.row()][spawn.col()] = true;
        queue.add(new int[] {spawn.row(), spawn.col()});
        int reachable = 0;

        int[][] offsets = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        while (!queue.isEmpty()) {
            int[] cell = queue.poll();
            reachable++;

            for (int[] offset : offsets) {
                int row = cell[0] + offset[0];
                int col = cell[1] + offset[1];
                if (row < 0 || row >= map.getHeight() || col < 0 || col >= map.getWidth()) {
                    continue;
                }
                if (visited[row][col] || !map.isWalkable(col, row)) {
                    continue;
                }
                visited[row][col] = true;
                queue.add(new int[] {row, col});
            }
        }

        return reachable;
    }
}
