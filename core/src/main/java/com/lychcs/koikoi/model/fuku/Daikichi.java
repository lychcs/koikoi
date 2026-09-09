package com.lychcs.koikoi.model.fuku;

public class Daikichi implements Fuku {

    @Override
    public String getName() { return "Daikichi (Großes Glück)"; }

    @Override
    public String getDescription() { return "Gain +25 Mon"; }

    @Override
    public void consume(FukuContext context) {
        context.addMon(25);
    }
}
