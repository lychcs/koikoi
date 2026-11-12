package com.lychcs.koikoi.render;

import com.badlogic.gdx.math.Vector2;
import com.lychcs.koikoi.TestAssetFiles;
import com.lychcs.koikoi.world.AsciiMap;
import com.lychcs.koikoi.world.AsciiMapLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Validates the deterministic Japanese scenery plan (paths, gates, temple, groves). */
class SceneryPlanTest {

    private static final float SCALE = 1f / 64f;
    private static final long SEED = 0x51E4_2A11_9C3DL;

    private AsciiMap map;
    private Heightfield field;

    @BeforeEach
    void setUp() throws IOException {
        map = AsciiMapLoader.parse(
            TestAssetFiles.read("maps/spring_village.txt"), "spring_village");
        field = createField(map);
    }

    private static Heightfield createField(AsciiMap map) {
        float mapWidth = map.getWorldWidth() * SCALE;
        float mapHeight = map.getWorldHeight() * SCALE;
        float size = Math.max(mapWidth, mapHeight) * 1.6f + 52f;
        float centerX = mapWidth * 0.5f;
        float centerZ = -mapHeight * 0.5f;
        Heightfield field = Heightfield.generate(1234L,
            centerX - size * 0.5f, centerZ - size * 0.5f, size, 96, 3.2f);
        SceneryPlan.levelClearings(field, map, SCALE);
        return field;
    }

    @Test
    void planIsDeterministic() {
        SceneryPlan first = SceneryPlan.generate(map, field, SCALE, SEED);
        SceneryPlan second = SceneryPlan.generate(map, field, SCALE, SEED);

        assertEquals(first.getBoxes(), second.getBoxes());
        assertEquals(first.getCylinders(), second.getCylinders());
        assertEquals(first.getSpheres(), second.getSpheres());
        assertEquals(first.getPathTiles(), second.getPathTiles());
        assertEquals(first.getToriiAnchors(), second.getToriiAnchors());
        assertEquals(first.getTreeAnchors(), second.getTreeAnchors());
        assertEquals(first.getNatureInstances(), second.getNatureInstances());
    }

    @Test
    void organicNatureInstancesAreGroundedAndReplaceTreeRocks() {
        SceneryPlan plan = SceneryPlan.generate(map, field, SCALE, SEED);
        List<SceneryPlan.NatureInstance> instances = plan.getNatureInstances();

        assertFalse(instances.isEmpty(), "the imported nature models must be placed");
        Set<SceneryPlan.NatureKind> kinds = new HashSet<>();
        for (SceneryPlan.NatureInstance instance : instances) {
            kinds.add(instance.kind());
            assertTrue(Float.isFinite(instance.x()) && Float.isFinite(instance.y())
                    && Float.isFinite(instance.z()),
                "nature transforms must be finite: " + instance);
            assertTrue(instance.scale() > 0.2f && instance.scale() < 2f,
                "model scale must stay in a sane range: " + instance);
            float ground = field.getHeight(instance.x(), instance.z());
            assertTrue(instance.y() >= ground - 0.06f,
                "models must not sink through the heightfield: " + instance);
            assertTrue(instance.y() <= ground + 0.40f,
                "models must not hover above the heightfield: " + instance);
        }

        assertTrue(kinds.contains(SceneryPlan.NatureKind.MAPLE_TREE),
            "sakura hills need the maple canopy model");
        assertTrue(kinds.contains(SceneryPlan.NatureKind.BOULDER_ROUND)
                || kinds.contains(SceneryPlan.NatureKind.BOULDER_TALL),
            "blocky stone outcrops must become boulders");
        assertTrue(kinds.contains(SceneryPlan.NatureKind.BUSH_SMALL)
                || kinds.contains(SceneryPlan.NatureKind.BUSH_LARGE),
            "undergrowth needs organic bushes");
        assertTrue(kinds.contains(SceneryPlan.NatureKind.GRASS_SMALL)
                || kinds.contains(SceneryPlan.NatureKind.GRASS_LARGE),
            "undergrowth needs organic grass tufts");
    }

    @Test
    void noBlockySakuraOrBambooCrownsRemain() {
        SceneryPlan plan = SceneryPlan.generate(map, field, SCALE, SEED);

        assertTrue(plan.getBoxes().stream().noneMatch(
                box -> box.material() == SceneryPlan.MATERIAL_SAKURA_LEAVES),
            "no box may use the sakura-leaf texture anymore; crowns are organic models");
        assertTrue(plan.getBoxes().stream().noneMatch(
                box -> box.material() == SceneryPlan.MATERIAL_GRASS
                    && box.sy() < 0.3f && box.sx() > 0.3f && box.sz() > 0.3f),
            "flat grass/bamboo crown slabs must be replaced by organic leaf masses");

        // Every bamboo grove gets five internodes and node rings per stalk.
        long bambooCylinders = plan.getCylinders().stream()
            .filter(cylinder -> cylinder.sides() == 8)
            .count();
        assertTrue(bambooCylinders >= plan.getBambooAnchors().size() * 3L * 5L,
            "bamboo must be built from segmented round culms");
        assertFalse(plan.getSpheres().isEmpty(),
            "bamboo leaf masses and lantern finials need low-poly organic blobs");
    }


    @Test
    void dirtPathsConnectTheVillagePointsOfInterest() {
        SceneryPlan plan = SceneryPlan.generate(map, field, SCALE, SEED);

        assertFalse(plan.getPathTiles().isEmpty());
        assertEquals(plan.getPathTiles().size(), plan.getPathPlates().size());
        for (AsciiMap.Tile tile : plan.getPathTiles()) {
            assertTrue(map.isWalkable(tile.col(), tile.row()),
                "dirt paths must stay on walkable cells: " + tile);
        }
        assertTrue(plan.getPathTiles().containsAll(map.tilesOf(AsciiMap.SHRINE)),
            "the generated paths must reach the shrine");
    }

    @Test
    void shrineApproachHasToriiGatesAndStoneLanterns() {
        SceneryPlan plan = SceneryPlan.generate(map, field, SCALE, SEED);

        assertFalse(plan.getToriiAnchors().isEmpty());
        assertFalse(plan.getLanternAnchors().isEmpty());

        long vermillionCylinders = plan.getCylinders().stream()
            .filter(cylinder -> cylinder.red() > 0.6f
                && cylinder.green() < 0.45f && cylinder.blue() < 0.3f)
            .count();
        assertTrue(vermillionCylinders >= plan.getToriiAnchors().size() * 4L,
            "every torii gate needs round vermillion pillars and curved lintels");
    }

    @Test
    void templeSitsOnAFlatClearingNearTheShrine() {
        SceneryPlan plan = SceneryPlan.generate(map, field, SCALE, SEED);
        assertEquals(1, plan.getTempleAnchors().size());

        Vector2 temple = plan.getTempleAnchors().get(0);
        AsciiMap.Tile shrine = map.tilesOf(AsciiMap.SHRINE).get(0);
        float shrineX = map.tileCenterX(shrine.col()) * SCALE;
        float shrineZ = -map.tileCenterY(shrine.row()) * SCALE;
        float distance = (float) Math.hypot(temple.x - shrineX, temple.y - shrineZ);
        assertTrue(distance > 1f && distance < 6f,
            "the temple must sit behind the shrine entrance, was " + distance);

        float reference = field.getHeight(temple.x, temple.y);
        for (int i = 0; i < 12; i++) {
            double angle = i * Math.PI / 6.0;
            float x = temple.x + (float) Math.cos(angle) * 3.5f;
            float z = temple.y + (float) Math.sin(angle) * 3.5f;
            assertEquals(reference, field.getHeight(x, z), 0.35f,
                "the temple platform needs a flat clearing");
        }
    }

    @Test
    void templeEntranceAnchorSitsInFrontOfTheTemple() {
        SceneryPlan plan = SceneryPlan.generate(map, field, SCALE, SEED);
        assertEquals(plan.getTempleAnchors().size(), plan.getTempleEntranceAnchors().size());

        Vector2 temple = plan.getTempleAnchors().get(0);
        Vector2 entrance = plan.getTempleEntranceAnchors().get(0);
        float distance = (float) Math.hypot(
            entrance.x - temple.x, entrance.y - temple.y);
        assertEquals(3.10f, distance, 0.02f,
            "the entrance anchor belongs on the front steps");

        AsciiMap.Tile shrine = map.tilesOf(AsciiMap.SHRINE).get(0);
        float shrineX = map.tileCenterX(shrine.col()) * SCALE;
        float shrineZ = -map.tileCenterY(shrine.row()) * SCALE;
        float templeToShrine = (float) Math.hypot(
            shrineX - temple.x, shrineZ - temple.y);
        float entranceToShrine = (float) Math.hypot(
            shrineX - entrance.x, shrineZ - entrance.y);
        assertTrue(entranceToShrine < templeToShrine,
            "the entrance must face the shrine approach");
    }

    @Test
    void bambooAndSakuraScatterOverTheHillsButNotTheVillageFloor() {
        SceneryPlan plan = SceneryPlan.generate(map, field, SCALE, SEED);

        int vegetation = plan.getTreeAnchors().size() + plan.getBambooAnchors().size();
        assertTrue(vegetation > 0, "density noise must grow at least some vegetation");
        assertTrue(plan.getTreeAnchors().size() <= 96);
        assertTrue(plan.getBambooAnchors().size() <= 64);

        for (Vector2 anchor : plan.getTreeAnchors()) {
            assertFalse(isWalkableFloor(anchor.x, anchor.y),
                "sakura must not block the walkable village floor");
        }
        for (Vector2 anchor : plan.getBambooAnchors()) {
            assertFalse(isWalkableFloor(anchor.x, anchor.y),
                "bamboo must not block the walkable village floor");
        }
    }

    @Test
    void everySceneryPrimitiveStaysInsideTheHeightmapPatch() {
        SceneryPlan plan = SceneryPlan.generate(map, field, SCALE, SEED);
        assertFalse(plan.getBoxes().isEmpty());
        assertFalse(plan.getCylinders().isEmpty());
        assertFalse(plan.getSpheres().isEmpty());

        float minX = field.getOriginX() - 3f;
        float maxX = field.getOriginX() + field.getSize() + 3f;
        float minZ = field.getOriginZ() - 3f;
        float maxZ = field.getOriginZ() + field.getSize() + 3f;
        for (SceneryPlan.Box box : plan.getBoxes()) {
            assertTrue(box.x() >= minX && box.x() <= maxX, "box X outside the patch");
            assertTrue(box.z() >= minZ && box.z() <= maxZ, "box Z outside the patch");
            assertTrue(Float.isFinite(box.y()));
            assertTrue(box.sx() > 0f && box.sy() > 0f && box.sz() > 0f);
        }
        for (SceneryPlan.Cylinder cylinder : plan.getCylinders()) {
            assertTrue(cylinder.x1() >= minX && cylinder.x1() <= maxX,
                "cylinder X outside the patch");
            assertTrue(cylinder.z1() >= minZ && cylinder.z1() <= maxZ,
                "cylinder Z outside the patch");
            assertTrue(cylinder.x2() >= minX && cylinder.x2() <= maxX,
                "cylinder end X outside the patch");
            assertTrue(cylinder.z2() >= minZ && cylinder.z2() <= maxZ,
                "cylinder end Z outside the patch");
            assertTrue(cylinder.radius1() > 0f && cylinder.radius2() > 0f);
            assertTrue(cylinder.sides() >= 4);
        }
        for (SceneryPlan.Sphere sphere : plan.getSpheres()) {
            assertTrue(sphere.x() >= minX && sphere.x() <= maxX,
                "sphere X outside the patch");
            assertTrue(sphere.z() >= minZ && sphere.z() <= maxZ,
                "sphere Z outside the patch");
            assertTrue(sphere.radiusX() > 0f && sphere.radiusY() > 0f
                && sphere.radiusZ() > 0f);
        }
    }

    @Test
    void levelClearingsOnlyFlattenTheStructureFootprints() {
        Heightfield natural = Heightfield.generate(1234L,
            field.getOriginX(), field.getOriginZ(), field.getSize(), 96, 3.2f);

        // The spawn clearing is level, the far corner is still natural terrain.
        AsciiMap.Tile spawn = map.getPlayerSpawn();
        float spawnX = map.tileCenterX(spawn.col()) * SCALE;
        float spawnZ = -map.tileCenterY(spawn.row()) * SCALE;
        float reference = field.getHeight(spawnX, spawnZ);
        assertEquals(reference, field.getHeight(spawnX + 1.5f, spawnZ), 0.2f);

        float x = field.getOriginX() + 5f;
        float z = field.getOriginZ() + 5f;
        assertEquals(natural.getHeight(x, z), field.getHeight(x, z), 0.0001f);
    }

    @Test
    void lanternsAndTempleTorchesEmitWarmPointLights() {
        SceneryPlan plan = SceneryPlan.generate(map, field, SCALE, SEED);
        List<SceneryPlan.WarmLight> lights = plan.getWarmLights();

        assertEquals(plan.getLanternAnchors().size() + 2, lights.size(),
            "every stone lantern plus the two temple torches must emit light");
        for (SceneryPlan.WarmLight light : lights) {
            assertTrue(light.radius() > 1f, "point lights need a usable radius");
            assertTrue(light.red() > light.green() && light.green() > light.blue(),
                "lantern ink must be golden/orange");
            assertTrue(Float.isFinite(light.y()));
        }

        for (int i = 0; i < plan.getLanternAnchors().size(); i++) {
            Vector2 anchor = plan.getLanternAnchors().get(i);
            SceneryPlan.WarmLight light = lights.get(i);
            assertEquals(anchor.x, light.x(), 0.01f);
            assertEquals(anchor.y, light.z(), 0.01f);
        }

        Vector2 temple = plan.getTempleAnchors().get(0);
        for (int i = lights.size() - 2; i < lights.size(); i++) {
            float distance = (float) Math.hypot(
                lights.get(i).x() - temple.x, lights.get(i).z() - temple.y);
            assertTrue(distance < 4.2f, "temple torches must flank the entrance");
        }
    }

    @Test
    void everyWallCellHasAVisibleStructure() {
        SceneryPlan plan = SceneryPlan.generate(map, field, SCALE, SEED);
        Set<Long> visible = new HashSet<>();
        for (Vector2 anchor : plan.getFenceAnchors()) {
            visible.add(cellKey(anchor.x, anchor.y));
        }
        for (Vector2 anchor : plan.getStoneWallAnchors()) {
            visible.add(cellKey(anchor.x, anchor.y));
        }

        int wallCells = 0;
        for (int row = 0; row < map.getHeight(); row++) {
            for (int col = 0; col < map.getWidth(); col++) {
                if (map.glyphAt(col, row) != AsciiMap.WALL) {
                    continue;
                }
                wallCells++;
                long key = ((long) row << 32) ^ (col & 0xFFFFFFFFL);
                assertTrue(visible.contains(key),
                    "collision cell " + col + "," + row + " needs a visible fence/stone");
            }
        }
        assertEquals(wallCells, visible.size(),
            "fence and stone cells must exactly cover the collision cells");

        for (Vector2 fence : plan.getFenceAnchors()) {
            long key = cellKey(fence.x, fence.y);
            int col = (int) (key & 0xFFFFFFFFL);
            int row = (int) (key >>> 32);
            assertTrue(row == 0 || row == map.getHeight() - 1
                    || col == 0 || col == map.getWidth() - 1,
                "fences belong on the map boundary");
        }
        for (Vector2 stone : plan.getStoneWallAnchors()) {
            long key = cellKey(stone.x, stone.y);
            int col = (int) (key & 0xFFFFFFFFL);
            int row = (int) (key >>> 32);
            assertFalse(row == 0 || row == map.getHeight() - 1
                    || col == 0 || col == map.getWidth() - 1,
                "stone outcrops belong to interior obstacle blocks");
        }
    }

    @Test
    void toriiLanternsAndTempleEmitCollisionSolids() {
        SceneryPlan plan = SceneryPlan.generate(map, field, SCALE, SEED);
        List<SceneryPlan.Solid> solids = plan.getSolids();

        // Two pillars per torii, one base per lantern and four temple solids
        // (hall, offering box, two torches).
        assertEquals(plan.getToriiAnchors().size() * 2
                + plan.getLanternAnchors().size() + 4,
            solids.size(), "every physical scenery piece needs a solid footprint");

        for (SceneryPlan.Solid solid : solids) {
            assertTrue(solid.halfWidth() > 0f && solid.halfDepth() > 0f);
            assertTrue(Float.isFinite(solid.x()) && Float.isFinite(solid.z()));
            assertTrue(Float.isFinite(solid.topY()) && solid.topY() > 0f,
                "solid tops must expose a finite camera-boom height");
        }

        Vector2 temple = plan.getTempleAnchors().get(0);
        boolean hallNearTemple = false;
        for (SceneryPlan.Solid solid : solids) {
            if (solid.halfWidth() > 2f && solid.halfDepth() > 1f
                && Math.hypot(solid.x() - temple.x, solid.z() - temple.y) < 1f) {
                hallNearTemple = true;
            }
        }
        assertTrue(hallNearTemple, "the temple hall needs a large solid footprint");
    }

    @Test
    void shrinePlazaAndDirtPathsStayOpen() {
        SceneryPlan plan = SceneryPlan.generate(map, field, SCALE, SEED);
        AsciiMap.Tile shrine = map.tilesOf(AsciiMap.SHRINE).get(0);
        float shrineX = map.tileCenterX(shrine.col()) * SCALE;
        float shrineZ = -map.tileCenterY(shrine.row()) * SCALE;

        for (Vector2 lantern : plan.getLanternAnchors()) {
            float distance = (float) Math.hypot(lantern.x - shrineX, lantern.y - shrineZ);
            assertTrue(distance >= 4.49f,
                "lanterns must keep the shrine plaza clear, was " + distance);
        }

        for (AsciiMap.Tile pathTile : plan.getPathTiles()) {
            float pathX = map.tileCenterX(pathTile.col()) * SCALE;
            float pathZ = -map.tileCenterY(pathTile.row()) * SCALE;
            for (SceneryPlan.Solid solid : plan.getSolids()) {
                float distance = (float) Math.hypot(solid.x() - pathX, solid.z() - pathZ);
                float footprint = Math.max(solid.halfWidth(), solid.halfDepth()) + 0.5f;
                assertTrue(distance > footprint,
                    "dirt path tiles must stay fully clear of solid scenery: solid at "
                        + solid.x() + "," + solid.z() + " near tile " + pathTile);
            }
        }
    }

    @Test
    void vegetationAvoidsSteepCliffSlopes() {
        SceneryPlan plan = SceneryPlan.generate(map, field, SCALE, SEED);

        assertFalse(plan.getTreeAnchors().isEmpty(),
            "the test village must grow at least some sakura trees");
        for (Vector2 tree : plan.getTreeAnchors()) {
            float slope = SceneryPlan.slopeAt(field, tree.x, tree.y);
            assertTrue(slope <= SceneryPlan.MAX_PLANT_SLOPE + 0.001f,
                "sakura must not root on a cliff wall, slope=" + slope
                    + " at " + tree.x + "," + tree.y);
        }
        for (Vector2 bamboo : plan.getBambooAnchors()) {
            float slope = SceneryPlan.slopeAt(field, bamboo.x, bamboo.y);
            assertTrue(slope <= SceneryPlan.MAX_PLANT_SLOPE + 0.001f,
                "bamboo must not root on a cliff wall, slope=" + slope);
        }
    }

    /** Map cell key of a scenery anchor given in world units. */
    private long cellKey(float worldX, float worldZ) {
        float tileUnits = map.getTileSize() * SCALE;
        int col = (int) (worldX / tileUnits);
        int row = map.getHeight() - 1 - (int) (-worldZ / tileUnits);
        return ((long) row << 32) ^ (col & 0xFFFFFFFFL);
    }

    private boolean isWalkableFloor(float worldX, float worldZ) {
        float tileUnits = map.getTileSize() * SCALE;
        float mapWidthUnits = map.getWorldWidth() * SCALE;
        float mapHeightUnits = map.getWorldHeight() * SCALE;
        if (worldX < 0f || worldX >= mapWidthUnits
            || worldZ > 0f || worldZ <= -mapHeightUnits) {
            return false;
        }
        int col = (int) (worldX / tileUnits);
        int row = map.getHeight() - 1 - (int) (-worldZ / tileUnits);
        return map.isWalkable(col, row);
    }
}
