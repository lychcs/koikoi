package com.lychcs.koikoi.render;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Validates FPS mouse look, camera-relative WASD and the kiting dash math. */
class FirstPersonCameraControllerTest {

    private FirstPersonCameraController controller;
    private final Vector2 planar = new Vector2();
    private final Vector3 forward = new Vector3();

    @BeforeEach
    void setUp() {
        controller = new FirstPersonCameraController();
    }

    @Test
    void defaultViewLooksNorthInTheAsciiWorld() {
        controller.getForward(forward);
        assertEquals(0f, forward.x, 0.0001f);
        assertEquals(0f, forward.y, 0.0001f);
        assertEquals(-1f, forward.z, 0.0001f);

        controller.getGroundForward(planar);
        assertEquals(0f, planar.x, 0.0001f);
        assertEquals(1f, planar.y, 0.0001f);
    }

    @Test
    void mouseLookTurnsTheViewHorizontally() {
        controller.applyLook(100f, 0f);

        assertEquals(-100f * FirstPersonCameraController.DEFAULT_SENSITIVITY,
            controller.getYawDegrees(), 0.0001f);
        assertEquals(0f, controller.getPitchDegrees(), 0.0001f);
    }

    @Test
    void movingTheMouseUpLooksUp() {
        controller.applyLook(0f, -100f);

        assertTrue(controller.getPitchDegrees() > 0f,
            "negative screen Y delta (mouse up) must raise the pitch");
    }

    @Test
    void pitchIsClampedToAvoidAFlippedHorizon() {
        controller.applyLook(0f, -100000f);
        assertEquals(FirstPersonCameraController.MAX_PITCH_DEGREES,
            controller.getPitchDegrees(), 0.0001f);

        controller.applyLook(0f, 100000f);
        assertEquals(-FirstPersonCameraController.MAX_PITCH_DEGREES,
            controller.getPitchDegrees(), 0.0001f);
    }

    @Test
    void yawNinetyDegreesLooksWest() {
        controller.setYawDegrees(90f);
        controller.getForward(forward);
        assertEquals(-1f, forward.x, 0.0001f);
        assertEquals(0f, forward.z, 0.0001f);

        controller.getGroundForward(planar);
        assertEquals(-1f, planar.x, 0.0001f);
        assertEquals(0f, planar.y, 0.0001f);

        controller.getGroundRight(planar);
        assertEquals(0f, planar.x, 0.0001f);
        assertEquals(1f, planar.y, 0.0001f);
    }

    @Test
    void wasdMovesRelativeToTheView() {
        // Looking west: W must walk west and D must walk north.
        controller.setYawDegrees(90f);

        controller.moveDirection(0f, 1f, planar);
        assertEquals(-1f, planar.x, 0.0001f);
        assertEquals(0f, planar.y, 0.0001f);

        controller.moveDirection(1f, 0f, planar);
        assertEquals(0f, planar.x, 0.0001f);
        assertEquals(1f, planar.y, 0.0001f);
    }

    @Test
    void sIsABackpedalOppositeTheView() {
        controller.setYawDegrees(90f);
        controller.moveDirection(0f, -1f, planar);

        assertEquals(1f, planar.x, 0.0001f, "S must walk backwards (east while facing west)");
        assertEquals(0f, planar.y, 0.0001f);
    }

    @Test
    void diagonalWasdIsNormalised() {
        controller.moveDirection(1f, 1f, planar);

        assertEquals(1f, planar.len(), 0.0001f);
        assertTrue(planar.x > 0f && planar.y > 0f);
    }

    @Test
    void oppositeKeysCancelOut() {
        controller.moveDirection(1f - 1f, 1f - 1f, planar);

        assertEquals(0f, planar.len2(), 0.0001f);
    }

    @Test
    void degreesWrapIntoTheHalfOpenRange() {
        assertEquals(0f, FirstPersonCameraController.wrapDegrees(360f), 0.0001f);
        assertEquals(-170f, FirstPersonCameraController.wrapDegrees(190f), 0.0001f);
        assertEquals(170f, FirstPersonCameraController.wrapDegrees(-190f), 0.0001f);
        assertEquals(180f, FirstPersonCameraController.wrapDegrees(540f), 0.0001f);
    }

    @Test
    void resetReturnsToTheDefaultView() {
        controller.applyLook(250f, -80f);
        controller.reset();

        assertEquals(0f, controller.getYawDegrees(), 0.0001f);
        assertEquals(0f, controller.getPitchDegrees(), 0.0001f);
    }
}
