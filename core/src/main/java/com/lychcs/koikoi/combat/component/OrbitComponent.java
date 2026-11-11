package com.lychcs.koikoi.combat.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;

/** One active Omamori orbiting the player as a floating ASCII glyph. */
public final class OrbitComponent implements Component {

    /** Player entity the amulet orbits (weak reference by design). */
    public Entity owner;

    /** Current orbit angle in radians. */
    public float angle;

    /** Orbit speed in radians per second. */
    public float angularSpeed = 1.1f;

    /** Orbit radius around the player in world pixels. */
    public float radius = 48f;

    /** Phase of the vertical bobbing. */
    public float bobPhase;

    /** Current bobbing offset in world pixels (written by the system). */
    public float bobOffsetY;

    /** ASCII glyph drawn for this Omamori. */
    public String glyph = "*";

    public float red = 1f;
    public float green = 0.85f;
    public float blue = 0.35f;
}
