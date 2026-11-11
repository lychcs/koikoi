package com.lychcs.koikoi.combat.component;

import com.badlogic.ashley.core.Component;

/** Short-lived procedural particle (dust or cherry blossom petal). */
public final class ParticleComponent implements Component {

    /** Glyph drawn for the particle. */
    public String glyph = ".";

    public float age;
    public float lifetime = 0.6f;

    public float red = 1f;
    public float green = 1f;
    public float blue = 1f;

    /** Extra scale on top of the particle font. */
    public float scale = 1f;

    /** 1 at spawn, 0 at the end of the lifetime. */
    public float alpha() {
        if (lifetime <= 0f) {
            return 0f;
        }
        return Math.max(0f, 1f - age / lifetime);
    }

    public boolean isExpired() {
        return age >= lifetime;
    }
}
