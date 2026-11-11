package com.lychcs.koikoi.combat.component;

import com.badlogic.ashley.core.Component;

/**
 * Hit points of a damageable entity (hostiles and the overworld player).
 */
public final class HealthComponent implements Component {

    public float max = 100f;
    public float current = 100f;

    public HealthComponent set(float max) {
        this.max = Math.max(1f, max);
        this.current = this.max;
        return this;
    }

    public void damage(float amount) {
        if (amount <= 0f) {
            return;
        }
        current = Math.max(0f, current - amount);
    }

    /** Restores hit points, clamped to {@link #max}. */
    public void heal(float amount) {
        if (amount <= 0f) {
            return;
        }
        current = Math.min(max, current + amount);
    }

    public boolean isDead() {
        return current <= 0f;
    }

    public float ratio() {
        return max <= 0f ? 0f : Math.max(0f, Math.min(1f, current / max));
    }
}
