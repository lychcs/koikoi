package com.lychcs.koikoi.world;

import com.lychcs.koikoi.TestAssetFiles;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies that collision runs and visible wall cells are the same layout. */
class WallLayoutTest {

    private static AsciiMap village() throws IOException {
        return AsciiMapLoader.parse(
            TestAssetFiles.read("maps/spring_village.txt"), "spring_village");
    }

    @Test
    void everySolidCellIsCoveredExactlyOnce() throws IOException {
        AsciiMap map = village();
        int solidCells = 0;
        for (int row = 0; row < map.getHeight(); row++) {
            for (int col = 0; col < map.getWidth(); col++) {
                if (map.glyphAt(col, row) == AsciiMap.WALL) {
                    solidCells++;
                }
            }
        }

        Set<Long> covered = new HashSet<>();
        for (WallLayout.Run run : WallLayout.scan(map)) {
            for (int offset = 0; offset < run.length(); offset++) {
                long key = ((long) run.row() << 32) ^ (run.col() + offset);
                assertTrue(covered.add(key), "collision runs must not overlap");
            }
        }

        assertEquals(solidCells, covered.size(),
            "every solid map cell needs exactly one collision run");
    }

    @Test
    void boundaryRunsFollowTheMapBorder() throws IOException {
        AsciiMap map = village();
        List<WallLayout.Run> runs = WallLayout.scan(map);

        assertFalse(runs.isEmpty());
        for (WallLayout.Run run : runs) {
            boolean expected = run.row() == 0 || run.row() == map.getHeight() - 1
                || run.col() == 0 || run.col() + run.length() == map.getWidth();
            assertEquals(expected, run.boundary(),
                "run " + run.col() + "," + run.row() + " has the wrong boundary flag");
        }
        assertTrue(runs.stream().anyMatch(WallLayout.Run::boundary));
        assertTrue(runs.stream().anyMatch(run -> !run.boundary()),
            "the village has interior obstacle blocks that need stone walls");
    }

    @Test
    void runRectanglesMatchTheAsciiTiles() throws IOException {
        AsciiMap map = village();
        float tile = map.getTileSize();

        for (WallLayout.Run run : WallLayout.scan(map)) {
            assertEquals(run.col() * tile, run.x(), 0.001f);
            assertEquals(map.tileBottomY(run.row()), run.y(), 0.001f);
            assertEquals(run.length() * tile, run.width(), 0.001f);
            assertEquals(tile, run.height(), 0.001f);
        }
    }

    @Test
    void noRunCoversAWalkableCell() throws IOException {
        AsciiMap map = village();

        for (WallLayout.Run run : WallLayout.scan(map)) {
            for (int offset = 0; offset < run.length(); offset++) {
                assertFalse(map.isWalkable(run.col() + offset, run.row()),
                    "collision runs must only cover solid cells");
            }
        }
    }
}
