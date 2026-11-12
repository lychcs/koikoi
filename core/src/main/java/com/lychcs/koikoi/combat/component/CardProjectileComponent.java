package com.lychcs.koikoi.combat.component;

import com.badlogic.ashley.core.Component;

/**
 * A basic-attack card thrown from the player toward the mouse cursor.
 *
 * <p>Card projectiles are kinematic: the card system advances the transform
 * directly (no Box2D body) and destroys the entity on wall/enemy contact or
 * when {@link #travelled} reaches {@link #maxRange}.</p>
 */
public final class CardProjectileComponent implements Component {

    /** Linear velocity in world pixels per second. */
    public float vx;
    public float vy;

    /** Cached velocity magnitude in pixels per second. */
    public float speed = 1152f;

    /** Damage applied to the first hostile hit. */
    public float damage = 10f;

    /** Collision radius of the card. */
    public float radius = 7f;

    /** Maximum travel distance before the card expires. */
    public float maxRange = 768f;

    /** Distance already travelled. */
    public float travelled;

    /**
     * Logical card sigil used by the HUD and combat logs. Basic throws use a
     * directional arrow; locked 3P cards use a Hanafuda sigil ({@code *} petal,
     * {@code ~} ribbon, {@code Y} beast). The 3D card itself is a procedural
     * Hanafuda plate and no longer renders this character literally.
     */
    public char glyph = '*';

    /** Glowing parchment/gold tint. */
    public float red = 1f;
    public float green = 0.82f;
    public float blue = 0.45f;

    /** Coloured flight trail: crimson/sakura, azure/ribbon or gold/beast. */
    public float trailRed = 1.00f;
    public float trailGreen = 0.92f;
    public float trailBlue = 0.70f;

    /** Locked 3P-card archetype, or null for a basic card. */
    public CardDeckComponent.CardType deckCard;

    /** Red card: AoE damage radius in world pixels (0 = single target). */
    public float areaRadius;

    /** Blue card: refund player/companion cooldowns and mana on hit. */
    public boolean refundCooldowns;

    /** Blue card: mana restored on hit. */
    public float manaRefund;

    /** Gold card: stun/slow duration applied to the hit hostile. */
    public float stunDuration;

    /** Heavy-card knockback distance in world pixels (0 = none). */
    public float knockbackDistance;

    // ------------------------------------------------------------------
    // 3D aim ray (third-person crosshair / shoulder throw)
    // ------------------------------------------------------------------

    /** Shoulder-ray origin in libGDX world units (y = shoulder height). */
    public float rayOriginX;
    public float rayOriginY;
    public float rayOriginZ;

    /** Unit direction from the shoulder to the crosshair world point. */
    public float rayDirX;
    public float rayDirY;
    public float rayDirZ = -1f;

    /** true when the ray fields hold a real shoulder ray toward the crosshair. */
    public boolean hasWorldRay;

    /**
     * Height of the flying card in libGDX world units at the current 2D travel
     * distance. The screen supplies the pixel-to-unit world scale so the 2D
     * combat data stays in pixels.
     */
    public float worldHeight(float worldScale) {
        return worldHeightAt(travelled, worldScale);
    }

    /** Height at an arbitrary previous travel distance (used by the trail). */
    public float worldHeightAt(float travelledPixels, float worldScale) {
        if (!hasWorldRay) {
            return 0f;
        }
        return rayOriginY + rayDirY * travelledPixels * worldScale;
    }
}
