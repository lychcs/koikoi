package com.lychcs.koikoi.combat.component;

import com.badlogic.ashley.core.Component;

/**
 * Active slow effect of a hostile. Multiple seal ticks refresh the effect; the
 * strongest factor and the longest remaining time win.
 */
public final class SlowComponent implements Component {

    /** Movement multiplier (1 = unaffected, 0.45 = 55 % slow). */
    public float factor = 1f;

    /** Remaining seconds. */
    public float remaining;

    public void apply(float newFactor, float duration) {
        float clamped = Math.max(0.05f, Math.min(1f, newFactor));
        factor = Math.min(factor, clamped);
        remaining = Math.max(remaining, Math.max(0f, duration));
    }

    public boolean isExpired() {
        return remaining <= 0f;
    }
}
