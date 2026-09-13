package com.lychcs.koikoi.graphics;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

public final class JuiceManager {

    private static float trauma = 0f;
    private static float hitstopTimer = 0f;
    private static float flashAlpha = 0f;
    private static final Vector3 originalCamPos = new Vector3();
    private static boolean camStored = false;

    private JuiceManager() {}

    /**
     * Friert das Spiel für x Millisekunden ein (Skul-Impact)
     */
    public static void hitstop(float durationSeconds) {
        hitstopTimer = Math.max(hitstopTimer, durationSeconds);
    }

    /**
     * Erhöht die Wucht (0.0 bis 1.0). Shake skaliert quadratisch!
     */
    public static void addTrauma(float amount) {
        trauma = MathUtils.clamp(trauma + amount, 0f, 1f);
    }

    /**
     * Weißer Blitz für 1-2 Frames (Silhouette Stanzung)
     */
    public static void flashScreen(float alpha) {
        flashAlpha = alpha;
    }

    public static float update(float delta, Camera camera) {
        if (!camStored) {
            originalCamPos.set(camera.position);
            camStored = true;
        }

        // 1. Hitstop-Verarbeitung: friert Scene2D-Delta ein
        if (hitstopTimer > 0f) {
            hitstopTimer -= delta;
            return 0f; // Spiel-Logik pausiert komplett
        }

        // 2. Trauma Decay
        if (trauma > 0f) {
            trauma = Math.max(0f, trauma - delta * 1.2f);
            float shake = trauma * trauma; // Quadratischer Anstieg

            float offsetX = (MathUtils.random() * 2f - 1f) * 18f * shake;
            float offsetY = (MathUtils.random() * 2f - 1f) * 18f * shake;
            camera.position.set(originalCamPos.x + offsetX, originalCamPos.y + offsetY, originalCamPos.z);
        } else {
            camera.position.set(originalCamPos);
        }
        camera.update();

        // 3. Flash Decay
        if (flashAlpha > 0f) {
            flashAlpha = Math.max(0f, flashAlpha - delta * 6f);
        }

        return delta;
    }

    public static float getFlashAlpha() { return flashAlpha; }
}
