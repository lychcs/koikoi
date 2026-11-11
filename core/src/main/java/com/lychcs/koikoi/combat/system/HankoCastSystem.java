package com.lychcs.koikoi.combat.system;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;
import com.lychcs.koikoi.combat.HankoCastConfig;
import com.lychcs.koikoi.combat.ParticleBurst;
import com.lychcs.koikoi.combat.PlayerInput;
import com.lychcs.koikoi.combat.SealGeometry;
import com.lychcs.koikoi.combat.component.PlayerComponent;
import com.lychcs.koikoi.combat.component.SealComponent;
import com.lychcs.koikoi.combat.component.TransformComponent;

/**
 * Projects the selected Hanko seal onto the floor at the aim position.
 *
 * <p>The aim is clamped to the caster range and snapped to the tile grid. A cast
 * costs mana, starts the cast cooldown and spawns a {@link SealComponent} entity;
 * damage and slow are applied by {@link SealSystem}.</p>
 */
public final class HankoCastSystem extends IteratingSystem {

    /** Initial radius of a freshly spawned seal. */
    public static final float START_RADIUS = 26f;

    private final PlayerInput input;
    private final HankoCastConfig config;
    private final ParticleBurst particles;
    private final float gridSize;

    private final Vector2 target = new Vector2();

    public HankoCastSystem(PlayerInput input, HankoCastConfig config, float gridSize,
                           ParticleBurst particles) {
        super(Family.all(PlayerComponent.class, TransformComponent.class).get(), 1);
        if (input == null || config == null) {
            throw new IllegalArgumentException("input and config must not be null");
        }
        this.input = input;
        this.config = config;
        this.gridSize = gridSize;
        this.particles = particles;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        if (!input.castRequested) {
            return;
        }

        PlayerComponent player = Mappers.PLAYER.get(entity);
        TransformComponent transform = Mappers.TRANSFORM.get(entity);

        if (player.castCooldownRemaining > 0f || player.mana < config.manaCost) {
            return;
        }

        // Clamp the aim to the cast range and snap it to the floor grid.
        SealGeometry.clampCastTarget(transform.x, transform.y, input.aimX, input.aimY,
            config.castRange, target);
        SealGeometry.snapToGrid(target.x, target.y, gridSize, target);

        // Face the seal.
        float dx = target.x - transform.x;
        float dy = target.y - transform.y;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length > 0.001f) {
            player.facingX = dx / length;
            player.facingY = dy / length;
        }

        if (!player.spendMana(config.manaCost)) {
            return;
        }
        player.castCooldownRemaining = config.cooldown;
        input.castRequested = false;

        Entity seal = getEngine().createEntity();
        seal.add(new TransformComponent().set(target.x, target.y));

        SealComponent sealComponent = new SealComponent();
        sealComponent.duration = config.sealDuration;
        sealComponent.startRadius = START_RADIUS;
        sealComponent.endRadius = config.radius;
        sealComponent.damage = config.damage;
        sealComponent.slowFactor = config.slowFactor;
        sealComponent.slowDuration = config.slowDuration;
        sealComponent.label = config.label;
        sealComponent.red = config.red;
        sealComponent.green = config.green;
        sealComponent.blue = config.blue;
        seal.add(sealComponent);

        getEngine().addEntity(seal);

        if (particles != null) {
            particles.sparks(target.x, target.y, 8);
        }

        if (Gdx.app != null) {
            Gdx.app.log("HankoCastSystem", "Cast '" + config.label + "' at "
                + Math.round(target.x) + "," + Math.round(target.y)
                + " (mana " + Math.round(player.mana) + "/" + Math.round(player.maxMana) + ")");
        }
    }
}
