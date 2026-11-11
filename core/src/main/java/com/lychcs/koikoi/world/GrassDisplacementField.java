package com.lychcs.koikoi.world;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Interactive trampling state of the dense meadow layer.
 *
 * <p>Every disturbed glyph cell becomes a small spring. Walking entities push
 * the blades aside ({@code '|'} becomes {@code '/'} or {@code '\'}) and flatten
 * the ground directly below their feet ({@code '_'} or {@code ','}). When the
 * entity has passed, the spring swings the blade back until the cell settles
 * and the original terrain glyph becomes visible again.</p>
 *
 * <p>The field is pure CPU state: it reads the base glyphs from
 * {@link AsciiMap#denseTerrainAt(int, int)}, never changes the map, and holds
 * no LibGDX resources. Only meadow glyphs are affected; walls, canopy and
 * trigger tiles are ignored.</p>
 */
public final class GrassDisplacementField {

    /** Radius (world pixels) in which a moving entity bends the grass. */
    public static final float BEND_RADIUS = 32f;
    /** Radius (world pixels) below the feet that is flattened temporarily. */
    public static final float TRAMPLE_RADIUS = 12f;
    /** Seconds a trampled glyph stays before it regrows. */
    public static final float TRAMPLE_SECONDS = 0.55f;
    /** Entities slower than this (px/s) do not disturb the grass. */
    public static final float MIN_WALK_SPEED = 12f;
    /** Bend value from which the displaced glyph is drawn. */
    public static final float BEND_VISIBLE = 0.12f;

    /** Spring constant of the elastic recovery. */
    private static final float SPRING_STIFFNESS = 90f;
    /** Velocity damping of the elastic recovery (underdamped, one soft overshoot). */
    private static final float SPRING_DAMPING = 9f;
    /** Upper bound for the integration step, keeps the spring stable. */
    private static final float MAX_STEP = 0.05f;

    /** Receives the visible overrides of the disturbed cells. */
    public interface CellConsumer {
        void accept(int col, int row, char glyph, boolean trampled, float strength);
    }

    /**
     * Mutable foot tracker for one walking entity. The screen reuses one
     * instance per entity, so no allocations happen per frame.
     */
    public static final class Walker {
        public float x;
        public float y;
        public float velocityX;
        public float velocityY;

        private float previousX;
        private float previousY;
        private boolean tracked;

        /** Sets the foot position without changing the velocity (player body). */
        public void setFootPosition(float x, float y) {
            this.x = x;
            this.y = y;
        }

        /** Sets the movement vector explicitly (player body velocity). */
        public void setVelocity(float velocityX, float velocityY) {
            this.velocityX = velocityX;
            this.velocityY = velocityY;
        }

        /** Tracks a position and derives the velocity from the frame delta. */
        public void moveTo(float x, float y, float delta) {
            if (tracked && delta > 0f) {
                velocityX = (x - previousX) / delta;
                velocityY = (y - previousY) / delta;
            } else {
                velocityX = 0f;
                velocityY = 0f;
            }
            this.x = x;
            this.y = y;
            previousX = x;
            previousY = y;
            tracked = true;
        }
    }

    /** Spring state of one disturbed glyph cell. */
    private static final class Tuft {
        final int col;
        final int row;
        float bend;
        float bendVelocity;
        float trampleTimer;
        char bendGlyph = '/';
        char trampleGlyph = '_';

        Tuft(int col, int row) {
            this.col = col;
            this.row = row;
        }
    }

    private final AsciiMap map;
    private final float cellSize;
    private final float mapPixelHeight;
    private final int columns;
    private final int rows;
    private final Map<Long, Tuft> tufts = new HashMap<>();

    /**
     * @param map a map carrying a dense terrain layer (see
     *            {@link ProcGenMapSystem#densify(AsciiMap, long)})
     */
    public GrassDisplacementField(AsciiMap map) {
        if (map == null) {
            throw new IllegalArgumentException("map must not be null");
        }
        if (!map.hasDenseTerrain()) {
            throw new IllegalArgumentException("map has no dense terrain layer: " + map.getSource());
        }
        this.map = map;
        this.cellSize = map.getTileSize() / (float) AsciiMap.DETAIL_SCALE;
        this.mapPixelHeight = map.getWorldHeight();
        this.columns = map.getDenseTerrainWidth();
        this.rows = map.getDenseTerrainHeight();
    }

    /** Edge length of one dense glyph cell in world pixels. */
    public float getCellSize() {
        return cellSize;
    }

    /** Number of currently disturbed cells (0 when everything has settled). */
    public int activeCellCount() {
        return tufts.size();
    }

    /** Removes every override immediately (screen teardown / location change). */
    public void clear() {
        tufts.clear();
    }

    /**
     * Advances the springs and applies fresh impulses from the given feet.
     * Call once per frame before {@link #forEach(CellConsumer)}.
     */
    public void update(float delta, List<Walker> walkers) {
        if (delta <= 0f) {
            return;
        }
        float step = Math.min(delta, MAX_STEP);
        integrateSprings(step);

        if (walkers == null || walkers.isEmpty()) {
            return;
        }
        for (int i = 0; i < walkers.size(); i++) {
            Walker walker = walkers.get(i);
            float speed = (float) Math.sqrt(walker.velocityX * walker.velocityX
                + walker.velocityY * walker.velocityY);
            if (speed < MIN_WALK_SPEED) {
                continue;
            }
            applyWalker(walker, speed);
        }
    }

    /** Elastic recovery and trample-timer decay of every active cell. */
    private void integrateSprings(float step) {
        Iterator<Map.Entry<Long, Tuft>> iterator = tufts.entrySet().iterator();
        while (iterator.hasNext()) {
            Tuft tuft = iterator.next().getValue();

            float acceleration = -SPRING_STIFFNESS * tuft.bend
                - SPRING_DAMPING * tuft.bendVelocity;
            tuft.bendVelocity += acceleration * step;
            tuft.bend += tuft.bendVelocity * step;
            if (tuft.bend < 0f) {
                tuft.bend = 0f;
                tuft.bendVelocity *= -0.30f;
            }
            if (tuft.trampleTimer > 0f) {
                tuft.trampleTimer = Math.max(0f, tuft.trampleTimer - step);
            }

            if (tuft.trampleTimer <= 0f && tuft.bend < 0.02f
                && Math.abs(tuft.bendVelocity) < 0.05f) {
                // Fully righted: the static terrain glyph becomes visible again.
                iterator.remove();
            }
        }
    }

    /** Bends and tramples the meadow cells around one moving walker. */
    private void applyWalker(Walker walker, float speed) {
        float directionX = walker.velocityX / speed;

        int minCol = clampColumn((int) Math.floor((walker.x - BEND_RADIUS) / cellSize));
        int maxCol = clampColumn((int) Math.floor((walker.x + BEND_RADIUS) / cellSize));
        int minRow = clampRow((int) Math.floor((mapPixelHeight - walker.y - BEND_RADIUS) / cellSize));
        int maxRow = clampRow((int) Math.floor((mapPixelHeight - walker.y + BEND_RADIUS) / cellSize));

        for (int row = minRow; row <= maxRow; row++) {
            for (int col = minCol; col <= maxCol; col++) {
                char base = map.denseTerrainAt(col, row);
                if (!AsciiMap.isBendableGlyph(base)) {
                    continue;
                }

                float centerX = (col + 0.5f) * cellSize;
                float centerY = mapPixelHeight - (row + 0.5f) * cellSize;
                float dx = centerX - walker.x;
                float dy = centerY - walker.y;
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                if (distance > BEND_RADIUS) {
                    continue;
                }

                long key = key(col, row);
                Tuft tuft = tufts.get(key);
                if (tuft == null) {
                    tuft = new Tuft(col, row);
                    tufts.put(key, tuft);
                }

                // Primary lean follows the walk direction; the radial part
                // makes the blades part around the feet.
                float pushX = directionX * 0.55f;
                if (distance > 0.001f) {
                    pushX += (dx / BEND_RADIUS) * 0.75f;
                }
                tuft.bendGlyph = pushX >= 0f ? '/' : '\\';
                tuft.bend = Math.max(tuft.bend, 1f);
                tuft.bendVelocity = Math.max(tuft.bendVelocity, 0.25f);

                if (distance <= TRAMPLE_RADIUS) {
                    tuft.trampleTimer = Math.max(tuft.trampleTimer, TRAMPLE_SECONDS);
                    tuft.trampleGlyph = ((col + row) & 1) == 0 ? '_' : ',';
                }
            }
        }
    }

    private int clampColumn(int col) {
        return Math.max(0, Math.min(columns - 1, col));
    }

    private int clampRow(int row) {
        return Math.max(0, Math.min(rows - 1, row));
    }

    private static long key(int col, int row) {
        return ((long) row << 32) ^ (col & 0xFFFFFFFFL);
    }

    /**
     * Visits every cell that currently needs a dynamic override. Cells whose
     * spring has settled are skipped (the static glyph shows through).
     */
    public void forEach(CellConsumer consumer) {
        if (consumer == null) {
            return;
        }
        for (Tuft tuft : tufts.values()) {
            if (tuft.trampleTimer > 0f) {
                consumer.accept(tuft.col, tuft.row, tuft.trampleGlyph, true, 1f);
            } else if (tuft.bend >= BEND_VISIBLE) {
                consumer.accept(tuft.col, tuft.row, tuft.bendGlyph, false,
                    Math.min(1f, tuft.bend));
            }
        }
    }
}
