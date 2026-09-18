package com.lychcs.koikoi.model;

import com.lychcs.koikoi.model.hanko.HankoEffect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Unveraenderlicher Vorlagenpool des <b>vollstaendigen</b> Decks (48 physische
 * Karten) fuer Faehigkeiten, die eine Zielkarte ausserhalb der aktuellen Hand
 * benoetigen (Beispiel: Kitsune "Fox Trick").
 *
 * <p><b>Autoritative Quelle:</b> der Pool entsteht genau einmal aus der
 * bestehenden Deck-Factory ({@link Deck#initializeDeck()}) - es gibt bewusst
 * keine zweite, manuell gepflegte Kartenliste und kein zweites
 * Haeufigkeitsschema.</p>
 *
 * <p><b>Eigenschaften des Pools:</b></p>
 * <ul>
 *   <li>exakt {@value #TEMPLATE_COUNT} physische Vorlagen (4 Seasons x 12 Karten)
 *       mit ihrer urspruenglichen Haeufigkeitsverteilung (1 Hikari, 2 Beast,
 *       3 Ribbon, 6 Petal je Season),</li>
 *   <li><b>keine</b> Hankos: die Vorlagen stammen aus einer frischen
 *       Deck-Definition und uebernehmen nichts aus dem laufenden Run,</li>
 *   <li>der Pool wird ausschliesslich gelesen: es wird nie eine Karte daraus
 *       entfernt oder veraendert ({@link #all()} liefert eine unveraenderliche
 *       Liste),</li>
 *   <li>die Zielauswahl ist unabhaengig vom aktuellen Draw-, Discard- und
 *       Played-Zustand des Kampfes.</li>
 * </ul>
 *
 * <p>Damit bleibt die Zufallsgewichtung bei Level 1/2 korrekt: gezogen wird
 * gleichmaessig aus den <i>physischen</i> Vorlagen
 * ({@link #candidatesFor(Card)}), sodass Petals tatsaechlich sechsmal so haeufig
 * vorkommen wie Hikari.</p>
 */
public final class DeckTemplates {

    /** Anzahl der physischen Vorlagen des vollstaendigen Decks. */
    public static final int TEMPLATE_COUNT = 48;

    /** Ursprungsreihenfolge des vollstaendigen Decks (unveraenderlich). */
    private static final List<Card> ALL = createTemplates();

    /** Genau eine Vorlage je unterschiedlicher {@link CardID} (Level-3-Galerie). */
    private static final List<Card> DISTINCT_BY_CARD_ID = createDistinctByCardId(ALL);

    /** Zielkandidaten je Quell-CardID: alle physischen Vorlagen anderer CardID. */
    private static final Map<CardID, List<Card>> CANDIDATES_BY_SOURCE_ID = createCandidates(ALL);

    private DeckTemplates() {}

    private static List<Card> createTemplates() {
        Deck templates = new Deck();
        templates.initializeDeck();

        List<Card> snapshot = List.copyOf(templates.getCards());
        if (snapshot.size() != TEMPLATE_COUNT) {
            throw new IllegalStateException("Deck.initializeDeck() must produce exactly "
                + TEMPLATE_COUNT + " physical cards, but produced " + snapshot.size() + ".");
        }

        // Hanko-Freiheit ist die Voraussetzung dafuer, dass eine Transformation den
        // Hanko ausschliesslich von der Quellkarte uebernimmt.
        for (Card card : snapshot) {
            if (card.effect() != HankoEffect.NONE) {
                throw new IllegalStateException("Deck template '" + card.name() + "' carries Hanko "
                    + card.effect() + "; templates must be Hanko-free.");
            }
        }
        return snapshot;
    }

    private static List<Card> createDistinctByCardId(List<Card> templates) {
        Map<CardID, Card> firstByCardId = new LinkedHashMap<>();
        for (Card card : templates) {
            firstByCardId.putIfAbsent(card.id(), card);
        }
        return List.copyOf(new ArrayList<>(firstByCardId.values()));
    }

    private static Map<CardID, List<Card>> createCandidates(List<Card> templates) {
        Map<CardID, List<Card>> bySourceId = new EnumMap<>(CardID.class);
        for (Card source : templates) {
            if (bySourceId.containsKey(source.id())) {
                continue;
            }
            List<Card> candidates = new ArrayList<>();
            for (Card candidate : templates) {
                if (candidate.id() != source.id()) {
                    candidates.add(candidate);
                }
            }
            bySourceId.put(source.id(), List.copyOf(candidates));
        }
        return Collections.unmodifiableMap(bySourceId);
    }

    /** Alle {@value #TEMPLATE_COUNT} physischen Vorlagen in Ursprungsreihenfolge. */
    public static List<Card> all() {
        return ALL;
    }

    /** Genau eine Vorlage je unterschiedlicher CardID (stabile Reihenfolge). */
    public static List<Card> distinctByCardId() {
        return DISTINCT_BY_CARD_ID;
    }

    /** Erste Vorlage einer CardID oder {@code null}. */
    public static Card firstTemplateOf(CardID cardId) {
        if (cardId == null) {
            return null;
        }
        for (Card card : DISTINCT_BY_CARD_ID) {
            if (card.id() == cardId) {
                return card;
            }
        }
        return null;
    }

    /**
     * Alle physischen Zielvorlagen, deren {@link CardID} von der Quellkarte
     * verschieden ist (gleichmaessig gewichtet nach physischen Kopien). Liegt die
     * Quell-CardID nicht im Pool, wird der vollstaendige Pool geliefert.
     */
    public static List<Card> candidatesFor(Card source) {
        if (source == null) {
            return ALL;
        }
        List<Card> candidates = CANDIDATES_BY_SOURCE_ID.get(source.id());
        return candidates == null ? ALL : candidates;
    }

    /** true, wenn die Karte eine unveraenderte Vorlage des vollstaendigen Decks ist. */
    public static boolean isTemplate(Card card) {
        return card != null && ALL.contains(card);
    }

    /** true, wenn die CardID im vollstaendigen Deck ueberhaupt vorkommt. */
    public static boolean containsCardId(CardID cardId) {
        return cardId != null && CANDIDATES_BY_SOURCE_ID.containsKey(cardId);
    }

    /**
     * Basisname einer Vorlage ohne Kopiennummer ("Spring Ribbon #3" -&gt;
     * "Spring Ribbon"). Die Kopiennummer ist fuer eine transformierte Karte
     * irrefuehrend: sie gehoert zur physischen Kopie, nicht zum Kartentyp.
     */
    public static String baseNameOf(Card card) {
        return card == null ? null : baseNameOf(card.name());
    }

    /** Basisname eines Kartennamens ohne abschliessende Kopiennummer (" #3"). */
    public static String baseNameOf(String name) {
        if (name == null) {
            return null;
        }
        int hash = name.lastIndexOf(" #");
        if (hash < 0 || hash + 2 >= name.length()) {
            return name;
        }
        for (int i = hash + 2; i < name.length(); i++) {
            if (!Character.isDigit(name.charAt(i))) {
                return name;
            }
        }
        return name.substring(0, hash).trim();
    }
}
