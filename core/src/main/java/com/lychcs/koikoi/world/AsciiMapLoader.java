package com.lychcs.koikoi.world;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxRuntimeException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Loads plain-text ASCII maps for fixed structures (villages, tavern interiors,
 * boss arenas) - the replacement for the removed Tiled {@code .tmx} pipeline.
 *
 * <p><b>File format</b></p>
 * <pre>
 * !locationId spring_village
 * !season SPRING
 * !tileSize 64
 * !yokai spring_village_kitsune_01   (one line per 'Y', in reading order)
 * ############
 * #....@.....#
 * #..........#
 * ############
 * </pre>
 *
 * <p>Lines starting with {@code !} are metadata and must appear before the tile
 * grid. The remaining lines are the grid; every line must have the same length.
 * The legend is {@link AsciiMap#WALL}, {@link AsciiMap#FLOOR},
 * {@link AsciiMap#PLAYER_SPAWN}, {@link AsciiMap#YOKAI_SPAWN},
 * {@link AsciiMap#TAVERN_DOOR}, {@link AsciiMap#SHOP} and
 * {@link AsciiMap#SHRINE}.</p>
 *
 * <p>{@link #parse(String, String)} is pure Java and does not touch LibGDX
 * files, so it can be unit-tested without a GL context.</p>
 */
public final class AsciiMapLoader {

    /** Map used by the overworld until procedurally generated worlds are enabled. */
    public static final String DEFAULT_MAP_PATH = "maps/spring_village.txt";

    private static final String METADATA_PREFIX = "!";
    private static final String KEY_LOCATION_ID = "locationId";
    private static final String KEY_SEASON = "season";
    private static final String KEY_TILE_SIZE = "tileSize";
    private static final String KEY_YOKAI = "yokai";

    private static final String DEFAULT_SEASON = "SPRING";
    private static final int DEFAULT_TILE_SIZE = 64;
    private static final int MIN_SIZE = 3;

    private static final Set<Character> LEGEND = Set.of(
        AsciiMap.WALL,
        AsciiMap.FLOOR,
        AsciiMap.PLAYER_SPAWN,
        AsciiMap.YOKAI_SPAWN,
        AsciiMap.TAVERN_DOOR,
        AsciiMap.SHOP,
        AsciiMap.SHRINE);

    private AsciiMapLoader() {
    }

    /**
     * Loads a map from the internal asset folder ({@code assets/}).
     *
     * @throws GdxRuntimeException if the file does not exist
     * @throws IllegalArgumentException if the map is malformed
     */
    public static AsciiMap load(String internalPath) {
        if (internalPath == null || internalPath.isBlank()) {
            throw new IllegalArgumentException("internalPath must not be blank");
        }
        FileHandle file = Gdx.files.internal(internalPath);
        if (!file.exists()) {
            throw new GdxRuntimeException("ASCII map not found: " + internalPath);
        }
        return load(file);
    }

    /**
     * Loads a map from an explicit file handle.
     *
     * @throws GdxRuntimeException if the file does not exist
     * @throws IllegalArgumentException if the map is malformed
     */
    public static AsciiMap load(FileHandle file) {
        if (file == null) {
            throw new IllegalArgumentException("file must not be null");
        }
        if (!file.exists()) {
            throw new GdxRuntimeException("ASCII map not found: " + file.path());
        }
        return parse(file.readString("UTF-8"), file.path());
    }

    /**
     * Parses map text.
     *
     * @param text   complete map text (metadata header followed by the grid)
     * @param source human-readable source used in error messages (path or id)
     * @throws IllegalArgumentException if the map is malformed
     */
    public static AsciiMap parse(String text, String source) {
        if (text == null) {
            throw new IllegalArgumentException("ASCII map text must not be null (source: " + source + ")");
        }
        String mapSource = source == null || source.isBlank() ? "<inline>" : source;

        String[] rawLines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        int lastContentLine = rawLines.length - 1;
        while (lastContentLine >= 0 && rawLines[lastContentLine].isBlank()) {
            lastContentLine--;
        }

        String locationId = null;
        String seasonName = DEFAULT_SEASON;
        int tileSize = DEFAULT_TILE_SIZE;
        List<String> yokaiEncounterIds = new ArrayList<>();
        List<String> gridLines = new ArrayList<>();
        boolean gridStarted = false;

        for (int index = 0; index <= lastContentLine; index++) {
            String line = rawLines[index];
            int lineNumber = index + 1;

            if (!gridStarted && line.isBlank()) {
                continue;
            }

            if (line.startsWith(METADATA_PREFIX)) {
                if (gridStarted) {
                    throw error(mapSource, lineNumber, "metadata line after the tile grid");
                }
                String body = line.substring(METADATA_PREFIX.length()).trim();
                int space = body.indexOf(' ');
                String key = (space < 0 ? body : body.substring(0, space)).trim();
                String value = space < 0 ? "" : body.substring(space + 1).trim();
                switch (key) {
                    case KEY_LOCATION_ID -> locationId = requireValue(mapSource, lineNumber, key, value);
                    case KEY_SEASON -> seasonName = requireValue(mapSource, lineNumber, key, value);
                    case KEY_TILE_SIZE -> tileSize = parseTileSize(mapSource, lineNumber, value);
                    case KEY_YOKAI -> yokaiEncounterIds.add(requireValue(mapSource, lineNumber, key, value));
                    default -> throw error(mapSource, lineNumber, "unknown metadata key '" + key + "'");
                }
                continue;
            }

            if (line.isBlank()) {
                throw error(mapSource, lineNumber, "empty line inside the tile grid");
            }

            gridStarted = true;
            if (gridLines.isEmpty()) {
                validateWidth(mapSource, lineNumber, line);
            } else if (line.length() != gridLines.get(0).length()) {
                throw error(mapSource, lineNumber, "ragged tile row: expected "
                    + gridLines.get(0).length() + " characters but found " + line.length());
            }
            validateGlyphs(mapSource, lineNumber, line);
            gridLines.add(line);
        }

        if (gridLines.isEmpty()) {
            throw new IllegalArgumentException("ASCII map has no tile grid: " + mapSource);
        }

        int height = gridLines.size();
        int width = gridLines.get(0).length();
        if (width < MIN_SIZE || height < MIN_SIZE) {
            throw new IllegalArgumentException("ASCII map must be at least " + MIN_SIZE + "x" + MIN_SIZE
                + " but is " + width + "x" + height + ": " + mapSource);
        }

        char[][] tiles = new char[height][width];
        AsciiMap.Tile playerSpawn = null;
        List<AsciiMap.Tile> yokaiTiles = new ArrayList<>();

        for (int row = 0; row < height; row++) {
            String line = gridLines.get(row);
            for (int col = 0; col < width; col++) {
                char glyph = line.charAt(col);
                tiles[row][col] = glyph;
                if (glyph == AsciiMap.PLAYER_SPAWN) {
                    if (playerSpawn != null) {
                        throw error(mapSource, row + 1, "duplicate player spawn '@' (already at column "
                            + (playerSpawn.col() + 1) + ")");
                    }
                    playerSpawn = new AsciiMap.Tile(col, row);
                } else if (glyph == AsciiMap.YOKAI_SPAWN) {
                    yokaiTiles.add(new AsciiMap.Tile(col, row));
                }
            }
        }

        if (playerSpawn == null) {
            throw new IllegalArgumentException("ASCII map has no player spawn '@': " + mapSource);
        }
        if (!yokaiEncounterIds.isEmpty() && yokaiEncounterIds.size() != yokaiTiles.size()) {
            throw new IllegalArgumentException("ASCII map declares " + yokaiEncounterIds.size()
                + " '!yokai' entries but contains " + yokaiTiles.size() + " 'Y' spawns: " + mapSource);
        }

        List<AsciiMap.YokaiSpawn> yokaiSpawns = new ArrayList<>(yokaiTiles.size());
        for (int i = 0; i < yokaiTiles.size(); i++) {
            AsciiMap.Tile tile = yokaiTiles.get(i);
            String encounterId = i < yokaiEncounterIds.size() ? yokaiEncounterIds.get(i) : null;
            yokaiSpawns.add(new AsciiMap.YokaiSpawn(tile.col(), tile.row(), encounterId));
        }

        if (locationId == null || locationId.isBlank()) {
            locationId = defaultLocationId(mapSource);
        }

        return new AsciiMap(mapSource, locationId, seasonName, tileSize, width, height, tiles,
            playerSpawn, yokaiSpawns);
    }

    private static String requireValue(String source, int lineNumber, String key, String value) {
        if (value.isBlank()) {
            throw error(source, lineNumber, "metadata '" + key + "' needs a value");
        }
        return value;
    }

    private static int parseTileSize(String source, int lineNumber, String value) {
        if (value.isBlank()) {
            throw error(source, lineNumber, "metadata '" + KEY_TILE_SIZE + "' needs a value");
        }
        try {
            int tileSize = Integer.parseInt(value);
            if (tileSize <= 0) {
                throw error(source, lineNumber, "tileSize must be > 0 but was " + tileSize);
            }
            return tileSize;
        } catch (NumberFormatException e) {
            throw error(source, lineNumber, "tileSize is not an integer: '" + value + "'");
        }
    }

    private static void validateWidth(String source, int lineNumber, String line) {
        if (line.length() < MIN_SIZE) {
            throw error(source, lineNumber, "tile row is shorter than " + MIN_SIZE + " characters");
        }
    }

    private static void validateGlyphs(String source, int lineNumber, String line) {
        for (int col = 0; col < line.length(); col++) {
            char glyph = line.charAt(col);
            if (!LEGEND.contains(glyph)) {
                throw error(source, lineNumber, "unknown glyph '" + glyph + "' at column " + (col + 1)
                    + " (allowed: '#', '.', '@', 'Y', 'T', 'S', 'H')");
            }
        }
    }

    private static String defaultLocationId(String source) {
        String name = source;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        return name.isBlank() ? "ascii_map" : name.toLowerCase(Locale.ROOT);
    }

    private static IllegalArgumentException error(String source, int lineNumber, String message) {
        return new IllegalArgumentException("ASCII map " + source + ", line " + lineNumber + ": " + message);
    }
}
