package com.lychcs.koikoi.combat;

import com.badlogic.gdx.math.Vector2;

/**
 * Input snapshot consumed by the Ashley systems.
 *
 * <p>The screen fills this once per frame; systems only read it. One-shot flags
 * are cleared after {@code Engine.update}, so a key press triggers exactly one
 * dash, cast or ultimate - independent of how many entities a system iterates.</p>
 *
 * <p>Movement is MOBA-style: a right-click stores a world-space destination in
 * {@link #moveTarget}. The legacy {@link #moveX}/{@link #moveY} axes remain for
 * tests and optional developer fallback, but the overworld no longer fills them
 * from WASD.</p>
 */
public final class PlayerInput {

    /** Optional fallback horizontal axis in {@code [-1, 1]} (not used by default). */
    public float moveX;

    /** Optional fallback vertical axis in {@code [-1, 1]} (positive = up/north). */
    public float moveY;

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

