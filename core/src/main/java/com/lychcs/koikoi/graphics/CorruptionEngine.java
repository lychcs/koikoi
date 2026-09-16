package com.lychcs.koikoi.graphics;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;

public class CorruptionEngine {
    private static final int SIM_SIZE = 256;

    private final SpriteBatch batch;
    private final FrameBuffer fboA, fboB;
    private final ShaderProgram simShader;

    private boolean isPingPong = true;
    private float injectX = -1f, injectY = -1f, injectRadius = 0f;

    private float time = 0f;

    private static final String VERT =
        "attribute vec4 a_position; attribute vec2 a_texCoord0; varying vec2 v_texCoords; uniform mat4 u_projTrans; void main() { v_texCoords = a_texCoord0; gl_Position = u_projTrans * a_position; }";

    private static final String FRAG =
        "#ifdef GL_ES\nprecision highp float;\n#endif\n" +
            "varying vec2 v_texCoords; uniform sampler2D u_texture; uniform vec2 u_resolution;\n" +
            "uniform float u_feed; uniform float u_kill; uniform float u_time;\n" +
            "uniform vec2 u_injectPos; uniform float u_injectRadius;\n" +
            "void main() {\n" +
            "    vec2 step = 1.0 / u_resolution; vec2 uv = v_texCoords;\n" +
            "    vec2 center = texture2D(u_texture, uv).rg;\n" +
            "    vec2 laplace = center * -1.0;\n" +
            "    laplace += texture2D(u_texture, uv + vec2(-step.x, 0.0)).rg * 0.2;\n" +
            "    laplace += texture2D(u_texture, uv + vec2(step.x, 0.0)).rg * 0.2;\n" +
            "    laplace += texture2D(u_texture, uv + vec2(0.0, -step.y)).rg * 0.2;\n" +
            "    laplace += texture2D(u_texture, uv + vec2(0.0, step.y)).rg * 0.2;\n" +
            "    laplace += texture2D(u_texture, uv + vec2(-step.x, -step.y)).rg * 0.05;\n" +
            "    laplace += texture2D(u_texture, uv + vec2(step.x, -step.y)).rg * 0.05;\n" +
            "    laplace += texture2D(u_texture, uv + vec2(-step.x, step.y)).rg * 0.05;\n" +
            "    laplace += texture2D(u_texture, uv + vec2(step.x, step.y)).rg * 0.05;\n" +
            "    float u = center.r; float v = center.g; float reaction = u * v * v;\n" +
            "    float nextU = u + (1.0 * laplace.r - reaction + u_feed * (1.0 - u));\n" +
            "    float nextV = v + (0.5 * laplace.g + reaction - (u_feed + u_kill) * v);\n" +

            // FIX: Wir injizieren feste Masse, damit die Simulation überlebt und wuchern kann!
            "    if (u_injectRadius > 0.0 && distance(uv, u_injectPos) < u_injectRadius) {\n" +
            "        nextV = 1.0;\n" +
            "    }\n" +
            "    gl_FragColor = vec4(clamp(nextU, 0.0, 1.0), clamp(nextV, 0.0, 1.0), 0.0, 1.0);\n" +
            "}";

    public CorruptionEngine() {
        batch = new SpriteBatch();

        // HIER IST DER FIX: Wir zwingen den Batch exakt auf das 256x256 FBO!
        batch.getProjectionMatrix().setToOrtho2D(0, 0, SIM_SIZE, SIM_SIZE);

        simShader = new ShaderProgram(VERT, FRAG);

        fboA = new FrameBuffer(Pixmap.Format.RGBA8888, SIM_SIZE, SIM_SIZE, false);
        fboA.getColorBufferTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        fboB = new FrameBuffer(Pixmap.Format.RGBA8888, SIM_SIZE, SIM_SIZE, false);
        fboB.getColorBufferTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);

        fboA.begin();
        Gdx.gl.glClearColor(1f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        fboA.end();
    }

    public void injectDisturbance(float normalizedX, float normalizedY, float radius) {
        this.injectX = normalizedX;
        this.injectY = normalizedY;
        this.injectRadius = radius;
    }

    public void update(float delta) {
        time += delta; // Zeit hochzählen
        int iterations = 2;
        for (int i = 0; i < iterations; i++) {
            FrameBuffer source = isPingPong ? fboA : fboB;
            FrameBuffer target = isPingPong ? fboB : fboA;

            target.begin();
            batch.setShader(simShader);
            batch.begin();
            simShader.setUniformf("u_resolution", SIM_SIZE, SIM_SIZE);
            simShader.setUniformf("u_time", time); // Zeit an Shader übergeben
            simShader.setUniformf("u_feed", 0.035f);
            simShader.setUniformf("u_kill", 0.058f);
            simShader.setUniformf("u_injectPos", injectX, injectY);
            simShader.setUniformf("u_injectRadius", injectRadius);

            // FBO korrekt zeichnen (verhindert das umkippen)
            batch.draw(source.getColorBufferTexture(), 0, 0, SIM_SIZE, SIM_SIZE, 0, 0, SIM_SIZE, SIM_SIZE, false, true);
            batch.end();
            target.end();

            isPingPong = !isPingPong;
        }
        injectRadius = 0f;
    }

    public Texture getCorruptionMap() {
        return isPingPong ? fboA.getColorBufferTexture() : fboB.getColorBufferTexture();
    }

    public void dispose() {
        batch.dispose(); fboA.dispose(); fboB.dispose(); simShader.dispose();
    }
}
