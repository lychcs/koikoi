package com.lychcs.koikoi.combat.system;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.lychcs.koikoi.combat.ParticleBurst;
import com.lychcs.koikoi.combat.SealGeometry;
import com.lychcs.koikoi.combat.component.CardDeckComponent;
import com.lychcs.koikoi.combat.component.CardProjectileComponent;
import com.lychcs.koikoi.combat.component.CompanionComponent;
import com.lychcs.koikoi.combat.component.HealthComponent;
import com.lychcs.koikoi.combat.component.HostileComponent;
import com.lychcs.koikoi.combat.component.PlayerComponent;
import com.lychcs.koikoi.combat.component.SlowComponent;
import com.lychcs.koikoi.combat.component.TransformComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * Advances left-click card projectiles, applies damage to the first hostile,
 * detects wall contact and expires the card after its maximum range.
 *
 * <p>The wall check is injected by the screen so the system stays independent
 * of the ASCII map and Box2D. World travel is pure kinematic movement.</p>
 */
public final class CardProjectileSystem extends IteratingSystem {

    /** Query used to stop a card on a solid map tile or outside the map. */
    @FunctionalInterface
    public interface WallQuery {
        boolean isBlocked(float x, float y);
    }

    private static final Family HOSTILES = Family.all(
        HostileComponent.class, TransformComponent.class, HealthComponent.class).get();

    /** Player entities that can receive lifesteal healing. */
    private static final Family PLAYERS = Family.all(
        PlayerComponent.class, HealthComponent.class).get();

    private final ParticleBurst particles;
    private final WallQuery wallQuery;
    private final List<Entity> expired = new ArrayList<>();

    public CardProjectileSystem(ParticleBurst particles, WallQuery wallQuery) {
        super(Family.all(CardProjectileComponent.class, TransformComponent.class).get(), 4);
        this.particles = particles;
        this.wallQuery = wallQuery;
    }

    @Override
    public void update(float deltaTime) {
        expired.clear();
        super.update(deltaTime);

        for (int i = 0; i < expired.size(); i++) {
            getEngine().removeEntity(expired.get(i));
        }
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        CardProjectileComponent card = Mappers.CARD_PROJECTILE.get(entity);
        TransformComponent transform = Mappers.TRANSFORM.get(entity);
        if (card == null || transform == null) {
            return;
        }

        float stepX = card.vx * deltaTime;
        float stepY = card.vy * deltaTime;
        transform.x += stepX;
        transform.y += stepY;
        card.travelled += (float) Math.sqrt(stepX * stepX + stepY * stepY);

        if (wallQuery != null && wallQuery.isBlocked(transform.x, transform.y)) {
            impact(transform.x, transform.y, card);
            expired.add(entity);
            return;
        }

        for (Entity hostileEntity : getEngine().getEntitiesFor(HOSTILES)) {
            HealthComponent health = Mappers.HEALTH.get(hostileEntity);
            HostileComponent hostile = Mappers.HOSTILE.get(hostileEntity);
            TransformComponent target = Mappers.TRANSFORM.get(hostileEntity);
            if (health == null || hostile == null || target == null || health.isDead()) {
                continue;
            }

            if (SealGeometry.overlaps(transform.x, transform.y, card.radius,
                target.x, target.y, hostile.radius)) {
                if (card.areaRadius > 0f) {
                    applyAreaDamage(transform, card);
                } else {
                    applySingleHit(hostileEntity, health, hostile, target, card);
                }
                impact(target.x, target.y, card);
                expired.add(entity);
                return;
            }
        }

        if (card.travelled >= card.maxRange) {
            impact(transform.x, transform.y, card);
            expired.add(entity);
        }
    }

    /** Red card: damage every hostile inside the enlarged impact radius. */
    private void applyAreaDamage(TransformComponent impact, CardProjectileComponent card) {
        for (Entity hostileEntity : getEngine().getEntitiesFor(HOSTILES)) {
            HealthComponent health = Mappers.HEALTH.get(hostileEntity);
            HostileComponent hostile = Mappers.HOSTILE.get(hostileEntity);
            TransformComponent target = Mappers.TRANSFORM.get(hostileEntity);
            if (health == null || hostile == null || target == null || health.isDead()) {
                continue;
            }
            if (SealGeometry.overlaps(impact.x, impact.y, card.areaRadius,
                target.x, target.y, hostile.radius)) {
                health.damage(card.damage);
                healPlayerFromLifesteal(card.damage);
            }
        }
    }

    /** Normal, blue or gold card hit on one hostile. */
    private void applySingleHit(Entity hostileEntity, HealthComponent health,
                                HostileComponent hostile, TransformComponent target,
                                CardProjectileComponent card) {
        health.damage(card.damage);
        healPlayerFromLifesteal(card.damage);

        if (card.stunDuration > 0f) {
            SlowComponent slow = Mappers.SLOW.get(hostileEntity);
            if (slow == null) {
                slow = new SlowComponent();
                hostileEntity.add(slow);
            }
            slow.apply(0.0f, card.stunDuration);
            hostile.setSpeedScale(slow.factor);
        }

        if (card.refundCooldowns) {
            refundAbilities(card.manaRefund);
        }
    }

    /** Beast-Yaku sustain: heal the player for a share of the card damage. */
    private void healPlayerFromLifesteal(float damage) {
        if (damage <= 0f) {
            return;
        }
        for (Entity playerEntity : getEngine().getEntitiesFor(PLAYERS)) {
            PlayerComponent player = Mappers.PLAYER.get(playerEntity);
            HealthComponent health = Mappers.HEALTH.get(playerEntity);
            if (player != null && health != null
                && player.lifestealRemaining > 0f && player.lifestealPercent > 0f) {
                health.heal(damage * player.lifestealPercent);
            }
        }
    }

    /** Blue card: restore player mana and clear player/companion cooldowns. */
    private void refundAbilities(float mana) {
        for (Entity playerEntity : getEngine().getEntitiesFor(
            Family.all(PlayerComponent.class).get())) {
            PlayerComponent player = Mappers.PLAYER.get(playerEntity);
            if (player == null) {
                continue;
            }
            player.addMana(mana);
            player.dashCooldownRemaining = 0f;
            player.castCooldownRemaining = 0f;
            player.basicAttackCooldownRemaining = 0f;
        }

        for (Entity companionEntity : getEngine().getEntitiesFor(
            Family.all(CompanionComponent.class).get())) {
            CompanionComponent companion = Mappers.COMPANION.get(companionEntity);
            if (companion != null) {
                companion.ultimateCooldownRemaining = 0f;
                companion.fireCooldownRemaining = 0f;
            }
        }
    }

    /** Tiny/special ASCII impact burst at the hit position. */
    private void impact(float x, float y, CardProjectileComponent card) {
        if (particles == null) {
            return;
        }
        if (card.deckCard == CardDeckComponent.CardType.RED) {
            // Fire/explosion ink is warm orange-gold and additive, so it tints
            // the existing terrain instead of stamping an opaque sprite.
            particles.glyphBurst(x, y, "*#*", 1f, 0.58f, 0.18f);
        } else if (card.deckCard == CardDeckComponent.CardType.BLUE) {
            particles.glyphBurst(x, y, "*", card.red, card.green, card.blue);
        } else if (card.deckCard == CardDeckComponent.CardType.GOLD) {
            particles.glyphBurst(x, y, "*o", 1f, 0.82f, 0.32f);
        } else {
            particles.burst(x, y, 2, 2);
        }
    }
}
