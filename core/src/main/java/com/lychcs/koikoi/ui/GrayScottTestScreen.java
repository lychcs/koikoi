package com.lychcs.koikoi.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;

public class GrayScottTestScreen extends ScreenAdapter {

    // Grid-Auflösung der Simulation (512x512 ist ein guter Sweetspot)
    private static final int SIM_SIZE = 512;

    private SpriteBatch batch;
    private FrameBuffer fboA;
    private FrameBuffer fboB;
    private TextureRegion fboRegion;

    private ShaderProgram simShader;
    private ShaderProgram renderShader;

    // Simulations-Status
    private boolean isPingPong = true;
    private float touchX = -1f;
    private float touchY = -1f;
    private boolean isTouching = false;
    private float time = 0f;

    // ==========================================
    // 1. VERTEX SHADER (Standard)
    // ==========================================
    private static final String VERT_SHADER =
        "#ifdef GL_ES\n" +
            "precision mediump float;\n" +
            "#endif\n" +
            "attribute vec4 a_position;\n" +
            "attribute vec4 a_color;\n" +
            "attribute vec2 a_texCoord0;\n" +
            "varying vec4 v_color;\n" +
            "varying vec2 v_texCoords;\n" +
            "uniform mat4 u_projTrans;\n" +
            "void main() {\n" +
            "    v_color = a_color;\n" +
            "    v_texCoords = a_texCoord0;\n" +
            "    gl_Position = u_projTrans * a_position;\n" +
            "}";

    // ==========================================
    // 2. SIMULATIONS-SHADER (Die Turing-Magie)
    // ==========================================
    private static final String SIM_FRAG_SHADER =
        "#ifdef GL_ES\n" +
            "precision mediump float;\n" +
            "#endif\n" +
            "varying vec2 v_texCoords;\n" +
            "uniform sampler2D u_texture;\n" +
            "uniform vec2 u_resolution;\n" +
            "uniform float u_feed;\n" +
            "uniform float u_kill;\n" +
            "uniform vec2 u_touchPos;\n" +
            "uniform float u_isTouched;\n" +

            "void main() {\n" +
            "    vec2 pixelSize = 1.0 / u_resolution;\n" +
            "    vec2 uv = v_texCoords;\n" +

            "    // 3x3 Laplace-Filter (berechnet die Ausbreitung/Diffusion)\n" +
            "    vec2 center = texture2D(u_texture, uv).rg;\n" +
            "    vec2 laplace = center * -1.0;\n" +
            "    laplace += texture2D(u_texture, uv + vec2(-pixelSize.x, 0.0)).rg * 0.2;\n" +
            "    laplace += texture2D(u_texture, uv + vec2(pixelSize.x, 0.0)).rg * 0.2;\n" +
            "    laplace += texture2D(u_texture, uv + vec2(0.0, -pixelSize.y)).rg * 0.2;\n" +
            "    laplace += texture2D(u_texture, uv + vec2(0.0, pixelSize.y)).rg * 0.2;\n" +
            "    laplace += texture2D(u_texture, uv + vec2(-pixelSize.x, -pixelSize.y)).rg * 0.05;\n" +
            "    laplace += texture2D(u_texture, uv + vec2(pixelSize.x, -pixelSize.y)).rg * 0.05;\n" +
            "    laplace += texture2D(u_texture, uv + vec2(-pixelSize.x, pixelSize.y)).rg * 0.05;\n" +
            "    laplace += texture2D(u_texture, uv + vec2(pixelSize.x, pixelSize.y)).rg * 0.05;\n" +

            "    float u = center.r;\n" +
            "    float v = center.g;\n" +
            "    float reaction = u * v * v;\n" + // Die Kern-Reaktion!

            "    // Gray-Scott Differentialgleichung\n" +
            "    float du = 1.0; // Diffusion U (Realität)\n" +
            "    float dv = 0.5; // Diffusion V (Yokai-Essenz)\n" +
            "    float nextU = u + (du * laplace.r - reaction + u_feed * (1.0 - u));\n" +
            "    float nextV = v + (dv * laplace.g + reaction - (u_feed + u_kill) * v);\n" +

            "    // Maus-Injektion (Störfaktor durch den Spieler)\n" +
            "    if (u_isTouched > 0.5) {\n" +
            "        float dist = distance(uv, u_touchPos);\n" +
            "        if (dist < 0.04) {\n" +
            "            nextV = 1.0;\n" +
            "        }\n" +
            "    }\n" +

            "    gl_FragColor = vec4(clamp(nextU, 0.0, 1.0), clamp(nextV, 0.0, 1.0), 0.0, 1.0);\n" +
            "}";

    // ==========================================
    // 3. RENDER-SHADER (Psychedelischer Ukiyo-e Look)
    // ==========================================
    private static final String RENDER_FRAG_SHADER =
        "#ifdef GL_ES\n" +
            "precision mediump float;\n" +
            "#endif\n" +
            "varying vec2 v_texCoords;\n" +
            "uniform sampler2D u_texture;\n" +
            "uniform float u_time;\n" +

            "void main() {\n" +
            "    vec2 data = texture2D(u_texture, v_texCoords).rg;\n" +
            "    float u = data.r;\n" +
            "    float v = data.g;\n" +

            "    // Farb-Mapping: \n" +
            "    // U dominiert = Tiefblau/Schwarz (Reispapier-Basis)\n" +
            "    // V dominiert = Neon Pink/Cyan (Korruption)\n" +
            "    vec3 colorU = vec3(0.02, 0.05, 0.15);\n" +
            "    vec3 colorV = vec3(1.0, 0.1, 0.6);\n" + // Neon Pink
            "    vec3 colorMix = vec3(0.0, 0.9, 1.0);\n" + // Cyan Rand

            "    // Interpoliere basierend auf der V-Konzentration\n" +
            "    vec3 finalColor = mix(colorU, colorV, v);\n" +

            "    // Ein kleiner Randeffekt, um die Muster leuchten zu lassen\n" +
            "    float edge = clamp((v - 0.1) * 5.0, 0.0, 1.0);\n" +
            "    float border = edge * (1.0 - edge) * 4.0;\n" +
            "    finalColor += colorMix * border;\n" +

            "    gl_FragColor = vec4(finalColor, 1.0);\n" +
            "}";


    public GrayScottTestScreen() {
        batch = new SpriteBatch();
        ShaderProgram.pedantic = false;

        simShader = new ShaderProgram(VERT_SHADER, SIM_FRAG_SHADER);
        if (!simShader.isCompiled()) {
            System.err.println("SimShader Error: " + simShader.getLog());
        }

        renderShader = new ShaderProgram(VERT_SHADER, RENDER_FRAG_SHADER);
        if (!renderShader.isCompiled()) {
            System.err.println("RenderShader Error: " + renderShader.getLog());
        }

        // FBOs initialisieren (Standard RGBA)
        fboA = new FrameBuffer(Pixmap.Format.RGBA8888, SIM_SIZE, SIM_SIZE, false);
        fboA.getColorBufferTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);

        fboB = new FrameBuffer(Pixmap.Format.RGBA8888, SIM_SIZE, SIM_SIZE, false);
        fboB.getColorBufferTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);

        // Grid mit Realität (R = 1.0) füllen
        fboA.begin();
        Gdx.gl.glClearColor(1f, 0f, 0f, 1f); // U=1, V=0
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        fboA.end();

        // Initialer Samen in der Mitte
        touchX = 0.5f;
        touchY = 0.5f;
        isTouching = true;
        simulateStep(fboA, fboB);
        isTouching = false;

        // Input Setup für die Maus
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                isTouching = true;
                updateTouch(screenX, screenY);
                return true;
            }

            @Override
            public boolean touchDragged(int screenX, int screenY, int pointer) {
                updateTouch(screenX, screenY);
                return true;
            }

            @Override
            public boolean touchUp(int screenX, int screenY, int pointer, int button) {
                isTouching = false;
                return true;
            }

            private void updateTouch(int screenX, int screenY) {
                touchX = (float) screenX / Gdx.graphics.getWidth();
                // OpenGL Koordinaten (Y ist unten 0)
                touchY = 1.0f - ((float) screenY / Gdx.graphics.getHeight());
            }
        });
    }

    private void simulateStep(FrameBuffer source, FrameBuffer target) {
        target.begin();

        batch.setShader(simShader);
        batch.begin();

        simShader.setUniformf("u_resolution", SIM_SIZE, SIM_SIZE);
        // Diese Parameter definieren das Chaos!
        // f=0.030, k=0.055 ergibt expandierende organische Labyrinthe.
        simShader.setUniformf("u_feed", 0.0545f);
        simShader.setUniformf("u_kill", 0.0620f);
        simShader.setUniformf("u_touchPos", touchX, touchY);
        simShader.setUniformf("u_isTouched", isTouching ? 1.0f : 0.0f);

        // Wir zeichnen die Textur des Source-FBOs in das Target-FBO
        batch.draw(source.getColorBufferTexture(), 0, 0, SIM_SIZE, SIM_SIZE);

        batch.end();
        target.end();
    }

    @Override
    public void render(float delta) {
        time += delta;

        // 1. SIMULATIONS-PHASE
        // Wir lassen den Algorithmus 8x pro Frame laufen, damit er sichtbar schnell wächst.
        int iterations = 25;
        for (int i = 0; i < iterations; i++) {
            if (isPingPong) {
                simulateStep(fboA, fboB);
            } else {
                simulateStep(fboB, fboA);
            }
            isPingPong = !isPingPong;
        }

        // 2. RENDER-PHASE (Auf den echten Bildschirm)
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        FrameBuffer resultFbo = isPingPong ? fboA : fboB;
        fboRegion = new TextureRegion(resultFbo.getColorBufferTexture());
        fboRegion.flip(false, true); // Y-Achse korrigieren

        batch.setShader(renderShader);
        batch.begin();
        renderShader.setUniformf("u_time", time);

        // Vollbild-Rendering
        batch.draw(fboRegion, 0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        batch.end();
    }

    @Override
    public void dispose() {
        batch.dispose();
        fboA.dispose();
        fboB.dispose();
        simShader.dispose();
        renderShader.dispose();
    }
}
