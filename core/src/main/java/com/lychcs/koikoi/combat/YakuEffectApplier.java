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
 * spreads a slow with petal particles.</p>
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

    /** Applies one payload; {@link YakuEffectKind#NONE} is ignored. */
    public void apply(YakuEffectKind kind) {
        if (kind == null) {
            return;
        }
        switch (kind) {
            case KARMA_FIELD -> applyKarmaField();
            case LIFESTEAL -> applyLifesteal();
            case RESOURCE_REFUND -> applyResourceRefund();
            case PETAL_SPREAD -> applyPetalSpread();
            case NONE -> {
            }
        }
    }

    /** Karma: pulsing rune seal, tick damage and an instant Q-dash reset. */
    public void applyKarmaField() {
        PlayerComponent playerComponent = ecs.getPlayer();
        TransformComponent transform = ecs.getPlayerTransform();
        if (playerComponent == null || transform == null) {
            return;
        }
        playerComponent.dashCooldownRemaining = 0f;

        Entity seal = ecs.getEngine().createEntity();
        seal.add(new TransformComponent().set(transform.x, transform.y));
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
        ecs.getParticleBurst().glyphBurst(transform.x, transform.y, "*+*",
            field.red, field.green, field.blue);
    }

    /** Beast: heal for a share of all card damage for a short window. */
    public void applyLifesteal() {
        PlayerComponent playerComponent = ecs.getPlayer();
        if (playerComponent == null) {
            return;
        }
        playerComponent.grantLifesteal(LIFESTEAL_PERCENT, LIFESTEAL_SECONDS);
        TransformComponent transform = ecs.getPlayerTransform();
        if (transform != null) {
            ecs.getParticleBurst().glyphBurst(transform.x, transform.y, "*o",
                0.40f, 0.95f, 0.50f);
        }
    }

    /** Ribbon: mana refund, W-card reset and companion cooldown relief. */
    public void applyResourceRefund() {
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
        TransformComponent transform = ecs.getPlayerTransform();
        if (transform != null) {
            ecs.getParticleBurst().glyphBurst(transform.x, transform.y, "*+",
                0.45f, 0.72f, 0.95f);
        }
    }

    /** Petal: slow every nearby hostile and scatter petal glyphs. */
    public void applyPetalSpread() {
        TransformComponent transform = ecs.getPlayerTransform();
        if (transform == null) {
            return;
        }
        final float radiusSquared = PETAL_RADIUS * PETAL_RADIUS;
        for (Entity hostileEntity : ecs.getHostiles()) {
            HealthComponent health = Mappers.HEALTH.get(hostileEntity);
            HostileComponent hostile = Mappers.HOSTILE.get(hostileEntity);
            TransformComponent target = Mappers.TRANSFORM.get(hostileEntity);
            if (health == null || hostile == null || target == null || health.isDead()) {
                continue;
            }
            float dx = target.x - transform.x;
            float dy = target.y - transform.y;
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
            ecs.getParticleBurst().glyphBurst(target.x, target.y, "*o",
                0.95f, 0.55f, 0.75f);
        }
        ecs.getParticleBurst().glyphBurst(transform.x, transform.y, "*o*",
            0.95f, 0.55f, 0.75f);
    }
}