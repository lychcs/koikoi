package com.lychcs.koikoi.combat.system;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.lychcs.koikoi.combat.HostileDefeatListener;
import com.lychcs.koikoi.combat.component.HealthComponent;
import com.lychcs.koikoi.combat.component.HostileComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * Removes dead hostiles and notifies the game exactly once per defeat (the
 * screen then removes the roaming actor and marks the encounter defeated).
 */
public final class HostileCleanupSystem extends IteratingSystem {

    private final HostileDefeatListener listener;
    private final List<Entity> deadHostiles = new ArrayList<>();

    public HostileCleanupSystem(HostileDefeatListener listener) {
        super(Family.all(HostileComponent.class, HealthComponent.class).get(), 9);
        this.listener = listener;
    }

    @Override
    public void update(float deltaTime) {
        deadHostiles.clear();
        super.update(deltaTime);

        for (int i = 0; i < deadHostiles.size(); i++) {
            getEngine().removeEntity(deadHostiles.get(i));
        }
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        HostileComponent hostile = Mappers.HOSTILE.get(entity);
        HealthComponent health = Mappers.HEALTH.get(entity);

        if (hostile == null || health == null || !health.isDead() || hostile.defeated) {
            return;
        }

        hostile.defeated = true;
        deadHostiles.add(entity);

        if (listener != null) {
            listener.onHostileDefeated(entity, hostile);
        }
    }
}
