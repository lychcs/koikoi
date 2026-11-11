package com.lychcs.koikoi.combat.system;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.lychcs.koikoi.combat.CompanionFollow;
import com.lychcs.koikoi.combat.PlayerInput;
import com.lychcs.koikoi.combat.component.OrbitComponent;
import com.lychcs.koikoi.combat.component.TransformComponent;

/**
 * Moves the active Omamori glyphs around the player: a horizontal orbit plus the
 * documented vertical sine bobbing {@code sin(time * 3.0) * 4.0}.
 */
public final class OrbitSystem extends IteratingSystem {

    private final PlayerInput input;

    public OrbitSystem(PlayerInput input) {
        super(Family.all(OrbitComponent.class, TransformComponent.class).get(), 6);
        if (input == null) {
            throw new IllegalArgumentException("input must not be null");
        }
        this.input = input;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        OrbitComponent orbit = Mappers.ORBIT.get(entity);
        TransformComponent transform = Mappers.TRANSFORM.get(entity);

        orbit.angle += orbit.angularSpeed * deltaTime;

        TransformComponent owner = orbit.owner == null ? null : Mappers.TRANSFORM.get(orbit.owner);
        if (owner == null) {
            return;
        }

        // Elliptical orbit: wide horizontally, flatter vertically (top-down look).
        transform.x = owner.x + (float) Math.cos(orbit.angle) * orbit.radius;
        transform.y = owner.y + (float) Math.sin(orbit.angle) * orbit.radius * 0.55f;

        orbit.bobOffsetY = CompanionFollow.bobOffset(input.time, orbit.bobPhase);
        transform.renderOffsetY = orbit.bobOffsetY;
    }
}
