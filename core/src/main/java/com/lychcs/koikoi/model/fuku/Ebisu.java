package com.lychcs.koikoi.model.fuku;

public class Ebisu implements Fuku {

    @Override
    public String getName() {
        return "Ebisu's Blessing";
    }

    @Override
    public String getDescription() {
        return "Doubles your Mon (Max of 20 Mon)";
    }

    @Override
    public void consume(FukuContext context) {
        int currentMon = context.getMon();

        int amountToAdd = currentMon;

        // Cappen auf maximal 20
        if (amountToAdd > 20) {
            amountToAdd = 20;
        }

        // Nur ausführen, wenn der Spieler überhaupt schon Geld hat
        if (amountToAdd > 0) {
            context.addMon(amountToAdd);
        }
    }
}
