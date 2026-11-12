package com.lychcs.koikoi.combat.system;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.lychcs.koikoi.combat.component.CardBurstComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * Advances the short-lived 3D card impact bursts and removes them when their
 * lifetime ends. Rendering is done by the overworld screen, which reads the
 * same component to draw camera-facing billboards on the heightmap.
 */
public final class CardBurstSystem extends IteratingSystem {

    private final List<Entity> expired = new ArrayList<>();

    public CardBurstSystem() {
        super(Family.all(CardBurstComponent.class).get(), 6);
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
        CardBurstComponent burst = Mappers.CARD_BURST.get(entity);
        if (burst == null) {
            return;
        }
        float step = Math.max(0f, deltaTime);
        burst.age += step;
        burst.spin += burst.spinSpeed * step;
        if (burst.isExpired()) {
            expired.add(entity);
        }
    }
}
