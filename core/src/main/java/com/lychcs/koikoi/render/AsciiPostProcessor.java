package com.lychcs.koikoi.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;

/**
 * ASCII post-processing pass of the first-person overworld.
 *
 * <p>The 3D scene is rendered into an off-screen {@link FrameBuffer} with a
 * depth buffer. {@link #end()} then replays that image through
 * {@code ascii_post.frag}, which quantizes the scene into terminal cells and
 * stamps procedural glyphs, producing the dark moss-green ASCII matrix.</p>
 *
 * <p>The class owns its batch, shader and framebuffer; the screen only drives
 * {@link #resize(int, int)}, {@link #begin()}, {@link #end()} and
 * {@link #dispose()}.</p>
 */
public final class AsciiPostProcessor implements Disposable {

    public static final String VERTEX_SHADER = "shaders/default.vert";
    public static final String FRAGMENT_SHADER = "shaders/ascii_post.frag";

    /** Terminal ink of the glyph matrix (moss green). */
    public static final Color INK = new Color(0.40f, 0.86f, 0.46f, 1f);

    /** Golden ink tint used where warm lantern/temple light dominates. */
    public static final Color WARM_INK = new Color(1.00f, 0.68f, 0.26f, 1f);

    /** Deep terminal background used for the FBO clear and the bezel. */
    public static final Color BACKGROUND = new Color(0.012f, 0.030f, 0.017f, 1f);

    /** Terminal rows across the screen height; cells keep a monospace aspect. */
    private static final int TARGET_ROWS = 104;

    /** Width of one glyph cell relative to its height. */
    private static final float GLYPH_ASPECT = 0.58f;

    private final SpriteBatch batch = new SpriteBatch();
    private final ShaderProgram shader;

    private FrameBuffer fbo;
    private TextureRegion region;
    private int width;
    private int height;
    private float time;

    /** Camera basis for the procedural night sky (moon + drifting clouds). */
    private final Vector3 skyForward = new Vector3(0f, 0f, -1f);
    private final Vector3 skyRight = new Vector3(1f, 0f, 0f);
    private final Vector3 skyUp = new Vector3(0f, 1f, 0f);
    private float tanHalfFovX = 0.73f;
    private float tanHalfFovY = 0.73f;

    public AsciiPostProcessor() {
        shader = new ShaderProgram(
            Gdx.files.internal(VERTEX_SHADER), Gdx.files.internal(FRAGMENT_SHADER));
        if (!shader.isCompiled()) {
            String log = shader.getLog();
            shader.dispose();
            batch.dispose();
            throw new GdxRuntimeException("Failed to compile ascii_post shader:\n" + log);
        }
    }

    /** (Re)creates the scene framebuffer when the window size changed. */
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        if (fbo != null && this.width == width && this.height == height) {
            return;
        }
        disposeFramebuffer();

        fbo = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, true);
        region = new TextureRegion(fbo.getColorBufferTexture());
        region.flip(false, true);
        this.width = width;
        this.height = height;
    }

    /** true when the framebuffer already matches the requested size. */
    public boolean isReady(int width, int height) {
        return fbo != null && this.width == width && this.height == height;
    }

    /** Binds the scene framebuffer and prepares colour + depth for the 3D pass. */
    public void begin() {
        if (fbo == null) {
            return;
        }
        fbo.begin();
        Gdx.gl.glViewport(0, 0, fbo.getWidth(), fbo.getHeight());
        // Alpha 0 marks "sky" so the ASCII pass can stamp the night sky there;
        // every 3D vertex writes alpha 1.
        Gdx.gl.glClearColor(BACKGROUND.r, BACKGROUND.g, BACKGROUND.b, 0f);
        Gdx.gl.glClearDepthf(1f);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
    }

    /** Runs the ASCII glyph shader from the scene framebuffer to the screen. */
    public void end() {
        if (fbo == null) {
            return;
        }
        fbo.end();

        int screenWidth = Gdx.graphics.getWidth();
        int screenHeight = Gdx.graphics.getHeight();

        Gdx.gl.glViewport(0, 0, screenWidth, screenHeight);
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glClearColor(BACKGROUND.r, BACKGROUND.g, BACKGROUND.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        float cellHeight = Math.max(7f, screenHeight / (float) TARGET_ROWS);
        float cellWidth = cellHeight * GLYPH_ASPECT;

        batch.getProjectionMatrix().setToOrtho2D(0f, 0f, screenWidth, screenHeight);
        batch.begin();
        batch.setShader(shader);
        shader.setUniformf("u_resolution", (float) screenWidth, (float) screenHeight);
        shader.setUniformf("u_cellSize", cellWidth, cellHeight);
        shader.setUniformf("u_time", time);
        shader.setUniformf("u_ink", INK.r, INK.g, INK.b);
        shader.setUniformf("u_warmInk", WARM_INK.r, WARM_INK.g, WARM_INK.b);

        // Night sky: fixed moon direction and the current camera basis.
        shader.setUniformf("u_moonDir", NightSky.MOON_DIRECTION);
        shader.setUniformf("u_moonInk",
            NightSky.MOON_RED, NightSky.MOON_GREEN, NightSky.MOON_BLUE);
        shader.setUniformf("u_cloudInk",
            NightSky.CLOUD_RED, NightSky.CLOUD_GREEN, NightSky.CLOUD_BLUE);
        shader.setUniformf("u_skyForward", skyForward);
        shader.setUniformf("u_skyRight", skyRight);
        shader.setUniformf("u_skyUp", skyUp);
        shader.setUniformf("u_tanHalfFovX", tanHalfFovX);
        shader.setUniformf("u_tanHalfFovY", tanHalfFovY);
        shader.setUniformf("u_moonCosOuter",
            MathUtils.cosDeg(NightSky.MOON_RADIUS_DEGREES));
        shader.setUniformf("u_moonCosInner",
            MathUtils.cosDeg(NightSky.MOON_RADIUS_DEGREES * 0.78f));
        shader.setUniformf("u_cloudDriftX", NightSky.CLOUD_DRIFT_X);
        shader.setUniformf("u_cloudDriftY", NightSky.CLOUD_DRIFT_Y);

        batch.draw(region, 0f, 0f, screenWidth, screenHeight);
        batch.end();
        batch.setShader(null);
    }

    /** Screen time used by the glyph breathing animation. */
    public void setTime(float time) {
        this.time = time;
    }

    /**
     * Stores the camera basis used to reconstruct a view ray per fragment, so
     * the ASCII moon and cloud layers behave like a real skybox while turning.
     */
    public void setCamera(PerspectiveCamera camera) {
        if (camera == null) {
            return;
        }
        skyForward.set(camera.direction).nor();
        skyRight.set(skyForward).crs(Vector3.Y);
        if (skyRight.len2() <= 0.0001f) {
            skyRight.set(1f, 0f, 0f);
        } else {
            skyRight.nor();
        }
        skyUp.set(skyRight).crs(skyForward).nor();

        float halfFov = camera.fieldOfView * MathUtils.degreesToRadians * 0.5f;
        tanHalfFovY = Math.max(0.05f, (float) Math.tan(halfFov));
        float aspect = camera.viewportHeight <= 0f
            ? 1.777f : camera.viewportWidth / camera.viewportHeight;
        tanHalfFovX = tanHalfFovY * Math.max(0.1f, aspect);
    }

    /** Scene framebuffer (may be {@code null} before the first resize). */
    public FrameBuffer getFrameBuffer() {
        return fbo;
    }

    public ShaderProgram getShader() {
        return shader;
    }

    @Override
    public void dispose() {
        disposeFramebuffer();
        batch.dispose();
        shader.dispose();
    }

    private void disposeFramebuffer() {
        if (fbo != null) {
            fbo.dispose();
            fbo = null;
        }
        region = null;
        width = 0;
        height = 0;
    }
}
