package com.lychcs.koikoi.model.hanko;

import com.lychcs.koikoi.model.Card;
import com.lychcs.koikoi.model.CardID;
import com.lychcs.koikoi.model.Rank;

public class BloodSeal implements Hanko {

    @Override
    public String getName() {
        return "Yami Seal (Curse of the Yokai)";
    }

    @Override
    public String getDescription() {
        return "Transforms a Hikari card into its dark Yami counterpart.\nCard is destroyed after being played.";
    }

    @Override
    public boolean canTarget(Card card) {
        // Dieses Siegel kann NUR auf reine Hikari-Karten angewendet werden!
        return card.rank() == Rank.HIKARI && !card.hasHanko();
    }

    @Override
    public Card applyEffect(Card targetCard) {
        CardID newId = targetCard.id();
        String newName = targetCard.name();

        // 1. Die spezifische Transformation abfragen
        switch (targetCard.id()) {
            case WINTER_HIKARI_POLAR_LIGHT -> {
                newId = CardID.WINTER_YAMI_YUKI_ONNA;
                newName = "Yuki Onna";
            }
            // später: die anderen 3 Hikari-Karten
            // case AUTUMN_HIKARI_RED_MOON -> {
            //     newId = CardID.AUTUMN_YAMI_BLOOD_MOON;
            //     newName = "Blood Moon";
            // }
        }

        // 2. Die neue Yami-Karte erschaffen und den Zerstörungs-Effekt anhängen
        return new Card(
            newId,
            targetCard.season(),
            Rank.YAMI,
            newName,
            HankoEffect.YAMI_SEAL //Zerstörungs-Effekt
        );
    }
}
