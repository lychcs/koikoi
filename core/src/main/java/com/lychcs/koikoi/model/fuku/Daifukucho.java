package com.lychcs.koikoi.model.fuku;

public class Daifukucho implements Fuku {

    @Override
    public String getName() {
        return "Daifukucho (Kassenbuch)";
    }

    @Override
    public String getDescription() {
        return "Increase Interest-Cap by 5 Mon each Round";
    }

    @Override
    public void consume(FukuContext context) {
        context.addMaxInterestCap(5);
    }
}
