package com.lychcs.koikoi.model.fuku;

public class SenbaZuru implements Fuku {

    @Override
    public String getName() { return "Senba Zuru"; }

    @Override
    public String getDescription() { return "+1 Hand und +1 Discard dauerhaft."; }

    @Override
    public void consume(FukuContext context) {
        context.addMaxHands(1);
        context.addMaxDiscards(1);
    }
}
