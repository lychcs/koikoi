package com.lychcs.koikoi.render;

import com.lychcs.koikoi.run.GameSeason;
import com.lychcs.koikoi.world.AsciiMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates the cardinal season geography, the smooth hub transition zone, the
 * border torii placement and the 32-tile spatial chunk grid of the four-season
 * overworld.
 */
class WorldSeasonMapTest {

    private static final int TILES = WorldSeasonMap.DEFAULT_WORLD_TILES;

    private WorldSeasonMap world;
    private float hubX;
    private float hubZ;

    @BeforeEach
    void setUp() {
        world = WorldSeasonMap.createHubWorld(TILES);
        hubX = world.getHubWorldX();
        hubZ = world.getHubWorldZ();
    }

    @Test
    void cardinalRegionsFollowTheFourSeasonGeography() {
        assertEquals(WorldSeasonMap.Season.SANCTUARY, world.regionAtWorld(hubX, hubZ),
            "the hub centre is the neutral Sanctuary Village");
        assertEquals(WorldSeasonMap.Season.SPRING, world.regionAtWorld(hubX + 50f, hubZ),
            "East must resolve to spring (Haru)");
        assertEquals(WorldSeasonMap.Season.SUMMER, world.regionAtWorld(hubX, hubZ + 50f),
            "South must resolve to summer (Natsu)");
        assertEquals(WorldSeasonMap.Season.FALL, world.regionAtWorld(hubX - 50f, hubZ),
            "West must resolve to fall (Aki)");
        assertEquals(WorldSeasonMap.Season.WINTER, world.regionAtWorld(hubX, hubZ - 50f),
            "North must resolve to winter (Fuyu)");

        assertEquals(GameSeason.SPRING, WorldSeasonMap.Season.SPRING.gameSeason());
        assertEquals(GameSeason.SUMMER, WorldSeasonMap.Season.SUMMER.gameSeason());
        assertEquals(GameSeason.AUTUMN, WorldSeasonMap.Season.FALL.gameSeason());
        assertEquals(GameSeason.WINTER, WorldSeasonMap.Season.WINTER.gameSeason());
    }

    @Test
    void generatedMapPublishesSeasonMetadataAndHubLandmarks() {
        AsciiMap map = world.getMap();
        assertEquals(TILES, map.getWidth());
        assertEquals(TILES, map.getHeight());
        assertEquals(WorldSeasonMap.LOCATION_ID, map.getLocationId());
        assertEquals(WorldSeasonMap.TILE_SIZE, map.getTileSize());

        assertEquals(1, map.tilesOf(AsciiMap.SHRINE).size(), "one hub shrine");
        assertEquals(1, map.tilesOf(AsciiMap.TAVERN_DOOR).size(), "one hub tavern");
        assertEquals(1, map.tilesOf(AsciiMap.SHOP).size(), "one hub shop");
        assertEquals(1, map.tilesOf(AsciiMap.PLAYER_SPAWN).size(), "one player spawn");
        assertEquals(8, map.getYokaiSpawns().size(), "two Yokai per realm");

        assertNotNull(world.regionAtTile(world.getMap().getPlayerSpawn()));
    }

    @Test
    void easternYokaiAreGentleAndWesternYokaiAreHighTier() {
        AsciiMap map = world.getMap();
        for (AsciiMap.YokaiSpawn spawn : map.getYokaiSpawns()) {
            float x = map.tileCenterX(spawn.col()) * WorldSeasonMap.WORLD_UNIT_PER_PIXEL;
            float z = -map.tileCenterY(spawn.row()) * WorldSeasonMap.WORLD_UNIT_PER_PIXEL;
            WorldSeasonMap.Season region = world.regionAtWorld(x, z);
            if (region == WorldSeasonMap.Season.SPRING
                || region == WorldSeasonMap.Season.SUMMER) {
                assertEquals(WorldSeasonMap.ENCOUNTER_SPRING_SUMMER, spawn.encounterId());
            } else {
                assertEquals(WorldSeasonMap.ENCOUNTER_FALL_WINTER, spawn.encounterId());
            }
        }
    }

    @Test
    void transitionWeightsAreSmoothNormalizedAndMonotonic() {
        float previousSpring = -1f;
        float previousSanctuary = 2f;
        for (float distance = 0f; distance <= 60f; distance += 0.25f) {
            WorldSeasonMap.SeasonWeights weights = world.weightsAt(hubX + distance, hubZ);
            float sum = weights.sanctuary() + weights.spring() + weights.summer()
                + weights.fall() + weights.winter();
            assertEquals(1f, sum, 0.002f, "weights must always be normalized");
            assertTrue(weights.spring() >= previousSpring - 0.0001f,
                "the eastern realm weight must grow monotonically");
            assertTrue(weights.sanctuary() <= previousSanctuary + 0.0001f,
                "the sanctuary weight must fade monotonically");
            if (previousSpring >= 0f) {
                assertTrue(weights.spring() - previousSpring < 0.03f,
                    "no hard seam inside the transition zone");
            }
            previousSpring = weights.spring();
            previousSanctuary = weights.sanctuary();
        }

        assertEquals(1f, world.weightsAt(hubX, hubZ).sanctuary(), 0.001f,
            "the plaza core is fully neutral");
        assertTrue(world.weightsAt(hubX + WorldSeasonMap.SEASON_BLEND_START_TILES, hubZ)
            .sanctuary() > 0.9f, "the blend only starts at the transition edge");
        assertEquals(1f, world.weightsAt(
                hubX + WorldSeasonMap.SEASON_BLEND_END_TILES, hubZ).spring(), 0.001f,
            "blending completes at the end of the transition zone");

        WorldSeasonMap.SeasonWeights diagonal = world.weightsAt(hubX + 80f, hubZ + 80f);
        assertTrue(Math.abs(diagonal.spring() - 0.5f) < 0.01f,
            "diagonal corners share the two realms smoothly");
        assertTrue(Math.abs(diagonal.summer() - 0.5f) < 0.01f);
        assertEquals(0f, diagonal.sanctuary(), 0.001f);
    }

    @Test
    void fourMonumentalBorderGatesSitOnTheCardinalAxes() {
        List<WorldSeasonMap.BorderTorii> gates = world.getBorderTorii();
        assertEquals(4, gates.size());

        assertGate(gates.get(0), WorldSeasonMap.Season.SPRING,
            WorldSeasonMap.Cardinal.EAST, hubX, hubZ);
        assertGate(gates.get(1), WorldSeasonMap.Season.SUMMER,
            WorldSeasonMap.Cardinal.SOUTH, hubX, hubZ);
        assertGate(gates.get(2), WorldSeasonMap.Season.FALL,
            WorldSeasonMap.Cardinal.WEST, hubX, hubZ);
        assertGate(gates.get(3), WorldSeasonMap.Season.WINTER,
            WorldSeasonMap.Cardinal.NORTH, hubX, hubZ);

        for (WorldSeasonMap.BorderTorii gate : gates) {
            assertEquals(WorldSeasonMap.BORDER_GATE_DISTANCE_TILES,
                gate.distanceTiles(), 0.001f);
            assertTrue(gate.distanceTiles() >= 28f && gate.distanceTiles() <= 36f,
                "border gates belong to the 28-36 tile transition band");
            assertTrue(gate.distanceTiles() < WorldSeasonMap.MOUNTAIN_RING_DISTANCE_TILES,
                "the gate stands before the mountain pass");
            assertTrue(world.weightsAt(gate.x(), gate.z()).seasonal() > 0.85f,
                "the far side of a gate is fully seasonal");

            int col = (int) Math.floor(gate.x());
            int row = (int) Math.floor(-gate.z() - 0.001f);
            assertTrue(world.getMap().isWalkable(col, row),
                "every border torii spans a walkable paved pass");
        }
    }

    @Test
    void mountainRingSeparatesRealmsButKeepsThePassesOpen() {
        AsciiMap map = world.getMap();
        int centerCol = world.getHubTileCol();
        int centerRow = world.getHubTileRow();
        int ring = WorldSeasonMap.MOUNTAIN_RING_DISTANCE_TILES;

        assertEquals(AsciiMap.WALL, map.glyphAt(centerCol + ring, centerRow + 10),
            "the mountain ring blocks the seasonal boundary");
        assertTrue(map.isWalkable(centerCol + ring, centerRow),
            "the eastern paved pass stays open");
        assertTrue(map.isWalkable(centerCol - ring, centerRow),
            "the western paved pass stays open");
        assertTrue(map.isWalkable(centerCol, centerRow + ring),
            "the southern paved pass stays open");
        assertTrue(map.isWalkable(centerCol, centerRow - ring),
            "the northern paved pass stays open");

        for (AsciiMap.Tile road : world.getRoadTiles()) {
            assertTrue(map.isWalkable(road.col(), road.row()),
                "cardinal road cells must stay walkable: " + road);
        }
        assertFalse(world.getRoadTiles().isEmpty(), "the four roads must exist");
    }


    @Test
    void spatialChunksAre32TilesAndCullByDistance() {
        WorldSeasonMap.ChunkGrid grid = world.getChunkGrid();
        assertEquals(WorldSeasonMap.CHUNK_SIZE_TILES, (int) grid.getChunkSize());
        assertEquals(6, grid.getColumns(), "192 / 32 = 6 chunks per side");
        assertEquals(36, grid.count());

        assertEquals(0, grid.chunkOf(0f, -191f));
        assertEquals(35, grid.chunkOf(191f, -32f));
        assertEquals(grid.chunkOf(hubX, hubZ),
            grid.chunkOf(hubX + 4f, hubZ + 4f), "nearby points share a chunk");

        // A point deep inside one chunk: only that chunk is visible at radius 1.
        float x = 100f;
        float z = -100f;
        int[] visible = new int[grid.count()];
        int nearCount = grid.collectVisible(x, z, 1f, visible);
        assertEquals(1, nearCount);
        assertEquals(grid.chunkOf(x, z), visible[0]);

        int hubCount = grid.collectVisible(hubX, hubZ, 20f, visible);
        assertEquals(4, hubCount, "only the four plaza chunks are near the hub");

        int wideCount = grid.collectVisible(hubX, hubZ, 200f, visible);
        assertEquals(grid.count(), wideCount, "the whole map is within a wide radius");

        int farChunk = grid.chunkOf(0f, -191f);
        assertFalse(grid.isVisible(farChunk, hubX, hubZ, 20f),
            "far chunks are culled");
        assertTrue(grid.isVisible(farChunk, 0f, -191f, 1f),
            "the chunk containing the camera stays visible");
    }

    private void assertGate(WorldSeasonMap.BorderTorii gate,
                            WorldSeasonMap.Season season,
                            WorldSeasonMap.Cardinal cardinal,
                            float hubWorldX, float hubWorldZ) {
        assertEquals(season, gate.season());
        assertEquals(cardinal, gate.cardinal());
        switch (cardinal) {
            case EAST -> {
                assertEquals(hubWorldX + WorldSeasonMap.BORDER_GATE_DISTANCE_TILES,
                    gate.x(), 0.001f);
                assertEquals(hubWorldZ, gate.z(), 0.001f);
                assertEquals((float) (Math.PI * 0.5), gate.yaw(), 0.001f);
            }
            case WEST -> {
                assertEquals(hubWorldX - WorldSeasonMap.BORDER_GATE_DISTANCE_TILES,
                    gate.x(), 0.001f);
                assertEquals(hubWorldZ, gate.z(), 0.001f);
                assertEquals((float) (Math.PI * 0.5), gate.yaw(), 0.001f);
            }
            case SOUTH -> {
                assertEquals(hubWorldX, gate.x(), 0.001f);
                assertEquals(hubWorldZ + WorldSeasonMap.BORDER_GATE_DISTANCE_TILES,
                    gate.z(), 0.001f);
                assertEquals(0f, gate.yaw(), 0.001f);
            }
            default -> {
                assertEquals(hubWorldX, gate.x(), 0.001f);
                assertEquals(hubWorldZ - WorldSeasonMap.BORDER_GATE_DISTANCE_TILES,
                    gate.z(), 0.001f);
                assertEquals(0f, gate.yaw(), 0.001f);
            }
        }
    }

}
