package com.lychcs.koikoi.model.shikigami;

import com.lychcs.koikoi.model.shikigami.ability.FoxTrickAbility;
import com.lychcs.koikoi.model.shikigami.ability.ShikigamiAbility;
import com.lychcs.koikoi.scoring.ScoreAccumulator;
import com.lychcs.koikoi.scoring.ScoreContext;

import java.util.EnumMap;
import java.util.Map;

/**
 * Gereinigte Kitsune-Form mit der Faehigkeit "Fox Trick".
 *
 * <p>Kitsune ist <b>kein</b> Score-Multiplikator (der fruehere Foxfire-Ansatz mit
 * {@code x1.5 Mult} ist fachlich falsch und hier bewusst nicht vorhanden).
 * Stattdessen manipuliert Fox Trick beim Einsetzen in den Altar die aktuelle
 * Kampfhand: eine Karte wird in eine andere Karte des vollstaendigen 48-Karten-
 * Decks verwandelt - der Hanko der Quellkarte bleibt erhalten.</p>
 *
 * <p><b>Stufen:</b> Level 1 zufaellige Quelle und zufaelliges Ziel, Level 2 freie
 * Quellwahl bei zufaelligem Ziel, Level 3 freie Wahl von Quelle und Ziel. Die
 * Details je Stufe stehen in {@link FoxTrickAbility} (deklarativ ueber
 * {@link ShikigamiAbility}); hier gibt es keine UI- oder Dialoglogik.</p>
 *
 * <p><b>Grafik:</b> die gereinigten Formen verwenden ausschliesslich die
 * {@code SHIKIGAMI_*}-Atlasregionen. Das wilde Aseprite-Sheet
 * ({@code kitsune_wild.png}) ist bewusst <b>kein</b> Begleiterbild; fehlt eine
 * gereinigte Region, faellt die UI auf einen Textplatzhalter zurueck.</p>
 */
public class Kitsune extends Shikigami {

    /** Stabile Spezies-Id (identisch mit {@code YokaiSpecies.ID_KITSUNE}). */
    public static final String SPECIES_ID = "kitsune";

    /** Atlasregionen der drei gereinigten Formen (Konvention der Shikigami-Linien). */
    public static final String REGION_LEVEL_1 = "SHIKIGAMI_KO_KITSUNE";
    public static final String REGION_LEVEL_2 = "SHIKIGAMI_KITSUNE";
    public static final String REGION_LEVEL_3 = "SHIKIGAMI_DAI_KITSUNE";

    /** Faehigkeit je Entwicklungsstufe: einmal erzeugt, danach stabil. */
    private final Map<ShikigamiStage, FoxTrickAbility> abilities = new EnumMap<>(ShikigamiStage.class);

    public Kitsune(ShikigamiStage initialStage) {
        super(SPECIES_ID, "Ko-Kitsune", REGION_LEVEL_1, initialStage);
    }

    public Kitsune() {
        this(ShikigamiStage.LEVEL_1);
    }

    @Override
    public String getName() {
        return switch (getStage()) {
            case EGG -> "Kitsune Egg";
            case LEVEL_1 -> "Ko-Kitsune";
            case LEVEL_2 -> "Kitsune";
            case LEVEL_3 -> "Dai-Kitsune";
        };
    }

    @Override
    public String getDescription() {
        return switch (getStage()) {
            case EGG -> "A sleeping fox spirit. Fox Trick awakens at Level 1.";
            case LEVEL_1 -> "Fox Trick: When placed in the altar, a random card in your hand "
                + "transforms into another random card.";
            case LEVEL_2 -> "Fox Trick: When placed in the altar, choose a card in your hand. "
                + "It transforms into a random different card.";
            case LEVEL_3 -> "Fox Trick: When placed in the altar, choose a card in your hand, "
                + "then choose any different card for it to become.";
        };
    }

    @Override
    public String getAtlasRegionName() {
        return switch (getStage()) {
            case LEVEL_2 -> REGION_LEVEL_2;
            case LEVEL_3 -> REGION_LEVEL_3;
            case EGG, LEVEL_1 -> REGION_LEVEL_1;
        };
    }

    /**
     * Fox Trick je Stufe. Die schlafende Ei-Form hat wie beim Oni-Ei keine
     * Faehigkeit und liefert deshalb {@code null}; die UI legt sie dann ohne
     * Auswahldialog in den Altar.
     */
    @Override
    public ShikigamiAbility getAbility() {
        ShikigamiStage stage = getStage();
        if (stage == ShikigamiStage.EGG) {
            return null;
        }
        FoxTrickAbility ability = abilities.get(stage);
        if (ability == null) {
            ability = new FoxTrickAbility(stage);
            abilities.put(stage, ability);
        }
        return ability;
    }

    /**
     * Fox Trick addiert und multipliziert keinen Score: es entsteht bewusst
     * <b>kein</b> {@code ScoringEvent} - und damit auch keine faelschliche
     * "+0"-Anzeige in der Scoreanimation. Der Altar-Bonus bleibt ausschliesslich
     * scorebasierten Faehigkeiten (Oni) vorbehalten.
     */
    @Override
    public boolean activate(ScoreContext context, ScoreAccumulator acc) {
        return false;
    }
}
