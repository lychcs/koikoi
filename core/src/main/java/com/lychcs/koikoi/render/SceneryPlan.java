package com.lychcs.koikoi.render;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.lychcs.koikoi.world.AsciiMap;
import com.lychcs.koikoi.world.WallLayout;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Deterministic placement plan of the procedural Japanese scenery.
 *
 * <p>Pure data and math (no GL): it grows dirt paths between the player spawn
 * and the tavern/shop/shrine, dresses them with torii gates and stone lanterns,
 * builds a modular temple on the shrine clearing and scatters bamboo groves and
 * sakura trees over the heightfield with density noise. {@link JapaneseScenery}
 * turns the block-built structures into one static 3D mesh, while
 * {@link NatureModelRenderer} instances the imported organic tree, rock, bush
 * and grass models from this plan.</p>
 *
 * <p>Everything is seeded, so a location always produces the same village.</p>
 *
 * <p>Every solid {@code #} collision cell also becomes visible geometry - woven
 * Takefensu bamboo fences on the map boundary and mossy stone outcrops for the
 * interior obstacle blocks - and torii pillars, lanterns and the temple expose
 * {@link Solid} footprints consumed by the Box2D loading. Paths and the shrine
 * plaza are kept clear for WASD/S+Q kiting.</p>
 */
public final class SceneryPlan {

    /** Material ids shared with {@code ascii_world.frag}. */
    public static final int MATERIAL_GRASS = 0;
    public static final int MATERIAL_CLIFF_STONE = 1;
    public static final int MATERIAL_TORII_WOOD = 2;
    public static final int MATERIAL_ROOF_TILES = 3;
    public static final int MATERIAL_STONE_PATH = 4;
    public static final int MATERIAL_SAKURA_LEAVES = 5;
    public static final int MATERIAL_NONE = 255;

    /** One rectangular beam/box primitive in libGDX world units. */
    public record Box(float x, float y, float z, float sx, float sy, float sz, float yaw,
                      float red, float green, float blue, int material) {
    }

    /**
     * One tapered cylinder/frustum between two world-space points. Used for
     * bamboo culms, torii pillars, curved lintels, lantern shafts and lattice
     * bars. {@code sides} controls the silhouette (8-10 for round, 4-6 for
     * small structural details).
     */
    public record Cylinder(float x1, float y1, float z1,
                           float x2, float y2, float z2,
                           float radius1, float radius2, int sides, boolean caps,
                           float red, float green, float blue, int material) {
    }

    /** One low-poly ellipsoid, used for bamboo leaf masses and lantern finials. */
    public record Sphere(float x, float y, float z,
                         float radiusX, float radiusY, float radiusZ,
                         int slices, int stacks,
                         float red, float green, float blue, int material) {
    }

    /** One terrain-conforming dirt plate of a generated path. */
    public record PathPlate(float x, float z, float size,
                            float red, float green, float blue) {
    }

    /** Organic model families rendered by {@link NatureModelRenderer}. */
    public enum NatureKind {
        MAPLE_TREE,
        PINE_TREE_TALL,
        PINE_TREE_SMALL,
        BOULDER_ROUND,
        BOULDER_TALL,
        BUSH_SMALL,
        BUSH_LARGE,
        GRASS_SMALL,
        GRASS_LARGE,
        FLOWER_CLUMP
    }

    /** One imported model instance on the heightfield. */
    public record NatureInstance(NatureKind kind,
                                 float x, float y, float z,
                                 float yaw, float scale) {
    }

    /** Warm point light emitted by a Tōrō lantern or a temple torch. */
    public record WarmLight(float x, float y, float z, float radius,
                            float red, float green, float blue) {
    }

    /**
     * Physical footprint of a scenery structure in world units.
     *
     * <p>{@code x}/{@code z} is the centre, {@code halfWidth}/{@code halfDepth}
     * the unrotated half extents and {@code yaw} the rotation around Y. The
     * overworld converts these into rotated Box2D bodies so every visible
     * torii pillar, lantern, temple wall and offering box is solid.</p>
     *
     * <p>{@code topY} is the absolute libGDX Y of the highest point of the
     * structure. The third-person camera uses it to clamp its boom against tall
     * scenery (temple walls, torii pillars) while freely flying over low
     * footprints such as lantern bases.</p>
     */
    public record Solid(float x, float z, float halfWidth, float halfDepth, float yaw,
                        float topY) {
    }

    // Frugal Japanese palette of the procedural structures: warm wood-browns,
    // vermillion torii, slate stone and parchment-gold lantern light.
    private static final float[] TORII_RED = {0.64f, 0.25f, 0.15f};
    private static final float[] TORII_CAP = {0.11f, 0.09f, 0.07f};
    private static final float[] STONE = {0.40f, 0.44f, 0.46f};
    private static final float[] STONE_LIGHT = {0.50f, 0.55f, 0.56f};
    private static final float[] STONE_DARK = {0.21f, 0.25f, 0.27f};
    private static final float[] LANTERN_GLOW = {1.00f, 0.82f, 0.46f};
    private static final float[] WOOD = {0.40f, 0.27f, 0.16f};
    private static final float[] WOOD_DARK = {0.24f, 0.16f, 0.10f};
    private static final float[] ROOF_TILE = {0.20f, 0.25f, 0.28f};
    private static final float[] ROOF_TILE_DARK = {0.12f, 0.16f, 0.18f};
    private static final float[] BAMBOO = {0.25f, 0.40f, 0.22f};
    private static final float[] BAMBOO_LEAF = {0.28f, 0.46f, 0.24f};
    private static final float[] BAMBOO_NODE = {0.35f, 0.46f, 0.23f};
    private static final float[] SAKURA_TRUNK = {0.28f, 0.19f, 0.14f};
    private static final float[] SAKURA_BLOSSOM = {0.58f, 0.36f, 0.42f};
    private static final float[] SAKURA_BLOSSOM_LIGHT = {0.70f, 0.46f, 0.52f};
    private static final float[] DIRT = {0.33f, 0.25f, 0.17f};
    private static final float[] DIRT_DARK = {0.25f, 0.20f, 0.14f};
    private static final float[] FENCE_BAMBOO = {0.48f, 0.43f, 0.28f};
    private static final float[] FENCE_BINDING = {0.32f, 0.28f, 0.19f};
    private static final float[] STONE_MOSS = {0.30f, 0.36f, 0.31f};
    private static final float[] STONE_MOSS_DARK = {0.21f, 0.26f, 0.23f};
    private static final float[] MOSS_CAP = {0.20f, 0.34f, 0.22f};

    /** Half distance between the two torii pillars in world units. */
    private static final float TORII_SPAN = 1.25f;

    /**
     * Path clearance in world units: a lantern or torii pillar may not sit this
     * close to the centre of a dirt-path tile, keeping WASD kiting lanes open.
     * 0.85 covers the largest small-solid radius (lantern base 0.24) plus the
     * 0.5 half-width of a path tile with a safety margin.
     */
    private static final float PATH_CLEARANCE = 0.85f;
    /** Maximum number of scattered vegetation assemblies (mesh budget). */
    private static final int MAX_TREES = 96;
    private static final int MAX_BAMBOO = 64;
    /** Mesh budget of the large four-season world (chunk-culled at draw time). */
    private static final int LARGE_MAX_TREES = 420;
    private static final int LARGE_MAX_BAMBOO = 180;
    /** Coarse scatter grid of the seasonal realms in world units. */
    private static final float REGION_SCATTER_STEP = 5.0f;
    /** Steepest terrain gradient (rise/run) allowed under a tree or bamboo. */
    static final float MAX_PLANT_SLOPE = 0.55f;
    /** Central-difference radius used to measure that gradient. */
    private static final float SLOPE_SAMPLE_RADIUS = 0.80f;
    /** Scatter grid spacing in world units. */
    private static final float SCATTER_STEP = 1.7f;

    private final AsciiMap map;
    private final Heightfield field;
    private final float worldScale;
    private final long seed;
    /** Cardinal season model of the large hub world; never {@code null}. */
    private final WorldSeasonMap seasonWorld;
    /** Tree / bamboo assembly budget of this world. */
    private final int maxTrees;
    private final int maxBamboo;

    private final List<Box> boxes = new ArrayList<>();
    private final List<Cylinder> cylinders = new ArrayList<>();
    private final List<Sphere> spheres = new ArrayList<>();
    private final List<PathPlate> pathPlates = new ArrayList<>();
    private final List<AsciiMap.Tile> pathTiles = new ArrayList<>();
    private final List<Vector2> toriiAnchors = new ArrayList<>();
    private final List<Vector2> lanternAnchors = new ArrayList<>();
    private final List<Vector2> templeAnchors = new ArrayList<>();
    /** Front step centre of each temple, in world units. */
    private final List<Vector2> templeEntranceAnchors = new ArrayList<>();
    private final List<Vector2> treeAnchors = new ArrayList<>();
    private final List<Vector2> bambooAnchors = new ArrayList<>();
    private final List<NatureInstance> natureInstances = new ArrayList<>();
    private final List<WarmLight> warmLights = new ArrayList<>();
    private final List<Solid> solids = new ArrayList<>();
    private final List<Vector2> fenceAnchors = new ArrayList<>();
    private final List<Vector2> stoneWallAnchors = new ArrayList<>();
    private final List<AsciiMap.Tile> shrineTiles = new ArrayList<>();
    /** Friendly villager posts around the sanctuary plaza. */
    private final List<Vector2> villagerAnchors = new ArrayList<>();

    private SceneryPlan(AsciiMap map, Heightfield field, float worldScale, long seed,
                        WorldSeasonMap seasonWorld) {
        this.map = map;
        this.field = field;
        this.worldScale = worldScale;
        this.seed = seed;
        this.seasonWorld = seasonWorld;
        boolean large = seasonWorld != null && seasonWorld.isFourSeasonWorld();
        this.maxTrees = large ? LARGE_MAX_TREES : MAX_TREES;
        this.maxBamboo = large ? LARGE_MAX_BAMBOO : MAX_BAMBOO;
    }

    /** Builds the complete deterministic scenery plan of one location. */
    public static SceneryPlan generate(AsciiMap map, Heightfield field,
                                       float worldScale, long seed) {
        return generate(map, field, worldScale, seed,
            WorldSeasonMap.forMap(map, worldScale));
    }

    /** Builds the plan against an explicit four-season hub model. */
    public static SceneryPlan generate(AsciiMap map, Heightfield field,
                                       float worldScale, long seed,
                                       WorldSeasonMap seasonWorld) {
        if (map == null) {
            throw new IllegalArgumentException("map must not be null");
        }
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        if (!(worldScale > 0f)) {
            throw new IllegalArgumentException("worldScale must be > 0 but was " + worldScale);
        }
        WorldSeasonMap seasons = seasonWorld == null
            ? WorldSeasonMap.forMap(map, worldScale) : seasonWorld;
        SceneryPlan plan = new SceneryPlan(map, field, worldScale, seed, seasons);
        plan.shrineTiles.addAll(map.tilesOf(AsciiMap.SHRINE));
        plan.buildWalls();
        plan.buildPathsAndGates();
        plan.buildSeasonScenery();
        plan.buildShrines();
        plan.scatterVegetation();
        return plan;
    }

    // ------------------------------------------------------------------
    // Visible collision: bamboo fences (boundary) and mossy stone walls
    // ------------------------------------------------------------------

    /**
     * Replaces every invisible {@code #} collision cell with visible geometry:
     * Takefensu woven bamboo fences along the map boundary and low mossy stone
     * outcrops for the interior obstacle blocks.
     */
    private void buildWalls() {
        if (seasonWorld != null && seasonWorld.isFourSeasonWorld()) {
            buildLargeWorldWalls();
            return;
        }
        for (int row = 0; row < map.getHeight(); row++) {
            for (int col = 0; col < map.getWidth(); col++) {
                if (map.glyphAt(col, row) != AsciiMap.WALL) {
                    continue;
                }
                boolean boundary = row == 0 || row == map.getHeight() - 1
                    || col == 0 || col == map.getWidth() - 1;
                if (boundary) {
                    addFenceSegment(col, row);
                } else {
                    addStoneOutcrop(col, row);
                }
            }
        }
    }

    /**
     * Large-world wall dressing: 4-cell woven fence segments around the rim and
     * long cliff runs for the mountain ring / rock outcrops. One boulder per
     * few cells keeps the {@code ModelInstance} budget bounded while the mesh
     * chunks still cull whole stretches of wall.
     */
    private void buildLargeWorldWalls() {
        addBoundaryFenceRing();
        for (WallLayout.Run run : WallLayout.scan(map)) {
            if (run.boundary()) {
                continue;
            }
            addCliffRun(run);
        }
    }

    private void addBoundaryFenceRing() {
        int width = map.getWidth();
        int height = map.getHeight();
        addFenceLine(1, 0, 1, 0, width - 2, true);
        addFenceLine(1, height - 1, 1, 0, width - 2, true);
        addFenceLine(0, 1, 0, 1, height - 2, false);
        addFenceLine(width - 1, 1, 0, 1, height - 2, false);
    }

    /** Continuous woven fence in 4-cell segments following the terrain. */
    private void addFenceLine(int startCol, int startRow, int stepCol, int stepRow,
                              int count, boolean eastWest) {
        float yaw = eastWest ? 0f : (float) (Math.PI * 0.5);
        for (int i = 0; i < count; i += 4) {
            int length = Math.min(4, count - i);
            int col = startCol + stepCol * i;
            int row = startRow + stepRow * i;
            float halfSpan = (length - 1) * 0.5f;
            float x = centerX(new AsciiMap.Tile(col, row)) + stepCol * halfSpan;
            float z = centerZ(new AsciiMap.Tile(col, row)) + stepRow * halfSpan;
            float ground = field.getHeight(x, z);

            addBox(x, ground + 0.46f, z, length, 0.075f, 0.075f, yaw, FENCE_BAMBOO);
            addBox(x, ground + 0.82f, z, length, 0.070f, 0.070f, yaw, FENCE_BAMBOO);
            addBox(x, ground + 1.10f, z, length * 0.96f, 0.060f, 0.060f,
                yaw, FENCE_BINDING);

            float postOffset = length * 0.5f - 0.12f;
            float postX = eastWest ? postOffset : 0f;
            float postZ = eastWest ? 0f : postOffset;
            addBox(x - postX, ground + 0.60f, z - postZ, 0.09f, 1.20f, 0.09f,
                yaw, FENCE_BINDING);
            addBox(x + postX, ground + 0.60f, z + postZ, 0.09f, 1.20f, 0.09f,
                yaw, FENCE_BINDING);

            fenceAnchors.add(new Vector2(x, z));
        }
    }

    /** One long mossy cliff run with sparse boulder accents. */
    private void addCliffRun(WallLayout.Run run) {
        float cell = map.getTileSize() * worldScale;
        float baseX = run.x() * worldScale;
        float baseZ = -map.tileBottomY(run.row()) * worldScale - cell * 0.5f;
        for (int start = 0; start < run.length(); start += 4) {
            int length = Math.min(4, run.length() - start);
            float x = baseX + (start + length * 0.5f) * cell;
            float z = baseZ;
            float ground = field.getHeight(x, z);
            addBox(x, ground + 0.82f, z, length * cell, 1.64f, 0.94f,
                0f, STONE_DARK);
            addBox(x, ground + 1.72f, z, length * cell * 0.92f, 0.32f, 0.72f,
                0f, STONE);
            stoneWallAnchors.add(new Vector2(x, z));

            if (length >= 3) {
                addNatureOnGround(hash01(run.col() + start, run.row()) > 0.5f
                        ? NatureKind.BOULDER_TALL : NatureKind.BOULDER_ROUND,
                    x, z + (hash01(run.row(), run.col()) - 0.5f) * 0.5f,
                    hash01(run.col(), run.row() + start) * MathUtils.PI2,
                    0.82f + hash01(start, run.col()) * 0.34f);
            }
        }
    }

    /** One woven bamboo fence cell along the map boundary. */
    private void addFenceSegment(int col, int row) {
        float x = centerX(new AsciiMap.Tile(col, row));
        float z = centerZ(new AsciiMap.Tile(col, row));
        float ground = field.getHeight(x, z);
        // Top/bottom border runs east-west, left/right border runs north-south.
        boolean eastWest = row == 0 || row == map.getHeight() - 1;
        float yaw = eastWest ? 0f : (float) (Math.PI * 0.5);

        // Two round bamboo posts per cell (neighbour cells continue the line).
        addLocalCylinder(x, z, yaw, -0.44f, ground, 0f,
            -0.44f, ground + 1.20f, 0f, 0.055f, 0.050f, 8, true, FENCE_BINDING);
        addLocalCylinder(x, z, yaw, 0.44f, ground, 0f,
            0.44f, ground + 1.20f, 0f, 0.055f, 0.050f, 8, true, FENCE_BINDING);
        // Three horizontal bamboo rails.
        for (int rail = 0; rail < 3; rail++) {
            float railY = ground + 0.32f + rail * 0.32f;
            addLocalCylinder(x, z, yaw, -0.47f, railY, 0f,
                0.47f, railY, 0f, 0.030f, 0.030f, 6, true, FENCE_BAMBOO);
        }
        // Round vertical weave slats with visible node bands.
        for (int slat = -1; slat <= 1; slat++) {
            float localX = slat * 0.30f;
            addLocalCylinder(x, z, yaw, localX, ground + 0.23f, 0f,
                localX, ground + 1.09f, 0f, 0.025f, 0.024f, 6, true, FENCE_BAMBOO);
            addLocalCylinder(x, z, yaw, localX, ground + 0.60f, 0f,
                localX, ground + 0.66f, 0f, 0.034f, 0.034f, 6, true, FENCE_BINDING);
        }

        fenceAnchors.add(new Vector2(x, z));
    }

    /** Mossy stone outcrop for an interior obstacle wall cell. */
    private void addStoneOutcrop(int col, int row) {
        float x = centerX(new AsciiMap.Tile(col, row));
        float z = centerZ(new AsciiMap.Tile(col, row));
        float yaw = hash01(col, row) * MathUtils.PI2;
        boolean tall = hash01(col + 17, row + 3) > 0.54f;

        NatureKind kind = tall ? NatureKind.BOULDER_TALL : NatureKind.BOULDER_ROUND;
        float scale = 0.78f + hash01(col + 5, row + 11) * 0.42f;
        addNatureOnGround(kind, x, z, yaw, scale);

        // A smaller companion boulder breaks the uniform per-cell silhouette.
        if (hash01(col + 29, row + 7) > 0.62f) {
            float angle = hash01(col + 3, row + 19) * MathUtils.PI2;
            float offset = 0.30f + hash01(col + 13, row + 23) * 0.22f;
            addNatureOnGround(NatureKind.BOULDER_ROUND,
                x + MathUtils.cos(angle) * offset,
                z + MathUtils.sin(angle) * offset,
                angle + 1.3f, scale * 0.54f);
        }

        // Moss tufts and small flowers soften the stone wall cells.
        addNatureOnGround(NatureKind.GRASS_SMALL,
            x + 0.38f, z - 0.28f, yaw, 0.90f + scale * 0.20f);
        if (hash01(col + 41, row + 2) > 0.70f) {
            addNatureOnGround(NatureKind.FLOWER_CLUMP,
                x - 0.34f, z + 0.31f, yaw + 0.8f, 0.82f);
        }

        stoneWallAnchors.add(new Vector2(x, z));
    }


    // ------------------------------------------------------------------
    // Dirt paths, torii gates and stone lanterns
    // ------------------------------------------------------------------

    /** All walkable tiles of the generated dirt paths (deduplicated). */
    public static List<AsciiMap.Tile> plannedPathTiles(AsciiMap map) {
        List<AsciiMap.Tile> result = new ArrayList<>();
        if (map == null) {
            return result;
        }
        Set<Long> seen = new HashSet<>();
        AsciiMap.Tile spawn = map.getPlayerSpawn();
        for (AsciiMap.Tile goal : pointsOfInterest(map)) {
            for (AsciiMap.Tile tile : PathPlanner.findPath(map, spawn, goal)) {
                if (seen.add(tileKey(tile))) {
                    result.add(tile);
                }
            }
        }
        return result;
    }

    /** Shrine, tavern and shop cells that the dirt paths should connect to. */
    public static List<AsciiMap.Tile> pointsOfInterest(AsciiMap map) {
        List<AsciiMap.Tile> goals = new ArrayList<>();
        if (map == null) {
            return goals;
        }
        goals.addAll(map.tilesOf(AsciiMap.SHRINE));
        goals.addAll(map.tilesOf(AsciiMap.TAVERN_DOOR));
        goals.addAll(map.tilesOf(AsciiMap.SHOP));
        return goals;
    }

    /**
     * Levels the clearings under the procedural structures and gently smooths
     * the generated dirt paths. Must run before {@link #generate} so the plan
     * samples the final terrain heights.
     */
    public static void levelClearings(Heightfield field, AsciiMap map, float worldScale) {
        if (field == null || map == null || !(worldScale > 0f)) {
            return;
        }
        for (AsciiMap.Tile shrine : map.tilesOf(AsciiMap.SHRINE)) {
            // Wide enough for the whole temple footprint behind the shrine.
            field.flattenCircle(centerX(map, shrine, worldScale),
                centerZ(map, shrine, worldScale), 8.5f, 3.5f);
        }
        for (AsciiMap.Tile tavern : map.tilesOf(AsciiMap.TAVERN_DOOR)) {
            field.flattenCircle(centerX(map, tavern, worldScale),
                centerZ(map, tavern, worldScale), 3.4f, 2.0f);
        }
        for (AsciiMap.Tile shop : map.tilesOf(AsciiMap.SHOP)) {
            field.flattenCircle(centerX(map, shop, worldScale),
                centerZ(map, shop, worldScale), 3.4f, 2.0f);
        }
        AsciiMap.Tile spawn = map.getPlayerSpawn();
        field.flattenCircle(centerX(map, spawn, worldScale),
            centerZ(map, spawn, worldScale), 3.0f, 2.0f);

        // Large four-season world: level the four paved cardinal roads before
        // the plan samples the terrain heights.
        WorldSeasonMap seasons = WorldSeasonMap.forMap(map, worldScale);
        if (seasons.isFourSeasonWorld()) {
            List<AsciiMap.Tile> roads = seasons.getRoadTiles();
            for (int i = 0; i < roads.size(); i += 3) {
                AsciiMap.Tile tile = roads.get(i);
                field.flattenCircle(centerX(map, tile, worldScale),
                    centerZ(map, tile, worldScale), 1.7f, 1.1f);
            }
        }

        // Walked paths become shallow, smooth trails instead of stair steps.
        for (AsciiMap.Tile tile : plannedPathTiles(map)) {
            field.flattenCircle(centerX(map, tile, worldScale),
                centerZ(map, tile, worldScale), 0.55f, 0.9f);
        }
    }

    private void buildPathsAndGates() {
        AsciiMap.Tile spawn = map.getPlayerSpawn();
        Set<Long> plateKeys = new HashSet<>();
        List<List<AsciiMap.Tile>> paths = new ArrayList<>();
        List<AsciiMap.Tile> shrinePath = List.of();

        for (AsciiMap.Tile goal : pointsOfInterest(map)) {
            List<AsciiMap.Tile> path = PathPlanner.findPath(map, spawn, goal);
            if (path.isEmpty()) {
                continue;
            }
            paths.add(path);
            addPathPlates(path, plateKeys);
            if (shrinePath.isEmpty()
                && map.glyphAt(goal.col(), goal.row()) == AsciiMap.SHRINE) {
                shrinePath = path;
            }
        }

        for (List<AsciiMap.Tile> path : paths) {
            addLanterns(path);
        }

        // Torii gates mark the shrine approach: entrance, middle and the gate.
        if (shrinePath.size() >= 3) {
            addToriiGate(shrinePath, 1);
            int middle = shrinePath.size() / 2;
            if (middle > 1 && middle < shrinePath.size() - 2) {
                addToriiGate(shrinePath, middle);
            }
            addToriiGate(shrinePath, shrinePath.size() - 2);
        }
    }

    private void addPathPlates(List<AsciiMap.Tile> path, Set<Long> seen) {
        float tileSize = map.getTileSize() * worldScale * 0.94f;
        for (AsciiMap.Tile tile : path) {
            if (!seen.add(tileKey(tile))) {
                continue;
            }
            float hash = hash01(tile.col(), tile.row());
            float[] base = hash < 0.35f ? DIRT_DARK : DIRT;
            float shade = 0.88f + 0.24f * hash;
            pathPlates.add(new PathPlate(centerX(tile), centerZ(tile), tileSize,
                base[0] * shade, base[1] * shade, base[2] * shade));
            pathTiles.add(tile);
        }
    }

    private void addLanterns(List<AsciiMap.Tile> path) {
        for (int i = 3; i < path.size() - 2; i += 4) {
            AsciiMap.Tile previous = path.get(i - 1);
            AsciiMap.Tile next = path.get(i + 1);
            float dirX = next.col() - previous.col();
            // 2D north (+row decreasing) maps to 3D -z, so the Z step is the
            // plain row delta (see centerZ()).
            float dirZ = next.row() - previous.row();
            float length = (float) Math.sqrt(dirX * dirX + dirZ * dirZ);
            if (length <= 0.0001f) {
                continue;
            }
            dirX /= length;
            dirZ /= length;

            float side = ((i / 4) % 2 == 0) ? 1f : -1f;
            float offset = 1.15f * side;
            float x = centerX(path.get(i)) - dirZ * offset;
            float z = centerZ(path.get(i)) + dirX * offset;
            if (tooClose(lanternAnchors, x, z, 1.6f)
                || isNearShrine(x, z, 4.5f)
                || isNearPathCenter(x, z, PATH_CLEARANCE)) {
                continue;
            }
            addLantern(x, z, (float) Math.atan2(dirX, dirZ));
        }
    }

    private void addToriiGate(List<AsciiMap.Tile> path, int index) {
        int gateIndex = nearestStraightIndex(path, index);
        if (gateIndex < 0) {
            return;
        }
        AsciiMap.Tile previous = path.get(gateIndex - 1);
        AsciiMap.Tile next = path.get(gateIndex + 1);
        float dirX = next.col() - previous.col();
        float dirZ = next.row() - previous.row();
        float length = (float) Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (length <= 0.0001f) {
            return;
        }
        dirX /= length;
        dirZ /= length;

        float x = centerX(path.get(gateIndex));
        float z = centerZ(path.get(gateIndex));
        float yaw = (float) Math.atan2(dirX, dirZ);

        // Both pillars must stay off the walkable dirt path.
        float cos = (float) Math.cos(yaw);
        float sin = (float) Math.sin(yaw);
        if (isNearPathCenter(x - TORII_SPAN * cos, z + TORII_SPAN * sin, PATH_CLEARANCE)
            || isNearPathCenter(x + TORII_SPAN * cos, z - TORII_SPAN * sin, PATH_CLEARANCE)) {
            return;
        }
        if (tooClose(toriiAnchors, x, z, 2.4f)) {
            return;
        }
        addTorii(x, z, yaw);
    }

    /** Nearest straight path tile around {@code index}, or -1 if none. */
    private static int nearestStraightIndex(List<AsciiMap.Tile> path, int index) {
        for (int offset = 0; offset <= 3; offset++) {
            int forward = index + offset;
            if (forward > 0 && forward < path.size() - 1 && isStraight(path, forward)) {
                return forward;
            }
            int backward = index - offset;
            if (offset > 0 && backward > 0 && backward < path.size() - 1
                && isStraight(path, backward)) {
                return backward;
            }
        }
        return -1;
    }

    private static boolean isStraight(List<AsciiMap.Tile> path, int index) {
        AsciiMap.Tile previous = path.get(index - 1);
        AsciiMap.Tile current = path.get(index);
        AsciiMap.Tile next = path.get(index + 1);
        return (previous.col() == current.col() && next.col() == current.col())
            || (previous.row() == current.row() && next.row() == current.row());
    }


    // ------------------------------------------------------------------
    // Shrine / temple
    // ------------------------------------------------------------------

    private void buildShrines() {
        for (AsciiMap.Tile shrine : shrineTiles) {
            float x = centerX(shrine);
            float z = centerZ(shrine);

            // The shrine cell is the approach; continue one step past it along
            // the path and turn the temple front back towards the shrine.
            Vector2 direction = nearestPathDirection(x, z);
            float yaw = (float) Math.atan2(-direction.x, -direction.y);
            addTemple(x + direction.x * 4.0f, z + direction.y * 4.0f, yaw);
        }
    }

    /** Modular post-and-beam shrine with a stacked tiled roof. */
    private void addTemple(float x, float z, float yaw) {
        float ground = field.getHeight(x, z);

        // Stone platform and floor.
        addLocalBox(x, z, yaw, 0f, ground + 0.18f, 0f, 7.4f, 0.36f, 5.6f, STONE);
        addLocalBox(x, z, yaw, 0f, ground + 0.40f, 0f, 6.8f, 0.12f, 5.0f, STONE_LIGHT);

        // Three front steps.
        for (int step = 0; step < 3; step++) {
            float depth = 2.7f + step * 0.42f;
            addLocalBox(x, z, yaw, 0f, ground + 0.30f - step * 0.09f, depth,
                2.6f, 0.18f, 0.42f, STONE_LIGHT);
        }

        // Wooden posts (corners and mid-points).
        float[] postsX = {-2.9f, 0f, 2.9f};
        float[] postsZ = {-2.0f, 2.0f};
        for (float postX : postsX) {
            for (float postZ : postsZ) {
                if (postX == 0f && postZ < 0f) {
                    continue;
                }
                addLocalBox(x, z, yaw, postX, ground + 1.55f, postZ,
                    0.26f, 2.5f, 0.26f, WOOD_DARK);
            }
        }

        // Post-and-beam frame.
        addLocalBox(x, z, yaw, 0f, ground + 2.62f, -2.0f,
            6.6f, 0.24f, 0.30f, WOOD);
        addLocalBox(x, z, yaw, 0f, ground + 2.62f, 2.0f,
            6.6f, 0.24f, 0.30f, WOOD);
        addLocalBox(x, z, yaw, -2.9f, ground + 2.62f, 0f,
            0.30f, 0.24f, 4.2f, WOOD);
        addLocalBox(x, z, yaw, 2.9f, ground + 2.62f, 0f,
            0.30f, 0.24f, 4.2f, WOOD);

        // Back and side walls (the front stays open).
        addLocalBox(x, z, yaw, 0f, ground + 1.45f, -2.05f,
            5.8f, 2.0f, 0.16f, WOOD_DARK);
        addLocalBox(x, z, yaw, -2.95f, ground + 1.45f, 0f,
            0.16f, 2.0f, 3.9f, WOOD_DARK);
        addLocalBox(x, z, yaw, 2.95f, ground + 1.45f, 0f,
            0.16f, 2.0f, 3.9f, WOOD_DARK);

        // Offering box in front of the entrance.
        addLocalBox(x, z, yaw, 0f, ground + 0.78f, 2.45f,
            1.3f, 0.55f, 0.6f, WOOD);
        addLocalSolid(x, z, yaw, 0f, 2.45f, 0.65f, 0.30f, ground + 1.06f);

        // The hall itself is solid: the shrine approach in front stays open,
        // but the player cannot walk through the temple walls.
        addLocalSolid(x, z, yaw, 0f, -0.1f, 2.95f, 2.0f, ground + 4.05f);

        // Two torches flanking the steps: geometry plus golden point lights.
        addTempleTorch(x, z, yaw, -2.55f, 2.35f, ground);
        addTempleTorch(x, z, yaw, 2.55f, 2.35f, ground);

        // Stacked tiled roof with a ridge beam: reads as a tiled hip roof in ASCII.
        addLocalBox(x, z, yaw, 0f, ground + 2.98f, 0f, 8.6f, 0.26f, 6.4f, ROOF_TILE_DARK);
        addLocalBox(x, z, yaw, 0f, ground + 3.24f, 0f, 7.4f, 0.30f, 5.4f, ROOF_TILE);
        addLocalBox(x, z, yaw, 0f, ground + 3.52f, 0f, 6.0f, 0.28f, 4.4f, ROOF_TILE_DARK);
        addLocalBox(x, z, yaw, 0f, ground + 3.78f, 0f, 4.4f, 0.24f, 3.2f, ROOF_TILE);
        addLocalBox(x, z, yaw, 0f, ground + 3.96f, 0f, 6.2f, 0.18f, 0.30f, ROOF_TILE_DARK);

        templeAnchors.add(new Vector2(x, z));
        float cosYaw = MathUtils.cos(yaw);
        float sinYaw = MathUtils.sin(yaw);
        templeEntranceAnchors.add(new Vector2(
            x + sinYaw * 3.10f, z + cosYaw * 3.10f));
    }

    // ------------------------------------------------------------------
    // Torii gate and stone lantern (Toro)
    // ------------------------------------------------------------------

    /** Small post-and-bowl torch with a glowing flame and a warm light. */
    private void addTempleTorch(float centerX, float centerZ, float yaw,
                                float localX, float localZ, float ground) {
        addLocalCylinder(centerX, centerZ, yaw, localX, ground, localZ,
            localX, ground + 1.10f, localZ, 0.10f, 0.085f, 8, true, WOOD_DARK);
        addLocalCylinder(centerX, centerZ, yaw, localX, ground + 1.10f, localZ,
            localX, ground + 1.26f, localZ, 0.18f, 0.15f, 8, true, STONE_DARK);

        float cos = (float) Math.cos(yaw);
        float sin = (float) Math.sin(yaw);
        float worldX = centerX + localX * cos + localZ * sin;
        float worldZ = centerZ - localX * sin + localZ * cos;
        // Low-poly flame jewel instead of a glowing cube.
        addSphere(worldX, ground + 1.37f, worldZ,
            0.11f, 0.16f, 0.11f, 8, 4, LANTERN_GLOW);
        warmLights.add(new WarmLight(worldX, ground + 1.35f, worldZ, 6.5f,
            1.00f, 0.68f, 0.32f));
        addLocalSolid(centerX, centerZ, yaw, localX, localZ, 0.14f, 0.14f,
            ground + 1.42f);
    }

    private void addTorii(float x, float z, float yaw) {
        addTorii(x, z, yaw, 1f);
    }

    /**
     * Vermillion torii gate. {@code scale} enlarges every dimension, which the
     * four-season border gates use to become monumental realm thresholds.
     */
    private void addTorii(float x, float z, float yaw, float scale) {
        float ground = field.getHeight(x, z);
        float span = TORII_SPAN * scale;
        float height = 3.1f * scale;

        // Round stone plinths + round vermillion pillars.
        addLocalCylinder(x, z, yaw, -span, ground, 0f,
            -span, ground + 0.16f, 0f, 0.24f * scale, 0.22f * scale, 10, true, STONE);
        addLocalCylinder(x, z, yaw, span, ground, 0f,
            span, ground + 0.16f, 0f, 0.24f * scale, 0.22f * scale, 10, true, STONE);
        addLocalCylinder(x, z, yaw, -span, ground + 0.12f, 0f,
            -span, ground + height, 0f, 0.15f * scale, 0.135f * scale, 10, true, TORII_RED);
        addLocalCylinder(x, z, yaw, span, ground + 0.12f, 0f,
            span, ground + height, 0f, 0.15f * scale, 0.135f * scale, 10, true, TORII_RED);
        addLocalSolid(x, z, yaw, -span, 0f, 0.22f * scale, 0.22f * scale, ground + height);
        addLocalSolid(x, z, yaw, span, 0f, 0.22f * scale, 0.22f * scale, ground + height);

        // Nuki (round lower tie beam) through both pillars.
        addLocalCylinder(x, z, yaw, -span - 0.45f * scale, ground + 2.28f * scale, 0f,
            span + 0.45f * scale, ground + 2.28f * scale, 0f,
            0.11f * scale, 0.11f * scale, 10, true, TORII_RED);

        // Gakuzuka plaque: an octagonal medallion facing the approach, not a box.
        addLocalCylinder(x, z, yaw, 0f, ground + 2.55f * scale, -0.09f * scale,
            0f, ground + 2.55f * scale, 0.09f * scale,
            0.23f * scale, 0.23f * scale, 8, true, TORII_CAP);

        // Kasagi: two gently upturned curved beams. The upper dark cap gives the
        // classic torii silhouette without a single rectangular face.
        int segments = 12;
        float halfSpan = span + 0.75f * scale;
        for (int i = 0; i < segments; i++) {
            float t0 = -1f + 2f * i / segments;
            float t1 = -1f + 2f * (i + 1) / segments;
            float lift0 = 0.20f * scale * t0 * t0;
            float lift1 = 0.20f * scale * t1 * t1;
            addLocalCylinder(x, z, yaw,
                halfSpan * t0, ground + 2.86f * scale + lift0, 0f,
                halfSpan * t1, ground + 2.86f * scale + lift1, 0f,
                0.14f * scale, 0.14f * scale, 8, true, TORII_RED);
            addLocalCylinder(x, z, yaw,
                halfSpan * t0 * 0.94f, ground + 2.99f * scale + lift0 * 1.10f, 0f,
                halfSpan * t1 * 0.94f, ground + 2.99f * scale + lift1 * 1.10f, 0f,
                0.085f * scale, 0.085f * scale, 8, true, TORII_CAP);
        }

        toriiAnchors.add(new Vector2(x, z));
    }

    private void addLantern(float x, float z, float yaw) {
        float ground = field.getHeight(x, z);

        // Octagonal base tiers and round shaft.
        addCylinder(x, ground, z, x, ground + 0.24f, z,
            0.40f, 0.36f, 8, true, STONE);
        addCylinder(x, ground + 0.24f, z, x, ground + 0.40f, z,
            0.28f, 0.24f, 8, true, STONE_LIGHT);
        addCylinder(x, ground + 0.40f, z, x, ground + 0.95f, z,
            0.13f, 0.115f, 8, true, STONE);
        addCylinder(x, ground + 0.95f, z, x, ground + 1.08f, z,
            0.27f, 0.24f, 8, true, STONE_LIGHT);
        addCylinder(x, ground + 1.08f, z, x, ground + 1.12f, z,
            0.24f, 0.24f, 8, true, STONE_DARK);

        // Glowing core inside an octagonal lattice firebox.
        addCylinder(x, ground + 1.12f, z, x, ground + 1.46f, z,
            0.145f, 0.135f, 8, true, LANTERN_GLOW);
        for (int bar = 0; bar < 8; bar++) {
            double angle = Math.PI * 0.25 * bar + Math.PI * 0.125;
            float bx = x + (float) Math.cos(angle) * 0.225f;
            float bz = z + (float) Math.sin(angle) * 0.225f;
            addCylinder(bx, ground + 1.12f, bz, bx, ground + 1.46f, bz,
                0.025f, 0.025f, 4, true, STONE_LIGHT);
        }
        addCylinder(x, ground + 1.46f, z, x, ground + 1.50f, z,
            0.245f, 0.235f, 8, true, STONE_DARK);

        // Sloped octagonal pyramid roof + hōju finial.
        addCylinder(x, ground + 1.50f, z, x, ground + 1.74f, z,
            0.47f, 0.035f, 8, true, STONE_DARK);
        addSphere(x, ground + 1.82f, z, 0.075f, 0.10f, 0.075f,
            8, 4, STONE_LIGHT);

        // Warm golden point light around the lattice firebox.
        warmLights.add(new WarmLight(x, ground + 1.25f, z, 5.5f,
            1.00f, 0.76f, 0.38f));
        addSolid(x, z, 0.24f, 0.24f, yaw, ground + 1.90f);

        lanternAnchors.add(new Vector2(x, z));
    }


    // ------------------------------------------------------------------
    // Vegetation: bamboo groves and sakura trees
    // ------------------------------------------------------------------

    /**
     * Terrain gradient magnitude (rise/run) at a point, sampled with a central
     * difference. Used to keep tree and bamboo roots off steep cliff faces.
     */
    static float slopeAt(Heightfield field, float x, float z) {
        float step = SLOPE_SAMPLE_RADIUS;
        float dx = (field.getHeight(x + step, z) - field.getHeight(x - step, z))
            / (2f * step);
        float dz = (field.getHeight(x, z + step) - field.getHeight(x, z - step))
            / (2f * step);
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    private void scatterVegetation() {
        SimplexNoise densityNoise = new SimplexNoise(seed ^ 0x5CA3_9E11L);
        Random random = new Random(seed ^ 0xC0FF_EE00L);

        // The large world first grows its forests where the player walks; the
        // legacy ring scatter below still dresses the hills outside the map.
        if (seasonWorld != null && seasonWorld.isFourSeasonWorld()) {
            scatterRegionVegetation(random, densityNoise);
        }

        float startX = field.getOriginX() + 1.5f;
        float startZ = field.getOriginZ() + 1.5f;
        float endX = field.getOriginX() + field.getSize() - 1.5f;
        float endZ = field.getOriginZ() + field.getSize() - 1.5f;

        for (float z = startZ; z <= endZ; z += SCATTER_STEP) {
            for (float x = startX; x <= endX; x += SCATTER_STEP) {
                if (treeAnchors.size() >= maxTrees && bambooAnchors.size() >= maxBamboo) {
                    return;
                }
                float jitterX = x + (random.nextFloat() - 0.5f) * SCATTER_STEP * 0.7f;
                float jitterZ = z + (random.nextFloat() - 0.5f) * SCATTER_STEP * 0.7f;

                // Keep the whole village footprint (floors *and* the new fence /
                // stone collision structures) clear; the mountain hills outside
                // the map carry the bamboo groves and sakura forest.
                if (isInsideMap(jitterX, jitterZ)) {
                    continue;
                }
                // Cliff faces are too steep for a rooted tree: crowns would
                // visibly float off the vertical wall. Keep vegetation on
                // walkable slopes only.
                if (slopeAt(field, jitterX, jitterZ) > MAX_PLANT_SLOPE) {
                    continue;
                }
                if (tooClose(toriiAnchors, jitterX, jitterZ, 2.6f)
                    || tooClose(lanternAnchors, jitterX, jitterZ, 1.6f)
                    || tooClose(templeAnchors, jitterX, jitterZ, 6.4f)
                    || tooClose(treeAnchors, jitterX, jitterZ, 1.2f)
                    || tooClose(bambooAnchors, jitterX, jitterZ, 1.2f)) {
                    continue;
                }

                float density = densityNoise.fbm2(jitterX, jitterZ, 3, 0.055f, 2.1f, 0.55f);
                float normalized = (density + 1f) * 0.5f;
                if (normalized < 0.56f) {
                    continue;
                }

                boolean grove = normalized > 0.66f && random.nextFloat() < 0.55f;
                if (grove && bambooAnchors.size() < maxBamboo) {
                    addBamboo(jitterX, jitterZ, random);
                } else if (treeAnchors.size() < maxTrees) {
                    addSakura(jitterX, jitterZ, random);
                }
            }
        }
    }

    /**
     * Grows the seasonal forests inside the playable map. Spring favours
     * sakura maples, summer dense bamboo and pines, fall momiji and winter
     * frost pines; the shader tints the shared models per realm.
     */
    private void scatterRegionVegetation(Random random, SimplexNoise densityNoise) {
        float startX = 2.5f;
        float endX = map.getWorldWidth() * worldScale - 2.5f;
        float startZ = -map.getWorldHeight() * worldScale + 2.5f;
        float endZ = -2.5f;

        for (float z = startZ; z <= endZ; z += REGION_SCATTER_STEP) {
            for (float x = startX; x <= endX; x += REGION_SCATTER_STEP) {
                if (treeAnchors.size() >= maxTrees && bambooAnchors.size() >= maxBamboo) {
                    return;
                }
                float jitterX = x + (random.nextFloat() - 0.5f) * REGION_SCATTER_STEP * 0.8f;
                float jitterZ = z + (random.nextFloat() - 0.5f) * REGION_SCATTER_STEP * 0.8f;

                if (!isWalkableAt(jitterX, jitterZ)) {
                    continue;
                }
                if (slopeAt(field, jitterX, jitterZ) > MAX_PLANT_SLOPE) {
                    continue;
                }
                if (isNearPathCenter(jitterX, jitterZ, 1.7f)) {
                    continue;
                }
                if (tooClose(toriiAnchors, jitterX, jitterZ, 3.4f)
                    || tooClose(lanternAnchors, jitterX, jitterZ, 2.0f)
                    || tooClose(templeAnchors, jitterX, jitterZ, 8.0f)
                    || tooClose(treeAnchors, jitterX, jitterZ, 2.2f)
                    || tooClose(bambooAnchors, jitterX, jitterZ, 2.2f)
                    || isInsideSolid(jitterX, jitterZ)) {
                    continue;
                }

                // Keep the sanctuary crossroads readable; only sparse greenery
                // is allowed on the plaza itself.
                float hubDistance = Math.max(
                    Math.abs(jitterX - seasonWorld.getHubWorldX()),
                    Math.abs(jitterZ - seasonWorld.getHubWorldZ()));
                if (hubDistance < WorldSeasonMap.HUB_RADIUS_TILES - 4f
                    && random.nextFloat() > 0.12f) {
                    continue;
                }

                float density = densityNoise.fbm2(jitterX, jitterZ, 3, 0.055f, 2.1f, 0.55f);
                float normalized = (density + 1f) * 0.5f;
                if (normalized < 0.48f) {
                    continue;
                }

                WorldSeasonMap.Season region =
                    seasonWorld.regionAtWorld(jitterX, jitterZ);
                boolean summer = region == WorldSeasonMap.Season.SUMMER;
                boolean grove = summer
                    ? normalized > 0.54f && random.nextFloat() < 0.70f
                    : normalized > 0.68f && random.nextFloat() < 0.30f;
                if (grove && bambooAnchors.size() < maxBamboo) {
                    addBamboo(jitterX, jitterZ, random);
                } else if (treeAnchors.size() < maxTrees) {
                    addSakura(jitterX, jitterZ, random);
                }
            }
        }
    }

    /** true when the world position lies inside a scenery solid footprint. */
    private boolean isInsideSolid(float x, float z) {
        for (int i = 0; i < solids.size(); i++) {
            SceneryPlan.Solid solid = solids.get(i);
            float cos = MathUtils.cos(solid.yaw());
            float sin = MathUtils.sin(solid.yaw());
            float dx = x - solid.x();
            float dz = z - solid.z();
            float localX = dx * cos - dz * sin;
            float localZ = dx * sin + dz * cos;
            if (Math.abs(localX) <= solid.halfWidth() + 0.9f
                && Math.abs(localZ) <= solid.halfDepth() + 0.9f) {
                return true;
            }
        }
        return false;
    }

    /** true when the world position lies on a walkable map tile. */
    private boolean isWalkableAt(float worldX, float worldZ) {
        float tileUnits = map.getTileSize() * worldScale;
        float mapWidthUnits = map.getWorldWidth() * worldScale;
        float mapHeightUnits = map.getWorldHeight() * worldScale;
        if (worldX < 0f || worldX >= mapWidthUnits
            || worldZ > 0f || worldZ <= -mapHeightUnits) {
            return false;
        }
        int col = (int) (worldX / tileUnits);
        int row = map.getHeight() - 1 - (int) (-worldZ / tileUnits);
        return map.isWalkable(col, row);
    }

    private void addSakura(float x, float z, Random random) {
        float yaw = random.nextFloat() * MathUtils.PI2;
        WorldSeasonMap.Season region = seasonWorld == null
            ? WorldSeasonMap.Season.SANCTUARY
            : seasonWorld.regionAtWorld(x, z);
        // Spring (sakura) and fall (momiji) favour the maple model; summer and
        // winter grow pine woods. The shader supplies the actual realm colour.
        float mapleChance = switch (region) {
            case SPRING -> 0.88f;
            case SUMMER -> 0.30f;
            case FALL -> 0.92f;
            case WINTER -> 0.12f;
            default -> 0.74f;
        };
        boolean maple = random.nextFloat() < mapleChance;
        if (maple) {
            // Slightly larger maples give the sakura canopy a fuller silhouette.
            float scale = 0.88f + random.nextFloat() * 0.44f;
            addNatureOnGround(NatureKind.MAPLE_TREE, x, z, yaw, scale);
        } else {
            // A scattering of evergreens keeps the sakura hills from reading as
            // a single cloned canopy. Squat pines fill the gaps between talls.
            boolean squat = random.nextFloat() < 0.35f;
            float scale = squat
                ? 0.80f + random.nextFloat() * 0.55f
                : 0.62f + random.nextFloat() * 0.34f;
            addNatureOnGround(squat
                    ? NatureKind.PINE_TREE_SMALL : NatureKind.PINE_TREE_TALL,
                x, z, yaw, scale);
        }

        // Undergrowth: grass tufts, bushes and the occasional flower clump.
        int tufts = 2 + random.nextInt(3);
        for (int i = 0; i < tufts; i++) {
            float angle = random.nextFloat() * MathUtils.PI2;
            float radius = 0.62f + random.nextFloat() * 1.05f;
            NatureKind grass = random.nextBoolean()
                ? NatureKind.GRASS_SMALL : NatureKind.GRASS_LARGE;
            addNatureOnGround(grass,
                x + MathUtils.cos(angle) * radius,
                z + MathUtils.sin(angle) * radius,
                random.nextFloat() * MathUtils.PI2,
                0.72f + random.nextFloat() * 0.68f);
        }
        if (random.nextFloat() < 0.58f) {
            float angle = random.nextFloat() * MathUtils.PI2;
            float radius = 0.72f + random.nextFloat() * 0.85f;
            NatureKind bush = random.nextBoolean()
                ? NatureKind.BUSH_SMALL : NatureKind.BUSH_LARGE;
            addNatureOnGround(bush,
                x + MathUtils.cos(angle) * radius,
                z + MathUtils.sin(angle) * radius,
                random.nextFloat() * MathUtils.PI2,
                0.62f + random.nextFloat() * 0.55f);
        }
        if (random.nextFloat() < 0.30f) {
            addNatureOnGround(NatureKind.FLOWER_CLUMP,
                x + 0.42f, z - 0.35f,
                random.nextFloat() * MathUtils.PI2,
                0.65f + random.nextFloat() * 0.35f);
        }

        treeAnchors.add(new Vector2(x, z));
    }

    private void addBamboo(float x, float z, Random random) {
        float ground = field.getHeight(x, z);
        for (int i = 0; i < 3; i++) {
            float offsetX = (random.nextFloat() - 0.5f) * 0.55f;
            float offsetZ = (random.nextFloat() - 0.5f) * 0.55f;
            float height = 2.3f + random.nextFloat() * 1.5f;
            float baseX = x + offsetX;
            float baseZ = z + offsetZ;

            // Segmented culm: five tapered round internodes with raised rings.
            int segments = 5;
            float topRadius = 0.034f;
            for (int segment = 0; segment < segments; segment++) {
                float t0 = segment / (float) segments;
                float t1 = (segment + 1) / (float) segments;
                float y0 = ground + height * t0;
                float y1 = ground + height * t1;
                float r0 = 0.075f + (topRadius - 0.075f) * t0;
                float r1 = 0.075f + (topRadius - 0.075f) * t1;
                addCylinder(baseX, y0, baseZ, baseX, y1, baseZ,
                    r0, r1, 8, true, BAMBOO);
                if (segment < segments - 1) {
                    addCylinder(baseX, y1 - 0.018f, baseZ,
                        baseX, y1 + 0.018f, baseZ,
                        r1 + 0.018f, r1 + 0.018f, 8, true, BAMBOO_NODE);
                }
            }

            // Organic low-poly leaf masses replace the old pink crown boxes.
            int tufts = 2 + random.nextInt(2);
            for (int tuft = 0; tuft < tufts; tuft++) {
                float angle = random.nextFloat() * MathUtils.PI2;
                float radius = 0.12f + random.nextFloat() * 0.22f;
                addSphere(
                    baseX + MathUtils.cos(angle) * radius,
                    ground + height - 0.22f + random.nextFloat() * 0.30f,
                    baseZ + MathUtils.sin(angle) * radius,
                    0.30f + random.nextFloat() * 0.18f,
                    0.12f + random.nextFloat() * 0.08f,
                    0.30f + random.nextFloat() * 0.18f,
                    7, 3, BAMBOO_LEAF);
            }
        }

        // Organic undergrowth around the grove floor.
        for (int i = 0; i < 2; i++) {
            float angle = random.nextFloat() * MathUtils.PI2;
            float radius = 0.45f + random.nextFloat() * 0.55f;
            addNatureOnGround(random.nextBoolean()
                    ? NatureKind.GRASS_LARGE : NatureKind.GRASS_SMALL,
                x + MathUtils.cos(angle) * radius,
                z + MathUtils.sin(angle) * radius,
                random.nextFloat() * MathUtils.PI2,
                0.70f + random.nextFloat() * 0.55f);
        }
        if (random.nextFloat() < 0.35f) {
            addNatureOnGround(NatureKind.BUSH_SMALL,
                x + 0.38f, z + 0.26f,
                random.nextFloat() * MathUtils.PI2,
                0.55f + random.nextFloat() * 0.30f);
        }
        bambooAnchors.add(new Vector2(x, z));
    }
    // ------------------------------------------------------------------
    // Four-season hub scenery: paved roads, border gates, village houses
    // ------------------------------------------------------------------

    /**
     * Adds the four-season hub dressing: paved cardinal roads, monumental
     * border torii gates with flanking cliffs and the sanctuary village houses.
     * Small/legacy maps keep their original layout untouched.
     */
    private void buildSeasonScenery() {
        if (seasonWorld == null || !seasonWorld.isFourSeasonWorld()) {
            return;
        }
        buildCardinalRoads();
        buildBorderGates();
        buildHubHouses();
        buildWaysideShrines();
    }

    /** Four paved stone roads, plus alternating roadside lanterns. */
    private void buildCardinalRoads() {
        Set<Long> seen = new HashSet<>();
        float plateSize = map.getTileSize() * worldScale * 0.96f;
        for (AsciiMap.Tile tile : seasonWorld.getRoadTiles()) {
            if (!map.isWalkable(tile.col(), tile.row()) || !seen.add(tileKey(tile))) {
                continue;
            }
            float x = centerX(tile);
            float z = centerZ(tile);
            float shade = 0.82f + 0.26f * hash01(tile.col(), tile.row());
            pathPlates.add(new PathPlate(x, z, plateSize,
                STONE_LIGHT[0] * shade, STONE_LIGHT[1] * shade,
                STONE_LIGHT[2] * shade));
            pathTiles.add(tile);
        }

        // Alternating lantern posts keep the long avenues readable in ASCII.
        for (WorldSeasonMap.BorderTorii gate : seasonWorld.getBorderTorii()) {
            for (int distance = 12;
                 distance < WorldSeasonMap.MOUNTAIN_RING_DISTANCE_TILES;
                 distance += 12) {
                float lane = ((distance / 12) % 2 == 0) ? 2.1f : -2.1f;
                float x;
                float z;
                switch (gate.cardinal()) {
                    case EAST -> {
                        x = seasonWorld.getHubWorldX() + distance;
                        z = seasonWorld.getHubWorldZ() + lane;
                    }
                    case WEST -> {
                        x = seasonWorld.getHubWorldX() - distance;
                        z = seasonWorld.getHubWorldZ() + lane;
                    }
                    case SOUTH -> {
                        x = seasonWorld.getHubWorldX() + lane;
                        z = seasonWorld.getHubWorldZ() + distance;
                    }
                    default -> {
                        x = seasonWorld.getHubWorldX() + lane;
                        z = seasonWorld.getHubWorldZ() - distance;
                    }
                }
                if (tooClose(lanternAnchors, x, z, 2.4f)) {
                    continue;
                }
                addLantern(x, z, 0f);
            }
        }
    }

    /** Monumental realm gates flanked by stone cliffs and lanterns. */
    private void buildBorderGates() {
        for (WorldSeasonMap.BorderTorii gate : seasonWorld.getBorderTorii()) {
            float x = gate.x();
            float z = gate.z();
            float yaw = gate.yaw();
            addTorii(x, z, yaw, 2.35f);

            float cos = MathUtils.cos(yaw);
            float sin = MathUtils.sin(yaw);
            float ground = field.getHeight(x, z);
            for (int side = -1; side <= 1; side += 2) {
                float localX = side * 4.7f;
                addLocalBox(x, z, yaw, localX, ground + 1.05f, -0.3f,
                    4.8f, 2.1f, 1.8f, STONE_DARK);
                addLocalBox(x, z, yaw, localX, ground + 2.28f, -0.3f,
                    5.2f, 0.46f, 2.1f, STONE);
                addLocalSolid(x, z, yaw, localX, -0.3f, 2.4f, 0.9f,
                    ground + 2.55f);

                float worldX = x + localX * cos;
                float worldZ = z - localX * sin;
                addNatureOnGround(seasonWorld.regionAtWorld(worldX, worldZ)
                        == WorldSeasonMap.Season.WINTER
                        ? NatureKind.BOULDER_TALL : NatureKind.BOULDER_ROUND,
                    worldX + sin * 0.9f, worldZ + cos * 0.9f,
                    hash01(side + 7, 3) * MathUtils.PI2, 1.05f);
            }

            // One lantern per side of the pass, just before the gate.
            addLantern(x + cos * 3.4f + sin * 2.2f, z - sin * 3.4f + cos * 2.2f, yaw);
            addLantern(x - cos * 3.4f + sin * 2.2f, z + sin * 3.4f + cos * 2.2f, yaw);
        }
    }

    /** Traditional wooden village houses ringing the sanctuary plaza. */
    private void buildHubHouses() {
        int[][] posts = {
            {-14, -13}, {14, -13}, {-14, 13}, {14, 13},
            {0, -17}, {0, 17}, {-17, 0}, {17, 0}
        };
        int villagers = 0;
        for (int[] post : posts) {
            float x = seasonWorld.getHubWorldX() + post[0];
            float z = seasonWorld.getHubWorldZ() + post[1];
            if (!isInsideMap(x, z) || isNearPathCenter(x, z, 2.2f)
                || isNearShrine(x, z, 10f)) {
                continue;
            }
            float yaw = (float) Math.atan2(-post[0], -post[1]);
            addVillageHouse(x, z, yaw);

            if (villagers < 3) {
                float towardX = -post[0];
                float towardZ = -post[1];
                float length = (float) Math.sqrt(towardX * towardX + towardZ * towardZ);
                if (length > 0.001f) {
                    villagerAnchors.add(new Vector2(
                        x + towardX / length * 3.4f,
                        z + towardZ / length * 3.4f));
                    villagers++;
                }
            }
        }
    }

    /** One post-and-beam wooden house with a tiled hip roof and door lantern. */
    private void addVillageHouse(float x, float z, float yaw) {
        float ground = field.getHeight(x, z);

        // Stone foundation and timber body.
        addLocalBox(x, z, yaw, 0f, ground + 0.12f, 0f, 4.4f, 0.24f, 3.6f, STONE);
        addLocalBox(x, z, yaw, 0f, ground + 1.16f, 0f, 3.9f, 2.1f, 3.1f, WOOD);
        addLocalBox(x, z, yaw, 0f, ground + 1.06f, 1.57f, 1.15f, 1.55f, 0.08f, WOOD_DARK);
        addLocalBox(x, z, yaw, 0f, ground + 1.72f, 1.60f, 1.55f, 0.16f, 0.14f, WOOD_DARK);

        // Stacked tiled hip roof + ridge.
        addLocalBox(x, z, yaw, 0f, ground + 2.30f, 0f, 4.9f, 0.22f, 4.0f, ROOF_TILE_DARK);
        addLocalBox(x, z, yaw, 0f, ground + 2.54f, 0f, 3.8f, 0.28f, 3.0f, ROOF_TILE);
        addLocalBox(x, z, yaw, 0f, ground + 2.78f, 0f, 2.5f, 0.22f, 2.0f, ROOF_TILE_DARK);

        // Corner posts.
        for (int sideX = -1; sideX <= 1; sideX += 2) {
            for (int sideZ = -1; sideZ <= 1; sideZ += 2) {
                addLocalCylinder(x, z, yaw,
                    sideX * 1.85f, ground, sideZ * 1.5f,
                    sideX * 1.85f, ground + 2.16f, sideZ * 1.5f,
                    0.085f, 0.075f, 6, true, WOOD_DARK);
            }
        }

        addLocalSolid(x, z, yaw, 0f, 0f, 2.0f, 1.6f, ground + 2.5f);

        // Warm paper lantern beside the door.
        float cos = MathUtils.cos(yaw);
        float sin = MathUtils.sin(yaw);
        float lightX = x + 1.45f * cos + 1.75f * sin;
        float lightZ = z - 1.45f * sin + 1.75f * cos;
        addSphere(lightX, ground + 1.42f, lightZ, 0.14f, 0.20f, 0.14f,
            8, 4, LANTERN_GLOW);
        warmLights.add(new WarmLight(lightX, ground + 1.42f, lightZ, 4.8f,
            1.00f, 0.74f, 0.34f));
    }




    /** Small hillside shrines that give each realm an atmospheric landmark. */
    private void buildWaysideShrines() {
        int[][] spots = {
            {54, 26}, {72, -14},       // Spring hillside shrines
            {22, 58}, {-18, 70},       // Summer grove shrines
            {-52, -22}, {-70, 16},     // Fall momiji shrines
            {-16, -62}, {20, -74}      // Winter frost shrines
        };
        for (int[] spot : spots) {
            float x = seasonWorld.getHubWorldX() + spot[0];
            float z = seasonWorld.getHubWorldZ() + spot[1];
            if (!isInsideMap(x, z) || !isWalkableAt(x, z)
                || isNearPathCenter(x, z, 1.8f)) {
                continue;
            }
            float yaw = (float) Math.atan2(
                seasonWorld.getHubWorldX() - x, seasonWorld.getHubWorldZ() - z);
            addWaysideShrine(x, z, yaw);
        }
    }

    /** Tiny stone-based shrine with a stacked roof and a warm offering light. */
    private void addWaysideShrine(float x, float z, float yaw) {
        float ground = field.getHeight(x, z);
        addLocalBox(x, z, yaw, 0f, ground + 0.12f, 0f, 2.0f, 0.24f, 1.5f, STONE);
        addLocalBox(x, z, yaw, 0f, ground + 0.72f, 0f, 1.3f, 1.0f, 1.0f, WOOD_DARK);
        addLocalBox(x, z, yaw, 0f, ground + 1.34f, 0f, 2.0f, 0.16f, 1.6f, ROOF_TILE_DARK);
        addLocalBox(x, z, yaw, 0f, ground + 1.50f, 0f, 1.5f, 0.14f, 1.1f, ROOF_TILE);
        addLocalBox(x, z, yaw, 0f, ground + 0.58f, 1.0f, 0.8f, 0.30f, 0.4f, WOOD);
        addLocalSolid(x, z, yaw, 0f, 0f, 1.0f, 0.8f, ground + 1.7f);

        float cos = MathUtils.cos(yaw);
        float sin = MathUtils.sin(yaw);
        float lightX = x + 1.1f * cos + 1.1f * sin;
        float lightZ = z - 1.1f * sin + 1.1f * cos;
        addSphere(lightX, ground + 0.85f, lightZ, 0.11f, 0.16f, 0.11f,
            8, 4, LANTERN_GLOW);
        warmLights.add(new WarmLight(lightX, ground + 0.85f, lightZ, 4.2f,
            1.00f, 0.72f, 0.32f));

        templeAnchors.add(new Vector2(x, z));
    }

    // ------------------------------------------------------------------
    // Helpers and accessors
    // ------------------------------------------------------------------

    private void addBox(float x, float y, float z, float sx, float sy, float sz,
                        float yaw, float[] color) {
        boxes.add(new Box(x, y, z, sx, sy, sz, yaw,
            color[0], color[1], color[2], materialFor(color)));
    }

    /** Adds one tapered cylinder between two world-space points. */
    private void addCylinder(float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float radius1, float radius2, int sides, boolean caps,
                             float[] color) {
        cylinders.add(new Cylinder(x1, y1, z1, x2, y2, z2,
            radius1, radius2, sides, caps,
            color[0], color[1], color[2], materialFor(color)));
    }

    /** Adds a cylinder given in a structure's local frame. */
    private void addLocalCylinder(float centerX, float centerZ, float yaw,
                                  float localX1, float y1, float localZ1,
                                  float localX2, float y2, float localZ2,
                                  float radius1, float radius2, int sides, boolean caps,
                                  float[] color) {
        float cos = (float) Math.cos(yaw);
        float sin = (float) Math.sin(yaw);
        addCylinder(
            centerX + localX1 * cos + localZ1 * sin, y1,
            centerZ - localX1 * sin + localZ1 * cos,
            centerX + localX2 * cos + localZ2 * sin, y2,
            centerZ - localX2 * sin + localZ2 * cos,
            radius1, radius2, sides, caps, color);
    }

    /** Adds one low-poly ellipsoid. */
    private void addSphere(float x, float y, float z,
                           float radiusX, float radiusY, float radiusZ,
                           int slices, int stacks, float[] color) {
        spheres.add(new Sphere(x, y, z, radiusX, radiusY, radiusZ,
            slices, stacks, color[0], color[1], color[2], materialFor(color)));
    }

    /**
     * Maps the static scenery palette onto the six world textures. Colour
     * identity is intentional: every call site passes one of the palette
     * arrays above, so the mapping cannot drift with per-vertex shading.
     */
    private static int materialFor(float[] color) {
        if (color == LANTERN_GLOW) {
            return MATERIAL_NONE;
        }
        if (color == TORII_RED || color == TORII_CAP || color == WOOD || color == WOOD_DARK
            || color == SAKURA_TRUNK || color == FENCE_BINDING || color == FENCE_BAMBOO
            || color == BAMBOO) {
            return MATERIAL_TORII_WOOD;
        }
        if (color == ROOF_TILE || color == ROOF_TILE_DARK) {
            return MATERIAL_ROOF_TILES;
        }
        if (color == SAKURA_BLOSSOM || color == SAKURA_BLOSSOM_LIGHT) {
            return MATERIAL_SAKURA_LEAVES;
        }
        if (color == BAMBOO_LEAF) {
            // Bamboo foliage is an organic green mass, never a pink sakura box.
            return MATERIAL_GRASS;
        }
        if (color == DIRT || color == DIRT_DARK) {
            return MATERIAL_STONE_PATH;
        }
        return MATERIAL_CLIFF_STONE;
    }

    /** Adds a box given in the structure's local frame (local Z = facing direction). */
    private void addLocalBox(float centerX, float centerZ, float yaw,
                             float localX, float y, float localZ,
                             float sx, float sy, float sz, float[] color) {
        float cos = (float) Math.cos(yaw);
        float sin = (float) Math.sin(yaw);
        float worldX = centerX + localX * cos + localZ * sin;
        float worldZ = centerZ - localX * sin + localZ * cos;
        addBox(worldX, y, worldZ, sx, sy, sz, yaw, color);
    }

    /** Adds one imported model placement at an explicit world height. */
    private void addNature(NatureKind kind, float x, float y, float z,
                           float yaw, float scale) {
        natureInstances.add(new NatureInstance(kind, x, y, z, yaw, scale));
    }

    /**
     * Grounds a model on the heightfield. Imported pivots sit at the root, but
     * bushes/rocks have a small negative bbox overhang, so a per-kind offset
     * keeps their base tucked into the moss instead of hovering above it.
     */
    private void addNatureOnGround(NatureKind kind, float x, float z,
                                   float yaw, float scale) {
        float offset = switch (kind) {
            case BOULDER_ROUND, BOULDER_TALL -> 0.045f;
            case BUSH_SMALL -> 0.16f;
            case BUSH_LARGE -> 0.11f;
            default -> 0f;
        };
        addNature(kind, x, field.getHeight(x, z) + offset * scale, z, yaw, scale);
    }

    /** Adds one unrotated solid footprint in world units. */
    private void addSolid(float x, float z, float halfWidth, float halfDepth, float yaw,
                          float topY) {
        solids.add(new Solid(x, z, halfWidth, halfDepth, yaw, topY));
    }

    /** Adds a solid given in the structure's local frame (matches addLocalBox). */
    private void addLocalSolid(float centerX, float centerZ, float yaw,
                               float localX, float localZ,
                               float halfWidth, float halfDepth, float topY) {
        float cos = (float) Math.cos(yaw);
        float sin = (float) Math.sin(yaw);
        float worldX = centerX + localX * cos + localZ * sin;
        float worldZ = centerZ - localX * sin + localZ * cos;
        addSolid(worldX, worldZ, halfWidth, halfDepth, yaw, topY);
    }

    /** true when a point is closer than {@code radius} to a dirt-path tile centre. */
    private boolean isNearPathCenter(float x, float z, float radius) {
        float radius2 = radius * radius;
        for (int i = 0; i < pathTiles.size(); i++) {
            AsciiMap.Tile tile = pathTiles.get(i);
            float dx = centerX(tile) - x;
            float dz = centerZ(tile) - z;
            if (dx * dx + dz * dz < radius2) {
                return true;
            }
        }
        return false;
    }

    /** true when a point lies inside the shrine's open plaza radius. */
    private boolean isNearShrine(float x, float z, float radius) {
        float radius2 = radius * radius;
        for (int i = 0; i < shrineTiles.size(); i++) {
            AsciiMap.Tile shrine = shrineTiles.get(i);
            float dx = centerX(shrine) - x;
            float dz = centerZ(shrine) - z;
            if (dx * dx + dz * dz < radius2) {
                return true;
            }
        }
        return false;
    }

    private float centerX(AsciiMap.Tile tile) {
        return centerX(map, tile, worldScale);
    }

    private float centerZ(AsciiMap.Tile tile) {
        return centerZ(map, tile, worldScale);
    }

    private static float centerX(AsciiMap map, AsciiMap.Tile tile, float worldScale) {
        return map.tileCenterX(tile.col()) * worldScale;
    }

    private static float centerZ(AsciiMap map, AsciiMap.Tile tile, float worldScale) {
        return -map.tileCenterY(tile.row()) * worldScale;
    }

    /** Direction (3D XZ) of the path segment closest to a point. */
    private Vector2 nearestPathDirection(float x, float z) {
        float bestDistance = Float.MAX_VALUE;
        float dirX = 0f;
        float dirZ = 1f;
        for (int i = 1; i < pathTiles.size(); i++) {
            AsciiMap.Tile previous = pathTiles.get(i - 1);
            AsciiMap.Tile current = pathTiles.get(i);
            float dx = centerX(current) - x;
            float dz = centerZ(current) - z;
            float distance = dx * dx + dz * dz;
            if (distance >= bestDistance) {
                continue;
            }
            bestDistance = distance;
            dirX = current.col() - previous.col();
            dirZ = current.row() - previous.row();
        }
        float length = (float) Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (length <= 0.0001f) {
            return new Vector2(0f, 1f);
        }
        return new Vector2(dirX / length, dirZ / length);
    }

    /** true when the world position lies inside the ASCII map footprint. */
    private boolean isInsideMap(float worldX, float worldZ) {
        float mapWidthUnits = map.getWorldWidth() * worldScale;
        float mapHeightUnits = map.getWorldHeight() * worldScale;
        return worldX >= 0f && worldX < mapWidthUnits
            && worldZ <= 0f && worldZ > -mapHeightUnits;
    }

    private static boolean tooClose(List<Vector2> anchors, float x, float z, float radius) {
        float radius2 = radius * radius;
        for (int i = 0; i < anchors.size(); i++) {
            Vector2 anchor = anchors.get(i);
            float dx = anchor.x - x;
            float dz = anchor.y - z;
            if (dx * dx + dz * dz < radius2) {
                return true;
            }
        }
        return false;
    }

    private static long tileKey(AsciiMap.Tile tile) {
        return ((long) tile.row() << 32) ^ (tile.col() & 0xFFFFFFFFL);
    }

    /** Stable 0..1 hash used for dirt/tint variation. */
    private static float hash01(int col, int row) {
        int hash = col * 0x27d4eb2d ^ row * 0x9e3779b9;
        hash ^= hash >>> 15;
        hash *= 0x85ebca6b;
        hash ^= hash >>> 13;
        return (hash & 0xFFFF) / 65535f;
    }

    public List<Box> getBoxes() {
        return boxes;
    }

    /** Tapered round/faceted architecture primitives (bamboo, torii, lanterns). */
    public List<Cylinder> getCylinders() {
        return cylinders;
    }

    /** Low-poly organic ellipsoids (bamboo leaf masses, lantern finials). */
    public List<Sphere> getSpheres() {
        return spheres;
    }

    public List<PathPlate> getPathPlates() {
        return pathPlates;
    }

    public List<AsciiMap.Tile> getPathTiles() {
        return pathTiles;
    }

    public List<Vector2> getToriiAnchors() {
        return toriiAnchors;
    }

    public List<Vector2> getLanternAnchors() {
        return lanternAnchors;
    }

    public List<Vector2> getTempleAnchors() {
        return templeAnchors;
    }

    /** Front step centres of the temples, in generation order. */
    public List<Vector2> getTempleEntranceAnchors() {
        return templeEntranceAnchors;
    }

    public List<Vector2> getTreeAnchors() {
        return treeAnchors;
    }

    public List<Vector2> getBambooAnchors() {
        return bambooAnchors;
    }

    /** Imported tree/rock/bush/grass instances, in deterministic build order. */
    public List<NatureInstance> getNatureInstances() {
        return natureInstances;
    }

    /** Warm Tōrō/temple point lights in generation order (max 12 are rendered). */
    public List<WarmLight> getWarmLights() {
        return warmLights;
    }

    /** Physical footprints of torii pillars, lanterns and temple structures. */
    public List<Solid> getSolids() {
        return solids;
    }

    /** Centres of the boundary bamboo fence cells (one per wall cell). */
    public List<Vector2> getFenceAnchors() {
        return fenceAnchors;
    }

    /** Centres of the interior mossy stone wall cells. */
    public List<Vector2> getStoneWallAnchors() {
        return stoneWallAnchors;
    }

    /** Heightfield the plan was built against (also used for chunk bounds). */
    public Heightfield getField() {
        return field;
    }

    /** Cardinal season model of this world. */
    public WorldSeasonMap getWorldSeasonMap() {
        return seasonWorld;
    }

    /** Friendly villager posts of the sanctuary plaza. */
    public List<Vector2> getVillagerAnchors() {
        return villagerAnchors;
    }
}
