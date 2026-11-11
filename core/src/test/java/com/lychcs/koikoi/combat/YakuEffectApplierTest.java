package com.lychcs.koikoi.combat;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.lychcs.koikoi.combat.component.CardDeckComponent;
import com.lychcs.koikoi.combat.component.PlayerComponent;
import com.lychcs.koikoi.combat.component.SealComponent;
import com.lychcs.koikoi.combat.component.SlowComponent;
import com.lychcs.koikoi.run.YakuEffectKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** ECS-level verification of every unique Yaku payload. */
class YakuEffectApplierTest {

    private OverworldEcs ecs;
    private YakuEffectApplier applier;

    @BeforeEach
    void setUp() {
        ecs = new OverworldEcs(0f, 0f, List.of(), CombatStats.BASE, 64f, null,
            (x, y) -> false);
        applier = new YakuEffectApplier(ecs);
    }

    @Test
    void karmaResetsDashAndSpawnsPulsingRuneField() {
        PlayerComponent player = ecs.getPlayer();
        player.dashCooldownRemaining = 5f;

        applier.apply(YakuEffectKind.KARMA_FIELD);

        assertEquals(0f, player.dashCooldownRemaining, 0.0001f);
        var seals = ecs.getEngine().getEntitiesFor(Family.all(SealComponent.class).get());
        assertEquals(1, seals.size());
        SealComponent field = seals.first().getComponent(SealComponent.class);
        assertEquals(YakuEffectApplier.KARMA_TICK_DAMAGE, field.tickDamage, 0.0001f);
        assertEquals(YakuEffectApplier.KARMA_TICK_INTERVAL, field.tickInterval, 0.0001f);
        assertEquals(YakuEffectApplier.KARMA_DURATION, field.duration, 0.0001f);
    }

    @Test
    void beastGrantsLifestealBuff() {
        applier.apply(YakuEffectKind.LIFESTEAL);

        PlayerComponent player = ecs.getPlayer();
        assertEquals(YakuEffectApplier.LIFESTEAL_PERCENT, player.lifestealPercent, 0.0001f);
        assertEquals(YakuEffectApplier.LIFESTEAL_SECONDS, player.lifestealRemaining, 0.0001f);
    }

    @Test
    void ribbonRefundsManaAndResetsWCooldown() {
        PlayerComponent player = ecs.getPlayer();
        player.mana = 10f;
        CardDeckComponent deck = ecs.getPlayerDeck();
        deck.press();
        deck.update(CardDeckComponent.SHUFFLE_MAX_SECONDS);
        assertEquals(CardDeckComponent.State.COOLDOWN, deck.state);

        applier.apply(YakuEffectKind.RESOURCE_REFUND);

        assertEquals(50f, player.mana, 0.0001f);
        assertEquals(CardDeckComponent.State.IDLE, deck.state);
        assertEquals(0f, deck.cooldownRemaining, 0.0001f);
    }

    @Test
    void petalSpreadsSlowOnlyToNearbyHostiles() {
        Entity near = ecs.addHostile("near", 100f, 0f, 20f, 100f, null);
        Entity far = ecs.addHostile("far", 500f, 0f, 20f, 100f, null);

        applier.apply(YakuEffectKind.PETAL_SPREAD);

        SlowComponent slow = near.getComponent(SlowComponent.class);
        assertNotNull(slow, "nearby hostile must receive the petal status");
        assertEquals(YakuEffectApplier.PETAL_SLOW_FACTOR, slow.factor, 0.0001f);
        assertNull(far.getComponent(SlowComponent.class),
            "hostiles outside the petal radius must stay untouched");
    }

    @Test
    void noneAndNullPayloadsDoNothing() {
        PlayerComponent player = ecs.getPlayer();
        player.dashCooldownRemaining = 4f;

        applier.apply(YakuEffectKind.NONE);
        applier.apply(null);

        assertEquals(4f, player.dashCooldownRemaining, 0.0001f);
        assertEquals(0, ecs.getEngine().getEntitiesFor(
            Family.all(SealComponent.class).get()).size());
    }
}