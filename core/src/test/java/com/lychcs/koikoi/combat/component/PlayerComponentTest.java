package com.lychcs.koikoi.combat.component;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Validates the temporary Beast-Yaku lifesteal buff. */
class PlayerComponentTest {

    @Test
    void lifestealBuffClampsAndExpires() {
        PlayerComponent player = new PlayerComponent();
        player.grantLifesteal(1.4f, 2f);
        assertEquals(1f, player.lifestealPercent, 0.0001f);
        assertEquals(2f, player.lifestealRemaining, 0.0001f);

        player.updateBuffs(1.5f);
        assertEquals(0.5f, player.lifestealRemaining, 0.0001f);
        assertEquals(1f, player.lifestealPercent, 0.0001f);

        player.updateBuffs(1f);
        assertEquals(0f, player.lifestealRemaining, 0.0001f);
        assertEquals(0f, player.lifestealPercent, 0.0001f);
    }

    @Test
    void grantingAgainRefreshesTheLongestDuration() {
        PlayerComponent player = new PlayerComponent();
        player.grantLifesteal(0.2f, 3f);
        player.grantLifesteal(0.4f, 1f);

        assertEquals(0.4f, player.lifestealPercent, 0.0001f);
        assertEquals(3f, player.lifestealRemaining, 0.0001f);
    }
}