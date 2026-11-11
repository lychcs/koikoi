package com.lychcs.koikoi.combat.system;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.lychcs.koikoi.combat.component.HostileComponent;
import com.lychcs.koikoi.combat.component.SlowComponent;

/** Ticks slow effects and restores the hostile movement speed when they expire. */
public final class StatusSystem extends IteratingSystem {

    public StatusSystem() {
        super(Family.all(SlowComponent.class).get(), 5);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        SlowComponent slow = Mappers.SLOW.get(entity);
        if (slow == null) {
            return;
        }

        slow.remaining -= deltaTime;
        if (!slow.isExpired()) {
            return;
        }

        entity.remove(SlowComponent.class);

        HostileComponent hostile = Mappers.HOSTILE.get(entity);
        if (hostile != null) {
            hostile.setSpeedScale(1f);
        }
    }
}
