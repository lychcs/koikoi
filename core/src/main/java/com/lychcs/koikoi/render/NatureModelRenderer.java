package com.lychcs.koikoi.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g3d.Attribute;
import com.badlogic.gdx.graphics.g3d.Attributes;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.Shader;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.DepthTestAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.shaders.BaseShader;
import com.badlogic.gdx.graphics.g3d.utils.RenderContext;
import com.badlogic.gdx.graphics.g3d.utils.ShaderProvider;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.lychcs.koikoi.graphics.GameAssets;

import java.util.List;

/**
 * Draws the imported organic nature meshes through the Mokuhanga world look.
 *
 * <p>The {@link SceneryPlan} remains the single source of truth for placement:
 * every {@link SceneryPlan.NatureInstance} becomes one {@link ModelInstance}
 * with a deterministic yaw/scale and a ground-aligned pivot. Models are rendered
 * after the static scenery with a dedicated cel shader and again into the
 * normal/depth prepass with an alpha-cutout edge shader, so leaves receive ink
 * silhouettes without their rectangular cards turning into solid blocks.</p>
 *
 * <p>Owns two {@link ModelBatch} instances and their shaders. The model data
 * itself is borrowed from {@link GameAssets}; this class must never dispose it.</p>
 */
public final class NatureModelRenderer implements Disposable {

    public static final String VERTEX_SHADER = "shaders/nature_model.vert";
    public static final String FRAGMENT_SHADER = "shaders/nature_model.frag";
    public static final String EDGE_FRAGMENT_SHADER = "shaders/nature_edge.frag";

    /** Alpha cutoff for the painted leaf cards. */
    private static final float ALPHA_TEST = 0.35f;
    /** Distance cull radius of one nature chunk in world units. */
    private static final float CHUNK_CULL_RADIUS = 150f;

    private final Array<ModelInstance> instances = new Array<>();
    /** Spatial bins of {@link #instances}, indexed by the 32-tile chunk grid. */
    private final Array<Array<ModelInstance>> chunkInstances = new Array<>();
    /** Reused per-frame list of instances inside the visible chunks. */
    private final Array<ModelInstance> visibleInstances = new Array<>();
    private final WorldSeasonMap.ChunkGrid chunkGrid;
    private final float chunkCenterY;
    private final float chunkHalfHeight;
    /** Four-season hub model for the foliage tint uniforms. */
    private WorldSeasonMap seasonMap;
    private final Lighting lighting = new Lighting();
    private final ModelShaderProvider worldProvider =
        new ModelShaderProvider(false, lighting);
    private final ModelShaderProvider edgeProvider =
        new ModelShaderProvider(true, lighting);
    private final ModelBatch worldBatch = new ModelBatch(worldProvider);
    private final ModelBatch edgeBatch = new ModelBatch(edgeProvider);

    public NatureModelRenderer(SceneryPlan plan, GameAssets assets) {
        this(plan, assets, null);
    }

    /**
     * @param seasonMap optional four-season model; drives the foliage tint of
     *                  {@code nature_model.frag}
     */
    public NatureModelRenderer(SceneryPlan plan, GameAssets assets,
                               WorldSeasonMap seasonMap) {
        if (plan == null) {
            throw new IllegalArgumentException("plan must not be null");
        }
        if (assets == null) {
            throw new IllegalArgumentException("assets must not be null");
        }
        this.seasonMap = seasonMap;
        lighting.seasonMap = seasonMap;

        Heightfield field = plan.getField();
        this.chunkGrid = new WorldSeasonMap.ChunkGrid(field.getOriginX(),
            field.getOriginZ(), field.getSize(), WorldSeasonMap.CHUNK_SIZE_TILES);
        this.chunkCenterY = (field.getMinHeight() + field.getMaxHeight()) * 0.5f;
        this.chunkHalfHeight =
            (field.getMaxHeight() - field.getMinHeight()) * 0.5f + 3f;
        for (int i = 0; i < chunkGrid.count(); i++) {
            chunkInstances.add(new Array<>());
        }

        List<SceneryPlan.NatureInstance> placements = plan.getNatureInstances();
        for (int i = 0; i < placements.size(); i++) {
            SceneryPlan.NatureInstance placement = placements.get(i);
            Model model = assets.getModel(modelPath(placement.kind()));
            ModelInstance instance = new ModelInstance(model);
            prepareMaterials(instance);
            instance.transform.setToTranslation(placement.x(), placement.y(), placement.z())
                .rotateRad(Vector3.Y, placement.yaw())
                .scale(placement.scale(), placement.scale(), placement.scale());
            instances.add(instance);
            chunkInstances.get(chunkGrid.chunkOf(placement.x(), placement.z()))
                .add(instance);
        }
    }

    /** Number of placed model instances; useful for tests and debug HUDs. */
    public int getInstanceCount() {
        return instances.size;
    }

    /** Number of instances that survived the last chunk cull. */
    public int getVisibleInstanceCount() {
        return visibleInstances.size;
    }

    /** Number of spatial chunks the instances are binned into. */
    public int getChunkCount() {
        return chunkGrid.count();
    }

    /** Installs the four-season model used by the foliage tint. */
    public void setWorldSeasonMap(WorldSeasonMap seasonMap) {
        this.seasonMap = seasonMap;
        lighting.seasonMap = seasonMap;
    }

    /**
     * Rebuilds {@link #visibleInstances} from the chunks whose boxes are inside
     * the camera frustum and within {@link #CHUNK_CULL_RADIUS}.
     */
    private void selectVisible(Camera camera) {
        visibleInstances.clear();
        if (instances.isEmpty()) {
            return;
        }
        float cameraX = camera.position.x;
        float cameraZ = camera.position.z;
        float half = chunkGrid.getChunkSize() * 0.5f;
        for (int index = 0; index < chunkInstances.size; index++) {
            Array<ModelInstance> bin = chunkInstances.get(index);
            if (bin.size == 0) {
                continue;
            }
            if (!chunkGrid.isVisible(index, cameraX, cameraZ, CHUNK_CULL_RADIUS)) {
                continue;
            }
            if (!camera.frustum.boundsInFrustum(chunkGrid.chunkCenterX(index),
                chunkCenterY, chunkGrid.chunkCenterZ(index),
                half, chunkHalfHeight, half)) {
                continue;
            }
            visibleInstances.addAll(bin);
        }
    }

    /**
     * Renders all visible organic nature models with the same sunlight,
     * lanterns, fog and season uniforms as the terrain shader.
     */
    void renderWorld(Camera camera, float time,
                     int selectedCount,
                     float[] lightPositions, float[] lightColors, float[] lightRanges) {
        if (camera == null || instances.isEmpty()) {
            return;
        }
        selectVisible(camera);
        if (visibleInstances.isEmpty()) {
            return;
        }
        lighting.time = time;
        lighting.lightCount = Math.min(selectedCount, HeightmapTerrainRenderer.MAX_POINT_LIGHTS);
        if (lighting.lightCount > 0) {
            System.arraycopy(lightPositions, 0, lighting.lightPositions, 0,
                lighting.lightCount * 3);
            System.arraycopy(lightColors, 0, lighting.lightColors, 0,
                lighting.lightCount * 3);
            System.arraycopy(lightRanges, 0, lighting.lightRanges, 0,
                lighting.lightCount);
        }
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        worldBatch.begin(camera);
        worldBatch.render(visibleInstances);
        worldBatch.end();
    }

    /** Adds the visible models to the alpha-aware normal/depth outline prepass. */
    void renderEdge(Camera camera) {
        if (camera == null || instances.isEmpty()) {
            return;
        }
        selectVisible(camera);
        if (visibleInstances.isEmpty()) {
            return;
        }
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        edgeBatch.begin(camera);
        edgeBatch.render(visibleInstances);
        edgeBatch.end();
    }

    @Override
    public void dispose() {
        instances.clear();
        visibleInstances.clear();
        for (int i = 0; i < chunkInstances.size; i++) {
            chunkInstances.get(i).clear();
        }
        chunkInstances.clear();
        worldBatch.dispose();
        edgeBatch.dispose();
    }

    // ------------------------------------------------------------------
    // Placement mapping and material preparation
    // ------------------------------------------------------------------

    private static String modelPath(SceneryPlan.NatureKind kind) {
        return switch (kind) {
            case MAPLE_TREE -> GameAssets.NATURE_MAPLE_TREE;
            case PINE_TREE_TALL -> GameAssets.NATURE_PINE_TREE_TALL;
            case PINE_TREE_SMALL -> GameAssets.NATURE_PINE_TREE_SMALL;
            case BOULDER_ROUND -> GameAssets.NATURE_ROCK_ROUND;
            case BOULDER_TALL -> GameAssets.NATURE_ROCK_TALL;
            case BUSH_SMALL -> GameAssets.NATURE_BUSH_SMALL;
            case BUSH_LARGE -> GameAssets.NATURE_BUSH_LARGE;
            case GRASS_SMALL -> GameAssets.NATURE_GRASS_SMALL;
            case GRASS_LARGE -> GameAssets.NATURE_GRASS_LARGE;
            case FLOWER_CLUMP -> GameAssets.NATURE_FLOWER_CLUMP;
        };
    }

    /**
     * {@link ModelInstance} already owns copies of the model materials; only
     * annotate the foliage cards as double-sided. Per-instance tinting can be
     * added here later without touching the shared model.
     */
    private static void prepareMaterials(ModelInstance instance) {
        for (int i = 0; i < instance.materials.size; i++) {
            configureMaterial(instance.materials.get(i));
        }
    }

    /**
     * Forces every imported material into the opaque depth-writing path.
     *
     * <p>LibGDX's ObjLoader/ModelInstance may carry a {@link BlendingAttribute}
     * even though the shader performs hard alpha-cutout discard. A blending
     * attribute would sort the tree after opaque geometry, while the shader's
     * depth test is the only thing that can keep leaves behind a temple wall.
     * Removing it and declaring {@link DepthTestAttribute} keeps the whole
     * nature pass opaque, depth-tested and depth-writing.</p>
     */
    static void configureMaterial(Material material) {
        if (material == null) {
            return;
        }
        material.remove(BlendingAttribute.Type);
        material.set(new DepthTestAttribute(GL20.GL_LEQUAL, true));
        // Imported foliage is modelled as open cards; disable culling on
        // the material as documentation of that intent. The GL cull state
        // is also disabled around both batches.
        material.set(new IntAttribute(IntAttribute.CullFace, 0));
    }

    // ------------------------------------------------------------------
    // Shared uniform payload
    // ------------------------------------------------------------------

    private static final class Lighting {
        float time;
        int lightCount;
        WorldSeasonMap seasonMap;
        final float[] lightPositions =
            new float[HeightmapTerrainRenderer.MAX_POINT_LIGHTS * 3];
        final float[] lightColors =
            new float[HeightmapTerrainRenderer.MAX_POINT_LIGHTS * 3];
        final float[] lightRanges =
            new float[HeightmapTerrainRenderer.MAX_POINT_LIGHTS];
    }

    private static final class ModelShaderProvider implements ShaderProvider {
        private final boolean edge;
        private final Lighting lighting;
        private ModelShader shader;

        ModelShaderProvider(boolean edge, Lighting lighting) {
            this.edge = edge;
            this.lighting = lighting;
        }

        @Override
        public Shader getShader(Renderable renderable) {
            if (shader == null) {
                shader = new ModelShader(edge, lighting, renderable);
            }
            return shader;
        }

        @Override
        public void dispose() {
            if (shader != null) {
                shader.dispose();
                shader = null;
            }
        }
    }

    /**
     * Minimal BaseShader: only the uniforms used by nature_model.vert/frag.
     * The material's diffuse texture and Kd tint are uploaded per renderable.
     */
    private static final class ModelShader extends BaseShader {
        private final boolean edge;
        private final Lighting lighting;
        private final int uWorldTrans;
        private final int uProjViewTrans;
        private final int uView;
        private final int uFar;
        private final int uDiffuseTexture;
        private final int uDiffuseColor;
        private final int uAlphaTest;
        private final int uCameraPos;
        private final int uLightDir;
        private final int uFogColor;
        private final int uValleyFogColor;
        private final int uFogStart;
        private final int uFogEnd;
        private final int uFogBaseHeight;
        private final int uFogHeightSpan;
        private final int uHeightFogStrength;
        private final int uTime;
        private final int uLightCount;
        private final int uLightPos;
        private final int uLightColor;
        private final int uLightRange;

        ModelShader(boolean edge, Lighting lighting, Renderable renderable) {
            this.edge = edge;
            this.lighting = lighting;
            ShaderProgram program = new ShaderProgram(
                Gdx.files.internal(VERTEX_SHADER),
                Gdx.files.internal(edge ? EDGE_FRAGMENT_SHADER : FRAGMENT_SHADER));
            if (!program.isCompiled()) {
                String log = program.getLog();
                program.dispose();
                throw new GdxRuntimeException(
                    "Failed to compile nature model shader:\n" + log);
            }
            init(program, renderable);

            uWorldTrans = program.fetchUniformLocation("u_worldTrans", false);
            uProjViewTrans = program.fetchUniformLocation("u_projViewTrans", false);
            uView = program.fetchUniformLocation("u_view", false);
            uFar = program.fetchUniformLocation("u_far", false);
            uDiffuseTexture = program.fetchUniformLocation("u_diffuseTexture", false);
            uDiffuseColor = program.fetchUniformLocation("u_diffuseColor", false);
            uAlphaTest = program.fetchUniformLocation("u_alphaTest", false);
            uCameraPos = program.fetchUniformLocation("u_cameraPos", false);
            uLightDir = program.fetchUniformLocation("u_lightDir", false);
            uFogColor = program.fetchUniformLocation("u_fogColor", false);
            uValleyFogColor = program.fetchUniformLocation("u_valleyFogColor", false);
            uFogStart = program.fetchUniformLocation("u_fogStart", false);
            uFogEnd = program.fetchUniformLocation("u_fogEnd", false);
            uFogBaseHeight = program.fetchUniformLocation("u_fogBaseHeight", false);
            uFogHeightSpan = program.fetchUniformLocation("u_fogHeightSpan", false);
            uHeightFogStrength = program.fetchUniformLocation("u_heightFogStrength", false);
            uTime = program.fetchUniformLocation("u_time", false);
            uLightCount = program.fetchUniformLocation("u_lightCount", false);
            uLightPos = program.fetchUniformLocation("u_lightPos[0]", false);
            uLightColor = program.fetchUniformLocation("u_lightColor[0]", false);
            uLightRange = program.fetchUniformLocation("u_lightRange[0]", false);
        }

        @Override
        public void init() {
            // Compiled and initialized in the constructor; ModelBatch does not
            // call this for shaders returned by a ShaderProvider.
        }

        @Override
        public void begin(Camera camera, RenderContext context) {
            super.begin(camera, context);
            // ModelBatch's RenderContext starts with depth testing disabled.
            // Re-enable the exact state shared with terrain/scenery so nature
            // can be occluded by temple walls and torii, and can occlude later
            // dynamic boxes itself.
            context.setDepthTest(GL20.GL_LEQUAL, 0f, 1f);
            context.setDepthMask(true);
            context.setBlending(false, GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            context.setCullFace(0);
            program.setUniformMatrix(uProjViewTrans, camera.combined);
            if (edge) {
                program.setUniformMatrix(uView, camera.view);
                program.setUniformf(uFar, camera.far);
                return;
            }
            program.setUniformf(uCameraPos,
                camera.position.x, camera.position.y, camera.position.z);
            program.setUniformf(uLightDir,
                HeightmapTerrainRenderer.LIGHT_DIRECTION.x,
                HeightmapTerrainRenderer.LIGHT_DIRECTION.y,
                HeightmapTerrainRenderer.LIGHT_DIRECTION.z);
            Color fog = HeightmapTerrainRenderer.FOG_COLOR;
            program.setUniformf(uFogColor, fog.r, fog.g, fog.b);
            Color valley = HeightmapTerrainRenderer.VALLEY_FOG_COLOR;
            program.setUniformf(uValleyFogColor, valley.r, valley.g, valley.b);
            program.setUniformf(uFogStart, HeightmapTerrainRenderer.FOG_START);
            program.setUniformf(uFogEnd, HeightmapTerrainRenderer.FOG_END);
            program.setUniformf(uFogBaseHeight, HeightmapTerrainRenderer.FOG_BASE_HEIGHT);
            program.setUniformf(uFogHeightSpan, HeightmapTerrainRenderer.FOG_HEIGHT_SPAN);
            program.setUniformf(uHeightFogStrength,
                HeightmapTerrainRenderer.HEIGHT_FOG_STRENGTH);
            program.setUniformf(uTime, lighting.time);
            SeasonShaderUniforms.upload(program, lighting.seasonMap);
            program.setUniformi(uLightCount, lighting.lightCount);
            if (lighting.lightCount > 0) {
                program.setUniform3fv(uLightPos, lighting.lightPositions, 0,
                    lighting.lightCount * 3);
                program.setUniform3fv(uLightColor, lighting.lightColors, 0,
                    lighting.lightCount * 3);
                program.setUniform1fv(uLightRange, lighting.lightRanges, 0,
                    lighting.lightCount);
            }
        }

        @Override
        public void render(Renderable renderable, Attributes attributes) {
            program.setUniformMatrix(uWorldTrans, renderable.worldTransform);
            Attribute diffuse = attributes.get(ColorAttribute.Diffuse);
            if (diffuse instanceof ColorAttribute color) {
                program.setUniformf(uDiffuseColor,
                    color.color.r, color.color.g, color.color.b);
            } else {
                program.setUniformf(uDiffuseColor, 1f, 1f, 1f);
            }
            Attribute texture = attributes.get(TextureAttribute.Diffuse);
            if (texture instanceof TextureAttribute tex) {
                int unit = context.textureBinder.bind(tex.textureDescription);
                program.setUniformi(uDiffuseTexture, unit);
            }
            program.setUniformf(uAlphaTest, ALPHA_TEST);
            super.render(renderable, attributes);
        }

        @Override
        public int compareTo(Shader other) {
            return 0;
        }

        @Override
        public boolean canRender(Renderable instance) {
            return true;
        }

        @Override
        public void dispose() {
            ShaderProgram owned = program;
            super.dispose();
            if (owned != null) {
                owned.dispose();
            }
        }
    }
}
