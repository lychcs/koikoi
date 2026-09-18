package com.lychcs.koikoi.graphics;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.GdxRuntimeException;

public final class HankoShaderManager {

    private static ShaderProgram polychromeShader;
    private static ShaderProgram pulseShader;
    private static ShaderProgram voidFlameShader;
    private static float totalTime = 0f;
    private static boolean initialized = false;

    private HankoShaderManager() {}

    /**
     * Kompiliert die gemeinsam genutzten Hanko-Shader genau einmal. Ein zweiter
     * Aufruf ist wirkungslos, damit keine Shader mehrfach erzeugt werden.
     *
     * @throws GdxRuntimeException wenn ein Shader nicht kompiliert werden kann
     *         (Meldung enthaelt Shadername und Log)
     */
    public static void initialize() {
        if (initialized) {
            return;
        }

        ShaderProgram.pedantic = false;

        String vert = Gdx.files.internal("shaders/default.vert").readString();
        String polyFrag = Gdx.files.internal("shaders/polychrome.frag").readString();
        String pulseFrag = Gdx.files.internal("shaders/pulse.frag").readString();
        String flameFrag = Gdx.files.internal("shaders/yokai_flame.frag").readString();

        polychromeShader = compile("Hanko polychrome", vert, polyFrag);
        pulseShader = compile("Hanko pulse", vert, pulseFrag);
        voidFlameShader = compile("Hanko void flame", vert, flameFrag);

        initialized = true;
    }

    private static ShaderProgram compile(String name, String vertexShader, String fragmentShader) {
        ShaderProgram program = new ShaderProgram(vertexShader, fragmentShader);
        if (!program.isCompiled()) {
            String log = program.getLog();
            program.dispose();
            throw new GdxRuntimeException("Failed to compile " + name + " shader:\n" + log);
        }
        return program;
    }

    public static ShaderProgram getVoidFlameShader() { return voidFlameShader; }

    public static void update(float delta) {
        totalTime += delta;
    }

    public static float getTotalTime() {
        return totalTime;
    }

    /**
     * Liefert den Polychrom-Shader. Der Shader wird ausschliesslich in
     * {@link #initialize()} erzeugt - niemals beim Zeichnen.
     *
     * @throws GdxRuntimeException wenn der Shader nicht initialisiert ist
     */
    public static ShaderProgram getPolychromeShader() {
        if (polychromeShader == null) {
            throw new GdxRuntimeException(
                "Hanko polychrome shader is not initialized: call HankoShaderManager.initialize() first.");
        }
        return polychromeShader;
    }

    /**
     * Liefert den Puls-Shader. Der Shader wird ausschliesslich in
     * {@link #initialize()} erzeugt - niemals beim Zeichnen.
     *
     * @throws GdxRuntimeException wenn der Shader nicht initialisiert ist
     */
    public static ShaderProgram getPulseShader() {
        if (pulseShader == null) {
            throw new GdxRuntimeException(
                "Hanko pulse shader is not initialized: call HankoShaderManager.initialize() first.");
        }
        return pulseShader;
    }

    /**
     * Gibt alle Shader genau einmal frei. Ein zweiter Aufruf ist wirkungslos;
     * die Felder werden anschliessend zurueckgesetzt, damit ein spaeterer
     * Zugriff nicht auf einen disposed Shader zeigt.
     */
    public static void dispose() {
        if (polychromeShader != null) {
            polychromeShader.dispose();
            polychromeShader = null;
        }
        if (pulseShader != null) {
            pulseShader.dispose();
            pulseShader = null;
        }
        if (voidFlameShader != null) {
            voidFlameShader.dispose();
            voidFlameShader = null;
        }
        initialized = false;
    }
}
