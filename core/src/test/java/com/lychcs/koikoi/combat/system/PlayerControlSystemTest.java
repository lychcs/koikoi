package com.lychcs.koikoi.combat.system;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.lychcs.koikoi.combat.PlayerInput;
import com.lychcs.koikoi.combat.component.CardDeckComponent;
import com.lychcs.koikoi.combat.component.CardProjectileComponent;
import com.lychcs.koikoi.combat.component.MotionComponent;
import com.lychcs.koikoi.combat.component.PlayerComponent;
import com.lychcs.koikoi.combat.component.SquashStretchComponent;
import com.lychcs.koikoi.combat.component.TransformComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ECS integration of 8-way movement, dash I-frames and squash & stretch. */
class PlayerControlSystemTest {

    private Engine engine;
    private PlayerInput input;
    private TransformComponent transform;
    private MotionComponent motion;
    private PlayerComponent playerComponent;
    private CardDeckComponent deck;
    private SquashStretchComponent squash;
    private AtomicInteger emittedParticles;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        input = new PlayerInput();
        emittedParticles = new AtomicInteger();

        engine.addSystem(new PlayerControlSystem(input,
            (x, y, dust, petals) -> emittedParticles.addAndGet(dust + petals)));

        Entity player = engine.createEntity();
        transform = new TransformComponent().set(0f, 0f);
        player.add(transform);

        motion = new MotionComponent().set(0f, 0f, 100f);
        player.add(motion);

        playerComponent = new PlayerComponent();
        playerComponent.maxMana = 100f;
        playerComponent.mana = 50f;
        playerComponent.manaRegenPerSecond = 10f;
        player.add(playerComponent);

        deck = new CardDeckComponent();
        player.add(deck);

        squash = new SquashStretchComponent();
        player.add(squash);

        engine.addEntity(player);
    }

    @Test
    void cardinalInputSetsFullSpeed() {
        input.moveX = 1f;
        engine.update(0.1f);

        assertEquals(100f, motion.vx, 0.0001f);
        assertEquals(0f, motion.vy, 0.0001f);
    }

    @Test
    void diagonalInputKeepsFullSpeed() {
        input.moveX = 1f;
        input.moveY = 1f;
        engine.update(0.1f);

        assertEquals(100f * 0.70710678f, motion.vx, 0.001f);
        assertEquals(100f * 0.70710678f, motion.vy, 0.001f);
        assertEquals(100f, motion.speed(), 0.001f);
    }

    @Test
    void manaRegeneratesUpToTheMaximum() {
        input.moveX = 0f;
        engine.update(0.1f);

        assertEquals(51f, playerComponent.mana, 0.001f);

        for (int i = 0; i < 100; i++) {
            engine.update(0.1f);
        }
        assertEquals(100f, playerComponent.mana, 0.001f);
    }

    @Test
    void rightClickTargetSteersPlayerAndStopsAtThreshold() {
        input.moveTarget.set(10f, 0f);
        input.hasMoveTarget = true;

        engine.update(0.1f);

        assertEquals(100f, motion.vx, 0.001f);
        assertEquals(0f, motion.vy, 0.001f);
        assertEquals(1f, playerComponent.facingX, 0.001f);

        transform.x = 9.9f;
        engine.update(0.1f);

        assertFalse(input.hasMoveTarget, "arrival must clear the destination");
        assertEquals(0f, motion.vx, 0.001f);
        assertEquals(0f, motion.vy, 0.001f);
    }

    @Test
    void rightClickTargetOverridesFallbackAxes() {
        input.moveX = 1f;
        input.moveY = 0f;
        input.moveTarget.set(0f, 10f);
        input.hasMoveTarget = true;

        engine.update(0.1f);

        assertEquals(0f, motion.vx, 0.001f);
        assertEquals(100f, motion.vy, 0.001f);
        assertEquals(1f, playerComponent.facingY, 0.001f);
    }

    @Test
    void qDashUsesMouseAimInsteadOfKeyboardAxes() {
        input.moveX = 1f;
        input.aimX = 0f;
        input.aimY = 50f;
        input.dashRequested = true;

        engine.update(0.016f);

        assertTrue(playerComponent.isDashing());
        assertEquals(0f, motion.vx, 0.001f);
        assertEquals(playerComponent.dashSpeed, motion.vy, 0.001f);
        assertEquals(0f, playerComponent.facingX, 0.001f);
        assertEquals(1f, playerComponent.facingY, 0.001f);
    }

    @Test
    void dashAppliesDashVelocityCooldownAndIFrames() {
        input.moveY = 1f;
        input.dashRequested = true;
        engine.update(0.016f);

        assertTrue(playerComponent.isDashing());
        assertTrue(playerComponent.isInvulnerable());
        assertEquals(playerComponent.dashSpeed, motion.vy, 0.001f);
        assertTrue(playerComponent.dashCooldownRemaining > 0f);
        assertTrue(emittedParticles.get() > 0, "dash must emit particles");
    }

    @Test
    void dashCannotRetriggerWhileOnCooldown() {
        input.dashRequested = true;
        engine.update(0.016f);

        float cooldownAfterFirstDash = playerComponent.dashCooldownRemaining;
        input.dashRequested = true;
        engine.update(0.016f);

        assertTrue(playerComponent.dashCooldownRemaining <= cooldownAfterFirstDash,
            "cooldown must keep ticking instead of resetting");
    }

    @Test
    void iFramesExpireAfterTheDash() {
        input.dashRequested = true;
        engine.update(0.016f);
        assertTrue(playerComponent.isInvulnerable());

        // The key is released again; only a new press may trigger a new dash.
        input.dashRequested = false;
        for (int i = 0; i < 40; i++) {
            engine.update(0.05f);
        }

        assertFalse(playerComponent.isInvulnerable());
        assertFalse(playerComponent.isDashing());
    }

    @Test
    void dashConsumesTheMoveOrderSoThePlayerStopsAfterwards() {
        input.moveTarget.set(100f, 0f);
        input.hasMoveTarget = true;
        input.aimX = 0f;
        input.aimY = 50f;
        input.dashRequested = true;

        engine.update(0.016f);

        assertTrue(playerComponent.isDashing());
        assertFalse(input.hasMoveTarget, "a dash must consume the right-click move order");
        assertEquals(0f, motion.vx, 0.001f);
        assertEquals(playerComponent.dashSpeed, motion.vy, 0.001f);

        // Let the dash finish: the old destination must not be resumed.
        input.dashRequested = false;
        for (int i = 0; i < 10; i++) {
            engine.update(0.05f);
        }

        assertFalse(playerComponent.isDashing());
        assertEquals(0f, motion.vx, 0.001f);
        assertEquals(0f, motion.vy, 0.001f);
    }

    @Test
    void stopCommandClearsTheMoveTargetAndHaltsImmediately() {
        input.moveTarget.set(100f, 0f);
        input.hasMoveTarget = true;
        engine.update(0.016f);
        assertEquals(100f, motion.vx, 0.001f);

        input.stopRequested = true;
        engine.update(0.016f);

        assertFalse(input.hasMoveTarget, "S must cancel the move order");
        assertEquals(0f, motion.vx, 0.001f);
        assertEquals(0f, motion.vy, 0.001f);
    }

    @Test
    void stopCommandDoesNotCancelAnActiveDash() {
        input.aimX = 100f;
        input.aimY = 0f;
        input.dashRequested = true;
        engine.update(0.016f);
        assertTrue(playerComponent.isDashing());

        input.dashRequested = false;
        input.stopRequested = true;
        engine.update(0.016f);

        assertTrue(playerComponent.isDashing(), "S must not interrupt a dash");
        assertEquals(playerComponent.dashSpeed, motion.vx, 0.001f);
    }

    @Test
    void leftClickThrowsDirectionalCardAndCooldownBlocksSpam() {
        input.aimX = 100f;
        input.aimY = 64f;
        input.basicAttackRequested = true;
        engine.update(0.016f);

        assertEquals(1, countCards(), "one left-click must spawn one card");
        CardProjectileComponent card = firstCard();
        assertEquals(1152f, card.speed, 0.01f);
        assertEquals('>', card.glyph);

        input.basicAttackRequested = true;
        engine.update(0.016f);
        assertEquals(1, countCards(), "0.4s cooldown must block another card");

        playerComponent.basicAttackCooldownRemaining = 0f;
        input.basicAttackRequested = true;
        engine.update(0.016f);
        assertEquals(2, countCards());
    }

    @Test
    void lockedBlueCardEnhancesThrowAndStartsDeckCooldown() {
        deck.press();
        deck.update(CardDeckComponent.SHUFFLE_INTERVAL);
        deck.press();
        assertEquals(CardDeckComponent.State.LOCKED, deck.state);
        assertEquals(CardDeckComponent.CardType.BLUE, deck.activeCard());

        input.aimX = 100f;
        input.aimY = 64f;
        input.basicAttackRequested = true;
        engine.update(0.016f);

        CardProjectileComponent card = firstCard();
        assertEquals(CardDeckComponent.CardType.BLUE, card.deckCard);
        assertTrue(card.refundCooldowns);
        assertEquals(30f, card.manaRefund, 0.001f);
        assertEquals(CardDeckComponent.State.COOLDOWN, deck.state);
    }

    @Test
    void cardSelectKeyStartsShuffleAndSecondPressLocks() {
        input.cardSelectRequested = true;
        engine.update(0.016f);
        assertEquals(CardDeckComponent.State.SHUFFLING, deck.state);
        assertEquals(CardDeckComponent.CardType.RED, deck.activeCard());

        // Without another press the highlight cycles through the equipped slots.
        input.cardSelectRequested = false;
        engine.update(CardDeckComponent.SHUFFLE_INTERVAL);
        assertEquals(CardDeckComponent.CardType.BLUE, deck.activeCard());

        // A second W press freezes the currently highlighted card.
        input.cardSelectRequested = true;
        engine.update(0.016f);
        assertEquals(CardDeckComponent.State.LOCKED, deck.state);
        assertEquals(CardDeckComponent.CardType.BLUE, deck.activeCard());
    }

    private int countCards() {
        return engine.getEntitiesFor(Family.all(CardProjectileComponent.class).get()).size();
    }

    private CardProjectileComponent firstCard() {
        Entity card = engine.getEntitiesFor(Family.all(CardProjectileComponent.class).get())
            .first();
        return Mappers.CARD_PROJECTILE.get(card);
    }

    @Test
    void squashFollowsTheMovementVector() {
        input.moveX = 1f;
        engine.update(0.1f);

        assertEquals(1.15f, squash.scaleX, 0.0001f);
        assertEquals(1f, squash.scaleY, 0.0001f);

        input.moveX = 0f;
        input.moveY = 1f;
        engine.update(0.1f);

        assertEquals(1f, squash.scaleX, 0.0001f);
        assertEquals(0.85f, squash.scaleY, 0.0001f);
    }
}
