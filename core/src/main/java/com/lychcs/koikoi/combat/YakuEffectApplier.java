package com.lychcs.koikoi.combat;

import com.badlogic.ashley.core.Entity;
import com.lychcs.koikoi.combat.component.CardDeckComponent;
import com.lychcs.koikoi.combat.component.CompanionComponent;
import com.lychcs.koikoi.combat.component.HealthComponent;
import com.lychcs.koikoi.combat.component.HostileComponent;
import com.lychcs.koikoi.combat.component.PlayerComponent;
import com.lychcs.koikoi.combat.component.SealComponent;
import com.lychcs.koikoi.combat.component.SlowComponent;
import com.lychcs.koikoi.combat.component.TransformComponent;
import com.lychcs.koikoi.combat.system.Mappers;
import com.lychcs.koikoi.run.YakuEffectKind;

/**
 * Applies the unique overworld payloads granted by completed Yaku.
 *
 * <p>Pure ECS logic (no screen, no GL): Karma creates a pulsing rune seal and
 * resets Q, Beast grants temporary lifesteal, Ribbon refunds mana/W, and Petal
 * spreads a slow with petal particles. An optional target lets the overworld
 * place the rune/ink bursts at the relevant combat location; without one the
 * player position is used.</p>
 */
public final class YakuEffectApplier {

    public static final float KARMA_DURATION = 4f;
    public static final float KARMA_RADIUS = 190f;
    public static final float KARMA_TICK_DAMAGE = 12f;
    public static final float KARMA_TICK_INTERVAL = 0.45f;

    public static final float LIFESTEAL_PERCENT = 0.30f;
    public static final float LIFESTEAL_SECONDS = 8f;

    public static final float RIBBON_MANA_REFUND = 40f;
    public static final float PETAL_RADIUS = 240f;
    public static final float PETAL_SLOW_FACTOR = 0.45f;
    public static final float PETAL_SLOW_SECONDS = 3f;

    private final OverworldEcs ecs;

    public YakuEffectApplier(OverworldEcs ecs) {
        if (ecs == null) {
            throw new IllegalArgumentException("ecs must not be null");
        }
        this.ecs = ecs;
    }

    /** Applies one payload at the player; {@link YakuEffectKind#NONE} is ignored. */
    public void apply(YakuEffectKind kind) {
        apply(kind, Float.NaN, Float.NaN);
    }

    /**
     * Applies one payload with an optional overworld target. Non-finite
     * coordinates fall back to the player position.
     */
    public void apply(YakuEffectKind kind, float targetX, float targetY) {
        if (kind == null) {
            return;
        }
        switch (kind) {
            case KARMA_FIELD -> applyKarmaField(targetX, targetY);
            case LIFESTEAL -> applyLifesteal(targetX, targetY);
            case RESOURCE_REFUND -> applyResourceRefund(targetX, targetY);
            case PETAL_SPREAD -> applyPetalSpread(targetX, targetY);
            case NONE -> {
            }
        }
    }

    /** Karma: pulsing rune seal, tick damage and an instant Q-dash reset. */
    public void applyKarmaField() {
        applyKarmaField(Float.NaN, Float.NaN);
    }

    /** Karma at an explicit target; the burst and rune seal share that point. */
    public void applyKarmaField(float targetX, float targetY) {
        PlayerComponent playerComponent = ecs.getPlayer();
        TransformComponent player = ecs.getPlayerTransform();
        if (playerComponent == null || player == null) {
            return;
        }
        playerComponent.dashCooldownRemaining = 0f;
        float x = effectX(targetX, player);
        float y = effectY(targetY, player);

        Entity seal = ecs.getEngine().createEntity();
        seal.add(new TransformComponent().set(x, y));
        SealComponent field = new SealComponent();
        field.duration = KARMA_DURATION;
        field.startRadius = 34f;
        field.endRadius = KARMA_RADIUS;
        field.damage = 0f;
        field.tickDamage = KARMA_TICK_DAMAGE;
        field.tickInterval = KARMA_TICK_INTERVAL;
        field.slowFactor = 0.75f;
        field.slowDuration = 0.8f;
        field.label = "*+x/\\|";
        field.red = 0.45f;
        field.green = 0.95f;
        field.blue = 0.70f;
        seal.add(field);
        ecs.getEngine().addEntity(seal);
        ecs.getParticleBurst().glyphBurst(x, y, "*+*",
            field.red, field.green, field.blue);
    }

    /** Beast: heal for a share of all card damage for a short window. */
    public void applyLifesteal() {
        applyLifesteal(Float.NaN, Float.NaN);
    }

    /** Beast aura at an explicit target; the buff stays on the player. */
    public void applyLifesteal(float targetX, float targetY) {
        PlayerComponent playerComponent = ecs.getPlayer();
        TransformComponent player = ecs.getPlayerTransform();
        if (playerComponent == null || player == null) {
            return;
        }
        playerComponent.grantLifesteal(LIFESTEAL_PERCENT, LIFESTEAL_SECONDS);
        float x = effectX(targetX, player);
        float y = effectY(targetY, player);
        ecs.getParticleBurst().glyphBurst(x, y, "*o*",
            0.40f, 0.95f, 0.50f);
    }

    /** Ribbon: mana refund, W-card reset and companion cooldown relief. */
    public void applyResourceRefund() {
        applyResourceRefund(Float.NaN, Float.NaN);
    }

    /** Ribbon refund explosion at an explicit target; resources stay player-bound. */
    public void applyResourceRefund(float targetX, float targetY) {
        PlayerComponent playerComponent = ecs.getPlayer();
        if (playerComponent != null) {
            playerComponent.addMana(RIBBON_MANA_REFUND);
            playerComponent.basicAttackCooldownRemaining = 0f;
            playerComponent.castCooldownRemaining = 0f;
        }
        CardDeckComponent deck = ecs.getPlayerDeck();
        if (deck != null) {
            deck.resetCooldown();
        }
        for (Entity companionEntity : ecs.getCompanions()) {
            CompanionComponent companion = Mappers.COMPANION.get(companionEntity);
            if (companion != null) {
                companion.fireCooldownRemaining = 0f;
                companion.ultimateCooldownRemaining = 0f;
            }
        }
        TransformComponent player = ecs.getPlayerTransform();
        if (player != null) {
            float x = effectX(targetX, player);
            float y = effectY(targetY, player);
            ecs.getParticleBurst().glyphBurst(x, y, "~*+~",
                0.45f, 0.72f, 0.95f);
        }
    }

    /** Petal: slow every nearby hostile and scatter petal glyphs. */
    public void applyPetalSpread() {
        applyPetalSpread(Float.NaN, Float.NaN);
    }

    /** Petal burst/slow field centred on the requested target. */
    public void applyPetalSpread(float targetX, float targetY) {
        TransformComponent player = ecs.getPlayerTransform();
        if (player == null) {
            return;
        }
        float x = effectX(targetX, player);
        float y = effectY(targetY, player);

        final float radiusSquared = PETAL_RADIUS * PETAL_RADIUS;
        for (Entity hostileEntity : ecs.getHostiles()) {
            HealthComponent health = Mappers.HEALTH.get(hostileEntity);
            HostileComponent hostile = Mappers.HOSTILE.get(hostileEntity);
            TransformComponent target = Mappers.TRANSFORM.get(hostileEntity);
            if (health == null || hostile == null || target == null || health.isDead()) {
                continue;
            }
            float dx = target.x - x;
            float dy = target.y - y;
            if (dx * dx + dy * dy > radiusSquared) {
                continue;
            }
            SlowComponent slow = Mappers.SLOW.get(hostileEntity);
            if (slow == null) {
                slow = new SlowComponent();
                hostileEntity.add(slow);
            }
            slow.apply(PETAL_SLOW_FACTOR, PETAL_SLOW_SECONDS);
            hostile.setSpeedScale(slow.factor);
            ecs.getParticleBurst().glyphBurst(target.x, target.y, "*o*",
                0.95f, 0.55f, 0.75f);
        }
        ecs.getParticleBurst().glyphBurst(x, y, "*o*",
            0.95f, 0.55f, 0.75f);
    }

    private static float effectX(float requestedX, TransformComponent player) {
        return Float.isFinite(requestedX) ? requestedX : player.x;
    }

    private static float effectY(float requestedY, TransformComponent player) {
        return Float.isFinite(requestedY) ? requestedY : player.y;
    }
}