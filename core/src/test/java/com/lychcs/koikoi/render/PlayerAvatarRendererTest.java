package com.lychcs.koikoi.render;

import com.badlogic.gdx.math.MathUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure procedural-animation tests for the 3D Y2K avatar rig (no GL). */
class PlayerAvatarRendererTest {

    private PlayerAvatarRenderer avatar;

    @BeforeEach
    void setUp() {
        avatar = new PlayerAvatarRenderer();
    }

    private void idle(float faceX, float faceZ) {
        avatar.update(0.016f, 0f, 0f, 0f, faceX, faceZ,
            false, 0f, false, faceX, faceZ, 0f, 0f, 0f, 0f);
    }

    @Test
    void avatarInitiallyFacesNorth() {
        idle(0f, -1f);

        assertEquals(0f, avatar.getYawDegrees(), 0.01f);
    }

    @Test
    void avatarTurnsSmoothlyTowardTheAim() {
        idle(0f, -1f);

        // Aim east: the yaw target is -90 degrees, but one frame may only turn a
        // fraction of that (idle turn rate).
        idle(1f, 0f);
        float first = avatar.getYawDegrees();
        assertTrue(first < 0f && first > -15f,
            "one frame must turn smoothly, was " + first);

        for (int i = 0; i < 90; i++) {
            idle(1f, 0f);
        }
        assertEquals(-90f, avatar.getYawDegrees(), 0.5f);
    }

    @Test
    void walkCycleBouncesAndSwingsOppositeArms() {
        float phase = (float) (Math.PI * 0.5 / PlayerAvatarRenderer.WALK_RATE);
        for (int i = 0; i < 50; i++) {
            avatar.update(0.016f, 0f, 0f, 0f, 0f, -1f,
                true, phase, false, 0f, -1f, 0f, 0f, 0f, 0f);
        }

        assertEquals(PlayerAvatarRenderer.WALK_BOUNCE, avatar.getBounce(), 0.005f);
        assertEquals(PlayerAvatarRenderer.WALK_LEG_SWING, avatar.getLegSwing(), 0.02f);
        assertEquals(-avatar.getLegSwing() * 0.7f, avatar.getArmSwing(), 1e-4f);

        // Stopping decays the sine pose back to neutral.
        for (int i = 0; i < 80; i++) {
            idle(0f, -1f);
        }
        assertTrue(Math.abs(avatar.getBounce()) < 0.005f);
        assertTrue(Math.abs(avatar.getLegSwing()) < 0.02f);
    }

    @Test
    void dashSnapsALeanAndRecordsTwoGhostFrames() {
        idle(1f, 0f);
        for (int i = 0; i < 8; i++) {
            float x = i * 0.2f;
            avatar.update(0.016f, x, 0f, 0f, 1f, 0f,
                true, i * 0.05f, true, 1f, 0f, 4.5f, 0f,
                (i + 1f) / 8f, 0f);
        }

        assertTrue(avatar.getDashLean() > 0.8f,
            "dash lean must snap to the dash, was " + avatar.getDashLean());
        assertTrue(avatar.getLeanPitch() > 0.25f,
            "the avatar must lean forward during a dash");
        assertTrue(avatar.hasGhostTrail(), "a dash spawns the motion ghost trail");
        assertEquals(2, avatar.getGhostCount());
        assertTrue(avatar.getGhostX(0) > avatar.getGhostX(1) + 0.05f,
            "the two recorded ghost frames must trail along the dash path");
        assertTrue(avatar.getTailPitch() > 0.15f,
            "the ponytail must trail behind the dash");
    }

    @Test
    void firstDashFrameDoesNotDrawACoincidentGhost() {
        idle(1f, 0f);

        avatar.update(0.016f, 0f, 0f, 0f, 1f, 0f,
            true, 0f, true, 1f, 0f, 4.5f, 0f, 0.10f, 0f);
        assertFalse(avatar.hasGhostTrail(),
            "the first dash frame has no previous pose to trail yet");
        assertEquals(0, avatar.getGhostCount());

        avatar.update(0.016f, 0.2f, 0f, 0f, 1f, 0f,
            true, 0.016f, true, 1f, 0f, 4.5f, 0f, 0.20f, 0f);
        assertTrue(avatar.hasGhostTrail());
        assertEquals(1, avatar.getGhostCount());
    }

    @Test
    void lateralDashRollsTheAvatarIntoTheTurn() {
        idle(0f, -1f); // facing north

        avatar.update(0.016f, 0f, 0f, 0f, 1f, 0f,
            true, 0f, true, 1f, 0f, 4.5f, 0f, 0.25f, 0f);

        assertTrue(Math.abs(avatar.getLeanRoll()) > 0.05f,
            "an east dash while facing north must roll the body, roll="
                + avatar.getLeanRoll());
    }

    @Test
    void dashTumblesAFullTurnWithMidRollCrouch() {
        idle(1f, 0f);

        avatar.update(0.016f, 0.2f, 0f, 0f, 1f, 0f,
            true, 0f, true, 1f, 0f, 4.5f, 0f, 0.5f, 0f);
        assertEquals(180f, avatar.getTumbleAngleDegrees(), 0.5f,
            "halfway through the dash the avatar is upside down");
        assertEquals(PlayerAvatarRenderer.TUMBLE_DIP, avatar.getTumbleDip(), 0.01f,
            "the tumble must crouch at mid-roll");

        avatar.update(0.016f, 0.4f, 0f, 0f, 1f, 0f,
            true, 0f, true, 1f, 0f, 4.5f, 0f, 1f, 0f);
        assertEquals(360f, avatar.getTumbleAngleDegrees(), 0.5f,
            "the roll completes a full 360 degrees over the dash");
        assertEquals(0f, avatar.getTumbleDip(), 0.01f);
    }

    @Test
    void throwFlickTwistsTorsosAndRelaxes() {
        idle(1f, 0f);

        avatar.update(0.016f, 0f, 0f, 0f, 1f, 0f,
            false, 0f, false, 1f, 0f, 0f, 0f, 0f, 0.5f);
        assertEquals(1f, avatar.getThrowFlick(), 0.01f);
        assertEquals(0.30f, avatar.getTorsoTwist(), 0.01f);

        avatar.update(0.016f, 0f, 0f, 0f, 1f, 0f,
            false, 0f, false, 1f, 0f, 0f, 0f, 0f, 1f);
        assertEquals(0f, avatar.getThrowFlick(), 0.01f);
        assertEquals(0f, avatar.getTorsoTwist(), 0.01f);
    }

    @Test
    void strafingBanksTheBodyIntoTheMovement() {
        idle(0f, -1f);
        avatar.update(0.016f, 0f, 0f, 0f, 0f, -1f,
            true, 0f, false, 0f, -1f, 4f, 0f, 0f, 0f);

        assertTrue(avatar.getLeanRoll() < -0.01f,
            "strafing east while facing north must bank the body, roll="
                + avatar.getLeanRoll());
    }

    @Test
    void dashLeanRelaxesAfterTheDash() {
        idle(1f, 0f);
        for (int i = 0; i < 6; i++) {
            avatar.update(0.016f, i * 0.2f, 0f, 0f, 1f, 0f,
                true, 0f, true, 1f, 0f, 4.5f, 0f, (i + 1f) / 6f, 0f);
        }
        for (int i = 0; i < 60; i++) {
            idle(1f, 0f);
        }

        assertTrue(avatar.getDashLean() < 0.1f,
            "dash lean must relax back to idle, was " + avatar.getDashLean());
        assertFalse(avatar.hasGhostTrail(), "ghosts disappear after the dash");
    }

    @Test
    void yawInterpolationWrapsAcrossTheAngleSeam() {
        assertEquals(0.2832f, PlayerAvatarRenderer.shortestAngleDelta(3.0f, -3.0f), 0.001f);
        assertEquals(-0.2832f, PlayerAvatarRenderer.shortestAngleDelta(-3.0f, 3.0f), 0.001f);

        // A step that crosses PI must keep the angle wrapped but still approach
        // the target along the shortest arc.
        float next = PlayerAvatarRenderer.stepAngle(3.1f, -3.1f, 0.05f);
        float remaining = Math.abs(PlayerAvatarRenderer.shortestAngleDelta(next, -3.1f));
        assertTrue(remaining < 0.0832f,
            "the seam step must reduce the angular distance, remaining=" + remaining);
        assertEquals(-3.1f, PlayerAvatarRenderer.stepAngle(3.1f, -3.1f, 0.5f), 1e-4f);
    }

    @Test
    void wrapAngleStaysInTheHalfOpenRange() {
        assertEquals(0f, PlayerAvatarRenderer.wrapAngle(MathUtils.PI2), 1e-4f);
        assertEquals(MathUtils.PI, PlayerAvatarRenderer.wrapAngle(MathUtils.PI), 1e-4f);
        assertTrue(Math.abs(PlayerAvatarRenderer.wrapAngle(4.0f)) < MathUtils.PI);
    }
}
