package com.lychcs.koikoi.model.fuku;

public class UchideMallet implements Fuku {

    @Override
    public String getName() { return "Uchide's Mallet"; }

    @Override
    public String getDescription() { return "Gain 15 Mon"; }

    @Override
    public void consume(FukuContext context) {
        context.addMon(15);
    }
}
