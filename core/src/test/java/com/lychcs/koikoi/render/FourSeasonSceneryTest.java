package com.lychcs.koikoi.render;

import com.badlogic.gdx.math.Vector2;
import com.lychcs.koikoi.world.AsciiMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates the four-season scenery dressing: monumental border gates, paved
 * cardinal roads, sanctuary village houses and realm-specific vegetation.
 */
class FourSeasonSceneryTest {

    private static final float SCALE = 1f / 64f;
    private static final long SEED = 0x4EA5_0F1E_5EA5_0ABCL;

    private WorldSeasonMap world;
    private Heightfield field;
    /** Same terrain before the mountain-ring raise, used as a baseline. */
    private Heightfield plainField;
    private SceneryPlan plan;

    @BeforeEach
    void setUp() {
        world = WorldSeasonMap.createHubWorld(WorldSeasonMap.DEFAULT_WORLD_TILES);
        AsciiMap map = world.getMap();
        float mapWidth = map.getWorldWidth() * SCALE;
        float mapHeight = map.getWorldHeight() * SCALE;
        float size = Math.max(mapWidth, mapHeight) * 1.25f;
        float centerX = mapWidth * 0.5f;
        float centerZ = -mapHeight * 0.5f;
        float originX = centerX - size * 0.5f;
        float originZ = centerZ - size * 0.5f;
        plainField = Heightfield.generate(4242L, originX, originZ, size, 120, 3.2f);
        field = Heightfield.generate(4242L, originX, originZ, size, 120, 3.2f);
        field.raiseChebyshevRing(centerX, centerZ,
            WorldSeasonMap.MOUNTAIN_RING_DISTANCE_TILES,
            WorldSeasonMap.MOUNTAIN_RING_THICKNESS + 2.6f, 4.2f,
            WorldSeasonMap.ROAD_HALF_WIDTH_TILES + 2.5f);
        SceneryPlan.levelClearings(field, map, SCALE);
        plan = SceneryPlan.generate(map, field, SCALE, SEED, world);
    }

    @Test
    void monumentalBorderGatesStandAtAllFourPasses() {
        assertEquals(4, world.getBorderTorii().size());
        for (WorldSeasonMap.BorderTorii gate : world.getBorderTorii()) {
            boolean found = false;
            for (Vector2 anchor : plan.getToriiAnchors()) {
                if (Math.hypot(anchor.x - gate.x(), anchor.y - gate.z()) < 0.01f) {
                    found = true;
                    break;
                }
            }
            assertTrue(found, "missing border torii for " + gate.season());
        }

        // The monumental torii declares two tall pillar footprints per gate.
        int tallPillars = 0;
        for (SceneryPlan.Solid solid : plan.getSolids()) {
            if (solid.halfWidth() < 0.6f && solid.halfDepth() < 0.6f
                && solid.topY() > field.getHeight(solid.x(), solid.z()) + 5.5f) {
                tallPillars++;
            }
        }
        assertTrue(tallPillars >= 8,
            "each border gate needs two scaled (monumental) pillars, found " + tallPillars);
    }

    @Test
    void pavedCardinalRoadsAndVillageHousesRingTheSanctuary() {
        float hubX = world.getHubWorldX();
        float hubZ = world.getHubWorldZ();
        assertRoadPlate(hubX + 10f, hubZ);
        assertRoadPlate(hubX - 10f, hubZ);
        assertRoadPlate(hubX, hubZ + 10f);
        assertRoadPlate(hubX, hubZ - 10f);

        assertEquals(3, plan.getVillagerAnchors().size(),
            "the plaza houses need villager [E] TALK posts");
        for (Vector2 villager : plan.getVillagerAnchors()) {
            assertTrue(Math.hypot(villager.x - hubX, villager.y - hubZ) < 22f,
                "villagers must stay inside the sanctuary plaza");
        }
        assertFalse(plan.getBoxes().isEmpty(), "village houses must add roofs and walls");
        assertFalse(plan.getWarmLights().isEmpty(), "village lanterns must glow");
    }

    @Test
    void eachRealmGrowsItsOwnVegetationPalette() {
        int springMaples = 0;
        int fallMaples = 0;
        int summerPines = 0;
        int winterPines = 0;
        for (SceneryPlan.NatureInstance instance : plan.getNatureInstances()) {
            WorldSeasonMap.Season region =
                world.regionAtWorld(instance.x(), instance.z());
            boolean maple = instance.kind() == SceneryPlan.NatureKind.MAPLE_TREE;
            boolean pine = instance.kind() == SceneryPlan.NatureKind.PINE_TREE_TALL
                || instance.kind() == SceneryPlan.NatureKind.PINE_TREE_SMALL;
            if (maple && region == WorldSeasonMap.Season.SPRING) {
                springMaples++;
            } else if (maple && region == WorldSeasonMap.Season.FALL) {
                fallMaples++;
            } else if (pine && region == WorldSeasonMap.Season.SUMMER) {
                summerPines++;
            } else if (pine && region == WorldSeasonMap.Season.WINTER) {
                winterPines++;
            }
        }
        assertTrue(springMaples > 0, "spring must grow sakura maples");
        assertTrue(fallMaples > 0, "fall must grow momiji maples");
        assertTrue(summerPines > 0, "summer must grow pine woods");
        assertTrue(winterPines > 0, "winter must grow frost pines");
    }

    @Test
    void everyNatureModelStaysGroundedOnTheHeightfield() {
        for (SceneryPlan.NatureInstance instance : plan.getNatureInstances()) {
            float ground = field.getHeight(instance.x(), instance.z());
            assertTrue(instance.y() >= ground - 0.06f,
                "nature must not sink through the terrain: " + instance);
            assertTrue(instance.y() <= ground + 0.40f,
                "nature must not hover above the terrain: " + instance);
            assertTrue(instance.scale() > 0.2f && instance.scale() < 2f,
                "nature scale must stay sane: " + instance);
        }
    }

    @Test
    void mountainRingRaisesTheRidgeButKeepsTheCardinalPassesLow() {
        float hubX = world.getHubWorldX();
        float hubZ = world.getHubWorldZ();
        float ring = WorldSeasonMap.MOUNTAIN_RING_DISTANCE_TILES;

        float ridgeRaise = 0f;
        float passRaise = 0f;
        for (int offset = 6; offset <= 14; offset++) {
            ridgeRaise = Math.max(ridgeRaise,
                field.getHeight(hubX + ring, hubZ + offset)
                    - plainField.getHeight(hubX + ring, hubZ + offset));
        }
        for (float lane = -1f; lane <= 1f; lane += 0.5f) {
            passRaise = Math.max(passRaise,
                field.getHeight(hubX + ring, hubZ + lane)
                    - plainField.getHeight(hubX + ring, hubZ + lane));
        }
        assertTrue(ridgeRaise > 1.5f,
            "the mountain ring must raise a visible ridge, was " + ridgeRaise);
        assertTrue(passRaise < ridgeRaise * 0.5f,
            "the cardinal pass must stay low enough to walk, ridge="
                + ridgeRaise + " pass=" + passRaise);
    }

    private void assertRoadPlate(float x, float z) {
        for (SceneryPlan.PathPlate plate : plan.getPathPlates()) {
            if (Math.hypot(plate.x() - x, plate.z() - z) < 1.0f) {
                return;
            }
        }
        throw new AssertionError("missing paved road plate near " + x + "," + z);
    }
}
