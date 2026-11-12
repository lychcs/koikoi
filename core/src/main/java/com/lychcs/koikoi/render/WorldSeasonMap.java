package com.lychcs.koikoi.render;

import com.lychcs.koikoi.run.GameSeason;
import com.lychcs.koikoi.world.AsciiMap;
import com.lychcs.koikoi.world.AsciiMapLoader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure data and math model of the four-season Hanafuda overworld.
 *
 * <p>The world is one large square ASCII map whose centre is the neutral
 * Sanctuary Village crossroads. Four cardinal realms surround the hub, each
 * entered through a monumental border torii gate and a narrow mountain pass:</p>
 *
 * <pre>
 *                  NORTH (Winter - Fuyu)
 *                          |
 *   WEST (Fall - Aki) -- SANCTUARY -- EAST (Spring - Haru)
 *                          |
 *                  SOUTH (Summer - Natsu)
 * </pre>
 *
 * <p>Map row {@code 0} is the northern edge and rows map to world {@code -z},
 * so world {@code +z} is south and world {@code -z} is north. One world unit
 * equals one 64 px tile, which matches {@code OverworldScreen.WORLD_SCALE}.</p>
 *
 * <p>The class owns no GL or physics resource, so season assignment, smooth
 * transition weights, border gate placement and the spatial chunk grid can all
 * be unit tested without a graphics context. The renderers consume the same
 * weights through {@link SeasonShaderUniforms}.</p>
 */
public final class WorldSeasonMap {

    /** Side length of the generated hub world in tiles. */
    public static final int DEFAULT_WORLD_TILES = 192;
    /** Smallest accepted four-season world. */
    public static final int MIN_WORLD_TILES = 48;
    /** Chebyshev radius of the neutral Sanctuary Village plaza. */
    public static final int HUB_RADIUS_TILES = 20;
    /** Distance of the four monumental border torii gates from the hub. */
    public static final int BORDER_GATE_DISTANCE_TILES = 32;
    /** Distance of the mountain ring that separates the seasonal realms. */
    public static final int MOUNTAIN_RING_DISTANCE_TILES = 40;
    /** Thickness of that mountain ring in tiles. */
    public static final int MOUNTAIN_RING_THICKNESS = 2;
    /** Half width of the four paved cardinal roads in tiles. */
    public static final int ROAD_HALF_WIDTH_TILES = 1;
    /** Season blending starts this many tiles from the hub centre. */
    public static final float SEASON_BLEND_START_TILES = 16f;
    /** Season blending is complete this many tiles from the hub centre. */
    public static final float SEASON_BLEND_END_TILES = 34f;
    /** Width of the smooth hub / border transition zone. */
    public static final float SEASON_BLEND_WIDTH_TILES =
        SEASON_BLEND_END_TILES - SEASON_BLEND_START_TILES;
    /** Edge length of one spatial culling chunk in tiles / world units. */
    public static final int CHUNK_SIZE_TILES = 32;
    /** Tile edge length of the generated ASCII map in world pixels. */
    public static final int TILE_SIZE = 64;
    /** World units per ASCII pixel ({@code 1 / 64}). */
    public static final float WORLD_UNIT_PER_PIXEL = 1f / TILE_SIZE;

    /** Sakura pink foliage (#F4A4B8). */
    public static final float[] SPRING_FOLIAGE = {0.957f, 0.643f, 0.722f};
    /** Fresh emerald spring grass. */
    public static final float[] SPRING_GROUND = {0.32f, 0.50f, 0.28f};
    /** Deep vibrant jade summer foliage. */
    public static final float[] SUMMER_FOLIAGE = {0.145f, 0.545f, 0.267f};
    /** Lush emerald summer grass. */
    public static final float[] SUMMER_GROUND = {0.16f, 0.38f, 0.20f};
    /** Vermillion maple foliage (#D94E34) blended with golden amber (#E89B3C). */
    public static final float[] FALL_FOLIAGE = {0.851f, 0.306f, 0.204f};
    /** Russet autumn ground. */
    public static final float[] FALL_GROUND = {0.40f, 0.27f, 0.15f};
    /** Frosted pine needles over crisp snow (#DCE6F2). */
    public static final float[] WINTER_FOLIAGE = {0.863f, 0.902f, 0.949f};
    /** Pale ice-blue snow terrain. */
    public static final float[] WINTER_GROUND = {0.78f, 0.84f, 0.92f};
    /** Neutral hub greenery, also used as the shader fallback. */
    public static final float[] SANCTUARY_FOLIAGE = {0.62f, 0.76f, 0.58f};
    public static final float[] SANCTUARY_GROUND = {0.26f, 0.38f, 0.26f};

    /** Location id of the generated world map. */
    public static final String LOCATION_ID = "four_seasons_world";
    /** Repeatable gentle Kitsune encounter of the spring/summer realms. */
    public static final String ENCOUNTER_SPRING_SUMMER = "encounter_kitsune_wild";
    /** Repeatable high-tier Oni encounter of the fall/winter realms. */
    public static final String ENCOUNTER_FALL_WINTER = "encounter_oni_wild";

    /** One of the five overworld realms. */
    public enum Season {
        /** Neutral central shrine village crossroads. */
        SANCTUARY("SANCTUARY VILLAGE", GameSeason.SPRING),
        /** East - Haru. */
        SPRING("SPRING — HARU", GameSeason.SPRING),
        /** South - Natsu. */
        SUMMER("SUMMER — NATSU", GameSeason.SUMMER),
        /** West - Aki. */
        FALL("FALL — AKI", GameSeason.AUTUMN),
        /** North - Fuyu. */
        WINTER("WINTER — FUYU", GameSeason.WINTER);

        private final String banner;
        private final GameSeason gameSeason;

        Season(String banner, GameSeason gameSeason) {
            this.banner = banner;
            this.gameSeason = gameSeason;
        }

        /** HUD banner text, e.g. {@code SPRING — HARU}. */
        public String banner() {
            return banner;
        }

        /** Run-session season announced when entering this realm. */
        public GameSeason gameSeason() {
            return gameSeason;
        }

        /** true for the four cardinal seasonal realms. */
        public boolean isSeasonal() {
            return this != SANCTUARY;
        }
    }

    /** One of the four cardinal directions of the compass HUD. */
    public enum Cardinal {
        NORTH('N'),
        EAST('E'),
        SOUTH('S'),
        WEST('W');

        private final char glyph;

        Cardinal(char glyph) {
            this.glyph = glyph;
        }

        /** Single-letter compass glyph ({@code N/E/S/W}). */
        public char glyph() {
            return glyph;
        }

        /**
         * Cardinal direction of a horizontal look/facing vector.
         * World {@code -z} is north and {@code +x} is east.
         */
        public static Cardinal fromDirection(float directionX, float directionZ) {
            double angle = Math.atan2(directionX, -directionZ);
            double quarter = Math.PI * 0.25;
            if (angle >= -quarter && angle < quarter) {
                return NORTH;
            }
            if (angle >= quarter && angle < quarter * 3) {
                return EAST;
            }
            if (angle >= quarter * 3 || angle < -quarter * 3) {
                return SOUTH;
            }
            return WEST;
        }
    }

    /**
     * One monumental border torii gate between the hub valley and a season.
     *
     * @param season        realm announced on the far side of the gate
     * @param cardinal      cardinal approach of the gate
     * @param x             gate centre in world X
     * @param z             gate centre in world Z
     * @param yaw           gate rotation around Y (pillars along local X)
     * @param distanceTiles distance from the hub centre in tiles
     */
    public record BorderTorii(Season season, Cardinal cardinal,
                              float x, float z, float yaw, float distanceTiles) {
    }

    /**
     * Normalized sanctuary / seasonal weights at one world position.
     *
     * <p>Weights always sum to {@code 1}. Inside the hub the sanctuary weight is
     * positive; on the diagonal outside the hub the four seasonal weights share
     * the influence, so crossing a corner never produces a hard seam.</p>
     */
    public record SeasonWeights(float sanctuary, float spring, float summer,
                                float fall, float winter) {

        public SeasonWeights {
            if (sanctuary < 0f || spring < 0f || summer < 0f
                || fall < 0f || winter < 0f) {
                throw new IllegalArgumentException("season weights must not be negative");
            }
            float sum = sanctuary + spring + summer + fall + winter;
            if (Math.abs(sum - 1f) > 0.002f) {
                throw new IllegalArgumentException(
                    "season weights must sum to 1 but were " + sum);
            }
        }

        /** 1 inside the seasonal realms, 0 at the sanctuary core. */
        public float seasonal() {
            return Math.max(0f, 1f - sanctuary);
        }

        /** Weight of one realm. */
        public float weight(Season season) {
            return switch (season) {
                case SANCTUARY -> sanctuary;
                case SPRING -> spring;
                case SUMMER -> summer;
                case FALL -> fall;
                case WINTER -> winter;
            };
        }

        /** Strongest realm at this position; ties resolve to the sanctuary. */
        public Season dominant() {
            Season best = Season.SANCTUARY;
            float bestWeight = sanctuary;
            if (spring > bestWeight + 0.0001f) {
                best = Season.SPRING;
                bestWeight = spring;
            }
            if (summer > bestWeight + 0.0001f) {
                best = Season.SUMMER;
                bestWeight = summer;
            }
            if (fall > bestWeight + 0.0001f) {
                best = Season.FALL;
                bestWeight = fall;
            }
            if (winter > bestWeight) {
                best = Season.WINTER;
            }
            return best;
        }
    }


    private final AsciiMap map;
    private final int worldTiles;
    private final float hubWorldX;
    private final float hubWorldZ;
    private final int hubTileCol;
    private final int hubTileRow;
    private final ChunkGrid chunkGrid;
    private final List<BorderTorii> borderTorii;
    private final List<AsciiMap.Tile> roadTiles;

    private WorldSeasonMap(AsciiMap map, int worldTiles, float worldScale) {
        this.map = map;
        this.worldTiles = worldTiles;
        this.hubTileCol = worldTiles / 2;
        this.hubTileRow = worldTiles / 2;
        this.hubWorldX = map.getWorldWidth() * worldScale * 0.5f;
        this.hubWorldZ = -map.getWorldHeight() * worldScale * 0.5f;
        this.chunkGrid = new ChunkGrid(0f, -worldTiles, worldTiles, CHUNK_SIZE_TILES);

        List<BorderTorii> gates = new ArrayList<>(4);
        float eastX = hubWorldX + BORDER_GATE_DISTANCE_TILES;
        float westX = hubWorldX - BORDER_GATE_DISTANCE_TILES;
        float northZ = hubWorldZ - BORDER_GATE_DISTANCE_TILES;
        float southZ = hubWorldZ + BORDER_GATE_DISTANCE_TILES;
        float crossYaw = (float) (Math.PI * 0.5);
        gates.add(new BorderTorii(Season.SPRING, Cardinal.EAST,
            eastX, hubWorldZ, crossYaw, BORDER_GATE_DISTANCE_TILES));
        gates.add(new BorderTorii(Season.SUMMER, Cardinal.SOUTH,
            hubWorldX, southZ, 0f, BORDER_GATE_DISTANCE_TILES));
        gates.add(new BorderTorii(Season.FALL, Cardinal.WEST,
            westX, hubWorldZ, crossYaw, BORDER_GATE_DISTANCE_TILES));
        gates.add(new BorderTorii(Season.WINTER, Cardinal.NORTH,
            hubWorldX, northZ, 0f, BORDER_GATE_DISTANCE_TILES));
        this.borderTorii = Collections.unmodifiableList(gates);
        this.roadTiles = Collections.unmodifiableList(buildRoadTiles(worldTiles));
    }

    /** Builds the default 192 x 192 four-season hub world. */
    public static WorldSeasonMap createHubWorld() {
        return createHubWorld(DEFAULT_WORLD_TILES);
    }

    /** Builds a deterministic four-season hub world of {@code tiles} side length. */
    public static WorldSeasonMap createHubWorld(int tiles) {
        if (tiles < MIN_WORLD_TILES) {
            throw new IllegalArgumentException("world needs at least " + MIN_WORLD_TILES
                + " tiles per side but was " + tiles);
        }
        AsciiMap map = AsciiMapLoader.parse(generateText(tiles), LOCATION_ID);
        return new WorldSeasonMap(map, tiles, WORLD_UNIT_PER_PIXEL);
    }

    /** Wraps an existing map (used by tests and the legacy small village). */
    public static WorldSeasonMap forMap(AsciiMap map) {
        return forMap(map, WORLD_UNIT_PER_PIXEL);
    }

    /** Wraps an existing map with an explicit pixel-to-world scale. */
    public static WorldSeasonMap forMap(AsciiMap map, float worldScale) {
        if (map == null) {
            throw new IllegalArgumentException("map must not be null");
        }
        if (!(worldScale > 0f)) {
            throw new IllegalArgumentException("worldScale must be > 0 but was " + worldScale);
        }
        int tiles = Math.max(MIN_WORLD_TILES, Math.min(map.getWidth(), map.getHeight()));
        return new WorldSeasonMap(map, tiles, worldScale);
    }

    public AsciiMap getMap() {
        return map;
    }

    /** Side length of the world in tiles / world units. */
    public int getWorldTiles() {
        return worldTiles;
    }

    public float getHubWorldX() {
        return hubWorldX;
    }

    public float getHubWorldZ() {
        return hubWorldZ;
    }

    public int getHubTileCol() {
        return hubTileCol;
    }

    public int getHubTileRow() {
        return hubTileRow;
    }

    /** true when this is the large four-season world, not the small test village. */
    public boolean isFourSeasonWorld() {
        return worldTiles >= 64;
    }

    /** Spatial 32 x 32 culling grid of the playable map. */
    public ChunkGrid getChunkGrid() {
        return chunkGrid;
    }

    /** The four monumental border gates, in {@code E, S, W, N} order. */
    public List<BorderTorii> getBorderTorii() {
        return borderTorii;
    }

    /** Paved cardinal road cells around the hub crossroads (deduplicated). */
    public List<AsciiMap.Tile> getRoadTiles() {
        return roadTiles;
    }


    /**
     * Smooth seasonal weights at a world position. The transition zone spans
     * {@link #SEASON_BLEND_START_TILES} to {@link #SEASON_BLEND_END_TILES} tiles
     * away from the hub; beyond it the realm reaches full strength.
     */
    public SeasonWeights weightsAt(float worldX, float worldZ) {
        float start = SEASON_BLEND_START_TILES;
        float end = SEASON_BLEND_END_TILES;
        float dx = worldX - hubWorldX;
        float dz = worldZ - hubWorldZ;

        float east = smoothstep(start, end, dx);
        float south = smoothstep(start, end, dz);
        float west = smoothstep(start, end, -dx);
        float north = smoothstep(start, end, -dz);
        float sum = east + south + west + north;

        if (sum > 1f) {
            return new SeasonWeights(0f, east / sum, south / sum, west / sum, north / sum);
        }
        return new SeasonWeights(1f - sum, east, south, west, north);
    }

    /** Realm containing a world position, including the neutral sanctuary. */
    public Season regionAtWorld(float worldX, float worldZ) {
        return weightsAt(worldX, worldZ).dominant();
    }

    /** Realm containing an ASCII tile. */
    public Season regionAtTile(AsciiMap.Tile tile) {
        if (tile == null) {
            return Season.SANCTUARY;
        }
        float x = (tile.col() + 0.5f) * TILE_SIZE * WORLD_UNIT_PER_PIXEL;
        float z = -(tile.row() + 0.5f) * TILE_SIZE * WORLD_UNIT_PER_PIXEL;
        return regionAtWorld(x, z);
    }

    /** Ground albedo target of one realm (shared with ascii_world.frag). */
    public static float[] groundAlbedo(Season season) {
        return switch (season) {
            case SANCTUARY -> SANCTUARY_GROUND;
            case SPRING -> SPRING_GROUND;
            case SUMMER -> SUMMER_GROUND;
            case FALL -> FALL_GROUND;
            case WINTER -> WINTER_GROUND;
        };
    }

    /** Foliage tint target of one realm (shared with both nature shaders). */
    public static float[] foliageTint(Season season) {
        return switch (season) {
            case SANCTUARY -> SANCTUARY_FOLIAGE;
            case SPRING -> SPRING_FOLIAGE;
            case SUMMER -> SUMMER_FOLIAGE;
            case FALL -> FALL_FOLIAGE;
            case WINTER -> WINTER_FOLIAGE;
        };
    }

    // ------------------------------------------------------------------
    // Map generation
    // ------------------------------------------------------------------

    /**
     * Deterministic ASCII map text of a four-season hub world. Uses exactly the
     * format of hand-authored maps, so {@link AsciiMapLoader} validation applies.
     */
    public static String generateText(int tiles) {
        if (tiles < MIN_WORLD_TILES) {
            throw new IllegalArgumentException("world needs at least " + MIN_WORLD_TILES
                + " tiles per side but was " + tiles);
        }
        int center = tiles / 2;
        char[][] grid = new char[tiles][tiles];
        for (char[] row : grid) {
            Arrays.fill(row, AsciiMap.FLOOR);
        }

        // Seal the world with the boundary fence.
        for (int i = 0; i < tiles; i++) {
            grid[0][i] = AsciiMap.WALL;
            grid[tiles - 1][i] = AsciiMap.WALL;
            grid[i][0] = AsciiMap.WALL;
            grid[i][tiles - 1] = AsciiMap.WALL;
        }

        // Square mountain ring with four narrow cardinal passes.
        int ringStart = MOUNTAIN_RING_DISTANCE_TILES;
        int ringEnd = ringStart + MOUNTAIN_RING_THICKNESS;
        for (int row = 1; row < tiles - 1; row++) {
            for (int col = 1; col < tiles - 1; col++) {
                int dx = col - center;
                int dz = row - center;
                int chebyshev = Math.max(Math.abs(dx), Math.abs(dz));
                if (chebyshev < ringStart || chebyshev >= ringEnd) {
                    continue;
                }
                if (Math.abs(dx) <= ROAD_HALF_WIDTH_TILES
                    || Math.abs(dz) <= ROAD_HALF_WIDTH_TILES) {
                    continue; // the paved pass stays open
                }
                grid[row][col] = AsciiMap.WALL;
            }
        }


        // Sparse 2-cell rock outcrops inside the seasonal realms only.
        for (int row = 3; row < tiles - 3; row++) {
            for (int col = 3; col < tiles - 3; col++) {
                int dx = col - center;
                int dz = row - center;
                int chebyshev = Math.max(Math.abs(dx), Math.abs(dz));
                if (chebyshev <= HUB_RADIUS_TILES + 4 || chebyshev >= ringStart - 3) {
                    continue;
                }
                if (Math.abs(dx) <= ROAD_HALF_WIDTH_TILES + 1
                    || Math.abs(dz) <= ROAD_HALF_WIDTH_TILES + 1) {
                    continue;
                }
                if (hash01(col, row) >= 0.004f) {
                    continue;
                }
                boolean horizontal = ((col + row) & 1) == 0;
                for (int step = 0; step < 2; step++) {
                    int c = horizontal ? col + step : col;
                    int r = horizontal ? row : row + step;
                    if (c > 0 && c < tiles - 1 && r > 0 && r < tiles - 1) {
                        grid[r][c] = AsciiMap.WALL;
                    }
                }
            }
        }

        // The sanctuary plaza is always an open, level crossroads.
        for (int row = 1; row < tiles - 1; row++) {
            for (int col = 1; col < tiles - 1; col++) {
                int chebyshev = Math.max(Math.abs(col - center), Math.abs(row - center));
                if (chebyshev <= HUB_RADIUS_TILES) {
                    grid[row][col] = AsciiMap.FLOOR;
                }
            }
        }

        // Four paved roads: carve last so nothing can block them.
        for (int offset = -center + 1; offset < center; offset++) {
            for (int lane = -ROAD_HALF_WIDTH_TILES; lane <= ROAD_HALF_WIDTH_TILES; lane++) {
                setFloor(grid, tiles, center + offset, center + lane);
                setFloor(grid, tiles, center + lane, center + offset);
            }
        }

        // Hub landmarks: shrine, tavern, shop and the player spawn.
        grid[center - 10][center - 10] = AsciiMap.SHRINE;
        grid[center - 6][center + 11] = AsciiMap.TAVERN_DOOR;
        grid[center + 11][center - 6] = AsciiMap.SHOP;
        grid[center + 10][center] = AsciiMap.PLAYER_SPAWN;

        // Two wild Yokai per realm; the northern and western realms hold the
        // high-tier Oni, the eastern and southern realms the roaming Kitsune.
        int[][] spawns = {
            {center + 6, center + 55}, {center - 10, center + 78},
            {center + 55, center + 6}, {center + 78, center - 10},
            {center - 6, center - 55}, {center + 10, center - 78},
            {center - 55, center - 6}, {center - 78, center + 10}
        };
        for (int[] spawn : spawns) {
            setFloor(grid, tiles, spawn[0], spawn[1]);
            grid[spawn[0]][spawn[1]] = AsciiMap.YOKAI_SPAWN;
        }

        // The loader assigns !yokai lines in reading order (row-major), so the
        // encounter list must follow the same order.
        List<int[]> spawnCells = new ArrayList<>(spawns.length);
        for (int row = 0; row < tiles; row++) {
            for (int col = 0; col < tiles; col++) {
                if (grid[row][col] == AsciiMap.YOKAI_SPAWN) {
                    spawnCells.add(new int[]{row, col});
                }
            }
        }

        StringBuilder text = new StringBuilder(tiles * (tiles + 2) + 256);
        text.append("!locationId ").append(LOCATION_ID).append('\n');
        text.append("!season ").append(GameSeason.SPRING.name()).append('\n');
        text.append("!tileSize ").append(TILE_SIZE).append('\n');
        for (int[] cell : spawnCells) {
            text.append("!yokai ")
                .append(encounterForCell(cell[0], cell[1], center)).append('\n');
        }
        for (int row = 0; row < tiles; row++) {
            text.append(grid[row]).append('\n');
        }
        return text.toString();
    }

    private static void setFloor(char[][] grid, int tiles, int row, int col) {
        if (row > 0 && row < tiles - 1 && col > 0 && col < tiles - 1) {
            grid[row][col] = AsciiMap.FLOOR;
        }
    }

    /** Encounter id of a Yokai spawn cell: gentle east/south, Oni west/north. */
    private static String encounterForCell(int row, int col, int center) {
        int dx = col - center;
        int dz = row - center;
        boolean gentle = Math.abs(dx) >= Math.abs(dz) ? dx >= 0 : dz >= 0;
        return gentle ? ENCOUNTER_SPRING_SUMMER : ENCOUNTER_FALL_WINTER;
    }

    private static List<AsciiMap.Tile> buildRoadTiles(int tiles) {
        int center = tiles / 2;
        List<AsciiMap.Tile> cells = new ArrayList<>(tiles * 6);
        Set<Long> seen = new HashSet<>();
        for (int offset = -center + 1; offset < center; offset++) {
            for (int lane = -ROAD_HALF_WIDTH_TILES; lane <= ROAD_HALF_WIDTH_TILES; lane++) {
                addRoadTile(cells, seen, tiles, center + offset, center + lane);
                addRoadTile(cells, seen, tiles, center + lane, center + offset);
            }
        }
        return cells;
    }

    private static void addRoadTile(List<AsciiMap.Tile> out, Set<Long> seen,
                                    int tiles, int col, int row) {
        if (col <= 0 || col >= tiles - 1 || row <= 0 || row >= tiles - 1) {
            return; // the sealed world border is never a paved road cell
        }
        long key = ((long) row << 32) ^ (col & 0xFFFFFFFFL);
        if (seen.add(key)) {
            out.add(new AsciiMap.Tile(col, row));
        }
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        if (edge1 <= edge0) {
            return value >= edge1 ? 1f : 0f;
        }
        float t = (value - edge0) / (edge1 - edge0);
        if (t <= 0f) {
            return 0f;
        }
        if (t >= 1f) {
            return 1f;
        }
        return t * t * (3f - 2f * t);
    }

    private static float hash01(int col, int row) {
        int hash = col * 0x27d4eb2d ^ row * 0x9e3779b9;
        hash ^= hash >>> 15;
        hash *= 0x85ebca6b;
        hash ^= hash >>> 13;
        return (hash & 0xFFFF) / 65535f;
    }

    // ------------------------------------------------------------------
    // Spatial chunk grid
    // ------------------------------------------------------------------

    /**
     * Square 32-tile spatial culling grid shared by the terrain mesh, the
     * static scenery, the nature {@code ModelInstance} batches and the Box2D
     * collision bodies.
     *
     * <p>Every chunk is a fixed cell of world space. A chunk is visible when the
     * horizontal gap between the camera and the chunk's bounding box is within
     * the requested cull radius; the renderers additionally frustum-test the
     * chunk box. World positions outside the grid clamp to the nearest chunk.</p>
     */
    public static final class ChunkGrid {

        private final float originX;
        private final float originZ;
        private final float size;
        private final float chunkSize;
        private final int columns;
        private final int rows;

        public ChunkGrid(float originX, float originZ, float size, float chunkSize) {
            if (!(size > 0f) || !(chunkSize > 0f)) {
                throw new IllegalArgumentException("size and chunkSize must be > 0");
            }
            this.originX = originX;
            this.originZ = originZ;
            this.size = size;
            this.chunkSize = chunkSize;
            this.columns = Math.max(1, (int) Math.ceil(size / chunkSize));
            this.rows = columns;
        }

        public float getOriginX() {
            return originX;
        }

        public float getOriginZ() {
            return originZ;
        }

        public float getSize() {
            return size;
        }

        public float getChunkSize() {
            return chunkSize;
        }

        public int getColumns() {
            return columns;
        }

        public int getRows() {
            return rows;
        }

        public int count() {
            return columns * rows;
        }

        /** Chunk index (row-major) of a world position, clamped to the grid. */
        public int chunkOf(float worldX, float worldZ) {
            int col = Math.min(columns - 1, Math.max(0,
                (int) Math.floor((worldX - originX) / chunkSize)));
            int row = Math.min(rows - 1, Math.max(0,
                (int) Math.floor((worldZ - originZ) / chunkSize)));
            return row * columns + col;
        }

        /** Chunk column of an index. */
        public int columnOf(int index) {
            return index % columns;
        }

        /** Chunk row of an index. */
        public int rowOf(int index) {
            return index / columns;
        }

        public float chunkMinX(int index) {
            return originX + columnOf(index) * chunkSize;
        }

        public float chunkMinZ(int index) {
            return originZ + rowOf(index) * chunkSize;
        }

        public float chunkCenterX(int index) {
            return chunkMinX(index) + chunkSize * 0.5f;
        }

        public float chunkCenterZ(int index) {
            return chunkMinZ(index) + chunkSize * 0.5f;
        }

        /** true when the chunk box lies within {@code radius} of the camera. */
        public boolean isVisible(int index, float cameraX, float cameraZ, float radius) {
            if (index < 0 || index >= count()) {
                return false;
            }
            float dx = Math.abs(cameraX - chunkCenterX(index)) - chunkSize * 0.5f;
            float dz = Math.abs(cameraZ - chunkCenterZ(index)) - chunkSize * 0.5f;
            dx = Math.max(0f, dx);
            dz = Math.max(0f, dz);
            return dx * dx + dz * dz <= radius * radius;
        }

        /**
         * Writes all chunk indices within {@code radius} into {@code out}.
         *
         * @return number of indices written
         */
        public int collectVisible(float cameraX, float cameraZ, float radius, int[] out) {
            if (out == null) {
                throw new IllegalArgumentException("out must not be null");
            }
            int written = 0;
            for (int index = 0; index < count() && written < out.length; index++) {
                if (isVisible(index, cameraX, cameraZ, radius)) {
                    out[written++] = index;
                }
            }
            return written;
        }
    }
}


