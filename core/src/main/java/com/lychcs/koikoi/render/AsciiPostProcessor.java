package com.lychcs.koikoi.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;

/**
 * Atmospheric post-processing pass of the third-person overworld.
 *
 * <p>The 3D world and player avatar render at native resolution into a scene
 * {@link FrameBuffer}. {@link #end()} then runs a single atmospheric pass:</p>
 * <ul>
 *   <li>soft, restrained warm bloom around lanterns, the moon and magic,</li>
 *   <li>stationary Washi-paper/watercolor grain (~6%),</li>
 *   <li>distance-attenuated deep-indigo ink-wash outlines.</li>
 * </ul>
 *
 * <p>There is no ASCII glyph quantization in this pass. Magic glyphs are
 * rendered directly as 3D atlas quads by {@link HeightmapTerrainRenderer}, so
 * buildings, foliage and terrain can never acquire a false character grid.</p>
 *
 * <p>The class owns its batch, shader and framebuffers; the screen only drives
 * {@link #resize(int, int)}, {@link #begin()}, {@link #end()} and
 * {@link #dispose()}.</p>
 */
public final class AsciiPostProcessor implements Disposable {

    public static final String VERTEX_SHADER = "shaders/default.vert";
    public static final String FRAGMENT_SHADER = "shaders/ascii_post.frag";

    /** Subtle stationary paper grain amplitude. */
    public static final float GRAIN_STRENGTH = 0.06f;

    /** Bloom sample radius in screen pixels. */
    public static final float BLOOM_RADIUS_PIXELS = 10f;

    /** Outline distance attenuation: full ink near, soft wash far away. */
    public static final float OUTLINE_FADE_START = 26f;
    public static final float OUTLINE_FADE_END = 110f;

    /** Warm lantern gold (#F2C14E) for lantern cores and bloom. */
    public static final Color WARM_INK = new Color(0.949f, 0.757f, 0.306f, 1f);

    /** Crisp midnight-indigo ink (#0B0E1D) of the distance-attenuated outlines. */
    public static final Color OUTLINE_INK = new Color(0.043f, 0.055f, 0.114f, 1f);

    /** Deep midnight-indigo terminal background used for the FBO clear and bezel. */
    public static final Color BACKGROUND = new Color(0.043f, 0.055f, 0.114f, 1f);

    private final SpriteBatch batch = new SpriteBatch();
    private final ShaderProgram shader;

    private FrameBuffer fbo;
    /** Normal/depth prepass of the current frame (ink-wash outline input). */
    private FrameBuffer edgeFbo;
    private TextureRegion region;

    private int width;
    private int height;
    private float time;
    private float farPlane = 300f;

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
        // Nearest sampling is mandatory: linear filtering would interpolate the
        // alpha of an opaque silhouette (1.0) with sky (0.0), producing false
        // ~0.5 magic tags along torii beams, temple roofs and tree canopies.
        fbo.getColorBufferTexture().setFilter(
            Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        region = new TextureRegion(fbo.getColorBufferTexture());
        region.flip(false, true);
        // Plain RGBA target with a depth renderbuffer: readable by GL2/GLES2,
        // no depth-texture or MRT extension required. Nearest filtering keeps
        // the packed normal/depth bytes exact at silhouette samples.
        edgeFbo = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, true);
        edgeFbo.getColorBufferTexture().setFilter(
            Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);

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
        // The HUD's SpriteBatch may leave blending enabled. Magic quads carry
        // alpha 0.5 as a tag, so the scene must write raw alpha without blending.
        Gdx.gl.glDisable(GL20.GL_BLEND);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
        Gdx.gl.glDepthMask(true);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
    }

    /** Runs the atmospheric bloom / Washi grain / ink-wash outline pass. */
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

        batch.getProjectionMatrix().setToOrtho2D(0f, 0f, screenWidth, screenHeight);
        batch.begin();
        batch.setShader(shader);
        shader.setUniformf("u_resolution", (float) screenWidth, (float) screenHeight);
        shader.setUniformf("u_time", time);
        shader.setUniformf("u_grain", GRAIN_STRENGTH);
        shader.setUniformf("u_bloomRadius", BLOOM_RADIUS_PIXELS);
        shader.setUniformf("u_outlineFadeStart", OUTLINE_FADE_START);
        shader.setUniformf("u_outlineFadeEnd", OUTLINE_FADE_END);
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

        // Ink-wash outline: depth/normal prepass from the terrain renderer.
        shader.setUniformf("u_outlineInk", OUTLINE_INK.r, OUTLINE_INK.g, OUTLINE_INK.b);
        shader.setUniformf("u_far", farPlane);

        // Edge prepass texture on unit 1; scene texture uses unit 0.
        if (edgeFbo != null) {
            edgeFbo.getColorBufferTexture().bind(1);
            shader.setUniformi("u_edgeTexture", 1);
            shader.setUniformf("u_edgeEnabled", 1f);
        } else {
            shader.setUniformf("u_edgeEnabled", 0f);
        }
        // Texture.bind(int) leaves that unit active; the SpriteBatch binds the
        // scene texture on unit 0, so restore it before drawing.
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);

        batch.draw(region, 0f, 0f, screenWidth, screenHeight);
        batch.end();
        batch.setShader(null);
    }

    /** Screen time used by the drifting night-sky cloud bands. */
    public void setTime(float time) {
        this.time = time;
    }

    /**
     * Stores the camera basis used to reconstruct a view ray per fragment, so
     * the moon and cloud layers behave like a real skybox while turning.
     */
    public void setCamera(PerspectiveCamera camera) {
        if (camera == null) {
            return;
        }
        skyForward.set(camera.direction).nor();
        farPlane = Math.max(camera.near + 1f, camera.far);
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

    /** Normal/depth prepass target filled by the terrain renderer. */
    public FrameBuffer getEdgeFrameBuffer() {
        return edgeFbo;
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
        if (edgeFbo != null) {
            edgeFbo.dispose();
            edgeFbo = null;
        }
        region = null;
        width = 0;
        height = 0;
    }
}
