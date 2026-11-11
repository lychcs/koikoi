package com.lychcs.koikoi.combat;

import java.util.List;

/**
 * Pure target-query helpers shared by the ECS systems and the screen.
 *
 * <p>{@link #nearestIndex} powers Yokai companion auto-targeting, while
 * {@link #pickIndex} resolves the hostile under a right-click cursor position.
 * Both work on lightweight snapshots, so they can be tested without an ECS.
 * Dead targets are ignored; ties are broken deterministically by the smaller
 * target id.</p>
 */
public final class Targeting {

    /** Immutable target snapshot for {@link #nearestIndex(float, float, float, List)}. */
    public record Target(int id, float x, float y, boolean alive) {
    }

    /** Immutable circular target snapshot for mouse click picking. */
    public record CircleTarget(int id, float x, float y, float radius, boolean alive) {
        public CircleTarget {
            radius = Math.max(0f, radius);
        }
    }

    private Targeting() {
    }

    /**
     * Index of the living circular target under a world-space click point, or
     * {@code -1} when the click hits nothing.
     *
     * <p>A click hits a target when it lies inside the target's radius plus
     * {@code pickTolerance}. Overlapping targets resolve to the one with the
     * nearest centre; ties are broken deterministically by the smaller id, so
     * hovering the exact centre of two stacked hostiles is stable.</p>
     */
    public static int pickIndex(float clickX, float clickY, float pickTolerance,
                                List<CircleTarget> targets) {
        if (targets == null || targets.isEmpty()) {
            return -1;
        }

        float tolerance = Math.max(0f, pickTolerance);
        int bestIndex = -1;
        float bestDistanceSquared = Float.MAX_VALUE;
        int bestId = Integer.MAX_VALUE;

        for (int index = 0; index < targets.size(); index++) {
            CircleTarget target = targets.get(index);
            if (!target.alive()) {
                continue;
            }

            float dx = clickX - target.x();
            float dy = clickY - target.y();
            float distanceSquared = dx * dx + dy * dy;
            float reach = target.radius() + tolerance;

            if (distanceSquared > reach * reach + SealGeometry.BOUNDARY_EPSILON) {
                continue;
            }

            boolean closer = distanceSquared < bestDistanceSquared - SealGeometry.BOUNDARY_EPSILON;
            boolean equalButLowerId = Math.abs(distanceSquared - bestDistanceSquared)
                <= SealGeometry.BOUNDARY_EPSILON && target.id() < bestId;

            if (bestIndex < 0 || closer || equalButLowerId) {
                bestIndex = index;
                bestDistanceSquared = distanceSquared;
                bestId = target.id();
            }
        }

        return bestIndex;
    }

    /**
     * Index of the nearest living target inside {@code range}, or {@code -1}.
     * A target exactly on the range boundary counts as inside.
     */
    public static int nearestIndex(float originX, float originY, float range, List<Target> targets) {
        if (targets == null || targets.isEmpty() || range <= 0f) {
            return -1;
        }

        float rangeSquared = range * range + SealGeometry.BOUNDARY_EPSILON;
        int bestIndex = -1;
        float bestDistanceSquared = Float.MAX_VALUE;
        int bestId = Integer.MAX_VALUE;

        for (int index = 0; index < targets.size(); index++) {
            Target target = targets.get(index);
            if (!target.alive()) {
                continue;
            }

            float dx = target.x() - originX;
            float dy = target.y() - originY;
            float distanceSquared = dx * dx + dy * dy;

            if (distanceSquared > rangeSquared) {
                continue;
            }

            boolean closer = distanceSquared < bestDistanceSquared - SealGeometry.BOUNDARY_EPSILON;
            boolean equalButLowerId = Math.abs(distanceSquared - bestDistanceSquared)
                <= SealGeometry.BOUNDARY_EPSILON && target.id() < bestId;

            if (bestIndex < 0 || closer || equalButLowerId) {
                bestIndex = index;
                bestDistanceSquared = distanceSquared;
                bestId = target.id();
            }
        }

        return bestIndex;
    }

    /** Nearest living target inside {@code range}, or {@code null}. */
    public static Target nearest(float originX, float originY, float range, List<Target> targets) {
        int index = nearestIndex(originX, originY, range, targets);
        return index < 0 ? null : targets.get(index);
    }
}
