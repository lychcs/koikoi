package com.lychcs.koikoi.run;

import com.lychcs.koikoi.model.Card;

import java.util.Comparator;
import java.util.List;

/**
 * Sortier- und Einfuegeregeln der Spielerhand.
 *
 * <p>Es gibt genau EINEN Comparator je Modus. Er wird sowohl vom Sortierbutton
 * (einmalige Sortierung der vorhandenen Hand) als auch als Einfuegemodus fuer
 * neu gezogene Karten verwendet.</p>
 *
 * <p>Die Einfuegeregel ist stabil und deterministisch:</p>
 * <ol>
 *   <li>Die neue Karte wird der Reihe nach mit den vorhandenen Karten verglichen.</li>
 *   <li>Eingefuegt wird vor der ersten vorhandenen Karte, die nach dem Comparator
 *       groesser ist als die neue Karte (erste Position mit
 *       {@code compare(existing, neu) > 0}).</li>
 *   <li>Gibt es keine solche Position, wird die Karte am Ende angefuegt.</li>
 *   <li>Vorhandene Karten werden nie untereinander vertauscht; ihre relative
 *       Reihenfolge bleibt auch bei manuell verschobener Hand erhalten.</li>
 *   <li>Gleiche Sortierschluessel entscheidet der Comparator deterministisch
 *       ueber CardID und eindeutigen Kartennamen.</li>
 * </ol>
 *
 * <p>Ohne Modus ({@link Mode#NONE}) wird eine neue Karte wie bisher angehaengt.</p>
 */
public final class HandSorting {

    /** Verfuegbare Sortiermodi. */
    public enum Mode {
        /** Kein Sortiermodus: neue Karten werden angehaengt. */
        NONE,
        /** Sortierung nach Jahreszeit, dann Rang, CardID und Name. */
        SEASON,
        /** Sortierung nach Rang (absteigend), dann Jahreszeit, CardID und Name. */
        RANK
    }

    private HandSorting() {}

    /** Comparator des Modus; {@code null}, wenn kein Modus gewaehlt wurde. */
    public static Comparator<Card> comparatorFor(Mode mode) {
        if (mode == null) {
            return null;
        }
        switch (mode) {
            case SEASON:
                return Comparator
                    .comparing(Card::season)
                    .thenComparing(Card::rank)
                    .thenComparing(Card::id)
                    .thenComparing(Card::name);

            case RANK:
                return Comparator
                    .comparing(Card::rank, Comparator.reverseOrder())
                    .thenComparing(Card::season)
                    .thenComparing(Card::id)
                    .thenComparing(Card::name);

            case NONE:
            default:
                return null;
        }
    }

    /**
     * Sortiert die bereits vorhandene Hand einmalig nach dem gewaehlten Modus.
     * Der Modus bleibt als Einfuegemodus erhalten.
     */
    public static void sortOnce(List<Card> hand, Mode mode) {
        Comparator<Card> comparator = comparatorFor(mode);
        if (comparator != null) {
            hand.sort(comparator);
        }
    }

    /**
     * Einfuegeindex einer neu gezogenen Karte. Vorhandene Karten werden nicht
     * gegeneinander vertauscht.
     */
    public static int insertIndexFor(List<Card> hand, Card card, Mode mode) {
        Comparator<Card> comparator = comparatorFor(mode);
        if (comparator == null) {
            return hand.size();
        }

        int insertIndex = 0;
        while (
            insertIndex < hand.size() &&
                comparator.compare(hand.get(insertIndex), card) <= 0
        ) {
            insertIndex++;
        }
        return insertIndex;
    }

    /**
     * Fuegt eine neu gezogene Karte nach dem gespeicherten Modus ein und liefert
     * den Index, an dem sie gelandet ist (fuer den direkten Einflug zum Slot).
     */
    public static int insertCard(List<Card> hand, Card card, Mode mode) {
        int insertIndex = insertIndexFor(hand, card, mode);
        hand.add(insertIndex, card);
        return insertIndex;
    }
}
