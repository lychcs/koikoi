package com.lychcs.koikoi.model.shikigami.ability;

import com.lychcs.koikoi.model.Card;
import com.lychcs.koikoi.model.DeckTemplates;
import com.lychcs.koikoi.model.shikigami.ShikigamiStage;

import java.util.List;
import java.util.Random;

/**
 * Kitsune-Faehigkeit "Fox Trick" (ersetzt den fachlich falschen Foxfire-Ansatz:
 * <b>kein</b> Score-Multiplikator, sondern eine Manipulation der aktuellen
 * Kampfhand beim Einsetzen in den Altar).
 *
 * <p><b>Stufenverhalten</b> (Auswahlart ist deklarativ, die Aufloesung identisch):</p>
 * <ul>
 *   <li><b>Level 1</b> ({@link ShikigamiSelectionMode#CONFIRM_ONLY}): zufaellige
 *       Quellkarte aus der Hand, zufaellige Zielkarte aus dem gewichteten
 *       vollstaendigen Pool.</li>
 *   <li><b>Level 2</b> ({@link ShikigamiSelectionMode#SELECT_HAND_CARD}): der
 *       Spieler waehlt die Quellkarte, das Ziel bleibt zufaellig.</li>
 *   <li><b>Level 3</b>
 *       ({@link ShikigamiSelectionMode#SELECT_HAND_CARD_AND_TARGET_CARD}): der
 *       Spieler waehlt Quelle und Ziel frei (gleiche CardID ist ausgeschlossen).</li>
 * </ul>
 *
 * <p><b>Zufall:</b> gezogen wird aus den physischen Vorlagen
 * ({@link DeckTemplates#candidatesFor(Card)}), wodurch Petals ihre tatsaechliche
 * Haeufigkeit behalten. Es gibt keinen Wiederholungs-Loop: die Kandidatenliste
 * schliesst die eigene CardID von vornherein aus. Die Zufallsquelle ist
 * injizierbar beziehungsweise ueber einen Seed deterministisch.</p>
 *
 * <p>Der Hanko der Quellkarte bleibt erhalten (siehe
 * {@link CardTransformation#transform(Card, Card)}). Erschoepfung, Altar-Commit und
 * Animationen liegen bewusst ausserhalb dieses Modells.</p>
 */
public final class FoxTrickAbility implements ShikigamiAbility {

    /** Anzeigename der Faehigkeit. */
    public static final String NAME = "Fox Trick";

    private static final String PROMPT_CONFIRM =
        "Transform a random card in your hand into another random card.";
    private static final String PROMPT_SELECT_SOURCE =
        "Choose a card in your hand. It transforms into a random different card.";
    private static final String PROMPT_SELECT_SOURCE_AND_TARGET =
        "Choose a card in your hand, then choose any different card for it to become.";

    private final ShikigamiStage stage;
    private final Random random;

    public FoxTrickAbility(ShikigamiStage stage) {
        this(stage, new Random());
    }

    /** Deterministische Variante fuer Tests. */
    public FoxTrickAbility(ShikigamiStage stage, long seed) {
        this(stage, new Random(seed));
    }

    /**
     * @param random Zufallsquelle (nicht {@code null}); erlaubt deterministische
     *               Tests ohne globalen Zufallszustand
     */
    public FoxTrickAbility(ShikigamiStage stage, Random random) {
        if (stage == null) {
            throw new IllegalArgumentException("stage must not be null");
        }
        if (random == null) {
            throw new IllegalArgumentException("random must not be null");
        }
        this.stage = stage;
        this.random = random;
    }

    public ShikigamiStage getStage() {
        return stage;
    }

    @Override
    public String getAbilityName() {
        return NAME;
    }

    @Override
    public ShikigamiActivationTiming getTiming() {
        return ShikigamiActivationTiming.ON_ALTAR_PLACED;
    }

    @Override
    public ShikigamiSelectionMode getSelectionMode() {
        return switch (stage) {
            case LEVEL_2 -> ShikigamiSelectionMode.SELECT_HAND_CARD;
            case LEVEL_3 -> ShikigamiSelectionMode.SELECT_HAND_CARD_AND_TARGET_CARD;
            case EGG, LEVEL_1 -> ShikigamiSelectionMode.CONFIRM_ONLY;
        };
    }

    @Override
    public String getPromptText() {
        return switch (getSelectionMode()) {
            case SELECT_HAND_CARD -> PROMPT_SELECT_SOURCE;
            case SELECT_HAND_CARD_AND_TARGET_CARD -> PROMPT_SELECT_SOURCE_AND_TARGET;
            default -> PROMPT_CONFIRM;
        };
    }

    @Override
    public ShikigamiAbilityResult resolve(ShikigamiAbilityContext context) {
        if (context == null) {
            return ShikigamiAbilityResult.failed("Fox Trick requires an activation context.");
        }
        if (context.timing() != ShikigamiActivationTiming.ON_ALTAR_PLACED) {
            return ShikigamiAbilityResult.notApplicable(NAME,
                "it applies when placed in the altar, not at " + context.timing() + ".");
        }
        if (stage == ShikigamiStage.EGG) {
            return ShikigamiAbilityResult.notApplicable(NAME, "it awakens at Level 1.");
        }

        List<Card> hand = context.hand();
        if (hand.isEmpty()) {
            return ShikigamiAbilityResult.emptyHand(NAME);
        }

        Card source = resolveSource(context, hand);
        if (source == null) {
            return ShikigamiAbilityResult.invalidSelection("No card was selected to transform.");
        }
        if (!hand.contains(source)) {
            return ShikigamiAbilityResult.invalidSelection(
                "The selected card '" + source.name() + "' is no longer in your hand.");
        }

        Card target = resolveTarget(context, source);
        if (target == null) {
            return CardTransformation.isSameCardId(source, context.targetTemplate())
                ? ShikigamiAbilityResult.invalidSelection(
                    "The new card must be a different card type than '" + source.name() + "'.")
                : ShikigamiAbilityResult.invalidSelection("No valid new card was selected.");
        }

        Card replacement;
        try {
            replacement = CardTransformation.transform(source, target);
        } catch (IllegalArgumentException e) {
            return ShikigamiAbilityResult.invalidSelection(e.getMessage());
        }

        return ShikigamiAbilityResult.applied(source, replacement,
            CardTransformation.describe(source, replacement));
    }

    /** Quellkarte: gewaehlte Karte oder zufaellige Karte der aktuellen Hand. */
    private Card resolveSource(ShikigamiAbilityContext context, List<Card> hand) {
        if (getSelectionMode().requiresHandCard()) {
            return context.sourceCard();
        }
        return hand.get(random.nextInt(hand.size()));
    }

    /** Zielkarte: gewaehlte Vorlage (Level 3) oder gewichtete Zufallsvorlage. */
    private Card resolveTarget(ShikigamiAbilityContext context, Card source) {
        if (getSelectionMode().requiresTargetCard()) {
            Card selected = context.targetTemplate();
            if (selected == null || !DeckTemplates.isTemplate(selected)
                || CardTransformation.isSameCardId(source, selected)) {
                return null;
            }
            return selected;
        }

        // Kandidaten werden immer aus der TATSAECHLICH verwendeten Quellkarte
        // gebildet: bei zufaelliger Quelle ist context.sourceCard() leer, deshalb
        // wird hier direkt die aufgeloeste Karte verwendet. Damit ist die eigene
        // CardID von vornherein ausgeschlossen (kein Wiederholungs-Loop).
        List<Card> candidates = DeckTemplates.candidatesFor(source);
        if (candidates.isEmpty()) {
            return null;
        }
        Card drawn = candidates.get(random.nextInt(candidates.size()));
        return CardTransformation.isSameCardId(source, drawn) ? null : drawn;
    }
}
