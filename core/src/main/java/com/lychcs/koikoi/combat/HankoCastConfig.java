package com.lychcs.koikoi.combat;

/**
 * Mutable description of the currently selected Hanko spell in the overworld.
 *
 * <p>The screen updates this when the player selects another purchased Hanko or
 * when the Omamori loadout changes the seal radius. Systems only read it.</p>
 */
public final class HankoCastConfig {

    /** Display name of the selected Hanko (HUD + seal letters). */
    public String name = "White Seal";

    /** Short label engraved into the seal ring. */
    public String label = "WHITE SEAL";

    /** Final radius of the ground seal in world pixels. */
    public float radius = CombatStats.BASE.sealRadius();

    /** Damage applied once to every hostile inside the seal. */
    public float damage = 45f;

    /** Mana consumed by one cast. */
    public float manaCost = 25f;

    /** Movement speed factor of hostiles inside the seal (0.45 = 55 % slow). */
    public float slowFactor = 0.45f;

    /** Seconds the slow lasts after leaving the seal. */
    public float slowDuration = 2.5f;

    /** Minimum seconds between two casts. */
    public float cooldown = 0.35f;

    /** Maximum distance between player and seal centre. */
    public float castRange = 280f;

    /** Lifetime of the seal entity (expansion + fade). */
    public float sealDuration = 0.9f;

    /** Selected index inside the purchased Hanko list (HUD only). */
    public int hankoIndex = 0;

    /** Glow colour of the projected seal. */
    public float red = 0.95f;
    public float green = 0.95f;
    public float blue = 0.85f;
}
