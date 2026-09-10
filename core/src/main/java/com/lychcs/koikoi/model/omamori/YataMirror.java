package com.lychcs.koikoi.model.omamori; // oder omamoris, je nachdem wie dein Package heißt

import com.lychcs.koikoi.scoring.ScoreAccumulator;
import com.lychcs.koikoi.scoring.ScoreContext;

import java.util.List;

public class YataMirror implements Omamori {

    @Override
    public String getName() { return "Yata no Kagami"; }

    @Override
    public String getDescription() { return "..."; }

    @Override
    public Rarity getRarity() { return Rarity.EPIC; }

    @Override
    public boolean evaluate(ScoreContext context, ScoreAccumulator acc) {
        List<Omamori> omamoris = context.omamoris();
        int myIndex = omamoris.indexOf(this);

        // Prüfen, ob links von diesem Spiegel ein anderes Omamori liegt
        if (myIndex > 0) {
            Omamori leftOmamori = omamoris.get(myIndex - 1);

            // Verhindert Unendlichkeits-Loops, falls zwei Spiegel nebeneinander liegen
            if (!leftOmamori.getName().equals(this.getName())) {
                // SPIEGELT den Effekt des Nachbarn!
                // Es ruft einfach die Evaluate-Methode des linken Omamoris nochmal auf.
                return leftOmamori.evaluate(context, acc);
            }
        }
        return false;
    }
}
