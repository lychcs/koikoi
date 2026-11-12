package com.lychcs.koikoi.combat;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.lychcs.koikoi.combat.component.CardDeckComponent;
import com.lychcs.koikoi.combat.component.CompanionComponent;
import com.lychcs.koikoi.combat.component.HealthComponent;
import com.lychcs.koikoi.combat.component.HostileComponent;
import com.lychcs.koikoi.combat.component.MotionComponent;
import com.lychcs.koikoi.combat.component.OrbitComponent;
import com.lychcs.koikoi.combat.component.ParticleComponent;
import com.lychcs.koikoi.combat.component.PlayerComponent;
import com.lychcs.koikoi.combat.component.SquashStretchComponent;
import com.lychcs.koikoi.combat.component.TransformComponent;
import com.lychcs.koikoi.combat.system.CardProjectileSystem;
import com.lychcs.koikoi.combat.system.CardBurstSystem;
import com.lychcs.koikoi.combat.system.CompanionSystem;
import com.lychcs.koikoi.combat.system.HankoCastSystem;
import com.lychcs.koikoi.combat.system.HostileCleanupSystem;
import com.lychcs.koikoi.combat.system.Mappers;
import com.lychcs.koikoi.combat.system.OrbitSystem;
import com.lychcs.koikoi.combat.system.ParticleSystem;
import com.lychcs.koikoi.combat.system.PlayerControlSystem;
import com.lychcs.koikoi.combat.system.ProjectileSystem;
import com.lychcs.koikoi.combat.system.SealSystem;
import com.lychcs.koikoi.combat.system.StatusSystem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ashley ECS facade of the overworld combat layer.
 *
 * <p>Creates the player and all manual-combat systems once, and exposes the
 * player/hostile entities for rendering and for the Box2D adapter in the
 * screen. The floating companion/orbit layer is retired by
 * {@link #FLOATING_ORBITS_ENABLED}. Every active system is a normal Ashley
 * {@code IteratingSystem}; the engine order is explicit:</p>
 * <pre>
 * 0 control → 1 cast → 2 seal → 4 cards/projectiles → 5 status → 6 bursts
 * → 7 particles → 9 cleanup
 * </pre>
 */
public final class OverworldEcs {

    /** The GDD defines three initial Yokai battle pets. */
    public static final int MAX_COMPANIONS = 3;

    /**
     * Floating Omamori/companion orbit layer. Retired for manual-only combat:
     * no companion entities, orbit glyphs, auto-attacks or companion ultimates
     * are created in the overworld.
     */
    public static final boolean FLOATING_ORBITS_ENABLED = false;

    /** One orbiting Omamori glyph (created from the active amulet slots). */
    public record OrbitGlyph(String glyph, float red, float green, float blue) {
        public OrbitGlyph {
            if (glyph == null || glyph.isEmpty()) {
                throw new IllegalArgumentException("glyph must not be empty");
            }
        }
    }

    private final Engine engine = new Engine();
    private final PlayerInput input = new PlayerInput();
    private final HankoCastConfig hankoConfig = new HankoCastConfig();

    private final Entity playerEntity;
    private final List<Entity> companionEntities = new ArrayList<>();
    private final List<Entity> orbitEntities = new ArrayList<>();
    private final List<Entity> hostileEntities = new ArrayList<>();

    private final ParticleBurst particleBurst = new ParticleBurst() {
        @Override
        public void burst(float x, float y, int dustCount, int petalCount) {
            emitParticles(x, y, dustCount, petalCount);
        }

        @Override
        public void sparks(float x, float y, int count) {
            OverworldEcs.this.sparks(x, y, count);
        }

        @Override
        public void glyphBurst(float x, float y, String glyphs,
                               float red, float green, float blue) {
            OverworldEcs.this.glyphBurst(x, y, glyphs, red, green, blue);
        }
    };

    public OverworldEcs(float playerX, float playerY,
                        List<CompanionSpec> companions,
                        CombatStats stats,
                        float gridSize,
                        HostileDefeatListener defeatListener,
                        CardProjectileSystem.WallQuery wallQuery) {
        this(playerX, playerY, companions, stats, gridSize, defeatListener, wallQuery,
            null, 1f / 64f);
    }

    public OverworldEcs(float playerX, float playerY,
                        List<CompanionSpec> companions,
                        CombatStats stats,
                        float gridSize,
                        HostileDefeatListener defeatListener,
                        CardProjectileSystem.WallQuery wallQuery,
                        CardProjectileSystem.TerrainQuery terrainQuery,
                        float worldScale) {
        if (stats == null) {
            throw new IllegalArgumentException("stats must not be null");
        }

        playerEntity = createPlayer(playerX, playerY, stats);

        // Systems in explicit execution order.
        engine.addSystem(new PlayerControlSystem(input, particleBurst, gridSize));
        engine.addSystem(new HankoCastSystem(input, hankoConfig, gridSize, particleBurst));
        engine.addSystem(new SealSystem(particleBurst));
        if (FLOATING_ORBITS_ENABLED) {
            engine.addSystem(new CompanionSystem(input, playerEntity, particleBurst));
        }
        engine.addSystem(new CardProjectileSystem(
            particleBurst, wallQuery, terrainQuery, worldScale));
        engine.addSystem(new ProjectileSystem(particleBurst));
        engine.addSystem(new StatusSystem());
        engine.addSystem(new CardBurstSystem());
        if (FLOATING_ORBITS_ENABLED) {
            engine.addSystem(new OrbitSystem(input));
        }
        engine.addSystem(new ParticleSystem());
        engine.addSystem(new HostileCleanupSystem(defeatListener));

        if (FLOATING_ORBITS_ENABLED && companions != null) {
            for (CompanionSpec spec : companions) {
                addCompanion(spec, stats);
            }
        }

        applyStats(stats);
    }

    private Entity createPlayer(float x, float y, CombatStats stats) {
        Entity player = engine.createEntity();
        player.add(new TransformComponent().set(x, y));
        player.add(new MotionComponent().set(0f, 0f, 120f));

        PlayerComponent playerComponent = new PlayerComponent();
        playerComponent.maxMana = 100f;
        playerComponent.mana = 100f;
        playerComponent.manaRegenPerSecond = stats.manaRegenPerSecond();
        playerComponent.dashCooldown = stats.dashCooldown();
        player.add(playerComponent);
        player.add(new HealthComponent().set(140f));
        player.add(new CardDeckComponent());

        player.add(new SquashStretchComponent());
        engine.addEntity(player);
        return player;
    }

    /** Adds one Yokai companion at its formation slot (max {@value #MAX_COMPANIONS}). */
    public Entity addCompanion(CompanionSpec spec, CombatStats stats) {
        if (!FLOATING_ORBITS_ENABLED) {
            return null;
        }
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        if (stats == null) {
            throw new IllegalArgumentException("stats must not be null");
        }
        if (companionEntities.size() >= MAX_COMPANIONS) {
            throw new IllegalStateException("At most " + MAX_COMPANIONS + " companions are allowed");
        }

        TransformComponent playerTransform = Mappers.TRANSFORM.get(playerEntity);
        double slotAngle = -Math.PI * 0.5 + spec.slot() * (Math.PI * 2.0 / 3.0);

        Entity companion = engine.createEntity();
        companion.add(new TransformComponent().set(
            playerTransform.x + (float) Math.cos(slotAngle) * 54f,
            playerTransform.y + (float) Math.sin(slotAngle) * 40f));

        CompanionComponent companionComponent = new CompanionComponent();
        companionComponent.name = spec.name();
        companionComponent.slot = spec.slot();
        companionComponent.damage = spec.damage();
        companionComponent.baseFireInterval = spec.baseFireInterval();
        companionComponent.fireInterval = Math.max(0.05f,
            spec.baseFireInterval() / stats.companionFireRate());
        companionComponent.projectileSpeed = spec.projectileSpeed();
        companionComponent.attackRange = spec.attackRange();
        companionComponent.ultimateRadius = spec.ultimateRadius();
        companionComponent.ultimateDamage = spec.ultimateDamage();
        companionComponent.ultimateCooldown = spec.ultimateCooldown();
        companionComponent.bobPhase = spec.slot() * 1.7f;
        companion.add(companionComponent);

        engine.addEntity(companion);
        companionEntities.add(companion);
        return companion;
    }

    /** Adds a hostile entity (wild Yokai). Returns the created entity. */
    public Entity addHostile(String encounterId, float x, float y, float radius,
                             float maxHealth, HostileComponent.HostileActor actor) {
        Entity hostile = engine.createEntity();
        hostile.add(new TransformComponent().set(x, y));

        HostileComponent hostileComponent = new HostileComponent();
        hostileComponent.encounterId = encounterId == null ? "" : encounterId;
        hostileComponent.radius = radius;
        hostileComponent.actor = actor;
        hostile.add(hostileComponent);

        hostile.add(new HealthComponent().set(maxHealth));

        engine.addEntity(hostile);
        hostileEntities.add(hostile);
        return hostile;
    }

    /** Removes a hostile entity (defeat or screen teardown). */
    public void removeHostile(Entity hostile) {
        if (hostile == null) {
            return;
        }
        hostileEntities.remove(hostile);
        engine.removeEntity(hostile);
    }

    /** Rebuilds the orbiting Omamori glyph entities from the active slots. */
    public void rebuildOmamoriOrbits(List<OrbitGlyph> glyphs) {
        for (Entity orbit : orbitEntities) {
            engine.removeEntity(orbit);
        }
        orbitEntities.clear();

        if (!FLOATING_ORBITS_ENABLED || glyphs == null || glyphs.isEmpty()) {
            return;
        }

        TransformComponent playerTransform = Mappers.TRANSFORM.get(playerEntity);
        float angleStep = (float) (Math.PI * 2.0 / glyphs.size());

        for (int i = 0; i < glyphs.size(); i++) {
            OrbitGlyph glyph = glyphs.get(i);

            Entity orbit = engine.createEntity();
            orbit.add(new TransformComponent().set(playerTransform.x, playerTransform.y));

            OrbitComponent orbitComponent = new OrbitComponent();
            orbitComponent.owner = playerEntity;
            orbitComponent.angle = i * angleStep;
            orbitComponent.angularSpeed = 1.1f + i * 0.12f;
            orbitComponent.radius = 46f + i * 6f;
            orbitComponent.bobPhase = i * 0.9f;
            orbitComponent.glyph = glyph.glyph();
            orbitComponent.red = glyph.red();
            orbitComponent.green = glyph.green();
            orbitComponent.blue = glyph.blue();
            orbit.add(orbitComponent);

            engine.addEntity(orbit);
            orbitEntities.add(orbit);
        }
    }

    /** Applies (possibly Omamori-buffed) combat stats to all existing entities. */
    public void applyStats(CombatStats stats) {
        PlayerComponent player = getPlayer();
        if (player != null) {
            player.manaRegenPerSecond = stats.manaRegenPerSecond();
            player.dashCooldown = stats.dashCooldown();
        }

        hankoConfig.radius = stats.sealRadius();

        for (int i = 0; i < companionEntities.size(); i++) {
            CompanionComponent companion = Mappers.COMPANION.get(companionEntities.get(i));
            if (companion != null) {
                companion.fireInterval = Math.max(0.05f,
                    companion.baseFireInterval / stats.companionFireRate());
            }
        }
    }

    // ------------------------------------------------------------------
    // Frame API
    // ------------------------------------------------------------------

    /** Advances all systems by {@code deltaTime} seconds. */
    public void update(float deltaTime) {
        engine.update(deltaTime);
        input.clearOneShots();
    }

    /** Removes every entity (used when the screen is disposed). */
    public void clear() {
        engine.removeAllEntities();
        companionEntities.clear();
        orbitEntities.clear();
        hostileEntities.clear();
    }

    public Engine getEngine() {
        return engine;
    }

    public PlayerInput getInput() {
        return input;
    }

    public HankoCastConfig getHankoConfig() {
        return hankoConfig;
    }

    public Entity getPlayerEntity() {
        return playerEntity;
    }

    public PlayerComponent getPlayer() {
        return Mappers.PLAYER.get(playerEntity);
    }

    public CardDeckComponent getPlayerDeck() {
        return Mappers.CARD_DECK.get(playerEntity);
    }

    public TransformComponent getPlayerTransform() {
        return Mappers.TRANSFORM.get(playerEntity);
    }

    public MotionComponent getPlayerMotion() {
        return Mappers.MOTION.get(playerEntity);
    }

    public SquashStretchComponent getPlayerSquash() {
        return Mappers.SQUASH.get(playerEntity);
    }

    public List<Entity> getCompanions() {
        return Collections.unmodifiableList(companionEntities);
    }

    public List<Entity> getOrbits() {
        return Collections.unmodifiableList(orbitEntities);
    }

    public List<Entity> getHostiles() {
        return Collections.unmodifiableList(hostileEntities);
    }

    public ParticleBurst getParticleBurst() {
        return particleBurst;
    }

    // ------------------------------------------------------------------
    // Procedural particles (ParticleBurst implementation)
    // ------------------------------------------------------------------

    private void emitParticles(float x, float y, int dustCount, int petalCount) {
        for (int i = 0; i < dustCount; i++) {
            spawnParticle(x, y, ".",
                0.62f, 0.60f, 0.55f,
                MathUtils.random(35f, 95f), 0.35f, 0.65f);
        }
        for (int i = 0; i < petalCount; i++) {
            spawnParticle(x, y, "*",
                1f, 0.62f, 0.78f,
                MathUtils.random(55f, 130f), 0.6f, 1.15f);
        }
    }

    /** Public spark burst for screens that want extra exorcism sparks. */
    public void sparks(float x, float y, int count) {
        for (int i = 0; i < count; i++) {
            spawnParticle(x, y, "+",
                1f, 0.86f, 0.38f,
                MathUtils.random(70f, 160f), 0.25f, 0.5f);
        }
    }

    /** ASCII impact glyphs such as the red card's {@code *#%*} explosion. */
    public void glyphBurst(float x, float y, String glyphs,
                           float red, float green, float blue) {
        if (glyphs == null || glyphs.isEmpty()) {
            return;
        }
        for (int i = 0; i < glyphs.length(); i++) {
            spawnParticle(x, y, String.valueOf(glyphs.charAt(i)),
                red, green, blue,
                MathUtils.random(60f, 170f), 0.35f, 0.7f);
        }
    }

    private void spawnParticle(float x, float y, String glyph,
                               float red, float green, float blue,
                               float speed, float minLifetime, float maxLifetime) {
        Entity particle = engine.createEntity();
        particle.add(new TransformComponent().set(x, y));

        float angle = MathUtils.random(0f, MathUtils.PI2);
        MotionComponent motion = new MotionComponent();
        motion.vx = MathUtils.cos(angle) * speed;
        motion.vy = MathUtils.sin(angle) * speed;
        particle.add(motion);

        ParticleComponent particleComponent = new ParticleComponent();
        particleComponent.glyph = glyph;
        particleComponent.red = red;
        particleComponent.green = green;
        particleComponent.blue = blue;
        particleComponent.lifetime = MathUtils.random(minLifetime, maxLifetime);
        particle.add(particleComponent);

        engine.addEntity(particle);
    }
}
