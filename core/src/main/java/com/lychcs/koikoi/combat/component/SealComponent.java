package com.lychcs.koikoi.combat.component;

import com.badlogic.ashley.core.Component;
import com.lychcs.koikoi.combat.SealGeometry;

/**
 * A ground-projected Hanko seal.
 *
 * <p>The seal expands with {@link SealGeometry#expandingRadius(float, float, float)}.
 * Damage is applied exactly once at spawn (full area); the slow is refreshed
 * while a hostile is inside the current radius.</p>
 */
public final class SealComponent implements Component {

    public float age;
    public float duration = 0.9f;

    public float startRadius = 26f;
    public float endRadius = 110f;

    public float damage = 45f;

    /** Damage per tick for pulsing Yaku rune fields (0 = no ticks). */
    public float tickDamage;
    /** Seconds between two tick-damage pulses. */
    public float tickInterval = 0.5f;
    /** Accumulated time since the last tick. */
    public float tickTimer;

    public float slowFactor = 0.45f;
    public float slowDuration = 2.5f;

    /** true after the one-time AoE damage has been applied. */
    public boolean damageApplied;

    /** Letters engraved around the seal ring (Hanko name). */
    public String label = "WHITE SEAL";

    /** Seal glow colour. */
    public float red = 0.95f;
    public float green = 0.95f;
    public float blue = 0.85f;

    /** 0 at spawn, 1 at the end of the lifetime. */
    public float progress() {
        if (duration <= 0f) {
            return 1f;
        }
        return Math.max(0f, Math.min(1f, age / duration));
    }

    /** Current animated radius. */
    public float currentRadius() {
        return SealGeometry.expandingRadius(startRadius, endRadius, progress());
    }
}
