package com.lychcs.koikoi.render;

import com.lychcs.koikoi.world.AsciiMap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Breadth-first path planner over the walkable tiles of an ASCII map.
 *
 * <p>The overworld uses it to grow dirt paths between the player spawn and the
 * tavern, shop and shrine. It only reads {@link AsciiMap#isWalkable(int, int)},
 * so the result always follows the existing collision layout instead of cutting
 * through walls. Pure Java: no GL, no screen, unit-testable.</p>
 */
public final class PathPlanner {

    /** 4-neighbourhood keeps paths orthogonal and easy to dress with lanterns. */
    private static final int[][] NEIGHBOURS = {
        {1, 0}, {-1, 0}, {0, 1}, {0, -1}
    };

    private PathPlanner() {
    }

    /**
     * Shortest walkable tile path from {@code start} to {@code goal}.
     *
     * @return the path including both endpoints, or an empty list when the goal
     *         is unreachable or outside the map
     */
    public static List<AsciiMap.Tile> findPath(AsciiMap map, AsciiMap.Tile start,
                                               AsciiMap.Tile goal) {
        if (map == null || start == null || goal == null) {
            return List.of();
        }
        int width = map.getWidth();
        int height = map.getHeight();
        if (!map.contains(start.col(), start.row())
            || !map.contains(goal.col(), goal.row())
            || !map.isWalkable(start.col(), start.row())
            || !map.isWalkable(goal.col(), goal.row())) {
            return List.of();
        }

        int startIndex = start.row() * width + start.col();
        int goalIndex = goal.row() * width + goal.col();
        if (startIndex == goalIndex) {
            return List.of(start);
        }

        int[] previous = new int[width * height];
        java.util.Arrays.fill(previous, -2);
        previous[startIndex] = -1;

        Deque<Integer> queue = new ArrayDeque<>();
        queue.add(startIndex);
        boolean found = false;

        while (!queue.isEmpty() && !found) {
            int current = queue.poll();
            int col = current % width;
            int row = current / width;

            for (int[] offset : NEIGHBOURS) {
                int nextCol = col + offset[0];
                int nextRow = row + offset[1];
                if (!map.isWalkable(nextCol, nextRow)) {
                    continue;
                }
                int nextIndex = nextRow * width + nextCol;
                if (previous[nextIndex] != -2) {
                    continue;
                }
                previous[nextIndex] = current;
                if (nextIndex == goalIndex) {
                    found = true;
                    break;
                }
                queue.add(nextIndex);
            }
        }

        if (!found) {
            return List.of();
        }

        List<AsciiMap.Tile> reversed = new ArrayList<>();
        int cursor = goalIndex;
        while (cursor != -1) {
            reversed.add(new AsciiMap.Tile(cursor % width, cursor / width));
            cursor = previous[cursor];
        }
        Collections.reverse(reversed);
        return reversed;
    }

    /** Shortest path to whichever of {@code goals} is reachable first. */
    public static List<AsciiMap.Tile> findPathToAny(AsciiMap map, AsciiMap.Tile start,
                                                    List<AsciiMap.Tile> goals) {
        if (goals == null || goals.isEmpty()) {
            return List.of();
        }
        List<AsciiMap.Tile> best = List.of();
        for (AsciiMap.Tile goal : goals) {
            List<AsciiMap.Tile> candidate = findPath(map, start, goal);
            if (!candidate.isEmpty()
                && (best.isEmpty() || candidate.size() < best.size())) {
                best = candidate;
            }
        }
        return best;
    }
}
