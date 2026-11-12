package com.lychcs.koikoi.render;

import com.lychcs.koikoi.world.AsciiMap;
import com.lychcs.koikoi.world.AsciiMapLoader;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Validates the BFS dirt-path planner over the ASCII map. */
class PathPlannerTest {

    private static AsciiMap map() {
        String text = String.join("\n",
            "!tileSize 64",
            "#######",
            "#@....#",
            "#.###.#",
            "#...H.#",
            "#######");
        return AsciiMapLoader.parse(text, "path_test_map");
    }

    @Test
    void findsAShortestWalkablePathToTheShrine() {
        AsciiMap map = map();
        AsciiMap.Tile start = map.getPlayerSpawn();
        AsciiMap.Tile goal = map.tilesOf(AsciiMap.SHRINE).get(0);

        List<AsciiMap.Tile> path = PathPlanner.findPath(map, start, goal);

        assertFalse(path.isEmpty());
        assertEquals(start, path.get(0));
        assertEquals(goal, path.get(path.size() - 1));
        for (AsciiMap.Tile tile : path) {
            assertTrue(map.isWalkable(tile.col(), tile.row()),
                "paths must never cut through walls: " + tile);
        }
        for (int i = 1; i < path.size(); i++) {
            AsciiMap.Tile previous = path.get(i - 1);
            AsciiMap.Tile current = path.get(i);
            int distance = Math.abs(previous.col() - current.col())
                + Math.abs(previous.row() - current.row());
            assertEquals(1, distance, "path steps must be 4-neighbour moves");
        }
    }

    @Test
    void shortestPathIsSixOrthogonalMoves() {
        AsciiMap map = map();
        List<AsciiMap.Tile> path = PathPlanner.findPath(map, map.getPlayerSpawn(),
            map.tilesOf(AsciiMap.SHRINE).get(0));

        // Spawn (1,1) -> (1,2) -> (1,3) -> (2,3) -> (3,3) -> shrine (4,3)
        // is the shortest route around the inner wall block.
        assertEquals(6, path.size());
    }

    @Test
    void wallGoalsAreUnreachable() {
        AsciiMap map = map();

        assertTrue(PathPlanner.findPath(map, map.getPlayerSpawn(),
            new AsciiMap.Tile(0, 0)).isEmpty());
        assertTrue(PathPlanner.findPath(map, map.getPlayerSpawn(),
            new AsciiMap.Tile(99, 99)).isEmpty());
    }

    @Test
    void findPathToAnyChoosesTheClosestOfSeveralGoals() {
        AsciiMap map = map();
        List<AsciiMap.Tile> goals = List.of(
            new AsciiMap.Tile(5, 3),
            new AsciiMap.Tile(2, 1));

        List<AsciiMap.Tile> path = PathPlanner.findPathToAny(map, map.getPlayerSpawn(), goals);

        assertEquals(new AsciiMap.Tile(2, 1), path.get(path.size() - 1));
        assertEquals(2, path.size());
    }

    @Test
    void nullArgumentsAreRejectedGracefully() {
        assertTrue(PathPlanner.findPath(null, null, null).isEmpty());
        assertTrue(PathPlanner.findPathToAny(map(), map().getPlayerSpawn(), null).isEmpty());
    }
}
