package com.lychcs.koikoi.combat;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;

/**
 * Input snapshot consumed by the Ashley systems.
 *
 * <p>The screen fills this once per frame; systems only read it. One-shot flags
 * are cleared after {@code Engine.update}, so a key press triggers exactly one
 * dash, cast or ultimate - independent of how many entities a system iterates.</p>
 *
 * <p>Movement is MOBA-style: a right-click stores a world-space destination in
 * {@link #moveTarget}. The first-person overworld additionally fills
 * {@link #moveDirection} every frame with the camera-relative WASD direction,
 * and {@link #moveX}/{@link #moveY} remain as a legacy test/developer
 * fallback.</p>
 */
public final class PlayerInput {

    /** Optional fallback horizontal axis in {@code [-1, 1]} (not used by default). */
    public float moveX;

    /** Optional fallback vertical axis in {@code [-1, 1]} (positive = up/north). */
    public float moveY;

    /**
     * Camera-relative ground direction in 2D world space, filled by the
     * first-person controller ({@code W} = forward, {@code D} = right).
     */
    public final Vector2 moveDirection = new Vector2();

    /** true while {@link #moveDirection} should steer the player. */
    public boolean hasMoveDirection;

    /** World-space right-click destination. */
    public final Vector2 moveTarget = new Vector2();

    /** true while {@link #moveTarget} should drive the player. */
    public boolean hasMoveTarget;

    /** Dash key (Q) was pressed this frame. */
    public boolean dashRequested;

    /** Stop key (S) was pressed this frame: cancel the move order and halt. */
    public boolean stopRequested;

    /** Hanko cast key was pressed this frame. */
    public boolean castRequested;

    /** Basic card throw requested this frame (left click, or right-click on a hostile). */
    public boolean basicAttackRequested;

    /** W was pressed this frame (start shuffle / lock active card). */
    public boolean cardSelectRequested;

    /** Companion ultimate key was pressed this frame. */
    public boolean ultimateRequested;

    /** Aim position in world coordinates (mouse). */
    public float aimX;

    /** Aim position in world coordinates (mouse). */
    public float aimY;

    /** Camera-ray origin in libGDX world units (y = eye height). */
    public final Vector3 aimRayOrigin = new Vector3();

    /** Unit camera-ray direction in libGDX world units (the crosshair line). */
    public final Vector3 aimRayDirection = new Vector3(0f, 0f, -1f);

    /** true when the screen filled a valid crosshair ray this frame. */
    public boolean hasAimRay;

    /** Accumulated screen time in seconds, used for bobbing and shader phases. */
    public float time;

    /** Resets all one-shot request flags. The movement target persists. */
    public void clearOneShots() {
        dashRequested = false;
        stopRequested = false;
        castRequested = false;
        basicAttackRequested = false;
        cardSelectRequested = false;
        ultimateRequested = false;
    }
}

