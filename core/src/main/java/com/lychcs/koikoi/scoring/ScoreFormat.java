package com.lychcs.koikoi.scoring;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Zentrale Formatierung fuer Scorewerte (Chips, Mult, Score).
 *
 * Regeln: ganze Zahlen ohne Nachkommastellen ("1", "10"), Dezimalwerte mit
 * maximal zwei Nachkommastellen ("1.5", "2.25"), Punkt als Dezimaltrennzeichen,
 * keine wissenschaftliche Schreibweise. Der Formatter wird genau einmal erzeugt.
 */
public final class ScoreFormat {

    /** Werte unterhalb dieser Schwelle werden als 0 angezeigt (verhindert "-0"). */
    private static final double ZERO_EPSILON = 0.005;

    private static final DecimalFormat FORMATTER = createFormatter();

    private ScoreFormat() {}

    private static DecimalFormat createFormatter() {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setDecimalSeparator('.');
        symbols.setGroupingSeparator(',');

        DecimalFormat formatter = new DecimalFormat("#0.##", symbols);
        formatter.setGroupingUsed(false);
        return formatter;
    }

    /** Formatiert einen Scorewert fuer UI und Debug-Ausgaben. */
    public static String format(double value) {
        if (!Double.isFinite(value) || Math.abs(value) < ZERO_EPSILON) {
            return "0";
        }
        return FORMATTER.format(value);
    }
}