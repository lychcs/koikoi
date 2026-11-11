package com.lychcs.koikoi.world;

import com.badlogic.gdx.math.Rectangle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable ASCII map of the overworld.
 *
 * <p>A map is a fixed grid of glyphs. Row {@code 0} is the northern edge of the
 * map; world coordinates follow LibGDX/Box2D (Y grows upwards), so
 * {@link #tileBottomY(int)} converts a row into the world Y of its lower edge.</p>
 *
 * <p><b>Legend</b> (see also {@code yaku_gdd.md} section 2.3):</p>
 * <ul>
 *   <li>{@link #WALL} {@code #}: solid wall / collision</li>
 *   <li>{@link #FLOOR} {@code .}: walkable floor</li>
 *   <li>{@link #PLAYER_SPAWN} {@code @}: player spawn</li>
 *   <li>{@link #YOKAI_SPAWN} {@code Y}: wild Yokai spawn</li>
 *   <li>{@link #TAVERN_DOOR} {@code T}: tavern door transition</li>
 *   <li>{@link #SHOP} {@code S}: shop trigger</li>
 *   <li>{@link #SHRINE} {@code H}: shrine trigger</li>
 * </ul>
 *
 * <p>All glyphs except {@link #WALL} are walkable: spawn and trigger glyphs mark
 * an otherwise empty floor tile. The class holds pure CPU data and has no
 * LibGDX natives, so it is usable in plain unit tests.</p>
 *
 * <p>Next to the gameplay grid a map may carry a <b>dense terrain layer</b>
 * ({@link #withDenseTerrain(char[][])}) that covers every tile with a
 * {@link #DETAIL_SCALE} x {@link #DETAIL_SCALE} block of meadow, bamboo,
 * torii, sakura and canopy glyphs. It is purely visual and never changes
 * collision or triggers.</p>
 */
public final class AsciiMap {

    /** Solid wall / collision tile. */
    public static final char WALL = '#';
    /** Walkable floor tile. */
    public static final char FLOOR = '.';
    /** Player spawn marker. */
    public static final char PLAYER_SPAWN = '@';
    /** Wild Yokai spawn marker. */
    public static final char YOKAI_SPAWN = 'Y';
    /** Tavern door transition marker. */
    public static final char TAVERN_DOOR = 'T';
    /** Shop trigger marker. */
    public static final char SHOP = 'S';
    /** Shrine trigger marker. */
    public static final char SHRINE = 'H';

    /**
     * Foliage glyphs of the dense meadow layer: quiet small punctuation and
     * light strokes only. No heavy letters or block glyphs.
     */
    public static final char[] MEADOW_GLYPHS = {
        '/', '\\', ',', '.', '\'', ';'
    };

    /**
     * Cream/parchment blossom sparks that appear between the dark blades
     * ({@code '*'}).
     */
    public static final char[] BLOSSOM_GLYPHS = {
        '*'
    };

    /** Bamboo grove glyphs: quiet slanted/vertical strokes. */
    public static final char[] BAMBOO_GLYPHS = {
        '/', '\\', ';'
    };

    /** Sparse torii/shrine cluster glyphs: thin horizontal accents only. */
    public static final char[] TORII_GLYPHS = {
        '-', '='
    };

    /** Fallen cherry-blossom carpet rendered as sparse petal dots. */
    public static final char[] SAKURA_GLYPHS = {
        ','
    };

    /**
     * Glyphs of the dense forest clusters: punctuation and light strokes, still
     * one small character per terminal cell.
     */
    public static final char[] CANOPY_GLYPHS = {
        ',', '.', '\'', ';', '/', '\\'
    };

    /**
     * Sub-glyph resolution of the dense terrain layer: every map tile is
     * covered by a {@code DETAIL_SCALE x DETAIL_SCALE} block of glyphs, so a
     * 64 px tile becomes a seamless 8 px ASCII terminal grid.
     */
    public static final int DETAIL_SCALE = 8;

    /** true if the glyph belongs to the meadow/foliage vocabulary. */
    public static boolean isMeadowGlyph(char glyph) {
        return contains(MEADOW_GLYPHS, glyph);
    }

    /** true if the glyph belongs to the pale blossom-spark vocabulary. */
    public static boolean isBlossomGlyph(char glyph) {
        return contains(BLOSSOM_GLYPHS, glyph);
    }

    /** true if the glyph belongs to the bamboo grove vocabulary. */
    public static boolean isBambooGlyph(char glyph) {
        return contains(BAMBOO_GLYPHS, glyph);
    }

    /** true if the glyph belongs to the torii/shrine vocabulary. */
    public static boolean isToriiGlyph(char glyph) {
        return contains(TORII_GLYPHS, glyph);
    }

    /** true if the glyph belongs to the fallen sakura carpet vocabulary. */
    public static boolean isSakuraGlyph(char glyph) {
        return contains(SAKURA_GLYPHS, glyph);
    }

    /** true if the glyph belongs to the forest/rock vocabulary. */
    public static boolean isCanopyGlyph(char glyph) {
        return contains(CANOPY_GLYPHS, glyph);
    }

    /** true if the glyph is a light grass stroke that reacts to feet. */
    public static boolean isBendableGlyph(char glyph) {
        return glyph == '/' || glyph == '\\' || glyph == ';';
    }

    /** true if the glyph can appear in the dense terrain detail layer. */
    public static boolean isDenseTerrainGlyph(char glyph) {
        return isBendableGlyph(glyph) || isBlossomGlyph(glyph) || isToriiGlyph(glyph)
            || isSakuraGlyph(glyph) || isCanopyGlyph(glyph);
    }

    private static boolean contains(char[] glyphs, char glyph) {
        for (char candidate : glyphs) {
            if (candidate == glyph) {
                return true;
            }
        }
        return false;
    }

    /** A tile coordinate on the map grid. */
    public record Tile(int col, int row) {
    }

    /**
     * A Yokai spawn tile with the stable encounter id from the {@code !yokai}
     * metadata lines (index-aligned with the {@code Y} glyphs in reading order).
     */
    public record YokaiSpawn(int col, int row, String encounterId) {
    }

    private final String source;
    private final String locationId;
    private final String seasonName;
    private final int tileSize;
    private final int width;
    private final int height;
    private final char[][] tiles;
    /** Dense visual layer ({@code height * DETAIL_SCALE} rows) or {@code null}. */
    private final char[][] denseTerrain;
    private final Tile playerSpawn;
    private final List<YokaiSpawn> yokaiSpawns;

    AsciiMap(String source, String locationId, String seasonName, int tileSize,
             int width, int height, char[][] tiles, Tile playerSpawn, List<YokaiSpawn> yokaiSpawns) {
        this(source, locationId, seasonName, tileSize, width, height, tiles, null,
            playerSpawn, yokaiSpawns);
    }

    AsciiMap(String source, String locationId, String seasonName, int tileSize,
             int width, int height, char[][] tiles, char[][] denseTerrain,
             Tile playerSpawn, List<YokaiSpawn> yokaiSpawns) {
        this.source = source;
        this.locationId = locationId;
        this.seasonName = seasonName;
        this.tileSize = tileSize;
        this.width = width;
        this.height = height;
        this.tiles = tiles;
        this.denseTerrain = copyDenseTerrain(denseTerrain, width, height, source);
        this.playerSpawn = playerSpawn;
        this.yokaiSpawns = List.copyOf(yokaiSpawns);
    }

    private static char[][] copyDenseTerrain(char[][] denseTerrain, int width, int height, String source) {
        if (denseTerrain == null) {
            return null;
        }

        int expectedHeight = height * DETAIL_SCALE;
        int expectedWidth = width * DETAIL_SCALE;
        if (denseTerrain.length != expectedHeight) {
            throw new IllegalArgumentException("Dense terrain has " + denseTerrain.length
                + " rows but " + expectedHeight + " are required for " + height + " tiles (map "
                + source + ")");
        }

        char[][] copy = new char[expectedHeight][expectedWidth];
        for (int row = 0; row < expectedHeight; row++) {
            char[] sourceRow = denseTerrain[row];
            if (sourceRow == null || sourceRow.length != expectedWidth) {
                throw new IllegalArgumentException("Dense terrain row " + row + " must have "
                    + expectedWidth + " columns (map " + source + ")");
            }
            for (int col = 0; col < expectedWidth; col++) {
                char glyph = sourceRow[col];
                if (!isDenseTerrainGlyph(glyph)) {
                    throw new IllegalArgumentException("Glyph '" + glyph + "' at dense terrain cell ("
                        + col + ", " + row + ") is not part of the Japanese dense-terrain vocabulary (map "
                        + source + ")");
                }
                copy[row][col] = glyph;
            }
        }
        return copy;
    }

    /** Source of this map (path or synthetic id), used in error messages. */
    public String getSource() {
        return source;
    }

    /** Stable location id reported to the run session. */
    public String getLocationId() {
        return locationId;
    }

    /** Season name from the map header (already trimmed, not yet enum-parsed). */
    public String getSeasonName() {
        return seasonName;
    }

    /** Edge length of one map cell in world pixels. */
    public int getTileSize() {
        return tileSize;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    /** Width of the whole map in world pixels. */
    public float getWorldWidth() {
        return width * (float) tileSize;
    }

    /** Height of the whole map in world pixels. */
    public float getWorldHeight() {
        return height * (float) tileSize;
    }

    /** true if this map carries the dense Qud-style terrain glyph layer. */
    public boolean hasDenseTerrain() {
        return denseTerrain != null;
    }

    /** Width of the dense terrain layer in glyph cells ({@code 0} when absent). */
    public int getDenseTerrainWidth() {
        return denseTerrain == null ? 0 : denseTerrain[0].length;
    }

    /** Height of the dense terrain layer in glyph cells ({@code 0} when absent). */
    public int getDenseTerrainHeight() {
        return denseTerrain == null ? 0 : denseTerrain.length;
    }

    /**
     * Glyph of the dense terrain layer at the given sub-cell.
     *
     * <p>Sub-cell {@code (0, 0)} is the north-west corner of the map; each map
     * tile is covered by a {@link #DETAIL_SCALE} x {@link #DETAIL_SCALE} block.
     * The layer is purely visual: {@link #isWalkable(int, int)} and
     * {@link #isSolid(int, int)} keep using the gameplay grid.</p>
     *
     * @throws IllegalStateException     if the map has no dense terrain layer
     * @throws IndexOutOfBoundsException if the cell is outside the layer
     */
    public char denseTerrainAt(int detailCol, int detailRow) {
        if (denseTerrain == null) {
            throw new IllegalStateException("Map " + source + " has no dense terrain layer");
        }
        if (detailCol < 0 || detailCol >= getDenseTerrainWidth()
            || detailRow < 0 || detailRow >= getDenseTerrainHeight()) {
            throw new IndexOutOfBoundsException("Dense terrain cell (" + detailCol + ", " + detailRow
                + ") is outside map " + source + " (" + getDenseTerrainWidth() + "x"
                + getDenseTerrainHeight() + ")");
        }
        return denseTerrain[detailRow][detailCol];
    }

    /**
     * Returns a copy of this map with the given dense terrain layer attached.
     * The gameplay grid, markers and metadata are preserved unchanged.
     *
     * @throws IllegalArgumentException if the layer has the wrong size or
     *                                  contains glyphs outside the Japanese dense-terrain vocabulary
     */
    public AsciiMap withDenseTerrain(char[][] denseTerrain) {
        if (denseTerrain == null) {
            throw new IllegalArgumentException("denseTerrain must not be null (map " + source + ")");
        }
        return new AsciiMap(source, locationId, seasonName, tileSize, width, height, tiles,
            denseTerrain, playerSpawn, yokaiSpawns);
    }

    public boolean contains(int col, int row) {
        return col >= 0 && col < width && row >= 0 && row < height;
    }

    /**
     * Glyph at the given cell.
     *
     * @throws IndexOutOfBoundsException if the cell is outside the map
     */
    public char glyphAt(int col, int row) {
        if (!contains(col, row)) {
            throw new IndexOutOfBoundsException(
                "Cell (" + col + ", " + row + ") is outside map " + source
                    + " (" + width + "x" + height + ")");
        }
        return tiles[row][col];
    }

    /** true, if the cell is inside the map and not a wall. */
    public boolean isWalkable(int col, int row) {
        return contains(col, row) && tiles[row][col] != WALL;
    }

    /** true, if the cell is a wall or outside the map (used for collision). */
    public boolean isSolid(int col, int row) {
        return !isWalkable(col, row);
    }

    /** Lower edge of the given row in world pixels. */
    public float tileBottomY(int row) {
        return (height - 1 - row) * (float) tileSize;
    }

    /** Centre X of the given column in world pixels. */
    public float tileCenterX(int col) {
        return col * (float) tileSize + tileSize * 0.5f;
    }

    /** Centre Y of the given row in world pixels. */
    public float tileCenterY(int row) {
        return tileBottomY(row) + tileSize * 0.5f;
    }

    /** Writes the world-space bounds of a cell into {@code out} (no allocation). */
    public Rectangle tileBounds(int col, int row, Rectangle out) {
        return out.set(col * (float) tileSize, tileBottomY(row), tileSize, tileSize);
    }

    /** Player spawn tile ({@code @}). */
    public Tile getPlayerSpawn() {
        return playerSpawn;
    }

    /** Wild Yokai spawns in reading order (row-major). */
    public List<YokaiSpawn> getYokaiSpawns() {
        return Collections.unmodifiableList(yokaiSpawns);
    }

    /** All cells carrying the given glyph, in reading order. */
    public List<Tile> tilesOf(char glyph) {
        List<Tile> result = new ArrayList<>();
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                if (tiles[row][col] == glyph) {
                    result.add(new Tile(col, row));
                }
            }
        }
        return result;
    }
}
