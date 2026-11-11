package com.lychcs.koikoi.combat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Yokai companion target tracking tests. */
class TargetingTest {

    private static final float RANGE = 200f;

    private static Targeting.Target target(int id, float x, float y, boolean alive) {
        return new Targeting.Target(id, x, y, alive);
    }

    private static Targeting.CircleTarget circle(int id, float x, float y, float radius) {
        return new Targeting.CircleTarget(id, x, y, radius, true);
    }

    @Test
    void picksTheNearestLivingTarget() {
        List<Targeting.Target> targets = List.of(
            target(1, 150f, 0f, true),
            target(2, 80f, 0f, true),
            target(3, -120f, 0f, true));

        assertEquals(1, Targeting.nearestIndex(0f, 0f, RANGE, targets));
        assertEquals(80f, Targeting.nearest(0f, 0f, RANGE, targets).x(), 0.0001f);
    }

    @Test
    void ignoresDeadTargets() {
        List<Targeting.Target> targets = List.of(
            target(1, 20f, 0f, false),
            target(2, 120f, 0f, true));

        assertEquals(1, Targeting.nearestIndex(0f, 0f, RANGE, targets));
    }

    @Test
    void ignoresTargetsOutsideTheRange() {
        List<Targeting.Target> targets = List.of(
            target(1, RANGE + 1f, 0f, true),
            target(2, 0f, RANGE + 1f, true));

        assertEquals(-1, Targeting.nearestIndex(0f, 0f, RANGE, targets));
        assertNull(Targeting.nearest(0f, 0f, RANGE, targets));
    }

    @Test
    void exactlyOnTheRangeBoundaryCountsAsInside() {
        List<Targeting.Target> targets = List.of(target(1, RANGE, 0f, true));

        assertEquals(0, Targeting.nearestIndex(0f, 0f, RANGE, targets));
    }

    @Test
    void equalDistanceTiesBreakOnTheLowerId() {
        List<Targeting.Target> targets = List.of(
            target(9, 50f, 0f, true),
            target(4, -50f, 0f, true));

        assertEquals(1, Targeting.nearestIndex(0f, 0f, RANGE, targets));
    }

    @Test
    void emptyOrInvalidInputsReturnNoTarget() {
        assertEquals(-1, Targeting.nearestIndex(0f, 0f, RANGE, List.of()));
        assertEquals(-1, Targeting.nearestIndex(0f, 0f, 0f, List.of(target(1, 1f, 1f, true))));
        assertEquals(-1, Targeting.nearestIndex(0f, 0f, RANGE, null));
    }

    @Test
    void pickFindsTheCircleUnderTheClick() {
        List<Targeting.CircleTarget> targets = List.of(
            circle(1, 100f, 0f, 30f),
            circle(2, -100f, 0f, 30f));

        assertEquals(0, Targeting.pickIndex(110f, 10f, 0f, targets));
        assertEquals(1, Targeting.pickIndex(-100f, 0f, 0f, targets));
        assertEquals(-1, Targeting.pickIndex(0f, 0f, 0f, targets));
    }

    @Test
    void pickUsesTheCursorToleranceAsExtraReach() {
        List<Targeting.CircleTarget> targets = List.of(circle(1, 0f, 0f, 10f));

        assertEquals(-1, Targeting.pickIndex(15f, 0f, 0f, targets));
        // Exactly radius + tolerance still counts as a hit.
        assertEquals(0, Targeting.pickIndex(15f, 0f, 5f, targets));
    }

    @Test
    void pickPrefersTheNearestCentreAndBreaksTiesByLowerId() {
        List<Targeting.CircleTarget> overlapping = List.of(
            circle(9, 30f, 0f, 50f),
            circle(4, 10f, 0f, 50f));
        assertEquals(0, Targeting.pickIndex(25f, 0f, 0f, overlapping));

        List<Targeting.CircleTarget> tied = List.of(
            circle(9, 10f, 0f, 50f),
            circle(4, -10f, 0f, 50f));
        assertEquals(1, Targeting.pickIndex(0f, 0f, 0f, tied));
    }

    @Test
    void pickIgnoresDeadTargetsAndInvalidInput() {
        List<Targeting.CircleTarget> targets = List.of(
            new Targeting.CircleTarget(1, 0f, 0f, 40f, false),
            circle(2, 200f, 0f, 10f));

        assertEquals(-1, Targeting.pickIndex(0f, 0f, 0f, targets));
        assertEquals(-1, Targeting.pickIndex(0f, 0f, 10f, List.of()));
        assertEquals(-1, Targeting.pickIndex(0f, 0f, 10f, null));
    }

    @Test
    void circleRadiusIsNeverNegative() {
        assertEquals(0f,
            new Targeting.CircleTarget(1, 0f, 0f, -20f, true).radius(), 0.0001f);
    }
}
