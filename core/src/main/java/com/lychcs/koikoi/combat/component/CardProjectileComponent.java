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

    /** Directional glyph rendered for the card ({@code > < ^ v} or {@code *}). */
    public char glyph = '*';

    /** Glowing parchment/gold tint. */
    public float red = 1f;
    public float green = 0.82f;
    public float blue = 0.45f;

    /** Locked W-card archetype, or null for a basic card. */
    public CardDeckComponent.CardType deckCard;

    /** Red card: AoE damage radius in world pixels (0 = single target). */
    public float areaRadius;

    /** Blue card: refund player/companion cooldowns and mana on hit. */
    public boolean refundCooldowns;

    /** Blue card: mana restored on hit. */
    public float manaRefund;

    /** Gold card: stun/slow duration applied to the hit hostile. */
    public float stunDuration;
}
