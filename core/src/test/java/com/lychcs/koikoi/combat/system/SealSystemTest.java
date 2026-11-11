package com.lychcs.koikoi.combat.system;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.lychcs.koikoi.combat.component.HealthComponent;
import com.lychcs.koikoi.combat.component.HostileComponent;
import com.lychcs.koikoi.combat.component.SealComponent;
import com.lychcs.koikoi.combat.component.SlowComponent;
import com.lychcs.koikoi.combat.component.TransformComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** ECS integration of the Hanko seal: one-time AoE damage and slow lifecycle. */
class SealSystemTest {

    private Engine engine;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        engine.addSystem(new SealSystem(null));
        engine.addSystem(new StatusSystem());
    }

    private Entity addSeal(float x, float y, float duration, float damage,
                           float slowFactor, float slowDuration) {
        Entity seal = engine.createEntity();
        seal.add(new TransformComponent().set(x, y));

        SealComponent sealComponent = new SealComponent();
        sealComponent.startRadius = 10f;
        sealComponent.endRadius = 100f;
        sealComponent.damage = damage;
        sealComponent.slowFactor = slowFactor;
        sealComponent.slowDuration = slowDuration;
        sealComponent.duration = duration;
        seal.add(sealComponent);

        engine.addEntity(seal);
        return seal;
    }

    private Entity addHostile(float x, float y, float radius, float health,
                              HostileComponent.HostileActor actor) {
        Entity hostile = engine.createEntity();
        hostile.add(new TransformComponent().set(x, y));

        HostileComponent hostileComponent = new HostileComponent();
        hostileComponent.radius = radius;
        hostileComponent.actor = actor;
        hostile.add(hostileComponent);

        hostile.add(new HealthComponent().set(health));
        engine.addEntity(hostile);
        return hostile;
    }

    private static HealthComponent healthOf(Entity entity) {
        return entity.getComponent(HealthComponent.class);
    }

    @Test
    void damagesEveryHostileInsideTheSealExactlyOnce() {
        addSeal(0f, 0f, 1f, 30f, 0.5f, 2f);

        Entity inside = addHostile(50f, 0f, 0f, 100f, null);
        Entity onBoundary = addHostile(100f, 0f, 0f, 100f, null);
        Entity outside = addHostile(101f, 0f, 0f, 100f, null);

        engine.update(0.016f);

        assertEquals(70f, healthOf(inside).current, 0.0001f);
        assertEquals(70f, healthOf(onBoundary).current, 0.0001f);
        assertEquals(100f, healthOf(outside).current, 0.0001f);

        engine.update(0.2f);
        assertEquals(70f, healthOf(inside).current, 0.0001f);
    }

    @Test
    void appliesTheSlowInsideTheCurrentRadius() {
        RecordingActor actor = new RecordingActor();
        addSeal(0f, 0f, 0.4f, 10f, 0.4f, 0.3f);
        Entity hostile = addHostile(50f, 0f, 0f, 100f, actor);

        // The seal expands with an ease-out curve; after 0.1 s it covers the target.
        engine.update(0.1f);

        SlowComponent slow = hostile.getComponent(SlowComponent.class);
        assertNotNull(slow);
        assertEquals(0.4f, slow.factor, 0.0001f);
        assertEquals(0.4f, actor.scale, 0.0001f);
    }

    @Test
    void slowExpiresAndRestoresTheMovementSpeed() {
        RecordingActor actor = new RecordingActor();
        addSeal(0f, 0f, 0.2f, 10f, 0.4f, 0.3f);
        Entity hostile = addHostile(50f, 0f, 0f, 100f, actor);

        engine.update(0.1f);
        assertNotNull(hostile.getComponent(SlowComponent.class));

        // Seal ends after 0.2 s, then the 0.3 s slow runs out.
        for (int i = 0; i < 40; i++) {
            engine.update(0.02f);
        }

        assertNull(hostile.getComponent(SlowComponent.class));
        assertEquals(1f, actor.scale, 0.0001f);
    }

    @Test
    void sealExpiresAfterItsDuration() {
        addSeal(0f, 0f, 0.3f, 10f, 0.5f, 1f);

        engine.update(0.4f);

        assertEquals(0, engine.getEntitiesFor(Family.all(SealComponent.class).get()).size());
    }

    @Test
    void pulsingKarmaFieldDealsTickDamageInsideItsRadius() {
        Entity seal = addSeal(0f, 0f, 1f, 0f, 1f, 0f);
        SealComponent field = seal.getComponent(SealComponent.class);
        field.tickDamage = 10f;
        field.tickInterval = 0.1f;

        Entity inside = addHostile(20f, 0f, 0f, 100f, null);

        engine.update(0.05f);
        assertEquals(100f, healthOf(inside).current, 0.0001f,
            "the field must not tick before its interval");

        engine.update(0.10f);
        assertEquals(90f, healthOf(inside).current, 0.0001f,
            "the first pulse must deal tick damage");

        engine.update(0.10f);
        assertEquals(80f, healthOf(inside).current, 0.0001f,
            "the field must keep pulsing");
    }

    private static final class RecordingActor implements HostileComponent.HostileActor {
        private float scale = 1f;

        @Override
        public void setSpeedScale(float scale) {
            this.scale = scale;
        }
    }
}
