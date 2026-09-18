package com.lychcs.koikoi.run;

import com.lychcs.koikoi.model.shikigami.Shikigami;

/**
 * Ergebnis einer Rekrutierungsbelohnung. Unveraenderlich; es entsteht genau eine
 * Instanz pro tatsaechlich vergebenem Shikigami.
 *
 * <p>{@code shikigami} ist {@code null}, wenn nichts vergeben wurde (Spezies bereits
 * im Besitz). Die Belohnung selbst gehoert danach dem {@link RunSession}, das sie
 * entweder im aktiven Beutel oder in der Reserve haelt.</p>
 */
public record ShikigamiReward(Outcome outcome, String speciesId, Shikigami shikigami) {

    public enum Outcome {
        /** Die neue Instanz liegt im aktiven Beutel. */
        ADDED_TO_BAG,
        /** Der Beutel war voll: die neue Instanz liegt verlustfrei in der Reserve. */
        ADDED_TO_RESERVE,
        /** Die Spezies ist bereits im Besitz: keine zweite Instanz, kein Duplikat. */
        ALREADY_RECRUITED,
        /**
         * Die Spezies hat noch keine rekrutierbare Shikigami-Form. Der Blocker ist
         * dokumentiert ({@code YokaiSpecies.getRewardBlockerReason()}); es wird
         * bewusst kein Ersatz-Shikigami erfunden.
         */
        REWARD_UNAVAILABLE
    }

    public static ShikigamiReward addedToBag(Shikigami shikigami) {
        return new ShikigamiReward(Outcome.ADDED_TO_BAG, shikigami.getId(), shikigami);
    }

    public static ShikigamiReward addedToReserve(Shikigami shikigami) {
        return new ShikigamiReward(Outcome.ADDED_TO_RESERVE, shikigami.getId(), shikigami);
    }

    public static ShikigamiReward alreadyRecruited(String speciesId) {
        return new ShikigamiReward(Outcome.ALREADY_RECRUITED, speciesId, null);
    }

    public static ShikigamiReward rewardUnavailable(String speciesId) {
        return new ShikigamiReward(Outcome.REWARD_UNAVAILABLE, speciesId, null);
    }

    /** true, wenn dieses Ergebnis eine neue Shikigami-Instanz vergeben hat. */
    public boolean isGranted() {
        return outcome == Outcome.ADDED_TO_BAG || outcome == Outcome.ADDED_TO_RESERVE;
    }
}
