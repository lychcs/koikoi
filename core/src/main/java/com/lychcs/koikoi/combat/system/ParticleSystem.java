package com.lychcs.koikoi.combat.system;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.lychcs.koikoi.combat.component.MotionComponent;
import com.lychcs.koikoi.combat.component.ParticleComponent;
import com.lychcs.koikoi.combat.component.TransformComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * Moves procedural dust and cherry-blossom particles, applies drag and a gentle
 * downward drift, and removes them when their lifetime ends.
 */
public final class ParticleSystem extends IteratingSystem {

    /** Horizontal/vertical drag per second. */
    public static final float DRAG = 2.4f;

    /** Downward drift of petals in pixels per second squared. */
    public static final float GRAVITY = 26f;

    private final List<Entity> expiredParticles = new ArrayList<>();

    public ParticleSystem() {
        super(Family.all(
            ParticleComponent.class, MotionComponent.class, TransformComponent.class).get(), 7);
    }

    @Override
    public void update(float deltaTime) {
        expiredParticles.clear();
        super.update(deltaTime);

        for (int i = 0; i < expiredParticles.size(); i++) {
            getEngine().removeEntity(expiredParticles.get(i));
        }
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        ParticleComponent particle = Mappers.PARTICLE.get(entity);
        TransformComponent transform = Mappers.TRANSFORM.get(entity);
        MotionComponent motion = Mappers.MOTION.get(entity);

        particle.age += deltaTime;
        transform.x += motion.vx * deltaTime;
        transform.y += motion.vy * deltaTime;

        float drag = Math.max(0f, 1f - DRAG * deltaTime);
        motion.vx *= drag;
        motion.vy = motion.vy * drag - GRAVITY * deltaTime;

        if (particle.isExpired()) {
            expiredParticles.add(entity);
        }
    }
}
