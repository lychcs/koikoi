package com.lychcs.koikoi.run;

import com.lychcs.koikoi.model.CardID;
import com.lychcs.koikoi.model.Rank;

public class EvolutionMapper {

    /**
     * Gibt die beiden Starter-Beasts für das erste Level der jeweiligen Season zurück.
     */
    public static CardID[] getSeasonBeastChoices(GameSeason season) {
        return switch (season) {
            case SPRING -> new CardID[]{CardID.SPRING_BEAST_KOI, CardID.SPRING_BEAST_CRANE};
            case SUMMER -> new CardID[]{CardID.SUMMER_BEAST_MOUNTAIN_MONKEY, CardID.SUMMER_BEAST_SERPENT};
            case AUTUMN -> new CardID[]{CardID.AUTUMN_BEAST_BOAR, CardID.AUTUMN_BEAST_DEER};
            case WINTER -> new CardID[]{CardID.WINTER_BEAST_ARCTIC_FOX, CardID.WINTER_BEAST_WHITE_OWL};
            default -> new CardID[]{CardID.UNKNOWN, CardID.UNKNOWN};
        };
    }

    /**
     * Wertet das Basis-Beast zum Licht- oder Schatten-Pendant auf.
     */
    public static CardID getEvolvedForm(CardID baseBeast, Rank alignment) {
        if (alignment == Rank.HIKARI) {
            return switch (baseBeast) {
                // Spring
                case SPRING_BEAST_KOI, SPRING_BEAST_CRANE -> CardID.SPRING_HIKARI_CHERRY_BLOSSOM_STORM;
                // Summer
                case SUMMER_BEAST_MOUNTAIN_MONKEY, SUMMER_BEAST_SERPENT -> CardID.SUMMER_HIKARI_GLAZING_SUN;
                // Autumn
                case AUTUMN_BEAST_BOAR, AUTUMN_BEAST_DEER -> CardID.AUTUMN_HIKARI_RED_MOON;
                // Winter
                case WINTER_BEAST_ARCTIC_FOX, WINTER_BEAST_WHITE_OWL -> CardID.WINTER_HIKARI_POLAR_LIGHT;
                default -> CardID.UNKNOWN;
            };
        } else if (alignment == Rank.YAMI) {
            return switch (baseBeast) {
                // Yami-Evolutionen (Beispiel-IDs anhand deines Enums)
                case SPRING_BEAST_KOI, SPRING_BEAST_CRANE -> CardID.SPRING_YAMI_XXX;
                case SUMMER_BEAST_MOUNTAIN_MONKEY, SUMMER_BEAST_SERPENT -> CardID.SUMMER_YAMI_XXX;
                case AUTUMN_BEAST_BOAR, AUTUMN_BEAST_DEER -> CardID.AUTUMN_YAMI_XXX;
                case WINTER_BEAST_ARCTIC_FOX, WINTER_BEAST_WHITE_OWL -> CardID.WINTER_YAMI_YUKI_ONNA;
                default -> CardID.UNKNOWN;
            };
        }
        return baseBeast;
    }
}
