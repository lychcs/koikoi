package com.lychcs.koikoi.run;

import com.lychcs.koikoi.scoring.YakuType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Validates the unique overworld payload of every Yaku family. */
class YakuEffectsTest {

    @Test
    void highTierYakuGrantTheKarmaField() {
        assertEquals(YakuEffectKind.KARMA_FIELD, YakuEffects.kindFor(YakuType.TRUE_SEASON));
        assertEquals(YakuEffectKind.KARMA_FIELD, YakuEffects.kindFor(YakuType.IMPERIAL_COURT));
        assertEquals(YakuEffectKind.KARMA_FIELD, YakuEffects.kindFor(YakuType.DUSK_AND_DAWN));
        assertEquals(YakuEffectKind.KARMA_FIELD, YakuEffects.kindFor(YakuType.MONOCHROME));
        assertEquals(YakuEffectKind.KARMA_FIELD, YakuEffects.kindFor(YakuType.HARMONY));
    }

    @Test
    void beastYakuGrantLifesteal() {
        for (YakuType type : new YakuType[] {
            YakuType.BEAST_PAIR_SPRING, YakuType.BEAST_PAIR_SUMMER,
            YakuType.BEAST_PAIR_AUTUMN, YakuType.BEAST_PAIR_WINTER,
            YakuType.FOUR_BEASTS, YakuType.WILD_COURT}) {
            assertEquals(YakuEffectKind.LIFESTEAL, YakuEffects.kindFor(type), type.name());
        }
    }

    @Test
    void ribbonAndPetalYakuGrantTheirSpecificPayloads() {
        assertEquals(YakuEffectKind.RESOURCE_REFUND, YakuEffects.kindFor(YakuType.FOUR_RIBBONS));
        assertEquals(YakuEffectKind.PETAL_SPREAD, YakuEffects.kindFor(YakuType.FOUR_PETALS));
        assertEquals(YakuEffectKind.PETAL_SPREAD,
            YakuEffects.kindFor(YakuType.FOUR_OF_ONE_SEASON));
    }

    @Test
    void onlyTheLoneSpiritFallbackHasNoPayload() {
        assertFalse(YakuEffects.hasPayload(YakuType.LONE_SPIRIT));
        assertTrue(YakuEffects.hasPayload(YakuType.HARMONY));
        assertTrue(YakuEffects.hasPayload(YakuType.FOUR_OF_ONE_SEASON));
        assertFalse(YakuEffects.hasPayload(null));
    }

    @Test
    void everyComboYakuHasAPayload() {
        for (YakuType type : YakuType.values()) {
            if (type == YakuType.LONE_SPIRIT) {
                continue;
            }
            assertTrue(YakuEffects.hasPayload(type), "missing payload for " + type);
        }
    }

    @Test
    void sessionQueuesOnlyPayloadBearingYakuAndDrainsOnce() {
        RunSession session = new RunSession();
        session.queueYakuEffect(YakuType.FOUR_BEASTS);
        session.queueYakuEffect(YakuType.LONE_SPIRIT);
        session.queueYakuEffect(YakuType.FOUR_RIBBONS);

        assertEquals(2, session.pendingYakuEffectCount());
        assertEquals(List.of(YakuType.FOUR_BEASTS, YakuType.FOUR_RIBBONS),
            session.drainPendingYakuEffects());
        assertEquals(0, session.pendingYakuEffectCount());
        assertTrue(session.drainPendingYakuEffects().isEmpty());
    }
}