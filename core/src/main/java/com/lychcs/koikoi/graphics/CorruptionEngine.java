package com.lychcs.koikoi.graphics;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.GdxRuntimeException;

public class CorruptionEngine {
    private static final int SIM_SIZE = 256;
    private static final float FIXED_TIME_STEP = 1f / 60f;
    private static final int ITERATIONS_PER_TICK = 2;
    private static final int MAX_TICKS_PER_FRAME = 4;
    private static final float MAX_FRAME_DELTA = 0.25f;

    private static final float FEED = 0.035f;
    private static final float KILL = 0.058f;

    private final SpriteBatch batch;
    private final FrameBuffer fboA, fboB;
    private final ShaderProgram simShader;

    private boolean isPingPong = true;
    private boolean active = false;
    private float accumulator = 0f;

    private boolean hasPendingInjection = false;
    private float pendingInjectRadius = 0f;
    private float injectX = -1f;
    private float injectY = -1f;

    private static final String VERT =
        "#ifdef GL_ES\nprecision mediump float;\n#endif\n" +
            "attribute vec4 a_position; attribute vec2 a_texCoord0; varying vec2 v_texCoords; uniform mat4 u_projTrans; void main() { v_texCoords = a_texCoord0; gl_Position = u_projTrans * a_position; }";

    private static final String FRAG =
        "#ifdef GL_ES\nprecision mediump float;\n#endif\n" +
            "varying vec2 v_texCoords; uniform sampler2D u_texture; uniform vec2 u_resolution;\n" +
            "uniform float u_feed; uniform float u_kill;\n" +
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
        batch.getProjectionMatrix().setToOrtho2D(0, 0, SIM_SIZE, SIM_SIZE);

        simShader = new ShaderProgram(VERT, FRAG);
        if (!simShader.isCompiled()) {
            throw new GdxRuntimeException(
                "CorruptionEngine Gray-Scott-Shader konnte nicht kompiliert werden:\n" + simShader.getLog()
            );
        }

        fboA = new FrameBuffer(Pixmap.Format.RGBA8888, SIM_SIZE, SIM_SIZE, false);
        fboA.getColorBufferTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        fboA.getColorBufferTexture().setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);

        fboB = new FrameBuffer(Pixmap.Format.RGBA8888, SIM_SIZE, SIM_SIZE, false);
        fboB.getColorBufferTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        fboB.getColorBufferTexture().setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);

        clearFbo(fboA);
        clearFbo(fboB);
        isPingPong = true;
        active = false;
    }

    private void clearFbo(FrameBuffer fbo) {
        fbo.begin();
        Gdx.gl.glClearColor(1f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        fbo.end();
    }

    public void reset() {
        clearFbo(fboA);
        clearFbo(fboB);
        isPingPong = true;
        accumulator = 0f;
        hasPendingInjection = false;
        pendingInjectRadius = 0f;
        injectX = -1f;
        injectY = -1f;
        active = false;
    }

    public void injectDisturbance(float normalizedX, float normalizedY, float radius) {
        if (radius > 0f) {
            this.injectX = normalizedX;
            this.injectY = normalizedY;
            this.pendingInjectRadius = radius;
            this.hasPendingInjection = true;
            this.active = true;
        }
    }

    public void update(float delta) {
        if (!active) {
            return;
        }

        if (delta <= 0f) {
            return;
        }

        if (delta > MAX_FRAME_DELTA) {
            delta = MAX_FRAME_DELTA;
        }

        accumulator += delta;

        int ticks = 0;
        while (accumulator >= FIXED_TIME_STEP && ticks < MAX_TICKS_PER_FRAME) {
            float currentRadius = hasPendingInjection ? pendingInjectRadius : 0f;
            hasPendingInjection = false;
            pendingInjectRadius = 0f;

            for (int i = 0; i < ITERATIONS_PER_TICK; i++) {
                stepSimulation(currentRadius);
            }

            accumulator -= FIXED_TIME_STEP;
            ticks++;
        }

        if (accumulator >= FIXED_TIME_STEP) {
            accumulator = 0f;
        }
    }

    private void stepSimulation(float radius) {
        FrameBuffer source = isPingPong ? fboA : fboB;
        FrameBuffer target = isPingPong ? fboB : fboA;

        target.begin();
        batch.setShader(simShader);
        batch.begin();

        setSimulationUniforms();
        setInjectionUniforms(radius);

        batch.draw(source.getColorBufferTexture(), 0, 0, SIM_SIZE, SIM_SIZE, 0, 0, SIM_SIZE, SIM_SIZE, false, true);

        batch.end();
        batch.setShader(null);
        target.end();

        isPingPong = !isPingPong;
    }

    private void setSimulationUniforms() {
        simShader.setUniformi("u_texture", 0);
        simShader.setUniformf("u_resolution", SIM_SIZE, SIM_SIZE);
        simShader.setUniformf("u_feed", FEED);
        simShader.setUniformf("u_kill", KILL);
    }

    private void setInjectionUniforms(float radius) {
        simShader.setUniformf("u_injectPos", injectX, injectY);
        simShader.setUniformf("u_injectRadius", radius);
    }

    public Texture getCorruptionMap() {
        return isPingPong ? fboA.getColorBufferTexture() : fboB.getColorBufferTexture();
    }

    public boolean isActive() {
        return active;
    }

    public void dispose() {
        batch.dispose();
        fboA.dispose();
        fboB.dispose();
        simShader.dispose();
    }
}
