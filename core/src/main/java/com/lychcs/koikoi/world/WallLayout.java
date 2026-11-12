package com.lychcs.koikoi.world;

import java.util.ArrayList;
import java.util.List;

/**
 * Scans the solid {@code #} cells of an ASCII map into collision runs.
 *
 * <p>This is the single source of truth shared by the Box2D physics loading and
 * the procedural scenery: every run becomes one static collision body, and the
 * same wall cells become visible bamboo fences (map boundary) or mossy stone
 * outcrops (interior obstacles). Physics and visuals therefore always line up -
 * no more invisible ghost walls.</p>
 *
 * <p>Pure CPU data, no GL or physics dependency, so the layout can be unit
 * tested in isolation.</p>
 */
public final class WallLayout {

    /**
     * One horizontal run of solid cells.
     *
     * @param col      first column of the run
     * @param row      map row of the run
     * @param length   number of cells in the run
     * @param boundary true for a run on the map border (fence), false for an
     *                 interior obstacle (stone wall)
     * @param x        world-pixel X of the run's left edge
     * @param y        world-pixel Y of the run's bottom edge
     * @param width    run width in world pixels
     * @param height   run height in world pixels (one tile)
     */
    public record Run(int col, int row, int length, boolean boundary,
                      float x, float y, float width, float height) {
    }

    private WallLayout() {
    }

    /** All solid runs of the map, row by row. */
    public static List<Run> scan(AsciiMap map) {
        List<Run> runs = new ArrayList<>();
        if (map == null) {
            return runs;
        }

        int tileSize = map.getTileSize();
        for (int row = 0; row < map.getHeight(); row++) {
            int col = 0;
            while (col < map.getWidth()) {
                if (map.glyphAt(col, row) != AsciiMap.WALL) {
                    col++;
                    continue;
                }

                int runStart = col;
                while (col < map.getWidth() && map.glyphAt(col, row) == AsciiMap.WALL) {
                    col++;
                }
                int runLength = col - runStart;
                boolean boundary = row == 0 || row == map.getHeight() - 1
                    || runStart == 0 || col == map.getWidth();

                runs.add(new Run(runStart, row, runLength, boundary,
                    runStart * (float) tileSize,
                    map.tileBottomY(row),
                    runLength * (float) tileSize,
                    tileSize));
            }
        }
        return runs;
    }
}
