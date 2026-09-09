package com.lychcs.koikoi.model.fuku;

public interface Fuku {
    String getName();
    String getDescription();

    /**
     * Löst den Effekt sofort aus und verändert den Run über den Context.
     */
    void consume(FukuContext context);
}
