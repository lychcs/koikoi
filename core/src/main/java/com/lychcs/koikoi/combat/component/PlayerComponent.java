package com.lychcs.koikoi.combat.component;

import com.badlogic.ashley.core.Component;
import com.lychcs.koikoi.combat.CombatStats;

/**
 * State of the overworld player: mana, dash and I-frame timers, cast cooldown
 * and facing direction. The ECS owns the logic; the screen mirrors the
 * resulting velocity onto the Box2D body.
 */
public final class PlayerComponent implements Component {

    public float maxMana = 100f;
    public float mana = 100f;
    public float manaRegenPerSecond = CombatStats.BASE.manaRegenPerSecond();

    /** Dash speed in pixels per second. */
    public float dashSpeed = 460f;

    /** Dash duration in seconds. */
    public float dashDuration = 0.16f;

    /** Remaining dash time (0 when not dashing). */
    public float dashRemaining;

    /** Full dash cooldown in seconds (already reduced by Omamori passives). */
    public float dashCooldown = CombatStats.BASE.dashCooldown();

    /** Remaining dash cooldown in seconds. */
    public float dashCooldownRemaining;

    /** Remaining invulnerability (I-frames) in seconds. */
    public float invulnerabilityRemaining;

    /** Seconds between two basic card throws (left click or right-click target). */
    public float basicAttackCooldown = 0.4f;

    /** Remaining basic-attack cooldown in seconds. */
    public float basicAttackCooldownRemaining;

    /** Cooldown between two Hanko casts in seconds. */
    public float castCooldown = 0.35f;

    /** Remaining cast cooldown in seconds. */
    public float castCooldownRemaining;

    /** On-hit healing percentage while {@link #lifestealRemaining} is > 0. */
    public float lifestealPercent;

    /** Remaining duration of the lifesteal buff in seconds. */
    public float lifestealRemaining;

    /** Last movement direction used for a dash without input. */
    public float facingX;
    public float facingY = -1f;

    /** Duration of the throw-arm flick/torso-twist animation in seconds. */
    public static final float THROW_ANIMATION_DURATION = 0.22f;

    /** Remaining throw animation; set when a card projectile spawns. */
    public float throwAnimationRemaining;

    /** Grants/refreshes the Beast-Yaku lifesteal buff. */
    public void grantLifesteal(float percent, float seconds) {
        lifestealPercent = Math.max(0f, Math.min(1f, percent));
        lifestealRemaining = Math.max(lifestealRemaining, Math.max(0f, seconds));
    }

    /** Advances temporary combat buffs. */
    public void updateBuffs(float deltaTime) {
        if (throwAnimationRemaining > 0f) {
            throwAnimationRemaining = Math.max(0f,
                throwAnimationRemaining - Math.max(0f, deltaTime));
        }
        if (lifestealRemaining > 0f) {
            lifestealRemaining = Math.max(0f, lifestealRemaining - Math.max(0f, deltaTime));
            if (lifestealRemaining <= 0f) {
                lifestealPercent = 0f;
            }
        }
    }

    public boolean isDashing() {
        return dashRemaining > 0f;
    }

    public boolean isInvulnerable() {
        return invulnerabilityRemaining > 0f;
    }

    /** Restores mana, never above the maximum. */
    public void addMana(float amount) {
        mana = Math.max(0f, Math.min(maxMana, mana + amount));
    }

    /** Consumes mana; returns false (and changes nothing) when it is not enough. */
    public boolean spendMana(float amount) {
        if (amount <= 0f) {
            return true;
        }
        if (mana < amount) {
            return false;
        }
        mana -= amount;
        return true;
    }
}
