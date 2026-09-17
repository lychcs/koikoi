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
            case EGG -> "Ein schlummernder Dämon. Benötigt Evolution.";
            case LEVEL_1 -> "Saison-Fokus: Petals (Blüten) der aktuellen Jahreszeit zählen als Ribbons (Bänder).";
            case LEVEL_2 -> "Chimären-Zorn: Ribbons (Bänder) zählen auch als Beasts (Tiere), aber Hikari (Licht) werden entwertet.";
            case LEVEL_3 -> "Dämonischer Tribut: Bei 5 Karten wird die höchste geopfert; die restlichen 4 übernehmen ihren Rang.";
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
                        mutated.add(new Card(c.id(), c.season(), Rank.RIBBON, c.name() + " (Band-Form)", c.effect()));
                    } else {
                        mutated.add(c);
                    }
                }
            }
            case LEVEL_2 -> {
                for (Card c : originalHand) {
                    if (c.rank() == Rank.RIBBON) {
                        mutated.add(new Card(c.id(), c.season(), Rank.BEAST, c.name() + " (Tier-Form)", c.effect()));
                    } else if (c.rank() == Rank.HIKARI) {
                        mutated.add(new Card(c.id(), c.season(), Rank.PETAL, c.name() + " (Entweiht)", c.effect()));
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
                            mutated.add(new Card(c.id(), c.season(), Rank.PETAL, c.name() + " (Opfergabe)", c.effect()));
                        } else {
                            mutated.add(new Card(c.id(), c.season(), highest.rank(), c.name() + " (Ermächtigt)", c.effect()));
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
            acc.multiplyMult(getName() + " (Blutpakt)", 1.5);
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
