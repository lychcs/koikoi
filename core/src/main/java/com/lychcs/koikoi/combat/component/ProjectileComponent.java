package com.lychcs.koikoi.combat.component;

import com.badlogic.ashley.core.Component;

/** A companion projectile travelling towards the position of its target. */
public final class ProjectileComponent implements Component {

    public float damage = 10f;

    public float age;
    public float lifetime = 1.6f;

    /** Collision radius of the projectile. */
    public float radius = 6f;

    public float red = 0.55f;
    public float green = 0.9f;
    public float blue = 1f;

    public boolean isExpired() {
        return age >= lifetime;
    }
}
