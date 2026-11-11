package com.lychcs.koikoi.combat.component;

import com.badlogic.ashley.core.Component;

/**
 * World position of an entity. {@code renderOffsetY} only affects drawing (for
 * example the companion sine bobbing) and never combat distances.
 */
public final class TransformComponent implements Component {

    public float x;
    public float y;

    /** Previous-frame position, useful for interpolation and diagnostics. */
    public float previousX;
    public float previousY;

    /** Purely visual vertical offset (bobbing, floating animations). */
    public float renderOffsetY;

    public TransformComponent set(float x, float y) {
        this.x = x;
        this.y = y;
        this.previousX = x;
        this.previousY = y;
        return this;
    }

    /** Snaps the transform without touching the previous position (teleport). */
    public TransformComponent teleport(float x, float y) {
        this.x = x;
        this.y = y;
        this.previousX = x;
        this.previousY = y;
        return this;
    }

    /** Y position used by the renderer (includes the visual offset). */
    public float renderY() {
        return y + renderOffsetY;
    }
}
