package com.lychcs.koikoi.combat.system;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;
import com.lychcs.koikoi.combat.CompanionFollow;
import com.lychcs.koikoi.combat.ParticleBurst;
import com.lychcs.koikoi.combat.PlayerInput;
import com.lychcs.koikoi.combat.SealGeometry;
import com.lychcs.koikoi.combat.Targeting;
import com.lychcs.koikoi.combat.component.CompanionComponent;
import com.lychcs.koikoi.combat.component.HealthComponent;
import com.lychcs.koikoi.combat.component.HostileComponent;
import com.lychcs.koikoi.combat.component.MotionComponent;
import com.lychcs.koikoi.combat.component.ProjectileComponent;
import com.lychcs.koikoi.combat.component.TransformComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * Autonomous Yokai companions: smooth delay-follow, sine bobbing, target
 * tracking, projectile auto-attacks and the hotkey ultimate.
 *
 * <p>All numbers live in components; the system only reads them. The player
 * entity is referenced for the formation position. Hostiles are queried from the
 * engine, so the system works with any hostile source.</p>
 */
public final class CompanionSystem extends IteratingSystem {

    private static final Family HOSTILES = Family.all(
        HostileComponent.class, TransformComponent.class, HealthComponent.class).get();

    /** Angular distance between two companion slots. */
    private static final double SLOT_ANGLE_STEP = Math.PI * 2.0 / 3.0;

    private final PlayerInput input;
    private final Entity playerEntity;
    private final ParticleBurst particles;

    private final Vector2 followTarget = new Vector2();
    private final Vector2 followResult = new Vector2();
    private final List<Targeting.Target> targets = new ArrayList<>();
    private final List<Entity> hostileEntities = new ArrayList<>();

    public CompanionSystem(PlayerInput input, Entity playerEntity, ParticleBurst particles) {
        super(Family.all(CompanionComponent.class, TransformComponent.class).get(), 3);
        if (input == null) {
            throw new IllegalArgumentException("input must not be null");
        }
        if (playerEntity == null) {
            throw new IllegalArgumentException("playerEntity must not be null");
        }
        this.input = input;
        this.playerEntity = playerEntity;
        this.particles = particles;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        CompanionComponent companion = Mappers.COMPANION.get(entity);
        TransformComponent transform = Mappers.TRANSFORM.get(entity);
        TransformComponent player = Mappers.TRANSFORM.get(playerEntity);

        if (player == null) {
            return;
        }

        // 1. Delay-follow towards the formation slot behind the player.
        double slotAngle = -Math.PI * 0.5 + companion.slot * SLOT_ANGLE_STEP;
        followTarget.set(
            player.x + (float) Math.cos(slotAngle) * companion.slotRadius,
            player.y + (float) Math.sin(slotAngle) * companion.slotRadius * 0.75f);

        CompanionFollow.step(transform.x, transform.y,
            followTarget.x, followTarget.y,
            companion.followLag, deltaTime, followResult);
        transform.x = followResult.x;
        transform.y = followResult.y;

        // 2. Floating posture (sine bobbing, rendering only).
        companion.bobOffsetY = CompanionFollow.bobOffset(input.time, companion.bobPhase);
        transform.renderOffsetY = companion.bobOffsetY;

        // 3. Timers.
        companion.fireCooldownRemaining = Math.max(0f, companion.fireCooldownRemaining - deltaTime);
        companion.ultimateCooldownRemaining =
            Math.max(0f, companion.ultimateCooldownRemaining - deltaTime);

        // 4. Autonomous target tracking.
        acquireTarget(companion, transform);

        // 5. Auto-attack.
        if (companion.target != null && companion.fireCooldownRemaining <= 0f) {
            TransformComponent targetTransform = Mappers.TRANSFORM.get(companion.target);
            if (targetTransform != null) {
                spawnProjectile(transform, targetTransform, companion);
                companion.fireCooldownRemaining = Math.max(0.05f, companion.fireInterval);
            }
        }

        // 6. Active ultimate via hotkey: every ready companion answers in the same frame.
        //    The input facade clears the one-shot flag after the engine update.
        if (input.ultimateRequested && companion.isUltimateReady()) {
            triggerUltimate(transform, companion);
        }
    }

    private void acquireTarget(CompanionComponent companion, TransformComponent transform) {
        hostileEntities.clear();
        targets.clear();

        for (Entity hostileEntity : getEngine().getEntitiesFor(HOSTILES)) {
            HealthComponent health = Mappers.HEALTH.get(hostileEntity);
            HostileComponent hostile = Mappers.HOSTILE.get(hostileEntity);
            TransformComponent target = Mappers.TRANSFORM.get(hostileEntity);

            if (health == null || hostile == null || target == null || health.isDead()) {
                continue;
            }

            hostileEntities.add(hostileEntity);
            targets.add(new Targeting.Target(hostileEntities.size() - 1, target.x, target.y, true));
        }

        int best = Targeting.nearestIndex(transform.x, transform.y, companion.attackRange, targets);
        companion.target = best < 0 ? null : hostileEntities.get(best);
    }

    private void spawnProjectile(TransformComponent origin, TransformComponent target,
                                 CompanionComponent companion) {
        float dx = target.x - origin.x;
        float dy = target.y - origin.y;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length <= 0.001f) {
            return;
        }

        Entity projectile = getEngine().createEntity();
        projectile.add(new TransformComponent().set(origin.x, origin.y + 24f));

        MotionComponent motion = new MotionComponent();
        motion.vx = dx / length * companion.projectileSpeed;
        motion.vy = dy / length * companion.projectileSpeed;
        projectile.add(motion);

        ProjectileComponent projectileComponent = new ProjectileComponent();
        projectileComponent.damage = companion.damage;
        projectile.add(projectileComponent);

        getEngine().addEntity(projectile);

        if (particles != null) {
            particles.sparks(origin.x, origin.y + 24f, 2);
        }
    }

    private void triggerUltimate(TransformComponent transform, CompanionComponent companion) {
        for (Entity hostileEntity : getEngine().getEntitiesFor(HOSTILES)) {
            HealthComponent health = Mappers.HEALTH.get(hostileEntity);
            HostileComponent hostile = Mappers.HOSTILE.get(hostileEntity);
            TransformComponent target = Mappers.TRANSFORM.get(hostileEntity);

            if (health == null || hostile == null || target == null || health.isDead()) {
                continue;
            }

            if (SealGeometry.overlaps(transform.x, transform.y, companion.ultimateRadius,
                target.x, target.y, hostile.radius)) {
                health.damage(companion.ultimateDamage);
            }
        }

        companion.ultimateCooldownRemaining = companion.ultimateCooldown;

        if (particles != null) {
            particles.sparks(transform.x, transform.y, 18);
        }

        if (Gdx.app != null) {
            Gdx.app.log("CompanionSystem", "Ultimate of '" + companion.name
                + "' (radius " + Math.round(companion.ultimateRadius) + "px)");
        }
    }
}
