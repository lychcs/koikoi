package com.lychcs.koikoi.world;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Procedural generation of outdoor overworld terrain (bamboo forests, spirit
 * glades) using cellular automata - no Tiled, no {@code .tmx}.
 *
 * <p>The generator fills the grid with random walls and then smooths it with the
 * classic four-five rule of cave generation: a cell becomes a wall when at least
 * five of its eight neighbours are walls, becomes floor when at most three are
 * walls, and keeps its state otherwise. Afterwards the map border is sealed, all
 * floor areas except the largest connected one are removed (so every walkable
 * tile is reachable), and the gameplay markers are placed:</p>
 * <ul>
 *   <li>{@code @} player spawn near the southern edge</li>
 *   <li>{@code Y} two wild Yokai spawns away from the player</li>
 *   <li>{@code T} tavern door, {@code S} shop, {@code H} shrine</li>
 * </ul>
 *
 * <p>The result is produced as ASCII map text and parsed through
 * {@link AsciiMapLoader#parse(String, String)}, so generated and hand-authored
 * maps share exactly the same validation and data model. Generation is
 * deterministic for a given seed.</p>
 */
public final class ProcGenMapSystem {

    /** Default size of a generated outdoor map in tiles. */
    public static final int DEFAULT_WIDTH = 40;
    public static final int DEFAULT_HEIGHT = 28;

    /** Default tile edge length in world pixels. */
    public static final int DEFAULT_TILE_SIZE = 64;

    /** Default location id and season reported for generated maps. */
    public static final String DEFAULT_LOCATION_ID = "spirit_glade";
    public static final String DEFAULT_SEASON = "SPRING";

    /** Repeatable wild Kitsune encounter used for generated Yokai spawns. */
    public static final String DEFAULT_YOKAI_ENCOUNTER_ID = "encounter_kitsune_wild";

    /** Smallest accepted map size in tiles. */
    public static final int MIN_SIZE = 12;

    /** Minimum share of walkable tiles (percent) a generated map must offer. */
    public static final int MIN_FLOOR_RATIO_PERCENT = 35;

    /** Probability that a cell starts as a wall before smoothing. */
    private static final double INITIAL_WALL_CHANCE = 0.45;

    /** Number of cellular-automata smoothing passes. */
    private static final int SMOOTHING_PASSES = 5;

    /** A cell becomes a wall at this many wall neighbours. */
    private static final int WALL_NEIGHBOUR_LIMIT = 5;

    /** A cell becomes floor at this many wall neighbours or fewer. */
    private static final int FLOOR_NEIGHBOUR_LIMIT = 3;

    /** Number of wild Yokai spawns per generated map. */
    private static final int YOKAI_SPAWN_COUNT = 2;

    /** Glyphs of the low-contrast backdrop terrain matrix. */
    public static final char TERRAIN_COMMA = ',';
    public static final char TERRAIN_DOT = '.';
    public static final char TERRAIN_TUFT = '`';

    /** Preferred Chebyshev distance between the player spawn and a feature. */
    private static final int FEATURE_MIN_DISTANCE = 6;

    private static final int[][] NEIGHBOUR_OFFSETS = {
        {-1, -1}, {-1, 0}, {-1, 1},
        {0, -1}, {0, 1},
        {1, -1}, {1, 0}, {1, 1}
    };

    private ProcGenMapSystem() {
    }

    /** Generates a default-sized outdoor map for the given seed. */
    public static AsciiMap generateOutdoor(long seed) {
        return generateOutdoor(seed, DEFAULT_WIDTH, DEFAULT_HEIGHT, DEFAULT_TILE_SIZE,
            DEFAULT_LOCATION_ID, DEFAULT_SEASON);
    }

    /** Generates an outdoor map with an explicit size and season. */
    public static AsciiMap generateOutdoor(long seed, int width, int height, int tileSize, String season) {
        return generateOutdoor(seed, width, height, tileSize, DEFAULT_LOCATION_ID, season);
    }

    /** Generates an outdoor map and returns the parsed, validated {@link AsciiMap}. */
    public static AsciiMap generateOutdoor(long seed, int width, int height, int tileSize,
                                           String locationId, String season) {
        return AsciiMapLoader.parse(
            generateOutdoorText(seed, width, height, tileSize, locationId, season),
            "procgen:" + seed);
    }

    /**
     * Generates the ASCII map text of an outdoor map (metadata header plus tile
     * grid). The text uses exactly the format of hand-authored maps and can be
     * parsed with {@link AsciiMapLoader#parse(String, String)} or written to disk.
     */
    public static String generateOutdoorText(long seed, int width, int height, int tileSize,
                                             String locationId, String season) {
        if (width < MIN_SIZE || height < MIN_SIZE) {
            throw new IllegalArgumentException("Generated maps must be at least " + MIN_SIZE + "x" + MIN_SIZE
                + " tiles but are " + width + "x" + height);
        }
        if (tileSize <= 0) {
            throw new IllegalArgumentException("tileSize must be > 0 but was " + tileSize);
        }
        String resolvedLocationId = locationId == null || locationId.isBlank()
            ? DEFAULT_LOCATION_ID : locationId.trim();
        String resolvedSeason = season == null || season.isBlank() ? DEFAULT_SEASON : season.trim();

        Random random = new Random(seed);
        char[][] terrain = new char[height][width];
        seedTerrain(terrain, random);
        for (int pass = 0; pass < SMOOTHING_PASSES; pass++) {
            smooth(terrain);
        }
        sealBorder(terrain);

        int requiredFloor = width * height * MIN_FLOOR_RATIO_PERCENT / 100;
        List<Integer> floors = largestFloorRegion(terrain);
        if (floors.size() < requiredFloor) {
            carveInnerClearing(terrain);
            floors = largestFloorRegion(terrain);
        }
        closeOtherRegions(terrain, floors);

        int playerIndex = choosePlayerSpawn(floors, width, height);
        int playerRow = playerIndex / width;
        int playerCol = playerIndex % width;
        Set<Integer> used = new HashSet<>();
        used.add(playerIndex);

        List<Integer> yokaiIndices = new ArrayList<>(YOKAI_SPAWN_COUNT);
        for (int i = 0; i < YOKAI_SPAWN_COUNT; i++) {
            int index = pickFeature(floors, used, random, playerRow, playerCol, width);
            if (index >= 0) {
                yokaiIndices.add(index);
                used.add(index);
            }
        }
        int tavernIndex = pickFeature(floors, used, random, playerRow, playerCol, width);
        used.add(tavernIndex);
        int shopIndex = pickFeature(floors, used, random, playerRow, playerCol, width);
        used.add(shopIndex);
        int shrineIndex = pickFeature(floors, used, random, playerRow, playerCol, width);

        for (int index : yokaiIndices) {
            terrain[index / width][index % width] = AsciiMap.YOKAI_SPAWN;
        }
        placeMarker(terrain, tavernIndex, AsciiMap.TAVERN_DOOR, width);
        placeMarker(terrain, shopIndex, AsciiMap.SHOP, width);
        placeMarker(terrain, shrineIndex, AsciiMap.SHRINE, width);
        terrain[playerRow][playerCol] = AsciiMap.PLAYER_SPAWN;

        StringBuilder text = new StringBuilder((width + 2) * (height + YOKAI_SPAWN_COUNT + 4));
        text.append("!locationId ").append(resolvedLocationId).append('\n');
        text.append("!season ").append(resolvedSeason).append('\n');
        text.append("!tileSize ").append(tileSize).append('\n');
        for (int i = 0; i < yokaiIndices.size(); i++) {
            text.append("!yokai ").append(DEFAULT_YOKAI_ENCOUNTER_ID).append('\n');
        }
        for (int row = 0; row < height; row++) {
            text.append(terrain[row]).append('\n');
        }
        return text.toString();
    }

    /**
     * Generates a low-contrast terrain matrix for the world backdrop: mostly
     * {@code ','} with sparse {@code '.'} and occasional {@code '`'} grass tufts.
     *
     * <p>The matrix contains no walls; it is drawn underneath the playable ASCII
     * map so the area between the coarse map glyphs never collapses into a black
     * void. Generation is deterministic for a given seed.</p>
     */
    public static char[][] generateFloorMatrix(long seed, int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(
                "Floor matrix size must be positive but was " + width + "x" + height);
        }

        Random random = new Random(seed);
        char[][] floor = new char[height][width];

        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                double roll = random.nextDouble();
                if (roll < 0.64) {
                    floor[row][col] = TERRAIN_COMMA;
                } else if (roll < 0.92) {
                    floor[row][col] = TERRAIN_DOT;
                } else {
                    floor[row][col] = TERRAIN_TUFT;
                }
            }
        }

        return floor;
    }

    // ------------------------------------------------------------------
    // Dense terrain layer (Caves of Qud style)
    // ------------------------------------------------------------------

    /** Glyph cells per map tile in the dense terrain layer. */
    public static final int DENSE_CELLS_PER_TILE = AsciiMap.DETAIL_SCALE;

    /**
     * Attaches the dense meadow/forest glyph layer to a map.
     *
     * <p>The result keeps the gameplay grid, collision and markers unchanged;
     * only the visual sub-glyph layer is added. Generation is deterministic
     * for a given seed.</p>
     *
     * @throws IllegalArgumentException if {@code map} is {@code null}
     */
    public static AsciiMap densify(AsciiMap map, long seed) {
        if (map == null) {
            throw new IllegalArgumentException("map must not be null");
        }
        return map.withDenseTerrain(generateDenseTerrain(map, seed));
    }

    /**
     * Generates the dense terrain glyph layer for a map.
     *
     * <p>Every map tile is split into {@link AsciiMap#DETAIL_SCALE} x
     * {@link AsciiMap#DETAIL_SCALE} glyph cells:</p>
     * <ul>
     *   <li>walls become clustered moss, slate and burnt-umber forest/rock
     *       masses ({@code 0 Q @ %}),</li>
     *   <li>open ground becomes mossy meadow blades
     *       ({@code / \ | ; , . '}) with cream blossom sparks
     *       ({@code * o °}),</li>
     *   <li>sparse bamboo groves ({@code | H Y !}), torii/shrine gates
     *       ({@code - = T +}) and fallen sakura carpets ({@code ~ x}) form
     *       organic clusters,</li>
     *   <li>low-frequency value noise carves groves that hug wall faces, and</li>
     *   <li>spawn/trigger tiles and their immediate neighbourhood stay low so
     *       the bright gameplay markers remain readable.</li>
     * </ul>
     *
     * @return a {@code height * DETAIL_SCALE} by {@code width * DETAIL_SCALE}
     *         glyph matrix (row 0 is the northern edge)
     */
    public static char[][] generateDenseTerrain(AsciiMap map, long seed) {
        if (map == null) {
            throw new IllegalArgumentException("map must not be null");
        }

        int cells = AsciiMap.DETAIL_SCALE;
        int denseWidth = map.getWidth() * cells;
        int denseHeight = map.getHeight() * cells;
        char[][] dense = new char[denseHeight][denseWidth];

        for (int row = 0; row < denseHeight; row++) {
            int tileRow = row / cells;
            for (int col = 0; col < denseWidth; col++) {
                int tileCol = col / cells;
                dense[row][col] = denseGlyphAt(map, seed, tileCol, tileRow, col, row);
            }
        }
        return dense;
    }

    private static char denseGlyphAt(AsciiMap map, long seed, int tileCol, int tileRow,
                                     int col, int row) {
        char base = map.glyphAt(tileCol, tileRow);

        // Rock faces and tree lines: dense, clustered moss/slate canopy masses.
        if (base == AsciiMap.WALL) {
            return canopyGlyph(seed, col, row);
        }

        // Spawn/trigger tiles (and their ring) keep low ground cover so the
        // bright T/S/H/Y/@ markers stay legible on top of the foliage.
        if (isMarker(base) || nearMarker(map, tileCol, tileRow)) {
            return lowMeadowGlyph(seed, col, row);
        }

        // Low-frequency groves: forest pockets that hug rock faces and spill
        // into the meadows in organic, clustered shapes.
        double patch = valueNoise(seed ^ 0x9E3779B97F4A7C15L, col * 0.08, row * 0.08);
        boolean huggingWall = isNextToWall(map, tileCol, tileRow);
        double canopyThreshold = huggingWall ? 0.52 : 0.70;
        if (patch > canopyThreshold) {
            return canopyGlyph(seed, col, row);
        }

        // Fallen sakura carpets: wide, soft patches of muted magenta petals.
        double sakura = valueNoise(seed ^ 0x3C6EF372FE94F82BL, col * 0.105, row * 0.105);
        if (sakura > (huggingWall ? 0.66 : 0.74)) {
            return sakuraGlyph(seed, col, row);
        }

        // Bamboo groves: vertical, slightly denser clusters near wall faces.
        double bamboo = valueNoise(seed ^ 0xD1B54A32D192ED03L, col * 0.09, row * 0.09);
        if (bamboo > (huggingWall ? 0.64 : 0.72)) {
            return bambooGlyph(seed, col, row);
        }

        // Very sparse horizontal accents only; no letter-shaped torii noise.
        double torii = valueNoise(seed ^ 0x94D049BB133111EBL, col * 0.055, row * 0.055);
        if (torii > (huggingWall ? 0.92 : 0.96)) {
            return toriiGlyph(col, row);
        }

        // Cream blossom sparks scattered through the dark blades.
        double flowers = valueNoise(seed ^ 0xBB67AE8584CAA73BL, col * 0.15, row * 0.15);
        double spark = hash01(seed ^ 0x2545F4914F6CDD1DL, col, row);
        if (flowers > 0.80 && spark > 0.55) {
            return blossomGlyph(seed, col, row);
        }

        return meadowGlyph(seed, col, row, huggingWall);
    }

    private static boolean isMarker(char glyph) {
        return glyph == AsciiMap.PLAYER_SPAWN || glyph == AsciiMap.YOKAI_SPAWN
            || glyph == AsciiMap.TAVERN_DOOR || glyph == AsciiMap.SHOP
            || glyph == AsciiMap.SHRINE;
    }

    private static boolean nearMarker(AsciiMap map, int col, int row) {
        for (int rowOffset = -1; rowOffset <= 1; rowOffset++) {
            for (int colOffset = -1; colOffset <= 1; colOffset++) {
                int neighbourCol = col + colOffset;
                int neighbourRow = row + rowOffset;
                if (map.contains(neighbourCol, neighbourRow)
                    && isMarker(map.glyphAt(neighbourCol, neighbourRow))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isNextToWall(AsciiMap map, int col, int row) {
        for (int rowOffset = -1; rowOffset <= 1; rowOffset++) {
            for (int colOffset = -1; colOffset <= 1; colOffset++) {
                if (colOffset == 0 && rowOffset == 0) {
                    continue;
                }
                int neighbourCol = col + colOffset;
                int neighbourRow = row + rowOffset;
                if (map.contains(neighbourCol, neighbourRow)
                    && map.glyphAt(neighbourCol, neighbourRow) == AsciiMap.WALL) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Spatially coherent quiet punctuation cluster; no heavy letters. */
    private static char canopyGlyph(long seed, int col, int row) {
        double species = valueNoise(seed ^ 0x7F4A7C15F39CC060L, col * 0.045, row * 0.045);
        if (species < 0.30) {
            return ';';
        }

        double leaf = valueNoise(seed ^ 0x2545F4914F6CDD1DL, col * 0.12, row * 0.12);
        if (leaf < 0.35) {
            return ',';
        }
        if (leaf < 0.65) {
            return '\'';
        }
        if (leaf < 0.85) {
            return '/';
        }
        return '\\';
    }

    /** Quiet meadow punctuation and light blades; no heavy glyphs. */
    private static char meadowGlyph(long seed, int col, int row, boolean undergrowth) {
        // Low-frequency density variation only; the vocabulary itself stays a
        // uniform, low-contrast field of small punctuation/blades.
        double density = valueNoise(seed ^ 0x6A09E667F3BCC909L, col * 0.0425, row * 0.0425);
        double d = Math.max(0.0, Math.min(1.0, density + (undergrowth ? 0.10 : 0.0)));
        double roll = hash01(seed ^ 0xA0761D6478BD642FL, col, row);

        if (roll < 0.28 - 0.10 * d) {
            return ',';
        }
        if (roll < 0.46 - 0.10 * d) {
            return '.';
        }
        if (roll < 0.58 - 0.08 * d) {
            return '\'';
        }
        if (roll < 0.72 + 0.04 * d) {
            return ';';
        }
        if (roll < 0.86 + 0.04 * d) {
            return '/';
        }
        return '\\';
    }

    /** Sparse ground cover used below spawn/trigger markers. */
    private static char lowMeadowGlyph(long seed, int col, int row) {
        double roll = hash01(seed ^ 0x8EBC6AF1A4C3D2E5L, col, row);
        if (roll < 0.40) {
            return ',';
        }
        if (roll < 0.70) {
            return '.';
        }
        return '\'';
    }

    /** Bamboo grove: quiet slanted/vertical strokes only. */
    private static char bambooGlyph(long seed, int col, int row) {
        double roll = hash01(seed ^ 0xB5297A4D68B4C9F1L, col, row);
        if (roll < 0.45) {
            return ';';
        }
        if (roll < 0.75) {
            return '/';
        }
        return '\\';
    }

    /** Very sparse horizontal accent; never forms letter-shaped gates. */
    private static char toriiGlyph(int col, int row) {
        return Math.floorMod(col + row, 3) == 0 ? '=' : '-';
    }

    /** Fallen cherry-blossom carpet rendered as sparse petal dots. */
    private static char sakuraGlyph(long seed, int col, int row) {
        return ',';
    }

    /** Cream/parchment blossom spark between the blades. */
    private static char blossomGlyph(long seed, int col, int row) {
        return '*';
    }

    /** Bilinear, smooth-step value noise in {@code [0, 1]}. */
    private static double valueNoise(long seed, double x, double y) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        double fx = x - x0;
        double fy = y - y0;
        double sx = fx * fx * (3.0 - 2.0 * fx);
        double sy = fy * fy * (3.0 - 2.0 * fy);

        double n00 = hash01(seed, x0, y0);
        double n10 = hash01(seed, x0 + 1, y0);
        double n01 = hash01(seed, x0, y0 + 1);
        double n11 = hash01(seed, x0 + 1, y0 + 1);

        double nx0 = n00 + (n10 - n00) * sx;
        double nx1 = n01 + (n11 - n01) * sx;
        return nx0 + (nx1 - nx0) * sy;
    }

    /** Deterministic 64-bit hash mapped to {@code [0, 1)}. */
    private static double hash01(long seed, int x, int y) {
        long hash = seed;
        hash ^= x * 0x9E3779B97F4A7C15L;
        hash ^= y * 0xC2B2AE3D27D4EB4FL;
        hash = (hash ^ (hash >>> 33)) * 0xFF51AFD7ED558CCDL;
        hash = (hash ^ (hash >>> 33)) * 0xC4CEB9FE1A85EC53L;
        hash ^= hash >>> 33;
        return (hash >>> 11) / (double) (1L << 53);
    }

    // ------------------------------------------------------------------
    // Cellular automata
    // ------------------------------------------------------------------

    private static void seedTerrain(char[][] terrain, Random random) {
        for (int row = 0; row < terrain.length; row++) {
            for (int col = 0; col < terrain[row].length; col++) {
                terrain[row][col] = random.nextDouble() < INITIAL_WALL_CHANCE
                    ? AsciiMap.WALL : AsciiMap.FLOOR;
            }
        }
    }

    private static void smooth(char[][] terrain) {
        int height = terrain.length;
        int width = terrain[0].length;
        char[][] next = new char[height][width];

        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int wallNeighbours = countWallNeighbours(terrain, row, col);
                if (wallNeighbours >= WALL_NEIGHBOUR_LIMIT) {
                    next[row][col] = AsciiMap.WALL;
                } else if (wallNeighbours <= FLOOR_NEIGHBOUR_LIMIT) {
                    next[row][col] = AsciiMap.FLOOR;
                } else {
                    next[row][col] = terrain[row][col];
                }
            }
        }

        for (int row = 0; row < height; row++) {
            System.arraycopy(next[row], 0, terrain[row], 0, width);
        }
    }

    /** Counts wall neighbours; everything outside the map counts as wall. */
    private static int countWallNeighbours(char[][] terrain, int row, int col) {
        int height = terrain.length;
        int width = terrain[0].length;
        int walls = 0;
        for (int[] offset : NEIGHBOUR_OFFSETS) {
            int neighbourRow = row + offset[0];
            int neighbourCol = col + offset[1];
            if (neighbourRow < 0 || neighbourRow >= height || neighbourCol < 0 || neighbourCol >= width
                || terrain[neighbourRow][neighbourCol] == AsciiMap.WALL) {
                walls++;
            }
        }
        return walls;
    }

    private static void sealBorder(char[][] terrain) {
        int height = terrain.length;
        int width = terrain[0].length;
        for (int col = 0; col < width; col++) {
            terrain[0][col] = AsciiMap.WALL;
            terrain[height - 1][col] = AsciiMap.WALL;
        }
        for (int row = 0; row < height; row++) {
            terrain[row][0] = AsciiMap.WALL;
            terrain[row][width - 1] = AsciiMap.WALL;
        }
    }

    /** Clears the inner area as a last resort when the automaton collapses. */
    private static void carveInnerClearing(char[][] terrain) {
        int height = terrain.length;
        int width = terrain[0].length;
        for (int row = 2; row < height - 2; row++) {
            for (int col = 2; col < width - 2; col++) {
                terrain[row][col] = AsciiMap.FLOOR;
            }
        }
    }

    // ------------------------------------------------------------------
    // Connectivity and marker placement
    // ------------------------------------------------------------------

    /** All cells of the largest 4-connected floor region, packed as {@code row * width + col}. */
    private static List<Integer> largestFloorRegion(char[][] terrain) {
        int height = terrain.length;
        int width = terrain[0].length;
        boolean[] visited = new boolean[width * height];
        List<Integer> largest = new ArrayList<>();

        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int start = row * width + col;
                if (visited[start] || terrain[row][col] == AsciiMap.WALL) {
                    continue;
                }

                List<Integer> region = new ArrayList<>();
                ArrayDeque<Integer> queue = new ArrayDeque<>();
                queue.add(start);
                visited[start] = true;

                while (!queue.isEmpty()) {
                    int current = queue.poll();
                    region.add(current);
                    int currentRow = current / width;
                    int currentCol = current % width;
                    enqueueFloor(terrain, visited, queue, currentRow - 1, currentCol, width, height);
                    enqueueFloor(terrain, visited, queue, currentRow + 1, currentCol, width, height);
                    enqueueFloor(terrain, visited, queue, currentRow, currentCol - 1, width, height);
                    enqueueFloor(terrain, visited, queue, currentRow, currentCol + 1, width, height);
                }

                if (region.size() > largest.size()) {
                    largest = region;
                }
            }
        }
        return largest;
    }

    private static void enqueueFloor(char[][] terrain, boolean[] visited, ArrayDeque<Integer> queue,
                                     int row, int col, int width, int height) {
        if (row < 0 || row >= height || col < 0 || col >= width) {
            return;
        }
        int index = row * width + col;
        if (visited[index] || terrain[row][col] == AsciiMap.WALL) {
            return;
        }
        visited[index] = true;
        queue.add(index);
    }

    /** Turns every floor tile that is not part of the kept region into a wall. */
    private static void closeOtherRegions(char[][] terrain, List<Integer> keep) {
        int height = terrain.length;
        int width = terrain[0].length;
        boolean[] keepFlags = new boolean[width * height];
        for (int index : keep) {
            keepFlags[index] = true;
        }

        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int index = row * width + col;
                if (terrain[row][col] != AsciiMap.WALL && !keepFlags[index]) {
                    terrain[row][col] = AsciiMap.WALL;
                }
            }
        }
    }

    /** Player spawn: the floor tile closest to the southern map centre. */
    private static int choosePlayerSpawn(List<Integer> floorCells, int width, int height) {
        int best = floorCells.get(0);
        long bestScore = Long.MAX_VALUE;
        for (int index : floorCells) {
            int row = index / width;
            int col = index % width;
            long score = Math.abs(col - width / 2) * 2L + Math.abs(row - (height - 3));
            if (score < bestScore) {
                bestScore = score;
                best = index;
            }
        }
        return best;
    }

    /**
     * Picks an unused floor tile with a preferred distance from the player spawn.
     * Falls back to a shorter distance when the map is too small.
     *
     * @return packed cell index, or {@code -1} when every tile is already used
     */
    private static int pickFeature(List<Integer> candidates, Set<Integer> used, Random random,
                                   int playerRow, int playerCol, int width) {
        List<Integer> shuffled = new ArrayList<>(candidates);
        Collections.shuffle(shuffled, random);

        for (int minimumDistance : new int[] {FEATURE_MIN_DISTANCE, 3, 1}) {
            for (int index : shuffled) {
                if (used.contains(index)) {
                    continue;
                }
                int row = index / width;
                int col = index % width;
                int distance = Math.max(Math.abs(row - playerRow), Math.abs(col - playerCol));
                if (distance >= minimumDistance) {
                    return index;
                }
            }
        }
        return -1;
    }

    private static void placeMarker(char[][] terrain, int index, char glyph, int width) {
        if (index < 0) {
            return;
        }
        terrain[index / width][index % width] = glyph;
    }
}
