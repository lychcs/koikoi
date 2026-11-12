package com.lychcs.koikoi.combat.system;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.lychcs.koikoi.combat.ParticleBurst;
import com.lychcs.koikoi.combat.component.CardBurstComponent;
import com.lychcs.koikoi.combat.component.CardProjectileComponent;
import com.lychcs.koikoi.combat.component.HealthComponent;
import com.lychcs.koikoi.combat.component.HostileComponent;
import com.lychcs.koikoi.combat.component.PlayerComponent;
import com.lychcs.koikoi.combat.component.SlowComponent;
import com.lychcs.koikoi.combat.component.TransformComponent;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Validates card travel, wall/enemy collision and impact removal. */
class CardProjectileSystemTest {

    @Test
    void cardHitsHostileAndIsRemoved() {
        Engine engine = new Engine();
        AtomicInteger particles = new AtomicInteger();
        ParticleBurst burst = (x, y, dust, petals) -> particles.addAndGet(dust + petals);
        engine.addSystem(new CardProjectileSystem(burst, (x, y) -> false));

        Entity card = cardAt(500f, 0f, 1000f);
        engine.addEntity(card);

        HealthComponent health = new HealthComponent().set(100f);
        Entity hostile = engine.createEntity();
        hostile.add(new TransformComponent().set(50f, 0f));
        hostile.add(new HostileComponent());
        hostile.add(health);
        engine.addEntity(hostile);

        engine.update(0.1f);

        assertEquals(90f, health.current, 0.001f);
        assertFalse(engine.getEntities().contains(card, true));
        assertTrue(particles.get() > 0, "card impact must spawn ASCII particles");
    }

    @Test
    void cardStopsAtWallBounds() {
        Engine engine = new Engine();
        AtomicInteger particles = new AtomicInteger();
        ParticleBurst burst = (x, y, dust, petals) -> particles.addAndGet(dust + petals);
        engine.addSystem(new CardProjectileSystem(burst, (x, y) -> x >= 20f));

        Entity card = cardAt(500f, 0f, 1000f);
        engine.addEntity(card);

        engine.update(0.1f);

        assertFalse(engine.getEntities().contains(card, true));
        assertTrue(particles.get() > 0, "wall impact must spawn ASCII particles");
    }

    @Test
    void cardExpiresAfterMaximumRange() {
        Engine engine = new Engine();
        ParticleBurst burst = (x, y, dust, petals) -> {
        };
        engine.addSystem(new CardProjectileSystem(burst, (x, y) -> false));

        Entity card = cardAt(500f, 0f, 40f);
        engine.addEntity(card);

        engine.update(0.1f);

        assertFalse(engine.getEntities().contains(card, true));
    }

    @Test
    void redCardExplodesInAnArea() {
        Engine engine = new Engine();
        engine.addSystem(new CardProjectileSystem(noopParticles(), (x, y) -> false));

        CardProjectileComponent card = cardComponentAt(500f, 0f, 1000f);
        card.areaRadius = 60f;
        card.damage = 14f;
        card.deckCard = com.lychcs.koikoi.combat.component.CardDeckComponent.CardType.RED;
        Entity projectile = new Entity();
        projectile.add(new TransformComponent().set(0f, 0f));
        projectile.add(card);
        engine.addEntity(projectile);

        HealthComponent first = spawnHostile(engine, 50f, 0f);
        HealthComponent second = spawnHostile(engine, 80f, 0f);
        engine.update(0.1f);

        assertEquals(86f, first.current, 0.001f);
        assertEquals(86f, second.current, 0.001f);
    }

    @Test
    void blueCardRefundsManaAndCooldowns() {
        Engine engine = new Engine();
        engine.addSystem(new CardProjectileSystem(noopParticles(), (x, y) -> false));

        Entity playerEntity = engine.createEntity();
        PlayerComponent player = new PlayerComponent();
        player.maxMana = 100f;
        player.mana = 10f;
        player.dashCooldownRemaining = 4f;
        player.castCooldownRemaining = 4f;
        player.basicAttackCooldownRemaining = 4f;
        playerEntity.add(player);
        engine.addEntity(playerEntity);

        CardProjectileComponent card = cardComponentAt(500f, 0f, 1000f);
        card.refundCooldowns = true;
        card.manaRefund = 30f;
        card.deckCard = com.lychcs.koikoi.combat.component.CardDeckComponent.CardType.BLUE;
        Entity projectile = new Entity();
        projectile.add(new TransformComponent().set(0f, 0f));
        projectile.add(card);
        engine.addEntity(projectile);
        spawnHostile(engine, 50f, 0f);

        engine.update(0.1f);

        assertEquals(40f, player.mana, 0.001f);
        assertEquals(0f, player.dashCooldownRemaining, 0.001f);
        assertEquals(0f, player.castCooldownRemaining, 0.001f);
        assertEquals(0f, player.basicAttackCooldownRemaining, 0.001f);
    }

    @Test
    void goldCardAppliesHighDamageAndStun() {
        Engine engine = new Engine();
        engine.addSystem(new CardProjectileSystem(noopParticles(), (x, y) -> false));

        CardProjectileComponent card = cardComponentAt(500f, 0f, 1000f);
        card.damage = 36f;
        card.stunDuration = 1.25f;
        card.deckCard = com.lychcs.koikoi.combat.component.CardDeckComponent.CardType.GOLD;
        Entity projectile = new Entity();
        projectile.add(new TransformComponent().set(0f, 0f));
        projectile.add(card);
        engine.addEntity(projectile);

        HealthComponent health = spawnHostile(engine, 50f, 0f);
        Entity target = engine.getEntitiesFor(
            com.badlogic.ashley.core.Family.all(HostileComponent.class).get()).first();

        engine.update(0.1f);

        assertEquals(64f, health.current, 0.001f);
        SlowComponent slow = Mappers.SLOW.get(target);
        assertTrue(slow != null && slow.factor <= 0.05f);
        assertTrue(slow.remaining > 1f);
    }

    @Test
    void beastLifestealHealsPlayerForCardDamage() {
        Engine engine = new Engine();
        engine.addSystem(new CardProjectileSystem(noopParticles(), (x, y) -> false));

        Entity playerEntity = engine.createEntity();
        PlayerComponent player = new PlayerComponent();
        player.grantLifesteal(0.5f, 8f);
        playerEntity.add(player);
        HealthComponent playerHealth = new HealthComponent().set(100f);
        playerHealth.current = 50f;
        playerEntity.add(playerHealth);
        engine.addEntity(playerEntity);

        CardProjectileComponent card = cardComponentAt(500f, 0f, 1000f);
        card.damage = 20f;
        Entity projectile = new Entity();
        projectile.add(new TransformComponent().set(0f, 0f));
        projectile.add(card);
        engine.addEntity(projectile);

        HealthComponent hostile = spawnHostile(engine, 50f, 0f);
        engine.update(0.1f);

        assertEquals(80f, hostile.current, 0.001f);
        assertEquals(60f, playerHealth.current, 0.001f,
            "lifesteal must heal 50% of the 20 card damage");
    }

    @Test
    void heavyCardFlashesAndKnocksTheTargetAwayFromThePlayer() {
        Engine engine = new Engine();
        engine.addSystem(new CardProjectileSystem(noopParticles(), (x, y) -> false));

        Entity playerEntity = engine.createEntity();
        playerEntity.add(new TransformComponent().set(0f, 0f));
        playerEntity.add(new PlayerComponent());
        playerEntity.add(new HealthComponent().set(100f));
        engine.addEntity(playerEntity);

        RecordingActor actor = new RecordingActor();
        HostileComponent hostileComponent = new HostileComponent();
        hostileComponent.radius = 20f;
        hostileComponent.actor = actor;
        Entity hostile = engine.createEntity();
        hostile.add(new TransformComponent().set(50f, 0f));
        hostile.add(hostileComponent);
        hostile.add(new HealthComponent().set(100f));
        engine.addEntity(hostile);

        CardProjectileComponent card = cardComponentAt(500f, 0f, 1000f);
        card.damage = 36f;
        card.deckCard = com.lychcs.koikoi.combat.component.CardDeckComponent.CardType.GOLD;
        card.knockbackDistance = 30f;
        Entity projectile = new Entity();
        projectile.add(new TransformComponent().set(0f, 0f));
        projectile.add(card);
        engine.addEntity(projectile);

        engine.update(0.1f);

        assertEquals(CardProjectileSystem.HIT_FLASH_SECONDS, actor.flashSeconds, 0.001f);
        assertEquals(30f, actor.knockbackDistance, 0.001f);
        assertTrue(actor.knockbackX > 0.9f, "knockback must point away from the player");
        assertEquals(0f, actor.knockbackY, 0.01f);
    }

    @Test
    void lockedCardImpactUsesTypeSpecificAsciiInk() {
        Engine engine = new Engine();
        StringBuilder glyphs = new StringBuilder();
        ParticleBurst particles = new ParticleBurst() {
            @Override
            public void burst(float x, float y, int dustCount, int petalCount) {
            }

            @Override
            public void glyphBurst(float x, float y, String value,
                                   float red, float green, float blue) {
                glyphs.append(value);
            }
        };
        engine.addSystem(new CardProjectileSystem(particles, (x, y) -> false));

        CardProjectileComponent card = cardComponentAt(500f, 0f, 1000f);
        card.damage = 14f;
        card.deckCard = com.lychcs.koikoi.combat.component.CardDeckComponent.CardType.RED;
        Entity projectile = new Entity();
        projectile.add(new TransformComponent().set(0f, 0f));
        projectile.add(card);
        engine.addEntity(projectile);
        spawnHostile(engine, 50f, 0f);

        engine.update(0.1f);

        assertTrue(glyphs.toString().contains("*.*"),
            "petal/Hikari impact must use its own ASCII ink, got: " + glyphs);
    }

    private static final class RecordingActor implements HostileComponent.HostileActor {
        float flashSeconds;
        float knockbackX;
        float knockbackY;
        float knockbackDistance;

        @Override
        public void setSpeedScale(float scale) {
        }

        @Override
        public void flashHit(float seconds) {
            flashSeconds = seconds;
        }

        @Override
        public void knockback(float directionX, float directionY, float distance) {
            knockbackX = directionX;
            knockbackY = directionY;
            knockbackDistance = distance;
        }
    }

    private static ParticleBurst noopParticles() {
        return (x, y, dust, petals) -> {
        };
    }

    private static HealthComponent spawnHostile(Engine engine, float x, float y) {
        HealthComponent health = new HealthComponent().set(100f);
        Entity hostile = engine.createEntity();
        hostile.add(new TransformComponent().set(x, y));
        hostile.add(new HostileComponent());
        hostile.add(health);
        engine.addEntity(hostile);
        return health;
    }

    private static CardProjectileComponent cardComponentAt(float vx, float vy, float maxRange) {
        CardProjectileComponent projectile = new CardProjectileComponent();
        projectile.vx = vx;
        projectile.vy = vy;
        projectile.maxRange = maxRange;
        return projectile;
    }

    private static Entity cardAt(float vx, float vy, float maxRange) {
        Entity card = new Entity();
        card.add(new TransformComponent().set(0f, 0f));
        card.add(cardComponentAt(vx, vy, maxRange));
        return card;
    }

    @Test
    void cardDivingIntoTheTerrainBurstsAndIsDestroyed() {
        Engine engine = new Engine();
        AtomicInteger particles = new AtomicInteger();
        ParticleBurst burst = (x, y, dust, petals) -> particles.addAndGet(dust + petals);
        engine.addSystem(new CardProjectileSystem(burst, (x, y) -> false,
            (x, z) -> 2f, 1f / 64f));

        CardProjectileComponent card = cardComponentAt(500f, 0f, 1000f);
        card.hasWorldRay = true;
        card.rayOriginY = 0.3f; // far below the 2-unit hillside
        card.rayDirY = 0f;
        Entity projectile = new Entity();
        projectile.add(new TransformComponent().set(0f, 0f));
        projectile.add(card);
        engine.addEntity(projectile);

        engine.update(0.1f);

        assertFalse(engine.getEntities().contains(projectile, true),
            "a card that dips below the terrain must be destroyed, not slide");
        assertTrue(particles.get() > 0,
            "terrain impact must spawn ASCII impact particles");
        assertTrue(engine.getEntitiesFor(Family.all(CardBurstComponent.class).get()).size() > 0,
            "terrain impact must spawn the 3D world burst");
    }

    @Test
    void cardImpactSpawnsTheMatching3DBurst() {
        Engine engine = new Engine();
        engine.addSystem(new CardProjectileSystem(noopParticles(), (x, y) -> false));

        CardProjectileComponent card = cardComponentAt(500f, 0f, 1000f);
        card.deckCard = com.lychcs.koikoi.combat.component.CardDeckComponent.CardType.BLUE;
        Entity projectile = new Entity();
        projectile.add(new TransformComponent().set(0f, 0f));
        projectile.add(card);
        engine.addEntity(projectile);
        spawnHostile(engine, 50f, 0f);

        engine.update(0.1f);

        CardBurstComponent.Style ribbon = null;
        boolean rune = false;
        boolean flash = false;
        for (Entity candidate : engine.getEntitiesFor(
            Family.all(CardBurstComponent.class).get())) {
            CardBurstComponent burst = Mappers.CARD_BURST.get(candidate);
            if (burst.style == CardBurstComponent.Style.RIBBON) {
                ribbon = burst.style;
            } else if (burst.style == CardBurstComponent.Style.RUNE) {
                rune = true;
            } else if (burst.style == CardBurstComponent.Style.FLASH) {
                flash = true;
            }
        }
        assertEquals(CardBurstComponent.Style.RIBBON, ribbon,
            "a ribbon card impact must spawn its waving ink burst");
        assertTrue(rune, "hostile hits must stamp the ground Karma rune burst");
        assertTrue(flash, "hostile hits must spawn the white-ink flash");
    }
}
