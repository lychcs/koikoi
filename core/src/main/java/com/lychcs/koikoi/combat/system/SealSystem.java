package com.lychcs.koikoi.combat.system;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.lychcs.koikoi.combat.ParticleBurst;
import com.lychcs.koikoi.combat.SealGeometry;
import com.lychcs.koikoi.combat.component.HealthComponent;
import com.lychcs.koikoi.combat.component.HostileComponent;
import com.lychcs.koikoi.combat.component.SealComponent;
import com.lychcs.koikoi.combat.component.SlowComponent;
import com.lychcs.koikoi.combat.component.TransformComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * Expands ground seals, applies the one-time AoE exorcism damage and keeps the
 * slow effect active for every hostile inside the current seal boundary.
 */
public final class SealSystem extends IteratingSystem {

    private static final Family HOSTILES = Family.all(
        HostileComponent.class, TransformComponent.class, HealthComponent.class).get();

    private final ParticleBurst particles;
    private final List<Entity> expiredSeals = new ArrayList<>();

    public SealSystem(ParticleBurst particles) {
        super(Family.all(SealComponent.class, TransformComponent.class).get(), 2);
        this.particles = particles;
    }

    @Override
    public void update(float deltaTime) {
        expiredSeals.clear();
        super.update(deltaTime);

        for (int i = 0; i < expiredSeals.size(); i++) {
            getEngine().removeEntity(expiredSeals.get(i));
        }
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        SealComponent seal = Mappers.SEAL.get(entity);
        TransformComponent transform = Mappers.TRANSFORM.get(entity);

        seal.age += deltaTime;

        if (!seal.damageApplied) {
            seal.damageApplied = true;
            applyDamage(transform, seal);
        }

        applySlow(transform, seal);
        applyTicks(transform, seal, deltaTime);

        if (seal.progress() >= 1f) {
            expiredSeals.add(entity);
        }
    }

    /** One-time damage to every hostile inside the final seal area. */
    private void applyDamage(TransformComponent sealTransform, SealComponent seal) {
        damageHostiles(sealTransform, seal.endRadius, seal.damage);
    }

    /** Pulsing Yaku field: applies the configured tick damage repeatedly. */
    private void applyTicks(TransformComponent sealTransform, SealComponent seal, float deltaTime) {
        if (seal.tickDamage <= 0f || seal.tickInterval <= 0f) {
            return;
        }
        seal.tickTimer += Math.max(0f, deltaTime);
        while (seal.tickTimer >= seal.tickInterval) {
            seal.tickTimer -= seal.tickInterval;
            damageHostiles(sealTransform, seal.currentRadius(), seal.tickDamage);
        }
    }

    /** Applies {@code damage} to every living hostile inside {@code radius}. */
    private void damageHostiles(TransformComponent sealTransform, float radius, float damage) {
        if (damage <= 0f) {
            return;
        }
        List<Entity> hostiles = new ArrayList<>();
        for (Entity hostile : getEngine().getEntitiesFor(HOSTILES)) {
            hostiles.add(hostile);
        }

        for (int i = 0; i < hostiles.size(); i++) {
            Entity hostileEntity = hostiles.get(i);
            HealthComponent health = Mappers.HEALTH.get(hostileEntity);
            HostileComponent hostile = Mappers.HOSTILE.get(hostileEntity);
            TransformComponent target = Mappers.TRANSFORM.get(hostileEntity);

            if (health == null || hostile == null || target == null || health.isDead()) {
                continue;
            }

            if (SealGeometry.overlaps(sealTransform.x, sealTransform.y, radius,
                target.x, target.y, hostile.radius)) {
                health.damage(damage);
                if (particles != null) {
                    particles.sparks(target.x, target.y, 6);
                }
            }
        }
    }

    /** Refreshes the slow while the hostile is inside the animated seal. */
    private void applySlow(TransformComponent sealTransform, SealComponent seal) {
        float radius = seal.currentRadius();

        for (Entity hostileEntity : getEngine().getEntitiesFor(HOSTILES)) {
            HealthComponent health = Mappers.HEALTH.get(hostileEntity);
            HostileComponent hostile = Mappers.HOSTILE.get(hostileEntity);
            TransformComponent target = Mappers.TRANSFORM.get(hostileEntity);

            if (health == null || hostile == null || target == null || health.isDead()) {
                continue;
            }

            if (!SealGeometry.overlaps(sealTransform.x, sealTransform.y, radius,
                target.x, target.y, hostile.radius)) {
                continue;
            }

            SlowComponent slow = Mappers.SLOW.get(hostileEntity);
            if (slow == null) {
                slow = new SlowComponent();
                hostileEntity.add(slow);
            }
            slow.apply(seal.slowFactor, seal.slowDuration);
            hostile.setSpeedScale(slow.factor);
        }
    }
}
