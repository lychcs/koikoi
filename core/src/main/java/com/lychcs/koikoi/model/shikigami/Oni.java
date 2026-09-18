package com.lychcs.koikoi.model.shikigami;

import com.lychcs.koikoi.model.Card;
import com.lychcs.koikoi.model.Rank;
import com.lychcs.koikoi.run.GameSeason;
import com.lychcs.koikoi.scoring.ScoreAccumulator;
import com.lychcs.koikoi.scoring.ScoreContext;

import java.util.ArrayList;
import java.util.List;

public class Oni extends Shikigami {

    public Oni(ShikigamiStage initialStage) {
        super("oni", "Ko-Oni", "SHIKIGAMI_KO_ONI", initialStage);
    }

    public Oni() {
        this(ShikigamiStage.LEVEL_1);
    }

    @Override
    public String getName() {
        return switch (getStage()) {
            case EGG -> "Oni-Ei";
            case LEVEL_1 -> "Ko-Oni";
            case LEVEL_2 -> "Oni";
            case LEVEL_3 -> "Dai-Oni";
        };
    }

    @Override
    public String getDescription() {
        return switch (getStage()) {
            case EGG -> "A slumbering demon. Requires evolution to take effect.";
            case LEVEL_1 -> "Season Focus: Petals of the current season count as Ribbons.";
            case LEVEL_2 -> "Chimera's Wrath: Ribbons also count as Beasts, and Hikari cards count as Petals.";
            case LEVEL_3 -> "Demonic Tribute: With 5 cards, the highest card becomes a Petal, the other 4 take on its rank and Mult is multiplied by x1.5.";
        };
    }

    /**
     * Mutiert die gespielten Karten VOR der Yaku-Erkennung,
     * sodass YakuDetector die neuen Ränge als vollwertige Kombinationen erkennt.
     */
    public List<Card> mutateHand(List<Card> originalHand, GameSeason currentSeason) {
        List<Card> mutated = new ArrayList<>();

        switch (getStage()) {
            case LEVEL_1 -> {
                for (Card c : originalHand) {
                    // Season-Abgleich über den Namen, da Season und GameSeason getrennte Enums sind
                    boolean sameSeason = c.season().name().equals(currentSeason.name());
                    if (c.rank() == Rank.PETAL && sameSeason) {
                        mutated.add(new Card(c.id(), c.season(), Rank.RIBBON, c.name() + " (Ribbon Form)", c.effect()));
                    } else {
                        mutated.add(c);
                    }
                }
            }
            case LEVEL_2 -> {
                for (Card c : originalHand) {
                    if (c.rank() == Rank.RIBBON) {
                        mutated.add(new Card(c.id(), c.season(), Rank.BEAST, c.name() + " (Beast Form)", c.effect()));
                    } else if (c.rank() == Rank.HIKARI) {
                        mutated.add(new Card(c.id(), c.season(), Rank.PETAL, c.name() + " (Desecrated)", c.effect()));
                    } else {
                        mutated.add(c);
                    }
                }
            }
            case LEVEL_3 -> {
                if (originalHand.size() == 5) {
                    // Höchste Karte nach Basis-Wert ermitteln
                    Card highest = originalHand.stream()
                        .max((a, b) -> Double.compare(a.rank().getBaseValue(), b.rank().getBaseValue()))
                        .orElse(null);

                    for (Card c : originalHand) {
                        if (c.equals(highest)) {
                            mutated.add(new Card(c.id(), c.season(), Rank.PETAL, c.name() + " (Offering)", c.effect()));
                        } else {
                            mutated.add(new Card(c.id(), c.season(), highest.rank(), c.name() + " (Empowered)", c.effect()));
                        }
                    }
                } else {
                    mutated.addAll(originalHand);
                }
            }
            default -> mutated.addAll(originalHand);
        }

        return mutated;
    }

    @Override
    public boolean activate(ScoreContext context, ScoreAccumulator acc) {
        if (getStage() == ShikigamiStage.LEVEL_3 && context.hand().getTotalCardCount() == 5) {
            acc.multiplyMult(getName() + " (Blood Pact)", 1.5);
            return true;
        }
        return false;
    }

    @Override
    public String getAtlasRegionName() {
        return switch (getStage()) {
            case LEVEL_1 -> "SHIKIGAMI_KO_ONI";
            case LEVEL_2 -> "SHIKIGAMI_ONI";
            case LEVEL_3 -> "SHIKIGAMI_DAI_ONI";
            default -> "SHIKIGAMI_KO_ONI";
        };
    }
}
