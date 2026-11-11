package com.lychcs.koikoi.world;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Validates the interactive grass springs and glyph overrides. */
class GrassDisplacementFieldTest {

    private static final String MAP_TEXT = """
        !locationId grass_test
        !tileSize 64
        ##########
        #........#
        #..@.....#
        #........#
        ##########
        """;

    @Test
    void movingWalkerBendsBladesAndTramplesTheGround() {
        AsciiMap map = mapWithDenseGlyph(';');
        GrassDisplacementField field = new GrassDisplacementField(map);
        GrassDisplacementField.Walker walker = walkerAt(352f, 160f, 120f, 0f);
        int footCol = (int) Math.floor(352f / field.getCellSize());
        int footRow = (int) Math.floor((map.getWorldHeight() - 160f) / field.getCellSize());

        field.update(1f / 60f, List.of(walker));
        Map<Long, CellView> cells = collect(field);

        assertFalse(cells.isEmpty(), "walking through meadow must disturb grass");
        assertTrue(cells.values().stream().anyMatch(cell -> cell.glyph() == '/'),
            "rightward motion must bend the blades to '/'");
        assertTrue(cells.values().stream().anyMatch(CellView::trampled),
            "the ground below the feet must be flattened");
        for (CellView cell : cells.values()) {
            if (cell.trampled()) {
                assertTrue(cell.glyph() == '_' || cell.glyph() == ',',
                    "trampled cells must use '_' or ',' but had '" + cell.glyph() + "'");
            }
        }

        CellView underFoot = cells.get(key(footCol, footRow));
        assertTrue(underFoot != null && underFoot.trampled(),
            "the cell directly below the feet must be trampled");
    }

    @Test
    void leftwardMotionBendsTheOtherWay() {
        AsciiMap map = mapWithDenseGlyph(';');
        GrassDisplacementField field = new GrassDisplacementField(map);
        GrassDisplacementField.Walker walker = walkerAt(352f, 160f, -120f, 0f);

        field.update(1f / 60f, List.of(walker));
        Map<Long, CellView> cells = collect(field);

        assertTrue(cells.values().stream().anyMatch(cell -> cell.glyph() == '\\'),
            "leftward motion must bend the blades to '\\'");
    }

    @Test
    void grassSpringsBackAfterTheWalkerLeaves() {
        AsciiMap map = mapWithDenseGlyph(';');
        GrassDisplacementField field = new GrassDisplacementField(map);
        GrassDisplacementField.Walker walker = walkerAt(352f, 160f, 120f, 0f);

        field.update(1f / 60f, List.of(walker));
        assertTrue(field.activeCellCount() > 0);

        for (int frame = 0; frame < 300; frame++) {
            field.update(1f / 60f, List.of());
        }

        assertEquals(0, field.activeCellCount(), "all blades must settle back");
        assertEquals(0, collect(field).size(), "settled cells must show the static glyph again");
        int footCol = (int) Math.floor(352f / field.getCellSize());
        int footRow = (int) Math.floor((map.getWorldHeight() - 160f) / field.getCellSize());
        assertEquals(';', map.denseTerrainAt(footCol, footRow), "the base map must never be modified");
    }

    @Test
    void canopyAndRockCellsAreNotDisplaced() {
        AsciiMap map = mapWithDenseGlyph('*');
        GrassDisplacementField field = new GrassDisplacementField(map);
        GrassDisplacementField.Walker walker = walkerAt(352f, 160f, 120f, 0f);

        for (int frame = 0; frame < 20; frame++) {
            field.update(1f / 60f, List.of(walker));
        }

        assertEquals(0, field.activeCellCount(), "non-blade glyphs must stay untouched");
    }

    @Test
    void standingStillDoesNotDisturbGrass() {
        AsciiMap map = mapWithDenseGlyph(';');
        GrassDisplacementField field = new GrassDisplacementField(map);
        GrassDisplacementField.Walker walker = walkerAt(352f, 160f, 0f, 0f);

        field.update(1f / 60f, List.of(walker));

        assertEquals(0, field.activeCellCount(), "idle feet must not bend the grass");
    }

    @Test
    void fieldRequiresADenseTerrainLayer() {
        AsciiMap map = AsciiMapLoader.parse(MAP_TEXT, "grass-test");

        assertThrows(IllegalArgumentException.class, () -> new GrassDisplacementField(map));
        assertThrows(IllegalArgumentException.class, () -> new GrassDisplacementField(null));
    }

    private static AsciiMap mapWithDenseGlyph(char denseGlyph) {
        AsciiMap map = AsciiMapLoader.parse(MAP_TEXT, "grass-test");
        char[][] dense = new char[map.getHeight() * AsciiMap.DETAIL_SCALE]
            [map.getWidth() * AsciiMap.DETAIL_SCALE];
        for (char[] row : dense) {
            Arrays.fill(row, denseGlyph);
        }
        return map.withDenseTerrain(dense);
    }

    private static GrassDisplacementField.Walker walkerAt(float x, float y, float vx, float vy) {
        GrassDisplacementField.Walker walker = new GrassDisplacementField.Walker();
        walker.setFootPosition(x, y);
        walker.setVelocity(vx, vy);
        return walker;
    }

    private static Map<Long, CellView> collect(GrassDisplacementField field) {
        Map<Long, CellView> cells = new HashMap<>();
        field.forEach((col, row, glyph, trampled, strength) ->
            cells.put(key(col, row), new CellView(col, row, glyph, trampled, strength)));
        return cells;
    }

    private static long key(int col, int row) {
        return ((long) row << 32) ^ (col & 0xFFFFFFFFL);
    }

    private record CellView(int col, int row, char glyph, boolean trampled, float strength) {
    }
}
