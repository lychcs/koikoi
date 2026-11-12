package com.lychcs.koikoi.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.lychcs.koikoi.graphics.GameAssets;

import java.util.List;

/**
 * Forward 3D renderer of the third-person overworld.
 *
 * <p>Builds 32-tile chunk terrain meshes from a generated {@link Heightfield}, one
 * solid dynamic mesh for the avatar/boxes/shadow decals, a magic glyph mesh for
 * particles and dash ghosts, and dedicated procedural effect batches for Kamon
 * seals, Hanafuda cards, Ofuda talismans and sumi brush trails. Imported organic
 * nature models are drawn through their own {@link NatureModelRenderer} into the
 * same world and outline passes. Solid geometry goes through the Mokuhanga/Sumi-e
 * world shader and the outline prepass; magic is drawn directly from
 * {@link AsciiGlyphAtlas} with {@code ascii_magic.frag}, so it can never be
 * post-quantized or leak onto scenery.</p>
 *
 * <p>All work happens on the render thread; the class owns its meshes, shaders
 * and the magic glyph texture and must be disposed by the screen.</p>
 */
public final class HeightmapTerrainRenderer implements Disposable {

    public static final String VERTEX_SHADER = "shaders/ascii_world.vert";
    public static final String FRAGMENT_SHADER = "shaders/ascii_world.frag";
    /** Normal/depth prepass consumed by the post-process cel outline. */
    public static final String EDGE_FRAGMENT_SHADER = "shaders/ascii_edge.frag";
    /** Direct 3D ASCII magic glyph layer (no post quantization). */
    public static final String MAGIC_VERTEX_SHADER = "shaders/ascii_magic.vert";
    public static final String MAGIC_FRAGMENT_SHADER = "shaders/ascii_magic.frag";
    /** Procedural effect shaders (all share {@link #MAGIC_VERTEX_SHADER}). */
    public static final String KAMON_FRAGMENT_SHADER = "shaders/kamon_seal.frag";
    public static final String OFUDA_FRAGMENT_SHADER = "shaders/ofuda_paper.frag";
    public static final String CARD_FRAGMENT_SHADER = "shaders/hanafuda_card.frag";
    public static final String RIBBON_FRAGMENT_SHADER = "shaders/ink_ribbon.frag";

    /** Sampler uniform names, index-aligned with {@link GameAssets#WORLD_TEXTURES}. */
    private static final String[] WORLD_TEXTURE_UNIFORMS = {
        "u_grassTexture", "u_cliffTexture", "u_toriiTexture",
        "u_roofTexture", "u_pathTexture", "u_sakuraTexture"
    };

    /** Extra quads per frame; excess billboards are dropped instead of growing. */
    private static final int MAX_DYNAMIC_QUADS = 2048;
    /** Extra magic glyph quads per frame. */
    private static final int MAX_MAGIC_QUADS = 512;
    private static final int MAX_SEAL_QUADS = 128;
    private static final int MAX_OFUDA_QUADS = 128;
    private static final int MAX_CARD_QUADS = 128;
    private static final int MAX_RIBBON_QUADS = 96;

    /** Floats per vertex: position (3) + normal (3) + packed colour (1) + material (1). */
    private static final int FLOATS_PER_VERTEX = 8;
    /** Floats per magic glyph vertex: position (3) + UV (2) + packed colour (1). */
    private static final int MAGIC_FLOATS_PER_VERTEX = 6;
    private static final int VERTICES_PER_QUAD = 4;
    private static final int INDICES_PER_QUAD = 6;

    /** Shared vertex layout of terrain, scenery and dynamic quads. */
    static final VertexAttributes VERTEX_ATTRIBUTES = new VertexAttributes(
        new VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"),
        new VertexAttribute(VertexAttributes.Usage.Normal, 3, "a_normal"),
        new VertexAttribute(VertexAttributes.Usage.ColorPacked, 4, "a_color"),
        new VertexAttribute(VertexAttributes.Usage.Generic, 1, "a_material"));

    /** Vertex layout of directly rendered ASCII magic glyph quads. */
    static final VertexAttributes MAGIC_VERTEX_ATTRIBUTES = new VertexAttributes(
        new VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"),
        new VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, "a_texCoord0"),
        new VertexAttribute(VertexAttributes.Usage.ColorPacked, 4, "a_color"));

    /** Direction from a surface towards the key light (normalized). */
    static final Vector3 LIGHT_DIRECTION = new Vector3(-0.35f, 0.86f, 0.37f).nor();

    /** Fog blends the far hills into the deep midnight-indigo sky (#0B0E1D). */
    static final Color FOG_COLOR = new Color(0.043f, 0.055f, 0.114f, 1f);
    /** Valley haze picks up the title screen's twilight violet (#3B1E54). */
    static final Color VALLEY_FOG_COLOR = new Color(0.231f, 0.118f, 0.329f, 1f);
    static final float FOG_START = 38f;
    static final float FOG_END = 235f;
    /** Subtle height fog pooling in valleys (base height + falloff span). */
    static final float FOG_BASE_HEIGHT = -1.8f;
    static final float FOG_HEIGHT_SPAN = 7.0f;
    static final float HEIGHT_FOG_STRENGTH = 0.34f;

    /** Warm Tōrō/temple point lights uploaded to the world shader. */
    public static final int MAX_POINT_LIGHTS = 12;
    /** Distance cull radius of one terrain mesh chunk in world units. */
    private static final float TERRAIN_CHUNK_CULL_RADIUS = 180f;
    private final float[] lightPositions = new float[MAX_POINT_LIGHTS * 3];
    private final float[] lightColors = new float[MAX_POINT_LIGHTS * 3];
    private final float[] lightRanges = new float[MAX_POINT_LIGHTS];
    private final int[] selectedLights = new int[MAX_POINT_LIGHTS];

    private final Heightfield field;
    private final ShaderProgram shader;
    private final ShaderProgram edgeShader;
    private final ShaderProgram magicShader;
    private final ShaderProgram kamonShader;
    private final ShaderProgram ofudaShader;
    private final ShaderProgram cardShader;
    private final ShaderProgram ribbonShader;
    /** One terrain mesh per 32-tile spatial chunk; empty chunks stay null. */
    private final Mesh[] terrainMeshes;
    private final WorldSeasonMap.ChunkGrid terrainChunkGrid;
    private final Mesh dynamicMesh;
    private final Mesh magicMesh;
    private final Texture magicGlyphTexture;
    /** Six nearest-filtered, repeat-wrapped triplanar world materials. */
    private final Texture[] worldTextures =
        new Texture[GameAssets.WORLD_TEXTURES.length];
    /** Optional procedural village: torii, lanterns, temple, groves, paths. */
    private JapaneseScenery scenery;
    /** Imported organic trees/rocks/bushes/grass drawn through ModelBatch. */
    private NatureModelRenderer natureRenderer;
    /** Four-season hub model; supplies the shader's seasonal blend uniforms. */
    private WorldSeasonMap seasonMap;
    /** Number of warm lights selected by the most recent begin(). */
    private int selectedLightCount;

    private final float[] dynamicVertices =
        new float[MAX_DYNAMIC_QUADS * VERTICES_PER_QUAD * FLOATS_PER_VERTEX];
    private final short[] dynamicIndices = new short[MAX_DYNAMIC_QUADS * INDICES_PER_QUAD];
    private int quadCount;

    private final float[] magicVertices =
        new float[MAX_MAGIC_QUADS * VERTICES_PER_QUAD * MAGIC_FLOATS_PER_VERTEX];
    private final short[] magicIndices = new short[MAX_MAGIC_QUADS * INDICES_PER_QUAD];
    private int magicQuadCount;

    /** Procedural effect batches (same vertex layout as magic glyphs). */
    private final EffectBatch sealBatch = new EffectBatch(MAX_SEAL_QUADS);
    private final EffectBatch ofudaBatch = new EffectBatch(MAX_OFUDA_QUADS);
    private final EffectBatch cardBatch = new EffectBatch(MAX_CARD_QUADS);
    private final EffectBatch ribbonBatch = new EffectBatch(MAX_RIBBON_QUADS);
    private float effectTime;

    private final Matrix4 projView = new Matrix4();

    /** Camera-facing horizontal right vector, refreshed by {@link #begin}. */
    private float rightX = 1f;
    private float rightZ = 0f;
    private final Vector3 cameraPosition = new Vector3();
    private final Vector3 vertexNormal = new Vector3();
    private final Vector3 boxNormal = new Vector3();
    private final Color vertexColor = new Color();

    public HeightmapTerrainRenderer(Heightfield field, GameAssets assets) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        if (assets == null) {
            throw new IllegalArgumentException("assets must not be null");
        }
        this.field = field;
        for (int i = 0; i < worldTextures.length; i++) {
            worldTextures[i] = assets.getTexture(GameAssets.WORLD_TEXTURES[i]);
        }
        this.shader = new ShaderProgram(
            Gdx.files.internal(VERTEX_SHADER), Gdx.files.internal(FRAGMENT_SHADER));
        if (!shader.isCompiled()) {
            String log = shader.getLog();
            shader.dispose();
            throw new GdxRuntimeException("Failed to compile ascii_world shader:\n" + log);
        }
        this.edgeShader = new ShaderProgram(
            Gdx.files.internal(VERTEX_SHADER), Gdx.files.internal(EDGE_FRAGMENT_SHADER));
        if (!edgeShader.isCompiled()) {
            String log = edgeShader.getLog();
            edgeShader.dispose();
            shader.dispose();
            throw new GdxRuntimeException("Failed to compile ascii_edge shader:\n" + log);
        }
        this.magicShader = new ShaderProgram(
            Gdx.files.internal(MAGIC_VERTEX_SHADER),
            Gdx.files.internal(MAGIC_FRAGMENT_SHADER));
        if (!magicShader.isCompiled()) {
            String log = magicShader.getLog();
            magicShader.dispose();
            edgeShader.dispose();
            shader.dispose();
            throw new GdxRuntimeException("Failed to compile ascii_magic shader:\n" + log);
        }
        this.kamonShader = compileEffectShader(KAMON_FRAGMENT_SHADER);
        this.ofudaShader = compileEffectShader(OFUDA_FRAGMENT_SHADER);
        this.cardShader = compileEffectShader(CARD_FRAGMENT_SHADER);
        this.ribbonShader = compileEffectShader(RIBBON_FRAGMENT_SHADER);
        this.magicGlyphTexture = AsciiGlyphAtlas.createTexture();
        this.magicGlyphTexture.setFilter(
            Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        this.terrainChunkGrid = terrainChunkGrid(field);
        this.terrainMeshes = buildTerrainMeshes(field, terrainChunkGrid);
        this.dynamicMesh = buildDynamicMesh();
        this.magicMesh = buildMagicMesh();
    }

    /** 32-tile culling grid aligned to the world origin, covering the field. */
    private static WorldSeasonMap.ChunkGrid terrainChunkGrid(Heightfield field) {
        float chunk = WorldSeasonMap.CHUNK_SIZE_TILES;
        float originX = (float) Math.floor(field.getOriginX() / chunk) * chunk;
        float originZ = (float) Math.floor(field.getOriginZ() / chunk) * chunk;
        float offset = Math.max(field.getOriginX() - originX,
            field.getOriginZ() - originZ);
        float span = (float) Math.ceil((field.getSize() + offset) / chunk) * chunk;
        return new WorldSeasonMap.ChunkGrid(originX, originZ, span, chunk);
    }

    /** Number of terrain chunks allocated for this field (debug/tests). */
    public int getTerrainChunkCount() {
        return terrainMeshes.length;
    }

    /** Compiles one procedural effect shader against the shared magic vertex shader. */
    private static ShaderProgram compileEffectShader(String fragmentPath) {
        ShaderProgram program = new ShaderProgram(
            Gdx.files.internal(MAGIC_VERTEX_SHADER), Gdx.files.internal(fragmentPath));
        if (!program.isCompiled()) {
            String log = program.getLog();
            program.dispose();
            throw new GdxRuntimeException(
                "Failed to compile effect shader " + fragmentPath + ":\n" + log);
        }
        return program;
    }

    /**
     * Starts a frame: binds the shader, uploads the camera uniforms and draws
     * the static terrain. Billboards queued afterwards are flushed by
     * {@link #end()}.
     */
    public void begin(PerspectiveCamera camera, float time) {
        if (camera == null) {
            throw new IllegalArgumentException("camera must not be null");
        }
        cameraPosition.set(camera.position);
        projView.set(camera.combined);

        // Solid world/scenery geometry is drawn unblended and fully opaque.
        Gdx.gl.glDisable(GL20.GL_BLEND);
        useOpaqueDepthState();

        // Billboard right vector: camera direction crossed with world up.
        float directionX = camera.direction.x;
        float directionZ = camera.direction.z;
        float length = (float) Math.sqrt(directionX * directionX + directionZ * directionZ);
        if (length > 0.0001f) {
            rightX = -directionZ / length;
            rightZ = directionX / length;
        } else {
            rightX = 1f;
            rightZ = 0f;
        }

        shader.bind();
        for (int i = 0; i < worldTextures.length; i++) {
            worldTextures[i].bind(i);
            shader.setUniformi(WORLD_TEXTURE_UNIFORMS[i], i);
        }
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
        // Finer world-space tiling keeps the painted grass petals small and
        // delicate instead of reading as large spots across the meadow.
        shader.setUniformf("u_textureWorldScale", 0.86f);
        shader.setUniformMatrix("u_projView", camera.combined);
        shader.setUniformf("u_cameraPos", cameraPosition.x, cameraPosition.y, cameraPosition.z);
        shader.setUniformf("u_lightDir",
            LIGHT_DIRECTION.x, LIGHT_DIRECTION.y, LIGHT_DIRECTION.z);
        shader.setUniformf("u_fogColor", FOG_COLOR.r, FOG_COLOR.g, FOG_COLOR.b);
        shader.setUniformf("u_valleyFogColor",
            VALLEY_FOG_COLOR.r, VALLEY_FOG_COLOR.g, VALLEY_FOG_COLOR.b);
        shader.setUniformf("u_fogStart", FOG_START);
        shader.setUniformf("u_fogEnd", FOG_END);
        shader.setUniformf("u_fogBaseHeight", FOG_BASE_HEIGHT);
        shader.setUniformf("u_fogHeightSpan", FOG_HEIGHT_SPAN);
        shader.setUniformf("u_heightFogStrength", HEIGHT_FOG_STRENGTH);
        shader.setUniformf("u_time", time);
        SeasonShaderUniforms.upload(shader, seasonMap);
        uploadWarmLights(camera, time);

        quadCount = 0;
        magicQuadCount = 0;
        sealBatch.reset();
        ofudaBatch.reset();
        cardBatch.reset();
        ribbonBatch.reset();
        effectTime = time;
        renderTerrain(shader, camera);
        if (scenery != null) {
            scenery.render(shader, camera);
        }
        if (natureRenderer != null) {
            natureRenderer.renderWorld(camera, time,
                selectedLightCount, lightPositions, lightColors, lightRanges);
            // ModelBatch's RenderContext resets depth state on end. Restore the
            // shared world depth state before the dynamic avatar/box flush.
            useOpaqueDepthState();
            shader.bind();
        }
    }

    /**
     * Restores the one depth state shared by terrain, static scenery and
     * imported nature. ModelBatch's RenderContext resets GL state on begin/end,
     * so every hand-off back to the direct meshes must call this explicitly.
     */
    private static void useOpaqueDepthState() {
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
        Gdx.gl.glDepthMask(true);
    }

    /**
     * Installs the procedural Japanese scenery (dirt paths, torii gates, stone
     * lanterns, temple, bamboo and sakura). The renderer becomes its owner and
     * disposes it together with the terrain.
     */
    public void setScenery(JapaneseScenery scenery) {
        if (this.scenery != null && this.scenery != scenery) {
            this.scenery.dispose();
        }
        this.scenery = scenery;
    }

    /** Currently installed scenery, or {@code null}. */
    public JapaneseScenery getScenery() {
        return scenery;
    }

    /**
     * Installs the organic model renderer. The renderer becomes owned by this
     * terrain renderer and is disposed with it.
     */
    public void setNatureRenderer(NatureModelRenderer natureRenderer) {
        if (this.natureRenderer != null && this.natureRenderer != natureRenderer) {
            this.natureRenderer.dispose();
        }
        this.natureRenderer = natureRenderer;
    }

    /** Currently installed organic nature renderer, or {@code null}. */
    public NatureModelRenderer getNatureRenderer() {
        return natureRenderer;
    }

    /**
     * Installs the four-season hub model whose centre and transition window
     * drive the shader's smooth seasonal blend.
     */
    public void setWorldSeasonMap(WorldSeasonMap seasonMap) {
        this.seasonMap = seasonMap;
    }

    /** Currently installed four-season hub model, or {@code null}. */
    public WorldSeasonMap getWorldSeasonMap() {
        return seasonMap;
    }

    /**
     * Uploads the nearest warm lantern/temple lights (up to
     * {@link #MAX_POINT_LIGHTS}) with a subtle per-light flicker.
     */
    private void uploadWarmLights(PerspectiveCamera camera, float time) {
        if (scenery == null) {
            selectedLightCount = 0;
            shader.setUniformi("u_lightCount", 0);
            return;
        }
        List<SceneryPlan.WarmLight> lights = scenery.getPlan().getWarmLights();
        int wanted = Math.min(lights.size(), MAX_POINT_LIGHTS);
        int selected = 0;

        for (int slot = 0; slot < wanted; slot++) {
            int bestIndex = -1;
            float bestDistance = Float.MAX_VALUE;
            for (int index = 0; index < lights.size(); index++) {
                if (alreadySelected(index, selected)) {
                    continue;
                }
                SceneryPlan.WarmLight light = lights.get(index);
                float dx = light.x() - camera.position.x;
                float dy = light.y() - camera.position.y;
                float dz = light.z() - camera.position.z;
                float distance = dx * dx + dy * dy + dz * dz;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestIndex = index;
                }
            }
            if (bestIndex < 0) {
                break;
            }
            selectedLights[slot] = bestIndex;
            selected++;
        }

        for (int i = 0; i < selected; i++) {
            SceneryPlan.WarmLight light = lights.get(selectedLights[i]);
            lightPositions[i * 3] = light.x();
            lightPositions[i * 3 + 1] = light.y();
            lightPositions[i * 3 + 2] = light.z();

            // Slow, subtle lantern breathing. The old 10%/1.16 Hz flicker was
            // fast enough to flip ASCII cells every frame; 2% at 0.18 Hz keeps
            // the lanterns alive without becoming visual noise.
            float flicker = 0.98f + 0.02f * MathUtils.sin(time * 1.1f + i * 1.7f);
            lightColors[i * 3] = light.red() * flicker;
            lightColors[i * 3 + 1] = light.green() * flicker;
            lightColors[i * 3 + 2] = light.blue() * flicker;
            lightRanges[i] = light.radius();
        }

        shader.setUniformi("u_lightCount", selected);
        selectedLightCount = selected;
        if (selected > 0) {
            shader.setUniform3fv("u_lightPos", lightPositions, 0, selected * 3);
            shader.setUniform3fv("u_lightColor", lightColors, 0, selected * 3);
            shader.setUniform1fv("u_lightRange", lightRanges, 0, selected);
        }
    }

    private boolean alreadySelected(int lightIndex, int selectedCount) {
        for (int i = 0; i < selectedCount; i++) {
            if (selectedLights[i] == lightIndex) {
                return true;
            }
        }
        return false;
    }

    /**
     * Queues one upright ASCII glyph quad that always faces the camera.
     * Magic is rendered directly through the glyph atlas; nothing is
     * post-quantized.
     */
    public void drawBillboard(float worldX, float groundY, float worldZ,
                              float width, float height, Color color) {
        drawBillboard(worldX, groundY, worldZ, width, height, color, 0f);
    }

    /**
     * Queues one camera-facing glyph quad rotated by {@code roll} radians
     * around the view axis (spinning rune seals, tumbling cards).
     */
    public void drawBillboard(float worldX, float groundY, float worldZ,
                              float width, float height, Color color, float roll) {
        if (color == null) {
            return;
        }
        int glyph = glyphForColor(color);
        float glyphWidth = fitGlyphWidth(width, height);
        float glyphHeight = fitGlyphHeight(width, height);
        float halfWidth = glyphWidth * 0.5f;
        float halfHeight = glyphHeight * 0.5f;
        float centerY = groundY + height * 0.5f;

        // Rotated billboard axes: right' = right*cos + up*sin,
        // up' = up*cos - right*sin.
        float cos = MathUtils.cos(roll);
        float sin = MathUtils.sin(roll);
        float axisRightX = rightX * cos;
        float axisRightY = sin;
        float axisRightZ = rightZ * cos;
        float axisUpX = -rightX * sin;
        float axisUpY = cos;
        float axisUpZ = -rightZ * sin;

        queueMagicQuad(
            worldX - axisRightX * halfWidth - axisUpX * halfHeight,
            centerY - axisRightY * halfWidth - axisUpY * halfHeight,
            worldZ - axisRightZ * halfWidth - axisUpZ * halfHeight,
            worldX + axisRightX * halfWidth - axisUpX * halfHeight,
            centerY + axisRightY * halfWidth - axisUpY * halfHeight,
            worldZ + axisRightZ * halfWidth - axisUpZ * halfHeight,
            worldX + axisRightX * halfWidth + axisUpX * halfHeight,
            centerY + axisRightY * halfWidth + axisUpY * halfHeight,
            worldZ + axisRightZ * halfWidth + axisUpZ * halfHeight,
            worldX - axisRightX * halfWidth + axisUpX * halfHeight,
            centerY - axisRightY * halfWidth + axisUpY * halfHeight,
            worldZ - axisRightZ * halfWidth + axisUpZ * halfHeight,
            color, glyph);
    }

    /**
     * Queues a generic ground decal as a ring of flat ASCII glyphs. Procedural
     * Kamon seals use {@link #drawKamonSeal} instead.
     */
    public void drawGroundQuad(float worldX, float worldZ, float halfSize, Color color) {
        if (color == null || !(halfSize > 0f)) {
            return;
        }
        int glyph = glyphForColor(color);
        if (halfSize < 0.28f) {
            queueMagicGroundGlyph(worldX, worldZ,
                Math.max(0.16f, halfSize * 1.6f), glyph, color);
            return;
        }
        float centerSize = Math.max(0.24f, halfSize * 1.10f);
        queueMagicGroundGlyph(worldX, worldZ, centerSize, glyph, color);
        if (halfSize >= 0.50f) {
            float ring = halfSize * 0.60f;
            float ringSize = Math.max(0.20f, halfSize * 0.72f);
            for (int i = 0; i < 4; i++) {
                double angle = Math.PI * 0.25 + i * Math.PI * 0.5;
                queueMagicGroundGlyph(
                    worldX + (float) Math.cos(angle) * ring,
                    worldZ + (float) Math.sin(angle) * ring,
                    ringSize, glyph, color);
            }
        }
    }

    /**
     * Queues a procedural Kamon crest on the heightfield. The crest is drawn
     * entirely in {@code kamon_seal.frag}; {@code phase} seeds its gentle
     * rotation and the colour selects sakura (red), wheel (blue) or both (gold).
     */
    public void drawKamonSeal(float worldX, float worldZ, float halfSize,
                              Color color, float phase) {
        if (color == null || !(halfSize > 0f) || !sealBatch.hasRoom()) {
            return;
        }
        float minX = worldX - halfSize;
        float maxX = worldX + halfSize;
        float minZ = worldZ - halfSize;
        float maxZ = worldZ + halfSize;
        float lift = 0.060f;
        vertexColor.set(color.r, color.g, color.b,
            MathUtils.clamp(phase - (float) Math.floor(phase), 0f, 0.9999f));
        sealBatch.addQuad(
            minX, field.getHeight(minX, minZ) + lift, minZ,
            maxX, field.getHeight(maxX, minZ) + lift, minZ,
            maxX, field.getHeight(maxX, maxZ) + lift, maxZ,
            minX, field.getHeight(minX, maxZ) + lift, maxZ,
            0f, 1f, 0f, 1f, vertexColor);
    }

    /**
     * Queues one procedural Hanafuda card face. {@code axisU}/{@code axisV}
     * span the card, so the shader's sumi border and inner motif tumble with
     * the card in 3D.
     */
    public void drawHanafudaCard(Vector3 center, Vector3 axisU, Vector3 axisV,
                                 float width, float height, Color color) {
        queueEffectPlate(cardBatch, center, axisU, axisV, width, height, color);
    }

    /** Queues one fluttering Ofuda paper talisman strip. */
    public void drawOfuda(Vector3 center, Vector3 axisU, Vector3 axisV,
                          float width, float height, Color color) {
        queueEffectPlate(ofudaBatch, center, axisU, axisV, width, height, color);
    }

    /**
     * Queues a tapered sumi-ink brush ribbon between two world-space points.
     * The strip faces the camera and is drawn by {@code ink_ribbon.frag}.
     */
    public void drawInkRibbon(float x0, float y0, float z0,
                              float x1, float y1, float z1,
                              float width, Color color) {
        if (color == null || !(width > 0f) || !ribbonBatch.hasRoom()) {
            return;
        }
        float dx = x1 - x0;
        float dy = y1 - y0;
        float dz = z1 - z0;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 0.0001f) {
            return;
        }
        dx /= length;
        dy /= length;
        dz /= length;
        float midX = (x0 + x1) * 0.5f;
        float midY = (y0 + y1) * 0.5f;
        float midZ = (z0 + z1) * 0.5f;

        // cross(direction, toCamera) gives a smooth camera-facing ribbon.
        float viewX = cameraPosition.x - midX;
        float viewY = cameraPosition.y - midY;
        float viewZ = cameraPosition.z - midZ;
        float sideX = dy * viewZ - dz * viewY;
        float sideY = dz * viewX - dx * viewZ;
        float sideZ = dx * viewY - dy * viewX;
        float sideLength = (float) Math.sqrt(sideX * sideX + sideY * sideY + sideZ * sideZ);
        if (sideLength < 0.0001f) {
            sideX = rightX;
            sideY = 0f;
            sideZ = rightZ;
        } else {
            sideX /= sideLength;
            sideY /= sideLength;
            sideZ /= sideLength;
        }

        float halfWidth = width * 0.5f;
        float halfLength = length * 0.5f;
        float tailX = midX - dx * halfLength;
        float tailY = midY - dy * halfLength;
        float tailZ = midZ - dz * halfLength;
        float headX = midX + dx * halfLength;
        float headY = midY + dy * halfLength;
        float headZ = midZ + dz * halfLength;

        ribbonBatch.addQuad(
            tailX - sideX * halfWidth, tailY - sideY * halfWidth, tailZ - sideZ * halfWidth,
            headX - sideX * halfWidth, headY - sideY * halfWidth, headZ - sideZ * halfWidth,
            headX + sideX * halfWidth, headY + sideY * halfWidth, headZ + sideZ * halfWidth,
            tailX + sideX * halfWidth, tailY + sideY * halfWidth, tailZ + sideZ * halfWidth,
            0f, 1f, 0f, 1f, color);
    }

    /** Queues an oriented procedural plate (card / Ofuda) with its own batch. */
    private void queueEffectPlate(EffectBatch batch,
                                  Vector3 center, Vector3 axisU, Vector3 axisV,
                                  float width, float height, Color color) {
        if (batch == null || center == null || axisU == null || axisV == null
            || color == null || !batch.hasRoom()) {
            return;
        }
        float halfU = Math.max(0f, width) * 0.5f;
        float halfV = Math.max(0f, height) * 0.5f;
        batch.addQuad(
            center.x - axisU.x * halfU - axisV.x * halfV,
            center.y - axisU.y * halfU - axisV.y * halfV,
            center.z - axisU.z * halfU - axisV.z * halfV,
            center.x + axisU.x * halfU - axisV.x * halfV,
            center.y + axisU.y * halfU - axisV.y * halfV,
            center.z + axisU.z * halfU - axisV.z * halfV,
            center.x + axisU.x * halfU + axisV.x * halfV,
            center.y + axisU.y * halfU + axisV.y * halfV,
            center.z + axisU.z * halfU + axisV.z * halfV,
            center.x - axisU.x * halfU + axisV.x * halfV,
            center.y - axisU.y * halfU + axisV.y * halfV,
            center.z - axisU.z * halfU + axisV.z * halfV,
            0f, 1f, 0f, 1f, color);
    }

    /** Crisp ground decal for real shadows (never ASCII-quantized). */
    public void drawCrispGroundQuad(float worldX, float worldZ, float halfSize, Color color) {
        if (color == null || !(halfSize > 0f) || quadCount >= MAX_DYNAMIC_QUADS) {
            return;
        }
        float minX = worldX - halfSize;
        float maxX = worldX + halfSize;
        float minZ = worldZ - halfSize;
        float maxZ = worldZ + halfSize;
        float lift = 0.035f;
        vertexColor.set(color.r, color.g, color.b, 1f);

        int base = quadCount * VERTICES_PER_QUAD * FLOATS_PER_VERTEX;
        writeFlatVertex(base, minX, field.getHeight(minX, minZ) + lift, minZ, vertexColor);
        writeFlatVertex(base + FLOATS_PER_VERTEX,
            maxX, field.getHeight(maxX, minZ) + lift, minZ, vertexColor);
        writeFlatVertex(base + FLOATS_PER_VERTEX * 2,
            maxX, field.getHeight(maxX, maxZ) + lift, maxZ, vertexColor);
        writeFlatVertex(base + FLOATS_PER_VERTEX * 3,
            minX, field.getHeight(minX, maxZ) + lift, maxZ, vertexColor);
        quadCount++;
    }

    /** Queues one flat glyph quad hugging the heightfield. */
    private void queueMagicGroundGlyph(float worldX, float worldZ, float size,
                                       int glyph, Color color) {
        if (magicQuadCount >= MAX_MAGIC_QUADS) {
            return;
        }
        float half = Math.max(0.05f, size * 0.5f);
        float minX = worldX - half;
        float maxX = worldX + half;
        float minZ = worldZ - half;
        float maxZ = worldZ + half;
        float lift = 0.035f;
        queueMagicQuad(
            minX, field.getHeight(minX, minZ) + lift, minZ,
            maxX, field.getHeight(maxX, minZ) + lift, minZ,
            maxX, field.getHeight(maxX, maxZ) + lift, maxZ,
            minX, field.getHeight(minX, maxZ) + lift, maxZ,
            color, glyph);
    }

    /**
     * Queues one solid 3D box with per-face normals. The basis vectors must be
     * orthonormal; they define the box axes in world space (right/up/forward).
     *
     * <p>Used by the procedural player-avatar rig and its dash ghosts. Boxes go
     * through the same cel world shader and the normal/depth edge prepass as the
     * terrain and scenery, so every silhouette gets a crisp outline.</p>
     *
     * @return true when the box was queued, false when the dynamic budget is full
     */
    public boolean drawBox(Vector3 center, Vector3 right, Vector3 up, Vector3 forward,
                           float sizeX, float sizeY, float sizeZ, Color color) {
        return queueBox(center, right, up, forward, sizeX, sizeY, sizeZ, color);
    }

    /**
     * Queues one magic box as a large ASCII glyph plate on its widest face.
     * Used for the Q-dash ghost trail and remaining glyph-based effects.
     */
    public boolean drawMagicBox(Vector3 center, Vector3 right, Vector3 up, Vector3 forward,
                                float sizeX, float sizeY, float sizeZ, Color color) {
        if (center == null || right == null || up == null || forward == null
            || color == null || magicQuadCount >= MAX_MAGIC_QUADS) {
            return false;
        }
        float ax = Math.abs(sizeX);
        float ay = Math.abs(sizeY);
        float az = Math.abs(sizeZ);
        int glyph = glyphForColor(color);
        if (ax <= ay && ax <= az) {
            // Thin along right: glyph plate spans up/forward.
            return queueMagicGlyphPlate(center, up, forward, ay, az, glyph, color);
        }
        if (ay <= ax && ay <= az) {
            // Thin along up: glyph plate spans right/forward (Hanafuda cards).
            return queueMagicGlyphPlate(center, right, forward, ax, az, glyph, color);
        }
        // Thin along forward: glyph plate spans right/up.
        return queueMagicGlyphPlate(center, right, up, ax, ay, glyph, color);
    }

    private boolean queueBox(Vector3 center, Vector3 right, Vector3 up, Vector3 forward,
                             float sizeX, float sizeY, float sizeZ, Color color) {
        if (center == null || right == null || up == null || forward == null
            || color == null || quadCount + 6 > MAX_DYNAMIC_QUADS) {
            return false;
        }
        float hx = Math.max(0.0001f, sizeX * 0.5f);
        float hy = Math.max(0.0001f, sizeY * 0.5f);
        float hz = Math.max(0.0001f, sizeZ * 0.5f);

        // +X / -X faces: normal ±right, plane spanned by up/forward.
        writeBoxFace(center.x + right.x * hx, center.y + right.y * hx,
            center.z + right.z * hx, up, forward, right, hy, hz, color);
        boxNormal.set(-right.x, -right.y, -right.z);
        writeBoxFace(center.x - right.x * hx, center.y - right.y * hx,
            center.z - right.z * hx, up, forward, boxNormal, hy, hz, color);

        // +Y / -Y faces: normal ±up, plane spanned by right/forward.
        writeBoxFace(center.x + up.x * hy, center.y + up.y * hy,
            center.z + up.z * hy, right, forward, up, hx, hz, color);
        boxNormal.set(-up.x, -up.y, -up.z);
        writeBoxFace(center.x - up.x * hy, center.y - up.y * hy,
            center.z - up.z * hy, right, forward, boxNormal, hx, hz, color);

        // +Z / -Z faces: normal ±forward, plane spanned by right/up.
        writeBoxFace(center.x + forward.x * hz, center.y + forward.y * hz,
            center.z + forward.z * hz, right, up, forward, hx, hy, color);
        boxNormal.set(-forward.x, -forward.y, -forward.z);
        writeBoxFace(center.x - forward.x * hz, center.y - forward.y * hz,
            center.z - forward.z * hz, right, up, boxNormal, hx, hy, color);
        return true;
    }

    /** Writes one quad of a box (four vertices with a shared face normal). */
    private void writeBoxFace(float cx, float cy, float cz,
                              Vector3 axisU, Vector3 axisV, Vector3 normal,
                              float halfU, float halfV, Color color) {
        int base = quadCount * VERTICES_PER_QUAD * FLOATS_PER_VERTEX;
        int stride = FLOATS_PER_VERTEX;
        writeNormalVertex(base,
            cx - axisU.x * halfU - axisV.x * halfV,
            cy - axisU.y * halfU - axisV.y * halfV,
            cz - axisU.z * halfU - axisV.z * halfV, normal, color);
        writeNormalVertex(base + stride,
            cx + axisU.x * halfU - axisV.x * halfV,
            cy + axisU.y * halfU - axisV.y * halfV,
            cz + axisU.z * halfU - axisV.z * halfV, normal, color);
        writeNormalVertex(base + stride * 2,
            cx + axisU.x * halfU + axisV.x * halfV,
            cy + axisU.y * halfU + axisV.y * halfV,
            cz + axisU.z * halfU + axisV.z * halfV, normal, color);
        writeNormalVertex(base + stride * 3,
            cx - axisU.x * halfU + axisV.x * halfV,
            cy - axisU.y * halfU + axisV.y * halfV,
            cz - axisU.z * halfU + axisV.z * halfV, normal, color);
        quadCount++;
    }

    /** Flushes the solid geometry and the direct 3D ASCII magic glyph layer. */
    public void end() {
        if (quadCount > 0) {
            int floats = quadCount * VERTICES_PER_QUAD * FLOATS_PER_VERTEX;
            dynamicMesh.setVertices(dynamicVertices, 0, floats);
            dynamicMesh.setIndices(dynamicIndices, 0, quadCount * INDICES_PER_QUAD);
            dynamicMesh.render(shader, GL20.GL_TRIANGLES);
        }
        if (magicQuadCount > 0) {
            int floats = magicQuadCount * VERTICES_PER_QUAD * MAGIC_FLOATS_PER_VERTEX;
            magicMesh.setVertices(magicVertices, 0, floats);
            magicMesh.setIndices(magicIndices, 0, magicQuadCount * INDICES_PER_QUAD);

            Gdx.gl.glDepthMask(false);
            renderProceduralEffects();
            magicShader.bind();
            magicShader.setUniformMatrix("u_projView", projView);
            magicShader.setUniformf("u_cameraPos",
                cameraPosition.x, cameraPosition.y, cameraPosition.z);
            magicShader.setUniformf("u_fogColor", FOG_COLOR.r, FOG_COLOR.g, FOG_COLOR.b);
            magicShader.setUniformf("u_fogStart", FOG_START);
            magicShader.setUniformf("u_fogEnd", FOG_END);
            magicGlyphTexture.bind(0);
            magicShader.setUniformi("u_glyphAtlas", 0);
            magicMesh.render(magicShader, GL20.GL_TRIANGLES);
            magicShader.end();
            Gdx.gl.glDepthMask(true);
        } else {
            Gdx.gl.glDepthMask(false);
            renderProceduralEffects();
            Gdx.gl.glDepthMask(true);
        }
    }

    /**
     * Flushes the procedural effect batches (Kamon seals, Hanafuda cards, Ofuda
     * paper talismans and sumi brush trails). They share the magic vertex layout
     * but use dedicated fragment shaders, so no effect relies on post-processed
     * glyph tags.
     */
    private void renderProceduralEffects() {
        if (sealBatch.count > 0) {
            kamonShader.bind();
            kamonShader.setUniformMatrix("u_projView", projView);
            kamonShader.setUniformf("u_time", effectTime);
            sealBatch.render(kamonShader);
            kamonShader.end();
        }
        if (ribbonBatch.count > 0) {
            ribbonShader.bind();
            ribbonShader.setUniformMatrix("u_projView", projView);
            ribbonBatch.render(ribbonShader);
            ribbonShader.end();
        }
        if (cardBatch.count > 0) {
            cardShader.bind();
            cardShader.setUniformMatrix("u_projView", projView);
            cardBatch.render(cardShader);
            cardShader.end();
        }
        if (ofudaBatch.count > 0) {
            ofudaShader.bind();
            ofudaShader.setUniformMatrix("u_projView", projView);
            ofudaBatch.render(ofudaShader);
            ofudaShader.end();
        }
    }

    /**
     * Draws only the terrain chunks whose 32-tile boxes are within the cull
     * radius and inside the camera frustum.
     */
    private void renderTerrain(ShaderProgram program, Camera camera) {
        if (program == null || camera == null) {
            return;
        }
        float cameraX = camera.position.x;
        float cameraZ = camera.position.z;
        float half = terrainChunkGrid.getChunkSize() * 0.5f;
        float centerY = (field.getMinHeight() + field.getMaxHeight()) * 0.5f;
        float halfHeight = (field.getMaxHeight() - field.getMinHeight()) * 0.5f + 2f;
        for (int index = 0; index < terrainMeshes.length; index++) {
            Mesh mesh = terrainMeshes[index];
            if (mesh == null) {
                continue;
            }
            if (!terrainChunkGrid.isVisible(index, cameraX, cameraZ,
                TERRAIN_CHUNK_CULL_RADIUS)) {
                continue;
            }
            if (!camera.frustum.boundsInFrustum(terrainChunkGrid.chunkCenterX(index),
                centerY, terrainChunkGrid.chunkCenterZ(index), half, halfHeight, half)) {
                continue;
            }
            mesh.render(program, GL20.GL_TRIANGLES);
        }
    }

    /**
     * Renders the normal/depth prepass used by the ASCII cel outline.
     *
     * <p>The scene (terrain, scenery and the solid dynamic boxes) is drawn into
     * a plain RGBA target with {@link #EDGE_FRAGMENT_SHADER}. The shader packs
     * the view-space normal and a 16-bit linear depth, so the post pass can
     * compare neighbouring texels without depth textures, MRT or any GL3 call.
     * Magic glyphs use a separate mesh and are never part of this outline
     * prepass.</p>
     */
    public void renderEdge(FrameBuffer target, PerspectiveCamera camera) {
        if (target == null || camera == null) {
            return;
        }
        target.begin();
        Gdx.gl.glViewport(0, 0, target.getWidth(), target.getHeight());
        Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
        Gdx.gl.glClearDepthf(1f);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
        Gdx.gl.glDepthMask(true);
        Gdx.gl.glDisable(GL20.GL_BLEND);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        edgeShader.bind();
        edgeShader.setUniformMatrix("u_projView", camera.combined);
        edgeShader.setUniformMatrix("u_view", camera.view);
        edgeShader.setUniformf("u_far", camera.far);

        renderTerrain(edgeShader, camera);
        if (scenery != null) {
            scenery.render(edgeShader, camera);
        }
        if (natureRenderer != null) {
            natureRenderer.renderEdge(camera);
            // ModelBatch's RenderContext resets depth state on end. Restore the
            // edge-pass depth state before the queued dynamic boxes.
            useOpaqueDepthState();
            edgeShader.bind();
        }
        if (quadCount > 0) {
            int floats = quadCount * VERTICES_PER_QUAD * FLOATS_PER_VERTEX;
            dynamicMesh.setVertices(dynamicVertices, 0, floats);
            dynamicMesh.setIndices(dynamicIndices, 0, quadCount * INDICES_PER_QUAD);
            dynamicMesh.render(edgeShader, GL20.GL_TRIANGLES);
        }

        edgeShader.end();
        target.end();
    }

    /** Heightfield used by the renderer. */
    public Heightfield getField() {
        return field;
    }

    public ShaderProgram getShader() {
        return shader;
    }

    @Override
    public void dispose() {
        if (scenery != null) {
            scenery.dispose();
            scenery = null;
        }
        if (natureRenderer != null) {
            natureRenderer.dispose();
            natureRenderer = null;
        }
        for (Mesh mesh : terrainMeshes) {
            if (mesh != null) {
                mesh.dispose();
            }
        }
        dynamicMesh.dispose();
        magicMesh.dispose();
        sealBatch.dispose();
        ofudaBatch.dispose();
        cardBatch.dispose();
        ribbonBatch.dispose();
        magicGlyphTexture.dispose();
        shader.dispose();
        edgeShader.dispose();
        magicShader.dispose();
        kamonShader.dispose();
        ofudaShader.dispose();
        cardShader.dispose();
        ribbonShader.dispose();
    }

    // ------------------------------------------------------------------
    // Mesh construction
    // ------------------------------------------------------------------

    private Mesh[] buildTerrainMeshes(Heightfield heightfield,
                                      WorldSeasonMap.ChunkGrid grid) {
        Mesh[] meshes = new Mesh[grid.count()];
        float step = heightfield.getStep();
        float lowest = heightfield.getMinHeight();
        // Clamp the range so the raised mountain rim does not crush the rolling
        // meadow into a single dark shade.
        float span = Math.max(0.0001f,
            Math.min(heightfield.getMaxHeight() - lowest, 12f));
        int resolution = heightfield.getResolution();
        for (int chunk = 0; chunk < grid.count(); chunk++) {
            float chunkMinX = grid.chunkMinX(chunk);
            float chunkMinZ = grid.chunkMinZ(chunk);
            int colStart = Math.max(0, (int) Math.floor(
                (chunkMinX - heightfield.getOriginX()) / step) - 1);
            int colEnd = Math.min(resolution, (int) Math.ceil(
                (chunkMinX + grid.getChunkSize() - heightfield.getOriginX()) / step) + 1);
            int rowStart = Math.max(0, (int) Math.floor(
                (chunkMinZ - heightfield.getOriginZ()) / step) - 1);
            int rowEnd = Math.min(resolution, (int) Math.ceil(
                (chunkMinZ + grid.getChunkSize() - heightfield.getOriginZ()) / step) + 1);
            if (colEnd <= colStart || rowEnd <= rowStart) {
                continue;
            }
            meshes[chunk] = buildTerrainChunk(heightfield, colStart, colEnd,
                rowStart, rowEnd, lowest, span);
        }
        return meshes;
    }

    private Mesh buildTerrainChunk(Heightfield heightfield, int colStart, int colEnd,
                                   int rowStart, int rowEnd, float lowest, float span) {
        int cols = colEnd - colStart + 1;
        int rows = rowEnd - rowStart + 1;
        int vertexCount = cols * rows;
        float[] vertices = new float[vertexCount * FLOATS_PER_VERTEX];
        float step = heightfield.getStep();
        int index = 0;

        for (int row = rowStart; row <= rowEnd; row++) {
            float z = heightfield.getZ(row);
            for (int col = colStart; col <= colEnd; col++) {
                float x = heightfield.getX(col);
                float height = heightfield.getHeightAt(col, row);

                // Central-difference normal of the heightfield surface.
                float left = heightfield.getHeightAt(col - 1, row);
                float right = heightfield.getHeightAt(col + 1, row);
                float back = heightfield.getHeightAt(col, row - 1);
                float front = heightfield.getHeightAt(col, row + 1);
                vertexNormal.set(left - right, 2f * step, back - front).nor();

                // Moss palette: valleys read dark, crests catch the moonlight.
                float elevation = MathUtils.clamp((height - lowest) / span, 0f, 1f);
                float jitter = 0.92f + 0.16f * hash01(col, row);
                float light = (0.52f + 0.72f * elevation) * jitter;

                // Steep cliff faces turn from moss into slate-grey rock.
                float slope = 1f - vertexNormal.y;
                float rock = MathUtils.clamp(slope * 3.2f, 0f, 1f);
                float mossRed = 0.145f * light;
                float mossGreen = 0.275f * light;
                float mossBlue = 0.185f * light;
                float rockRed = 0.300f * light;
                float rockGreen = 0.335f * light;
                float rockBlue = 0.360f * light;
                vertexColor.set(
                    mossRed + (rockRed - mossRed) * rock,
                    mossGreen + (rockGreen - mossGreen) * rock,
                    mossBlue + (rockBlue - mossBlue) * rock, 1f);

                vertices[index++] = x;
                vertices[index++] = height;
                vertices[index++] = z;
                vertices[index++] = vertexNormal.x;
                vertices[index++] = vertexNormal.y;
                vertices[index++] = vertexNormal.z;
                vertices[index++] = vertexColor.toFloatBits();
                // Terrain selects grass vs. cliff per fragment from the normal.
                vertices[index++] = SceneryPlan.MATERIAL_GRASS;
            }
        }

        short[] indices = new short[(cols - 1) * (rows - 1) * INDICES_PER_QUAD];
        int cursor = 0;
        for (int row = 0; row < rows - 1; row++) {
            for (int col = 0; col < cols - 1; col++) {
                short topLeft = (short) (row * cols + col);
                short topRight = (short) (topLeft + 1);
                short bottomLeft = (short) (topLeft + cols);
                short bottomRight = (short) (bottomLeft + 1);

                indices[cursor++] = topLeft;
                indices[cursor++] = bottomLeft;
                indices[cursor++] = bottomRight;
                indices[cursor++] = topLeft;
                indices[cursor++] = bottomRight;
                indices[cursor++] = topRight;
            }
        }

        Mesh mesh = new Mesh(true, vertexCount, indices.length, VERTEX_ATTRIBUTES);
        mesh.setVertices(vertices);
        mesh.setIndices(indices);
        return mesh;
    }

    private Mesh buildDynamicMesh() {
        int vertexCount = MAX_DYNAMIC_QUADS * VERTICES_PER_QUAD;
        int cursor = 0;
        for (int quad = 0; quad < MAX_DYNAMIC_QUADS; quad++) {
            short base = (short) (quad * VERTICES_PER_QUAD);
            dynamicIndices[cursor++] = base;
            dynamicIndices[cursor++] = (short) (base + 1);
            dynamicIndices[cursor++] = (short) (base + 2);
            dynamicIndices[cursor++] = base;
            dynamicIndices[cursor++] = (short) (base + 2);
            dynamicIndices[cursor++] = (short) (base + 3);
        }
        Mesh mesh = new Mesh(false, vertexCount, MAX_DYNAMIC_QUADS * INDICES_PER_QUAD,
            VERTEX_ATTRIBUTES);
        mesh.setIndices(dynamicIndices);
        return mesh;
    }

    private Mesh buildMagicMesh() {
        int vertexCount = MAX_MAGIC_QUADS * VERTICES_PER_QUAD;
        int cursor = 0;
        for (int quad = 0; quad < MAX_MAGIC_QUADS; quad++) {
            short base = (short) (quad * VERTICES_PER_QUAD);
            magicIndices[cursor++] = base;
            magicIndices[cursor++] = (short) (base + 1);
            magicIndices[cursor++] = (short) (base + 2);
            magicIndices[cursor++] = base;
            magicIndices[cursor++] = (short) (base + 2);
            magicIndices[cursor++] = (short) (base + 3);
        }
        Mesh mesh = new Mesh(false, vertexCount, MAX_MAGIC_QUADS * INDICES_PER_QUAD,
            MAGIC_VERTEX_ATTRIBUTES);
        mesh.setIndices(magicIndices);
        return mesh;
    }

    private void writeVertex(int offset, float x, float y, float z, Color color) {
        dynamicVertices[offset] = x;
        dynamicVertices[offset + 1] = y;
        dynamicVertices[offset + 2] = z;
        dynamicVertices[offset + 3] = vertexNormal.x;
        dynamicVertices[offset + 4] = vertexNormal.y;
        dynamicVertices[offset + 5] = vertexNormal.z;
        dynamicVertices[offset + 6] = color.toFloatBits();
    }

    /** Writes a vertex with an explicit normal (3D box faces). */
    private void writeNormalVertex(int offset, float x, float y, float z,
                                   Vector3 normal, Color color) {
        dynamicVertices[offset] = x;
        dynamicVertices[offset + 1] = y;
        dynamicVertices[offset + 2] = z;
        dynamicVertices[offset + 3] = normal.x;
        dynamicVertices[offset + 4] = normal.y;
        dynamicVertices[offset + 5] = normal.z;
        vertexColor.set(color.r, color.g, color.b, 1f);
        dynamicVertices[offset + 6] = vertexColor.toFloatBits();
        dynamicVertices[offset + 7] = SceneryPlan.MATERIAL_NONE;
    }

    private void writeFlatVertex(int offset, float x, float y, float z, Color color) {
        dynamicVertices[offset] = x;
        dynamicVertices[offset + 1] = y;
        dynamicVertices[offset + 2] = z;
        dynamicVertices[offset + 3] = 0f;
        dynamicVertices[offset + 4] = 1f;
        dynamicVertices[offset + 5] = 0f;
        dynamicVertices[offset + 6] = color.toFloatBits();
        dynamicVertices[offset + 7] = SceneryPlan.MATERIAL_NONE;
    }

    /** Stable glyph choice from the effect colour: bright magic gets dense ink. */
    private static int glyphForColor(Color color) {
        float luma = 0.2126f * color.r + 0.7152f * color.g + 0.0722f * color.b;
        float tone = Math.max(0.22f, Math.min(1f, luma));
        return AsciiGlyphAtlas.indexForTone(tone);
    }

    /** Fits the 7:11 glyph inside the requested plate height. */
    private static float fitGlyphHeight(float width, float height) {
        float maxHeight = Math.max(0f, width)
            * AsciiGlyphAtlas.CELL_HEIGHT / (float) AsciiGlyphAtlas.CELL_WIDTH;
        return Math.max(0.02f, Math.min(Math.max(0f, height), maxHeight));
    }

    /** Width of the aspect-correct glyph inside the requested plate width. */
    private static float fitGlyphWidth(float width, float height) {
        return fitGlyphHeight(width, height)
            * AsciiGlyphAtlas.CELL_WIDTH / (float) AsciiGlyphAtlas.CELL_HEIGHT;
    }

    /** Queues one aspect-correct glyph plate on an arbitrary 3D face. */
    private boolean queueMagicGlyphPlate(Vector3 center, Vector3 axisU, Vector3 axisV,
                                         float width, float height,
                                         int glyph, Color color) {
        if (center == null || axisU == null || axisV == null || color == null
            || magicQuadCount >= MAX_MAGIC_QUADS) {
            return false;
        }
        float halfU = fitGlyphWidth(width, height) * 0.5f;
        float halfV = fitGlyphHeight(width, height) * 0.5f;
        queueMagicQuad(
            center.x - axisU.x * halfU - axisV.x * halfV,
            center.y - axisU.y * halfU - axisV.y * halfV,
            center.z - axisU.z * halfU - axisV.z * halfV,
            center.x + axisU.x * halfU - axisV.x * halfV,
            center.y + axisU.y * halfU - axisV.y * halfV,
            center.z + axisU.z * halfU - axisV.z * halfV,
            center.x + axisU.x * halfU + axisV.x * halfV,
            center.y + axisU.y * halfU + axisV.y * halfV,
            center.z + axisU.z * halfU + axisV.z * halfV,
            center.x - axisU.x * halfU + axisV.x * halfV,
            center.y - axisU.y * halfU + axisV.y * halfV,
            center.z - axisU.z * halfU + axisV.z * halfV,
            color, glyph);
        return true;
    }

    /** Queues one glyph atlas quad with explicit four corners. */
    private void queueMagicQuad(float x0, float y0, float z0,
                                float x1, float y1, float z1,
                                float x2, float y2, float z2,
                                float x3, float y3, float z3,
                                Color color, int glyph) {
        if (color == null || magicQuadCount >= MAX_MAGIC_QUADS) {
            return;
        }
        float u0 = glyph / (float) AsciiGlyphAtlas.glyphCount();
        float u1 = (glyph + 1) / (float) AsciiGlyphAtlas.glyphCount();
        vertexColor.set(color.r, color.g, color.b, 1f);
        float colorBits = vertexColor.toFloatBits();
        int base = magicQuadCount * VERTICES_PER_QUAD * MAGIC_FLOATS_PER_VERTEX;
        // Texture row 0 is the glyph top, so top vertices sample v=0.
        writeMagicVertex(base, x0, y0, z0, u0, 1f, colorBits);
        writeMagicVertex(base + MAGIC_FLOATS_PER_VERTEX,
            x1, y1, z1, u1, 1f, colorBits);
        writeMagicVertex(base + MAGIC_FLOATS_PER_VERTEX * 2,
            x2, y2, z2, u1, 0f, colorBits);
        writeMagicVertex(base + MAGIC_FLOATS_PER_VERTEX * 3,
            x3, y3, z3, u0, 0f, colorBits);
        magicQuadCount++;
    }

    private void writeMagicVertex(int offset, float x, float y, float z,
                                  float u, float v, float colorBits) {
        magicVertices[offset] = x;
        magicVertices[offset + 1] = y;
        magicVertices[offset + 2] = z;
        magicVertices[offset + 3] = u;
        magicVertices[offset + 4] = v;
        magicVertices[offset + 5] = colorBits;
    }

    /**
     * Small indexed quad batch for procedural effects. All effect shaders use
     * the same position/UV/colour vertex layout as the magic glyph layer.
     */
    private static final class EffectBatch implements Disposable {
        private final float[] vertices;
        private final short[] indices;
        private final Mesh mesh;
        private final int maxQuads;
        private int count;

        EffectBatch(int maxQuads) {
            this.maxQuads = Math.max(1, maxQuads);
            this.vertices = new float[this.maxQuads * VERTICES_PER_QUAD * MAGIC_FLOATS_PER_VERTEX];
            this.indices = new short[this.maxQuads * INDICES_PER_QUAD];
            int cursor = 0;
            for (int quad = 0; quad < this.maxQuads; quad++) {
                short base = (short) (quad * VERTICES_PER_QUAD);
                indices[cursor++] = base;
                indices[cursor++] = (short) (base + 1);
                indices[cursor++] = (short) (base + 2);
                indices[cursor++] = base;
                indices[cursor++] = (short) (base + 2);
                indices[cursor++] = (short) (base + 3);
            }
            this.mesh = new Mesh(false, this.maxQuads * VERTICES_PER_QUAD,
                this.maxQuads * INDICES_PER_QUAD, MAGIC_VERTEX_ATTRIBUTES);
            this.mesh.setIndices(indices);
        }

        boolean hasRoom() {
            return count < maxQuads;
        }

        void reset() {
            count = 0;
        }

        void addQuad(float x0, float y0, float z0,
                     float x1, float y1, float z1,
                     float x2, float y2, float z2,
                     float x3, float y3, float z3,
                     float u0, float u1, float v0, float v1,
                     Color color) {
            if (color == null || count >= maxQuads) {
                return;
            }
            float colorBits = color.toFloatBits();
            int base = count * VERTICES_PER_QUAD * MAGIC_FLOATS_PER_VERTEX;
            write(base, x0, y0, z0, u0, v1, colorBits);
            write(base + MAGIC_FLOATS_PER_VERTEX,
                x1, y1, z1, u1, v1, colorBits);
            write(base + MAGIC_FLOATS_PER_VERTEX * 2,
                x2, y2, z2, u1, v0, colorBits);
            write(base + MAGIC_FLOATS_PER_VERTEX * 3,
                x3, y3, z3, u0, v0, colorBits);
            count++;
        }

        void render(ShaderProgram shader) {
            if (count <= 0) {
                return;
            }
            int floatCount = count * VERTICES_PER_QUAD * MAGIC_FLOATS_PER_VERTEX;
            mesh.setVertices(vertices, 0, floatCount);
            mesh.setIndices(indices, 0, count * INDICES_PER_QUAD);
            mesh.render(shader, GL20.GL_TRIANGLES);
        }

        private void write(int offset, float x, float y, float z,
                           float u, float v, float colorBits) {
            vertices[offset] = x;
            vertices[offset + 1] = y;
            vertices[offset + 2] = z;
            vertices[offset + 3] = u;
            vertices[offset + 4] = v;
            vertices[offset + 5] = colorBits;
        }

        @Override
        public void dispose() {
            mesh.dispose();
        }
    }

    /** Stable 0..1 hash for per-vertex moss tint variation. */
    private static float hash01(int col, int row) {
        int hash = col * 0x27d4eb2d ^ row * 0x9e3779b9;
        hash ^= hash >>> 15;
        hash *= 0x85ebca6b;
        hash ^= hash >>> 13;
        return (hash & 0xFFFF) / 65535f;
    }
}
