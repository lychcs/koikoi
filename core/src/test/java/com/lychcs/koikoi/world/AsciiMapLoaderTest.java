package com.lychcs.koikoi.world;

import com.lychcs.koikoi.TestAssetFiles;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Validates the text-map pipeline (legend, metadata, validation). */
class AsciiMapLoaderTest {

    private static final String SOURCE = "maps/spring_village.txt";

    @Test
    void loadsSpringVillageMap() throws IOException {
        AsciiMap map = AsciiMapLoader.parse(TestAssetFiles.read(SOURCE), SOURCE);

        assertEquals("spring_village", map.getLocationId());
        assertEquals("SPRING", map.getSeasonName());
        assertEquals(64, map.getTileSize());
        assertEquals(24, map.getWidth());
        assertEquals(18, map.getHeight());
        assertEquals(24 * 64f, map.getWorldWidth(), 0.001f);
        assertEquals(18 * 64f, map.getWorldHeight(), 0.001f);

        assertEquals(1, map.tilesOf(AsciiMap.PLAYER_SPAWN).size());
        assertEquals(1, map.tilesOf(AsciiMap.TAVERN_DOOR).size());
        assertEquals(1, map.tilesOf(AsciiMap.SHOP).size());
        assertEquals(1, map.tilesOf(AsciiMap.SHRINE).size());

        List<AsciiMap.YokaiSpawn> yokaiSpawns = map.getYokaiSpawns();
        assertEquals(1, yokaiSpawns.size());
        assertEquals("spring_village_kitsune_01", yokaiSpawns.get(0).encounterId());
    }

    @Test
    void springVillageIsSealedByWalls() throws IOException {
        AsciiMap map = AsciiMapLoader.parse(TestAssetFiles.read(SOURCE), SOURCE);

        for (int col = 0; col < map.getWidth(); col++) {
            assertTrue(map.isSolid(col, 0), "north border at column " + col);
            assertTrue(map.isSolid(col, map.getHeight() - 1), "south border at column " + col);
        }
        for (int row = 0; row < map.getHeight(); row++) {
            assertTrue(map.isSolid(0, row), "west border at row " + row);
            assertTrue(map.isSolid(map.getWidth() - 1, row), "east border at row " + row);
        }
    }

    @Test
    void spawnGlyphsAreWalkable() throws IOException {
        AsciiMap map = AsciiMapLoader.parse(TestAssetFiles.read(SOURCE), SOURCE);

        AsciiMap.Tile spawn = map.getPlayerSpawn();
        assertNotNull(spawn);
        assertTrue(map.isWalkable(spawn.col(), spawn.row()));
        for (AsciiMap.YokaiSpawn yokai : map.getYokaiSpawns()) {
            assertTrue(map.isWalkable(yokai.col(), yokai.row()));
        }
        for (AsciiMap.Tile tile : map.tilesOf(AsciiMap.TAVERN_DOOR)) {
            assertTrue(map.isWalkable(tile.col(), tile.row()));
        }
    }

    @Test
    void yokaiMetadataIsAssignedInReadingOrder() {
        String text = """
            !yokai first_encounter
            !yokai second_encounter
            #####
            #Y@Y#
            #####
            """;

        AsciiMap map = AsciiMapLoader.parse(text, "inline.txt");

        List<AsciiMap.YokaiSpawn> spawns = map.getYokaiSpawns();
        assertEquals(2, spawns.size());
        assertEquals(1, spawns.get(0).col());
        assertEquals("first_encounter", spawns.get(0).encounterId());
        assertEquals(3, spawns.get(1).col());
        assertEquals("second_encounter", spawns.get(1).encounterId());
    }

    @Test
    void defaultsAreAppliedWhenMetadataIsMissing() {
        AsciiMap map = AsciiMapLoader.parse("""
            ###
            #@#
            ###
            """, "maps/quiet_grove.txt");

        assertEquals("quiet_grove", map.getLocationId());
        assertEquals("SPRING", map.getSeasonName());
        assertEquals(64, map.getTileSize());
        assertTrue(map.getYokaiSpawns().isEmpty());
    }

    @Test
    void rejectsRaggedRows() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
            AsciiMapLoader.parse("""
                ####
                #@#
                ####
                """, "ragged.txt"));
        assertTrue(error.getMessage().contains("ragged"), error.getMessage());
    }

    @Test
    void rejectsUnknownGlyph() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
            AsciiMapLoader.parse("""
                ###
                #X#
                #@#
                ###
                """, "unknown.txt"));
        assertTrue(error.getMessage().contains("unknown glyph"), error.getMessage());
    }

    @Test
    void rejectsMissingPlayerSpawn() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
            AsciiMapLoader.parse("""
                ###
                #.#
                ###
                """, "nospawn.txt"));
        assertTrue(error.getMessage().contains("player spawn"), error.getMessage());
    }

    @Test
    void rejectsDuplicatePlayerSpawn() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
            AsciiMapLoader.parse("""
                ####
                #@@#
                ####
                """, "twospawns.txt"));
        assertTrue(error.getMessage().contains("duplicate"), error.getMessage());
    }

    @Test
    void rejectsYokaiMetadataCountMismatch() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
            AsciiMapLoader.parse("""
                !yokai only_one
                #####
                #@YY#
                #####
                """, "mismatch.txt"));
        assertTrue(error.getMessage().contains("'Y' spawns"), error.getMessage());
    }

    @Test
    void rejectsUnknownMetadataKey() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
            AsciiMapLoader.parse("""
                !seasonn SPRING
                ###
                #@#
                ###
                """, "typo.txt"));
        assertTrue(error.getMessage().contains("unknown metadata key"), error.getMessage());
    }

    @Test
    void rejectsMetadataAfterTheGrid() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
            AsciiMapLoader.parse("""
                ###
                #@#
                ###
                !season SPRING
                """, "late.txt"));
        assertTrue(error.getMessage().contains("after the tile grid"), error.getMessage());
    }
}
