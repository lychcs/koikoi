package com.lychcs.koikoi.render;

import com.badlogic.gdx.math.Vector2;
import com.lychcs.koikoi.world.AsciiMap;

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
 * turns this plan into one static 3D mesh.</p>
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

    /** One rectangular beam/box primitive in libGDX world units. */
    public record Box(float x, float y, float z, float sx, float sy, float sz, float yaw,
                      float red, float green, float blue) {
    }

    /** One terrain-conforming dirt plate of a generated path. */
    public record PathPlate(float x, float z, float size,
                            float red, float green, float blue) {
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
     */
    public record Solid(float x, float z, float halfWidth, float halfDepth, float yaw) {
    }

    // Frugal Japanese palette of the procedural structures.
    private static final float[] TORII_RED = {0.74f, 0.26f, 0.17f};
    private static final float[] TORII_CAP = {0.12f, 0.10f, 0.09f};
    private static final float[] STONE = {0.46f, 0.48f, 0.45f};
    private static final float[] STONE_LIGHT = {0.56f, 0.58f, 0.54f};
    private static final float[] STONE_DARK = {0.26f, 0.28f, 0.26f};
    private static final float[] LANTERN_GLOW = {1.00f, 0.78f, 0.38f};
    private static final float[] WOOD = {0.42f, 0.29f, 0.18f};
    private static final float[] WOOD_DARK = {0.28f, 0.19f, 0.12f};
    private static final float[] ROOF_TILE = {0.22f, 0.26f, 0.28f};
    private static final float[] ROOF_TILE_DARK = {0.14f, 0.17f, 0.19f};
    private static final float[] BAMBOO = {0.24f, 0.44f, 0.24f};
    private static final float[] BAMBOO_LEAF = {0.30f, 0.54f, 0.28f};
    private static final float[] SAKURA_TRUNK = {0.30f, 0.20f, 0.15f};
    private static final float[] SAKURA_BLOSSOM = {0.62f, 0.36f, 0.44f};
    private static final float[] SAKURA_BLOSSOM_LIGHT = {0.76f, 0.48f, 0.56f};
    private static final float[] DIRT = {0.36f, 0.28f, 0.19f};
    private static final float[] DIRT_DARK = {0.28f, 0.22f, 0.15f};
    private static final float[] FENCE_BAMBOO = {0.52f, 0.47f, 0.30f};
    private static final float[] FENCE_BINDING = {0.34f, 0.30f, 0.20f};
    private static final float[] STONE_MOSS = {0.34f, 0.38f, 0.32f};
    private static final float[] STONE_MOSS_DARK = {0.24f, 0.28f, 0.24f};
    private static final float[] MOSS_CAP = {0.22f, 0.38f, 0.24f};

    /** Half distance between the two torii pillars in world units. */
    private static final float TORII_SPAN = 1.25f;

    /**
     * Path clearance in world units: a lantern or torii pillar may not sit this
     * close to the centre of a dirt-path tile, keeping WASD kiting lanes open.
     */
    private static final float PATH_CLEARANCE = 0.70f;
    /** Maximum number of scattered vegetation assemblies (mesh budget). */
    private static final int MAX_TREES = 96;
    private static final int MAX_BAMBOO = 64;
    /** Scatter grid spacing in world units. */
    private static final float SCATTER_STEP = 1.7f;

    private final AsciiMap map;
    private final Heightfield field;
    private final float worldScale;
    private final long seed;

    private final List<Box> boxes = new ArrayList<>();
    private final List<PathPlate> pathPlates = new ArrayList<>();
    private final List<AsciiMap.Tile> pathTiles = new ArrayList<>();
    private final List<Vector2> toriiAnchors = new ArrayList<>();
    private final List<Vector2> lanternAnchors = new ArrayList<>();
    private final List<Vector2> templeAnchors = new ArrayList<>();
    private final List<Vector2> treeAnchors = new ArrayList<>();
    private final List<Vector2> bambooAnchors = new ArrayList<>();
    private final List<WarmLight> warmLights = new ArrayList<>();
    private final List<Solid> solids = new ArrayList<>();
    private final List<Vector2> fenceAnchors = new ArrayList<>();
    private final List<Vector2> stoneWallAnchors = new ArrayList<>();
    private final List<AsciiMap.Tile> shrineTiles = new ArrayList<>();

    private SceneryPlan(AsciiMap map, Heightfield field, float worldScale, long seed) {
        this.map = map;
        this.field = field;
        this.worldScale = worldScale;
        this.seed = seed;
    }

    /** Builds the complete deterministic scenery plan of one location. */
    public static SceneryPlan generate(AsciiMap map, Heightfield field,
                                       float worldScale, long seed) {
        if (map == null) {
            throw new IllegalArgumentException("map must not be null");
        }
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        if (!(worldScale > 0f)) {
            throw new IllegalArgumentException("worldScale must be > 0 but was " + worldScale);
        }
        SceneryPlan plan = new SceneryPlan(map, field, worldScale, seed);
        plan.shrineTiles.addAll(map.tilesOf(AsciiMap.SHRINE));
        plan.buildWalls();
        plan.buildPathsAndGates();
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

    /** One woven bamboo fence cell along the map boundary. */
    private void addFenceSegment(int col, int row) {
        float x = centerX(new AsciiMap.Tile(col, row));
        float z = centerZ(new AsciiMap.Tile(col, row));
        float ground = field.getHeight(x, z);
        // Top/bottom border runs east-west, left/right border runs north-south.
        boolean eastWest = row == 0 || row == map.getHeight() - 1;
        float yaw = eastWest ? 0f : (float) (Math.PI * 0.5);

        // Two posts per cell (neighbour cells continue the fence line).
        addLocalBox(x, z, yaw, -0.44f, ground + 0.60f, 0f,
            0.10f, 1.20f, 0.10f, FENCE_BINDING);
        addLocalBox(x, z, yaw, 0.44f, ground + 0.60f, 0f,
            0.10f, 1.20f, 0.10f, FENCE_BINDING);
        // Three horizontal bamboo rails.
        for (int rail = 0; rail < 3; rail++) {
            float railY = ground + 0.32f + rail * 0.32f;
            addLocalBox(x, z, yaw, 0f, railY, 0f,
                0.94f, 0.06f, 0.06f, FENCE_BAMBOO);
        }
        // Vertical weave slats.
        for (int slat = -1; slat <= 1; slat++) {
            addLocalBox(x, z, yaw, slat * 0.30f, ground + 0.66f, 0f,
                0.05f, 0.86f, 0.05f, FENCE_BAMBOO);
        }

        fenceAnchors.add(new Vector2(x, z));
    }

    /** Low mossy stone outcrop for an interior obstacle wall cell. */
    private void addStoneOutcrop(int col, int row) {
        float x = centerX(new AsciiMap.Tile(col, row));
        float z = centerZ(new AsciiMap.Tile(col, row));
        float ground = field.getHeight(x, z);
        float yaw = hash01(col, row) * 3.14f;

        addLocalBox(x, z, yaw, 0f, ground + 0.22f, 0f,
            0.94f, 0.44f, 0.94f, STONE_MOSS_DARK);
        addLocalBox(x, z, yaw, 0.04f, ground + 0.58f, -0.03f,
            0.72f, 0.32f, 0.72f, STONE_MOSS);
        addLocalBox(x, z, yaw, 0f, ground + 0.78f, 0f,
            0.52f, 0.12f, 0.52f, MOSS_CAP);

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
        addLocalSolid(x, z, yaw, 0f, 2.45f, 0.65f, 0.30f);

        // The hall itself is solid: the shrine approach in front stays open,
        // but the player cannot walk through the temple walls.
        addLocalSolid(x, z, yaw, 0f, -0.1f, 2.95f, 2.0f);

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
    }

    // ------------------------------------------------------------------
    // Torii gate and stone lantern (Toro)
    // ------------------------------------------------------------------

    /** Small post-and-bowl torch with a bright flame box and a warm light. */
    private void addTempleTorch(float centerX, float centerZ, float yaw,
                                float localX, float localZ, float ground) {
        addLocalBox(centerX, centerZ, yaw, localX, ground + 0.55f, localZ,
            0.20f, 1.1f, 0.20f, WOOD_DARK);
        addLocalBox(centerX, centerZ, yaw, localX, ground + 1.14f, localZ,
            0.36f, 0.20f, 0.36f, STONE_DARK);
        addLocalBox(centerX, centerZ, yaw, localX, ground + 1.30f, localZ,
            0.24f, 0.24f, 0.24f, LANTERN_GLOW);

        float cos = (float) Math.cos(yaw);
        float sin = (float) Math.sin(yaw);
        float worldX = centerX + localX * cos + localZ * sin;
        float worldZ = centerZ - localX * sin + localZ * cos;
        warmLights.add(new WarmLight(worldX, ground + 1.35f, worldZ, 6.5f,
            1.00f, 0.62f, 0.27f));
        addLocalSolid(centerX, centerZ, yaw, localX, localZ, 0.14f, 0.14f);
    }

    private void addTorii(float x, float z, float yaw) {
        float ground = field.getHeight(x, z);
        float span = TORII_SPAN;
        float height = 3.1f;

        // Two vermillion pillars.
        addLocalBox(x, z, yaw, -span, ground + height * 0.5f, 0f,
            0.30f, height, 0.30f, TORII_RED);
        addLocalBox(x, z, yaw, span, ground + height * 0.5f, 0f,
            0.30f, height, 0.30f, TORII_RED);
        addLocalSolid(x, z, yaw, -span, 0f, 0.16f, 0.16f);
        addLocalSolid(x, z, yaw, span, 0f, 0.16f, 0.16f);

        // Nuki (lower tie beam) and gakuzuka (centre plaque).
        addLocalBox(x, z, yaw, 0f, ground + 2.28f, 0f,
            span * 2f + 0.9f, 0.22f, 0.24f, TORII_RED);
        addLocalBox(x, z, yaw, 0f, ground + 2.55f, 0f,
            0.46f, 0.42f, 0.18f, TORII_CAP);

        // Kasagi (top lintel) with a dark cap - the iconic torii silhouette.
        addLocalBox(x, z, yaw, 0f, ground + 2.86f, 0f,
            span * 2f + 1.5f, 0.26f, 0.36f, TORII_RED);
        addLocalBox(x, z, yaw, 0f, ground + 3.06f, 0f,
            span * 2f + 1.1f, 0.16f, 0.30f, TORII_CAP);

        toriiAnchors.add(new Vector2(x, z));
    }

    private void addLantern(float x, float z, float yaw) {
        float ground = field.getHeight(x, z);

        addLocalBox(x, z, yaw, 0f, ground + 0.12f, 0f, 0.78f, 0.24f, 0.78f, STONE);
        addLocalBox(x, z, yaw, 0f, ground + 0.52f, 0f, 0.26f, 0.72f, 0.26f, STONE);
        addLocalBox(x, z, yaw, 0f, ground + 0.92f, 0f, 0.52f, 0.12f, 0.52f, STONE_LIGHT);
        // Warm light housing: the only emissive-looking box of the scenery.
        addLocalBox(x, z, yaw, 0f, ground + 1.19f, 0f, 0.40f, 0.42f, 0.40f, LANTERN_GLOW);
        addLocalBox(x, z, yaw, 0f, ground + 1.48f, 0f, 0.74f, 0.16f, 0.74f, STONE_DARK);
        addLocalBox(x, z, yaw, 0f, ground + 1.62f, 0f, 0.50f, 0.12f, 0.50f, STONE);
        addLocalBox(x, z, yaw, 0f, ground + 1.74f, 0f, 0.16f, 0.18f, 0.16f, STONE);

        // Warm golden point light around the light housing.
        warmLights.add(new WarmLight(x, ground + 1.25f, z, 5.5f,
            1.00f, 0.72f, 0.34f));
        addSolid(x, z, 0.24f, 0.24f, yaw);

        lanternAnchors.add(new Vector2(x, z));
    }


    // ------------------------------------------------------------------
    // Vegetation: bamboo groves and sakura trees
    // ------------------------------------------------------------------

    private void scatterVegetation() {
        SimplexNoise densityNoise = new SimplexNoise(seed ^ 0x5CA3_9E11L);
        Random random = new Random(seed ^ 0xC0FF_EE00L);

        float startX = field.getOriginX() + 1.5f;
        float startZ = field.getOriginZ() + 1.5f;
        float endX = field.getOriginX() + field.getSize() - 1.5f;
        float endZ = field.getOriginZ() + field.getSize() - 1.5f;

        for (float z = startZ; z <= endZ; z += SCATTER_STEP) {
            for (float x = startX; x <= endX; x += SCATTER_STEP) {
                if (treeAnchors.size() >= MAX_TREES && bambooAnchors.size() >= MAX_BAMBOO) {
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
                if (grove && bambooAnchors.size() < MAX_BAMBOO) {
                    addBamboo(jitterX, jitterZ, random);
                } else if (treeAnchors.size() < MAX_TREES) {
                    addSakura(jitterX, jitterZ, random);
                }
            }
        }
    }

    private void addSakura(float x, float z, Random random) {
        float ground = field.getHeight(x, z);
        float height = 1.7f + random.nextFloat() * 1.1f;
        float yaw = random.nextFloat() * 6.2831855f;

        addLocalBox(x, z, yaw, 0f, ground + height * 0.5f, 0f,
            0.24f, height, 0.24f, SAKURA_TRUNK);
        addLocalBox(x, z, yaw, 0.34f, ground + height * 0.72f, 0f,
            0.14f, 0.9f, 0.14f, SAKURA_TRUNK);
        addLocalBox(x, z, yaw, -0.30f, ground + height * 0.62f, 0.16f,
            0.12f, 0.8f, 0.12f, SAKURA_TRUNK);

        int clusters = 3 + random.nextInt(3);
        for (int i = 0; i < clusters; i++) {
            float offsetX = (random.nextFloat() - 0.5f) * 1.6f;
            float offsetZ = (random.nextFloat() - 0.5f) * 1.6f;
            float size = 0.9f + random.nextFloat() * 0.7f;
            float[] blossom = i % 2 == 0 ? SAKURA_BLOSSOM : SAKURA_BLOSSOM_LIGHT;
            addBox(x + offsetX, ground + height + random.nextFloat() * 0.6f, z + offsetZ,
                size, size * 0.62f, size, 0f, blossom);
        }

        treeAnchors.add(new Vector2(x, z));
    }

    private void addBamboo(float x, float z, Random random) {
        float ground = field.getHeight(x, z);
        for (int i = 0; i < 3; i++) {
            float offsetX = (random.nextFloat() - 0.5f) * 0.55f;
            float offsetZ = (random.nextFloat() - 0.5f) * 0.55f;
            float height = 2.3f + random.nextFloat() * 1.5f;
            addBox(x + offsetX, ground + height * 0.5f, z + offsetZ,
                0.09f, height, 0.09f, 0f, BAMBOO);
            addBox(x + offsetX, ground + height - 0.25f, z + offsetZ,
                0.5f, 0.24f, 0.5f, random.nextFloat() * 3.14f, BAMBOO_LEAF);
        }
        bambooAnchors.add(new Vector2(x, z));
    }


    // ------------------------------------------------------------------
    // Helpers and accessors
    // ------------------------------------------------------------------

    private void addBox(float x, float y, float z, float sx, float sy, float sz,
                        float yaw, float[] color) {
        boxes.add(new Box(x, y, z, sx, sy, sz, yaw, color[0], color[1], color[2]));
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

    /** Adds one unrotated solid footprint in world units. */
    private void addSolid(float x, float z, float halfWidth, float halfDepth, float yaw) {
        solids.add(new Solid(x, z, halfWidth, halfDepth, yaw));
    }

    /** Adds a solid given in the structure's local frame (matches addLocalBox). */
    private void addLocalSolid(float centerX, float centerZ, float yaw,
                               float localX, float localZ,
                               float halfWidth, float halfDepth) {
        float cos = (float) Math.cos(yaw);
        float sin = (float) Math.sin(yaw);
        float worldX = centerX + localX * cos + localZ * sin;
        float worldZ = centerZ - localX * sin + localZ * cos;
        addSolid(worldX, worldZ, halfWidth, halfDepth, yaw);
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

    public List<Vector2> getTreeAnchors() {
        return treeAnchors;
    }

    public List<Vector2> getBambooAnchors() {
        return bambooAnchors;
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
}
