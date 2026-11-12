package com.lychcs.koikoi.combat;

/**
 * Resolves the overworld card shuffle/lock request from the raw key and mouse
 * states.
 *
 * <p>Twisted-Fate-style card control sits on {@code E} so it is reachable with
 * the WASD hand, while {@code R} and right-click remain available. {@code E} is
 * also the interaction key, so pressing it in front of a tavern, shop or shrine
 * must open that interaction instead of shuffling the deck. A right-click that
 * already locked on to a hostile under the crosshair likewise belongs to the
 * attack, not to the deck.</p>
 *
 * <p>Pure logic (no libGDX input polling) so the binding can be unit-tested.</p>
 */
public final class CardSelectBinding {

    private CardSelectBinding() {
    }

    /**
     * @param rPressed           {@code R} was just pressed
     * @param ePressed           {@code E} was just pressed
     * @param rightPressed       right mouse button was just pressed
     * @param nearInteraction    {@code E} would open a tavern/shop/shrine
     * @param rightClickConsumed right-click already resolved a lock-on target
     * @return true when the W-deck should start shuffling or lock its card
     */
    public static boolean shouldShuffle(boolean rPressed, boolean ePressed,
                                        boolean rightPressed, boolean nearInteraction,
                                        boolean rightClickConsumed) {
        if (rPressed) {
            return true;
        }
        if (ePressed && !nearInteraction) {
            return true;
        }
        return rightPressed && !rightClickConsumed;
    }
}
