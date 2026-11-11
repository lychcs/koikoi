package com.lychcs.koikoi.run;

import com.lychcs.koikoi.scoring.YakuType;

/**
 * Maps every {@link YakuType} to its unique overworld payload.
 *
 * <p>Pure data: the card battle queues the payload, and the next overworld
 * applies it to the ECS. Keeping the mapping here prevents UI/scoring code from
 * knowing about player cooldowns or particles.</p>
 */
public final class YakuEffects {

    private YakuEffects() {
    }

    /** Payload granted by the given Yaku, or {@link YakuEffectKind#NONE}. */
    public static YakuEffectKind kindFor(YakuType type) {
        if (type == null) {
            return YakuEffectKind.NONE;
        }
        return switch (type) {
            case TRUE_SEASON, IMPERIAL_COURT, DUSK_AND_DAWN,
                 MONOCHROME, HARMONY -> YakuEffectKind.KARMA_FIELD;

            case BEAST_PAIR_SPRING, BEAST_PAIR_SUMMER, BEAST_PAIR_AUTUMN,
                 BEAST_PAIR_WINTER, FOUR_BEASTS, WILD_COURT -> YakuEffectKind.LIFESTEAL;

            case FOUR_RIBBONS -> YakuEffectKind.RESOURCE_REFUND;
            case FOUR_PETALS, FOUR_OF_ONE_SEASON -> YakuEffectKind.PETAL_SPREAD;
            default -> YakuEffectKind.NONE;
        };
    }

    /** true when completing this Yaku grants an overworld payload. */
    public static boolean hasPayload(YakuType type) {
        return kindFor(type) != YakuEffectKind.NONE;
    }
}