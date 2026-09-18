package com.lychcs.koikoi.model.shikigami.ability;

import com.lychcs.koikoi.model.Card;
import com.lychcs.koikoi.model.DeckTemplates;

/**
 * Reine, einmalige Kartentransformation fuer temporaere Faehigkeiten
 * (Beispiel: Kitsune "Fox Trick").
 *
 * <p>{@link Card} ist ein unveraenderlicher Record; die Transformation erzeugt
 * deshalb eine <b>neue</b> Karte. Sie uebernimmt von der Zielvorlage</p>
 * <ul>
 *   <li>{@link Card#id()} (CardID/Motiv),</li>
 *   <li>{@link Card#season()},</li>
 *   <li>{@link Card#rank()} (und damit den Kartenbasiswert),</li>
 *   <li>den Basisnamen des Motivs ohne Kopiennummer.</li>
 * </ul>
 *
 * <p>Von der Quellkarte uebernimmt sie <b>ausschliesslich</b>
 * {@link Card#effect()}: der Hanko bleibt exakt erhalten und wird weder entfernt
 * noch zufaellig geaendert. Die Vorlagen des Pools sind per Definition
 * Hanko-frei (siehe {@link DeckTemplates}), damit diese Uebernahme eindeutig
 * bleibt.</p>
 *
 * <p><b>Nicht betroffen:</b> persistentes Deck, RunSession-Deck, Deckvorlagen,
 * Draw- und Discard-Pile sowie andere physische Kopien derselben Karte - die
 * Transformation gilt ausschliesslich fuer die uebergebene Quellkarte.</p>
 */
public final class CardTransformation {

    private CardTransformation() {}

    /**
     * Erzeugt die temporaere Zielkarte: Zielmotiv plus Hanko der Quellkarte.
     *
     * @param source         die zu ersetzende Handkarte (liefert nur den Hanko)
     * @param targetTemplate die Zielvorlage aus dem vollstaendigen Deck
     * @throws IllegalArgumentException wenn eine Karte fehlt oder die Zielvorlage
     *         dieselbe CardID wie die Quellkarte besitzt
     */
    public static Card transform(Card source, Card targetTemplate) {
        if (source == null) {
            throw new IllegalArgumentException("source card must not be null");
        }
        if (targetTemplate == null) {
            throw new IllegalArgumentException("target template must not be null");
        }
        if (targetTemplate.id() == source.id()) {
            throw new IllegalArgumentException(
                "target card must have a different CardID than the source card (" + source.id() + ")");
        }

        return new Card(
            targetTemplate.id(),
            targetTemplate.season(),
            targetTemplate.rank(),
            DeckTemplates.baseNameOf(targetTemplate),
            source.effect()
        );
    }

    /** true, wenn beide Karten dieselbe CardID besitzen (nicht erlaubtes Ziel). */
    public static boolean isSameCardId(Card source, Card target) {
        return source != null && target != null && source.id() == target.id();
    }

    /**
     * true, wenn die Karte eine zulaessige Zielvorlage fuer die Quelle ist:
     * Vorlage des vollstaendigen Decks und andere CardID.
     */
    public static boolean isAllowedTarget(Card source, Card target) {
        return DeckTemplates.isTemplate(target) && !isSameCardId(source, target);
    }

    /** Kurze englische Beschreibung der Ersetzung fuer UI-Meldungen. */
    public static String describe(Card source, Card replacement) {
        if (source == null || replacement == null) {
            return "";
        }
        return "'" + source.name() + "' was transformed into '" + replacement.name() + "'.";
    }
}
