package com.lychcs.koikoi.render;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates the over-the-shoulder rig: mouse look, camera-relative WASD and
 * backpedal dash, the shoulder boom with its terrain/scenery clamps, and the
 * crosshair ray that names the 3D world point under the {@code +}.
 */
class ThirdPersonCameraControllerTest {

    private static final ThirdPersonCameraController.TerrainSampler FLAT_GROUND =
        (x, z) -> 0f;
    private static final ThirdPersonCameraController.ObstacleQuery NO_OBSTACLES =
        (x, y, z) -> false;

    private ThirdPersonCameraController controller;
    private final Vector2 planar = new Vector2();
    private final Vector3 forward = new Vector3();
    private final Vector3 target = new Vector3();
    private final Vector3 camera = new Vector3();
    private final Vector3 aimPoint = new Vector3();

    @BeforeEach
    void setUp() {
        controller = new ThirdPersonCameraController();
    }

    // ------------------------------------------------------------------
    // Look angles and camera-relative movement
    // ------------------------------------------------------------------

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

        assertEquals(-100f * ThirdPersonCameraController.DEFAULT_SENSITIVITY,
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
        assertEquals(ThirdPersonCameraController.MAX_PITCH_DEGREES,
            controller.getPitchDegrees(), 0.0001f);

        controller.applyLook(0f, 100000f);
        assertEquals(-ThirdPersonCameraController.MAX_PITCH_DEGREES,
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

    // ------------------------------------------------------------------
    // Over-the-shoulder rig
    // ------------------------------------------------------------------

    @Test
    void targetSitsAtShoulderHeightAboveTheFeet() {
        controller.getTarget(2f, 0.4f, -3f, target);

        assertEquals(2f, target.x, 0.0001f);
        assertEquals(0.4f + ThirdPersonCameraController.DEFAULT_HEIGHT_OFFSET,
            target.y, 0.0001f);
        assertEquals(-3f, target.z, 0.0001f);
    }

    @Test
    void defaultRigSitsBehindAndRightOfTheShoulder() {
        target.set(0f, 1.8f, 0f);
        controller.computeCameraPosition(target, FLAT_GROUND, NO_OBSTACLES, camera);

        // Yaw 0 looks north (-z), so the boom extends south (+z) and onto the
        // right shoulder (+x).
        assertEquals(ThirdPersonCameraController.DEFAULT_SHOULDER_OFFSET,
            camera.x, 0.0001f);
        assertEquals(1.8f, camera.y, 0.0001f);
        float boom = camera.dst(target);
        assertTrue(boom >= 4.5f && boom <= 5.5f, "boom length was " + boom);
        assertTrue(camera.z > 4.4f, "camera must sit behind the player");
    }

    @Test
    void crosshairLinePassesRightOfTheAvatar() {
        target.set(0f, 1.8f, 0f);
        controller.computeCameraPosition(target, FLAT_GROUND, NO_OBSTACLES, camera);
        controller.getForward(forward);

        // Camera and boom share the view direction: at the player's depth the
        // centre ray is exactly the shoulder offset to the right of the pivot.
        float rayX = camera.x + forward.x * (camera.z / -forward.z);
        assertEquals(ThirdPersonCameraController.DEFAULT_SHOULDER_OFFSET, rayX, 0.02f);
    }

    @Test
    void groundRisingAboveTheShoulderShortensTheBoom() {
        ThirdPersonCameraController.TerrainSampler hill = (x, z) -> z > 2f ? 4f : 0f;
        target.set(0f, 1.8f, 0f);

        controller.computeCameraPosition(target, hill, NO_OBSTACLES, camera);

        assertTrue(camera.z < 2.2f, "boom must stop before the hill, z=" + camera.z);
        assertTrue(camera.dst(target) < ThirdPersonCameraController.DEFAULT_DISTANCE,
            "the boom has to be shorter than the default orbit");
    }

    @Test
    void scenerySolidsShortenTheBoom() {
        ThirdPersonCameraController.ObstacleQuery wall = (x, y, z) -> z > 2f;
        target.set(0f, 1.8f, 0f);

        controller.computeCameraPosition(target, FLAT_GROUND, wall, camera);

        assertTrue(camera.z <= 2.05f, "boom must stop at the solid, z=" + camera.z);
    }

    @Test
    void boomNeverCollapsesIntoThePivot() {
        ThirdPersonCameraController.ObstacleQuery blockedEverywhere = (x, y, z) -> true;
        target.set(0f, 1.8f, 0f);

        controller.computeCameraPosition(target, FLAT_GROUND, blockedEverywhere, camera);

        float boom = camera.dst(target);
        assertTrue(boom >= ThirdPersonCameraController.MIN_DISTANCE - 0.01f,
            "minimum boom was " + boom);
    }

    @Test
    void upwardLookLiftsTheCameraAboveTheTerrain() {
        controller.setPitchDegrees(50f);
        target.set(0f, 1.8f, 0f);

        controller.computeCameraPosition(target, FLAT_GROUND, NO_OBSTACLES, camera);

        assertTrue(camera.y >= ThirdPersonCameraController.TERRAIN_CLEARANCE - 0.001f,
            "camera must not sink into the terrain, y=" + camera.y);
    }

    // ------------------------------------------------------------------
    // Crosshair ray
    // ------------------------------------------------------------------

    @Test
    void aimRayLandsOnTheTerrainUnderTheCrosshair() {
        Vector3 origin = new Vector3(0f, 2f, 0f);
        Vector3 direction = new Vector3(0f, -0.5f, -0.8660254f);

        boolean hit = controller.resolveAimPoint(origin, direction, FLAT_GROUND,
            NO_OBSTACLES, 40f, aimPoint);

        assertTrue(hit, "a downward crosshair must hit the ground");
        assertEquals(0f, aimPoint.x, 0.001f);
        assertEquals(0f, aimPoint.y, 0.01f);
        assertEquals(-3.4641f, aimPoint.z, 0.05f);
    }

    @Test
    void aimRayStopsAtSceneryBeforeTheGround() {
        ThirdPersonCameraController.ObstacleQuery wall = (x, y, z) -> z < -2f;

        boolean hit = controller.resolveAimPoint(new Vector3(0f, 2f, 0f),
            new Vector3(0f, 0f, -1f), FLAT_GROUND, wall, 40f, aimPoint);

        assertTrue(hit, "a level ray into a wall must report a hit");
        assertEquals(-2f, aimPoint.z, 0.02f, "the crosshair must stop at the wall face");
    }

    @Test
    void skyRayReturnsTheFarEndPoint() {
        boolean hit = controller.resolveAimPoint(new Vector3(0f, 2f, 0f),
            new Vector3(0f, 0.70710678f, -0.70710678f), FLAT_GROUND,
            NO_OBSTACLES, 10f, aimPoint);

        assertFalse(hit, "an upward crosshair must not report a ground hit");
        assertEquals(2f + 10f * 0.70710678f, aimPoint.y, 0.01f);
        assertEquals(-10f * 0.70710678f, aimPoint.z, 0.01f);
    }

    @Test
    void degreesWrapIntoTheHalfOpenRange() {
        assertEquals(0f, ThirdPersonCameraController.wrapDegrees(360f), 0.0001f);
        assertEquals(-170f, ThirdPersonCameraController.wrapDegrees(190f), 0.0001f);
        assertEquals(170f, ThirdPersonCameraController.wrapDegrees(-190f), 0.0001f);
        assertEquals(180f, ThirdPersonCameraController.wrapDegrees(540f), 0.0001f);
    }

    @Test
    void resetReturnsToTheDefaultView() {
        controller.applyLook(250f, -80f);
        controller.reset();

        assertEquals(0f, controller.getYawDegrees(), 0.0001f);
        assertEquals(0f, controller.getPitchDegrees(), 0.0001f);
    }
}
