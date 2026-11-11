package com.lychcs.koikoi.combat.system;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.lychcs.koikoi.combat.ParticleBurst;
import com.lychcs.koikoi.combat.SealGeometry;
import com.lychcs.koikoi.combat.component.HealthComponent;
import com.lychcs.koikoi.combat.component.HostileComponent;
import com.lychcs.koikoi.combat.component.MotionComponent;
import com.lychcs.koikoi.combat.component.ProjectileComponent;
import com.lychcs.koikoi.combat.component.TransformComponent;

import java.util.ArrayList;
import java.util.List;

/** Moves companion projectiles and applies their damage on the first hit. */
public final class ProjectileSystem extends IteratingSystem {

    private static final Family HOSTILES = Family.all(
        HostileComponent.class, TransformComponent.class, HealthComponent.class).get();

    private final ParticleBurst particles;
    private final List<Entity> expiredProjectiles = new ArrayList<>();

    public ProjectileSystem(ParticleBurst particles) {
        super(Family.all(
            ProjectileComponent.class, MotionComponent.class, TransformComponent.class).get(), 4);
        this.particles = particles;
    }

    @Override
    public void update(float deltaTime) {
        expiredProjectiles.clear();
        super.update(deltaTime);

        for (int i = 0; i < expiredProjectiles.size(); i++) {
            getEngine().removeEntity(expiredProjectiles.get(i));
        }
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        ProjectileComponent projectile = Mappers.PROJECTILE.get(entity);
        TransformComponent transform = Mappers.TRANSFORM.get(entity);
        MotionComponent motion = Mappers.MOTION.get(entity);

        projectile.age += deltaTime;
        transform.x += motion.vx * deltaTime;
        transform.y += motion.vy * deltaTime;

        for (Entity hostileEntity : getEngine().getEntitiesFor(HOSTILES)) {
            HealthComponent health = Mappers.HEALTH.get(hostileEntity);
            HostileComponent hostile = Mappers.HOSTILE.get(hostileEntity);
            TransformComponent target = Mappers.TRANSFORM.get(hostileEntity);

            if (health == null || hostile == null || target == null || health.isDead()) {
                continue;
            }

            if (SealGeometry.overlaps(transform.x, transform.y, projectile.radius,
                target.x, target.y, hostile.radius)) {
                health.damage(projectile.damage);
                expiredProjectiles.add(entity);

                if (particles != null) {
                    particles.sparks(target.x, target.y, 3);
                }
                return;
            }
        }

        if (projectile.isExpired()) {
            expiredProjectiles.add(entity);
        }
    }
}
