package com.lychcs.koikoi.combat.component;

import com.badlogic.ashley.core.Component;

/**
 * Linear velocity in world pixels per second.
 *
 * <p>For the player this is the desired velocity that the screen forwards to the
 * Box2D body; companions, projectiles and particles are moved directly by their
 * systems.</p>
 */
public final class MotionComponent implements Component {

    public float vx;
    public float vy;

    /** Maximum walking speed in pixels per second. */
    public float maxSpeed = 120f;

    public MotionComponent set(float vx, float vy, float maxSpeed) {
        this.vx = vx;
        this.vy = vy;
        this.maxSpeed = maxSpeed;
        return this;
    }

    public float speed() {
        return (float) Math.sqrt(vx * vx + vy * vy);
    }
}
