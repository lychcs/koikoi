package com.lychcs.koikoi.combat.system;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.lychcs.koikoi.combat.PlayerInput;
import com.lychcs.koikoi.combat.component.CompanionComponent;
import com.lychcs.koikoi.combat.component.HealthComponent;
import com.lychcs.koikoi.combat.component.HostileComponent;
import com.lychcs.koikoi.combat.component.MotionComponent;
import com.lychcs.koikoi.combat.component.PlayerComponent;
import com.lychcs.koikoi.combat.component.ProjectileComponent;
import com.lychcs.koikoi.combat.component.TransformComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ECS integration of companion follow, target tracking and auto-attack. */
class CompanionSystemTest {

    private Engine engine;
    private PlayerInput input;
    private Entity player;
    private Entity companion;
    private CompanionComponent companionComponent;
    private TransformComponent companionTransform;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        input = new PlayerInput();

        player = engine.createEntity();
        player.add(new TransformComponent().set(0f, 0f));
        player.add(new PlayerComponent());
        player.add(new MotionComponent());
        engine.addEntity(player);

        companion = engine.createEntity();
        companionTransform = new TransformComponent().set(0f, 0f);
        companion.add(companionTransform);

        companionComponent = new CompanionComponent();
        companionComponent.name = "Test-Oni";
        companionComponent.slot = 0;
        companionComponent.slotRadius = 50f;
        companionComponent.attackRange = 200f;
        companionComponent.fireInterval = 1f;
        companionComponent.fireCooldownRemaining = 0f;
        companionComponent.damage = 10f;
        companionComponent.projectileSpeed = 100f;
        companionComponent.ultimateRadius = 200f;
        companionComponent.ultimateDamage = 25f;
        companionComponent.ultimateCooldown = 5f;
        companion.add(companionComponent);

        engine.addEntity(companion);
        engine.addSystem(new CompanionSystem(input, player, null));
    }

    private Entity addHostile(float x, float y, float health) {
        Entity hostile = engine.createEntity();
        hostile.add(new TransformComponent().set(x, y));

        HostileComponent hostileComponent = new HostileComponent();
        hostileComponent.radius = 20f;
        hostile.add(hostileComponent);

        hostile.add(new HealthComponent().set(health));
        engine.addEntity(hostile);
        return hostile;
    }

    @Test
    void acquiresTheNearestHostileInRange() {
        Entity far = addHostile(180f, 0f, 100f);
        Entity near = addHostile(90f, 0f, 100f);

        engine.update(0.016f);

        assertSame(near, companionComponent.target);
        assertFalse(companionComponent.target == far);
    }

    @Test
    void ignoresHostilesOutsideTheAttackRange() {
        addHostile(companionComponent.attackRange + 50f, 0f, 100f);

        engine.update(0.016f);

        assertNull(companionComponent.target);
        assertEquals(0, engine.getEntitiesFor(Family.all(ProjectileComponent.class).get()).size());
    }

    @Test
    void firesAProjectileAtTheTrackedTarget() {
        addHostile(120f, 0f, 100f);

        engine.update(0.016f);

        assertEquals(1, engine.getEntitiesFor(Family.all(ProjectileComponent.class).get()).size());
        Entity projectile = engine.getEntitiesFor(Family.all(ProjectileComponent.class).get()).first();
        MotionComponent motion = projectile.getComponent(MotionComponent.class);
        assertNotNull(motion);
        assertTrue(motion.vx > 0f, "projectile must fly towards the target on the right");
        assertEquals(100f, motion.speed(), 1f);
    }

    @Test
    void ultimateDamagesNearbyHostilesAndStartsCooldown() {
        Entity hostile = addHostile(120f, 0f, 100f);
        HealthComponent health = hostile.getComponent(HealthComponent.class);

        input.ultimateRequested = true;
        engine.update(0.016f);

        assertEquals(75f, health.current, 0.0001f);
        assertTrue(companionComponent.ultimateCooldownRemaining > 0f);
        input.clearOneShots();
        assertFalse(input.ultimateRequested, "the facade clears the one-shot request");
    }

    @Test
    void followsThePlayerWithADelay() {
        engine.update(0.016f);

        assertTrue(companionTransform.y < 0f, "companion should drift to its slot below the player");
        assertTrue(companionTransform.y > -50f, "the follow must not snap instantly");
    }

    @Test
    void appliesTheSineBobbingOffset() {
        input.time = (float) (Math.PI / 6.0);
        engine.update(0.016f);

        assertEquals(4f, companionComponent.bobOffsetY, 0.0001f);
        assertEquals(4f, companionTransform.renderOffsetY, 0.0001f);
    }
}
