package com.lychcs.koikoi.scoring;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Zentrale Formatierung fuer Scorewerte (Chips, Mult, Score).
 *
 * Regeln: ganze Zahlen ohne Nachkommastellen ("1", "10", "2439"), Dezimalwerte
 * mit maximal EINER Nachkommastelle ("1.5", "4241.3"), Punkt als
 * Dezimaltrennzeichen, keine wissenschaftliche Schreibweise. Der Formatter wird
 * genau einmal erzeugt.
 */
public final class ScoreFormat {

    /** Werte unterhalb dieser Schwelle werden als 0 angezeigt (verhindert "-0"). */
    private static final double ZERO_EPSILON = 0.005;

    /** Anzeige- und Vergleichsgenauigkeit (eine Nachkommastelle). */
    private static final double DISPLAY_SCALE = 10.0;

    private static final DecimalFormat FORMATTER = createFormatter();

    private ScoreFormat() {}

    private static DecimalFormat createFormatter() {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setDecimalSeparator('.');
        symbols.setGroupingSeparator(',');

        DecimalFormat formatter = new DecimalFormat("#0.#", symbols);
        formatter.setGroupingUsed(false);
        return formatter;
    }

    /**
     * Rundet einen Scorewert auf die gemeinsame Anzeige-/Vergleichsgenauigkeit
     * (eine Nachkommastelle). Wird sowohl fuer die Darstellung als auch fuer den
     * Vergleich mit dem Target Score verwendet, damit sich beide nicht
     * widersprechen koennen.
     */
    public static double toDisplayPrecision(double value) {
        return Math.round(value * DISPLAY_SCALE) / DISPLAY_SCALE;
    }

    /**
     * Vergleichsregel fuer den Target Score: vergleicht beide Werte auf der
     * gemeinsamen Anzeige-Genauigkeit. Ein sichtbar angezeigtes "200" ist damit
     * auch tatsaechlich ein Erreichen des Ziels.
     */
    public static boolean reachesTarget(double value, double target) {
        return toDisplayPrecision(value) >= toDisplayPrecision(target);
    }

    /** Formatiert einen Scorewert fuer UI und Debug-Ausgaben. */
    public static String format(double value) {
        if (!Double.isFinite(value) || Math.abs(value) < ZERO_EPSILON) {
            return "0";
        }
        return FORMATTER.format(toDisplayPrecision(value));
    }
}