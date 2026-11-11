package com.lychcs.koikoi.combat.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.lychcs.koikoi.combat.CompanionFollow;

/**
 * Battle pet state: delay-follow, sine bobbing, auto-attack and the active
 * ultimate. The companion targets a hostile entity and fires projectiles at it.
 */
public final class CompanionComponent implements Component {

    /** Display name (for the HUD and the shader pass). */
    public String name = "Ko-Oni";

    /** 0-based slot, determines the follow offset around the player. */
    public int slot;

    /** Smoothing strength of the delay-follow in inverse seconds. */
    public float followLag = CompanionFollow.DEFAULT_LAG;

    /** Distance of the follow slot from the player. */
    public float slotRadius = 54f;

    /** Phase offset of the vertical sine bobbing. */
    public float bobPhase;

    /** Current bobbing offset in world pixels (written by the system). */
    public float bobOffsetY;

    /** Maximum distance at which the companion acquires a target. */
    public float attackRange = 340f;

    /** Base seconds between two shots (before Omamori fire-rate buffs). */
    public float baseFireInterval = 1.15f;

    /** Effective seconds between two shots (after Omamori fire-rate buffs). */
    public float fireInterval = 1.15f;

    /** Remaining time until the next shot. */
    public float fireCooldownRemaining;

    /** Damage of one projectile. */
    public float damage = 14f;

    /** Projectile speed in pixels per second. */
    public float projectileSpeed = 540f;

    /** Full ultimate cooldown in seconds. */
    public float ultimateCooldown = 14f;

    /** Remaining ultimate cooldown in seconds. */
    public float ultimateCooldownRemaining;

    /** Radius of the ultimate burst around the companion. */
    public float ultimateRadius = 170f;

    /** Damage of the ultimate burst. */
    public float ultimateDamage = 42f;

    /** Currently tracked hostile entity, or {@code null}. */
    public Entity target;

    /** true while the companion is ready to fire its ultimate. */
    public boolean isUltimateReady() {
        return ultimateCooldownRemaining <= 0f;
    }
}
