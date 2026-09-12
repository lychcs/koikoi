package com.lychcs.koikoi.graphics;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;

public final class HankoShaderManager {

    private static ShaderProgram polychromeShader;
    private static ShaderProgram pulseShader;
    private static ShaderProgram voidFlameShader;
    private static float totalTime = 0f;


    private HankoShaderManager() {}

    public static void initialize() {
        ShaderProgram.pedantic = false;

        String vert = Gdx.files.internal("shaders/default.vert").readString();
        String polyFrag = Gdx.files.internal("shaders/polychrome.frag").readString();
        String pulseFrag = Gdx.files.internal("shaders/pulse.frag").readString();
        String flameFrag = Gdx.files.internal("shaders/yokai_flame.frag").readString(); // NEU

        polychromeShader = new ShaderProgram(vert, polyFrag);
        pulseShader = new ShaderProgram(vert, pulseFrag);
        voidFlameShader = new ShaderProgram(vert, flameFrag); // NEU

        if (!voidFlameShader.isCompiled()) {
            Gdx.app.error("Shader", "Void Flame error: " + voidFlameShader.getLog());
        }
    }

    public static ShaderProgram getVoidFlameShader() { return voidFlameShader; }

    public static void update(float delta) {
        totalTime += delta;
    }

    public static float getTotalTime() {
        return totalTime;
    }

    public static ShaderProgram getPolychromeShader() {
        return polychromeShader;
    }

    public static ShaderProgram getPulseShader() {
        return pulseShader;
    }

    public static void dispose() {
        if (polychromeShader != null) polychromeShader.dispose();
        if (pulseShader != null) pulseShader.dispose();
        if (voidFlameShader != null) voidFlameShader.dispose();
    }
}
