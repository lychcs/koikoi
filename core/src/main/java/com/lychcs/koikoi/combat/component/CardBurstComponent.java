package com.lychcs.koikoi.combat.component;

import com.badlogic.ashley.core.Component;

/**
 * Short-lived 3D impact effect of a thrown card.
 *
 * <p>The projectile system spawns one burst at every card impact. The overworld
 * renders camera-facing billboards plus a procedural Kamon ground crest on the
 * heightmap: petal blossoms for the Hikari card, a horizontal ribbon wave for
 * the Ribbon card, heavy beast strokes for the Beast card, and a small parchment
 * flash for a basic throw. Radii are kept in world pixels, matching the 2D
 * combat data; the screen converts them with its world scale.</p>
 */
public final class CardBurstComponent implements Component {

    /** Visual flavour of the impact. */
    public enum Style {
        BASIC,
        PETAL,
        RIBBON,
        BEAST,
        FLASH,
        RUNE
    }

    public Style style = Style.BASIC;

    /** Seconds since the burst spawned. */
    public float age;

    /** Total lifetime in seconds. */
    public float duration = 0.5f;

    /** Radius at spawn and at the end of the lifetime, in world pixels. */
    public float startRadius = 12f;
    public float endRadius = 90f;

    /** Ink colour of the burst. */
    public float red = 1f;
    public float green = 0.82f;
    public float blue = 0.45f;

    /** Height of the effect above the ground in world pixels. */
    public float elevation = 64f;

    /** Rotation of the billboards around the view axis (radians). */
    public float spin;

    /** Rotation speed in radians per second. */
    public float spinSpeed = 3.2f;

    /** 0 at spawn, 1 at the end of the lifetime. */
    public float progress() {
        if (duration <= 0f) {
            return 1f;
        }
        return Math.max(0f, Math.min(1f, age / duration));
    }

    /** 1 at spawn, 0 at the end of the lifetime. */
    public float alpha() {
        return 1f - progress();
    }

    public boolean isExpired() {
        return progress() >= 1f;
    }
}
