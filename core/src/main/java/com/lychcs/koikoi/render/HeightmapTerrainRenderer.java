package com.lychcs.koikoi.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;

import java.util.List;

/**
 * Forward 3D renderer of the first-person overworld.
 *
 * <p>Builds one static terrain mesh from a generated {@link Heightfield} and
 * keeps one dynamic mesh for camera-facing billboards (wild Yokai, cards,
 * particles) and ground decals (Hanko seals). Everything is drawn with the
 * same unlit-plus-key-light shader, so the later ASCII post pass receives a
 * pure luminance image it can turn into glyph density.</p>
 *
 * <p>All work happens on the render thread; the class owns its shader and both
 * meshes and must be disposed by the screen.</p>
 */
public final class HeightmapTerrainRenderer implements Disposable {

    public static final String VERTEX_SHADER = "shaders/ascii_world.vert";
    public static final String FRAGMENT_SHADER = "shaders/ascii_world.frag";

    /** Extra quads per frame; excess billboards are dropped instead of growing. */
    private static final int MAX_DYNAMIC_QUADS = 512;

    /** Floats per vertex: position (3) + normal (3) + packed colour (1). */
    private static final int FLOATS_PER_VERTEX = 7;
    private static final int VERTICES_PER_QUAD = 4;
    private static final int INDICES_PER_QUAD = 6;

    /** Shared vertex layout of terrain, scenery and dynamic quads. */
    static final VertexAttributes VERTEX_ATTRIBUTES = new VertexAttributes(
        new VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"),
        new VertexAttribute(VertexAttributes.Usage.Normal, 3, "a_normal"),
        new VertexAttribute(VertexAttributes.Usage.ColorPacked, 4, "a_color"));

    /** Direction from a surface towards the key light (normalized). */
    private static final Vector3 LIGHT_DIRECTION = new Vector3(-0.35f, 0.86f, 0.37f).nor();

    /** Fog blends the far hills into the terminal background. */
    private static final Color FOG_COLOR = new Color(0.030f, 0.068f, 0.040f, 1f);
    private static final Color VALLEY_FOG_COLOR = new Color(0.055f, 0.098f, 0.108f, 1f);
    private static final float FOG_START = 38f;
    private static final float FOG_END = 235f;
    /** Subtle height fog pooling in valleys (base height + falloff span). */
    private static final float FOG_BASE_HEIGHT = -1.8f;
    private static final float FOG_HEIGHT_SPAN = 7.0f;
    private static final float HEIGHT_FOG_STRENGTH = 0.34f;

    /** Warm Tōrō/temple point lights uploaded to the world shader. */
    public static final int MAX_POINT_LIGHTS = 12;
    private final float[] lightPositions = new float[MAX_POINT_LIGHTS * 3];
    private final float[] lightColors = new float[MAX_POINT_LIGHTS * 3];
    private final float[] lightRanges = new float[MAX_POINT_LIGHTS];
    private final int[] selectedLights = new int[MAX_POINT_LIGHTS];

    private final Heightfield field;
    private final ShaderProgram shader;
    private final Mesh terrainMesh;
    private final Mesh dynamicMesh;
    /** Optional procedural village: torii, lanterns, temple, groves, paths. */
    private JapaneseScenery scenery;

    private final float[] dynamicVertices =
        new float[MAX_DYNAMIC_QUADS * VERTICES_PER_QUAD * FLOATS_PER_VERTEX];
    private final short[] dynamicIndices = new short[MAX_DYNAMIC_QUADS * INDICES_PER_QUAD];
    private int quadCount;

    /** Camera-facing horizontal right vector, refreshed by {@link #begin}. */
    private float rightX = 1f;
    private float rightZ = 0f;
    private final Vector3 cameraPosition = new Vector3();
    private final Vector3 vertexNormal = new Vector3();
    private final Color vertexColor = new Color();

    public HeightmapTerrainRenderer(Heightfield field) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        this.field = field;
        this.shader = new ShaderProgram(
            Gdx.files.internal(VERTEX_SHADER), Gdx.files.internal(FRAGMENT_SHADER));
        if (!shader.isCompiled()) {
            String log = shader.getLog();
            shader.dispose();
            throw new GdxRuntimeException("Failed to compile ascii_world shader:\n" + log);
        }
        this.terrainMesh = buildTerrainMesh(field);
        this.dynamicMesh = buildDynamicMesh();
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
        uploadWarmLights(camera, time);

        quadCount = 0;
        terrainMesh.render(shader, GL20.GL_TRIANGLES);
        if (scenery != null) {
            scenery.render(shader);
        }
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
     * Uploads the nearest warm lantern/temple lights (up to
     * {@link #MAX_POINT_LIGHTS}) with a subtle per-light flicker.
     */
    private void uploadWarmLights(PerspectiveCamera camera, float time) {
        if (scenery == null) {
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

            // Golden lantern flicker, offset per light so it never pulses in sync.
            float flicker = 0.90f + 0.10f * MathUtils.sin(time * 7.3f + i * 1.7f);
            lightColors[i * 3] = light.red() * flicker;
            lightColors[i * 3 + 1] = light.green() * flicker;
            lightColors[i * 3 + 2] = light.blue() * flicker;
            lightRanges[i] = light.radius();
        }

        shader.setUniformi("u_lightCount", selected);
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
     * Queues one upright quad that always faces the camera.
     *
     * @param worldX   centre X in world units
     * @param groundY  terrain height at the centre
     * @param worldZ   centre Z in world units
     * @param width    billboard width in world units
     * @param height   billboard height in world units
     * @param color    ink colour (alpha is ignored by the ASCII pass)
     */
    public void drawBillboard(float worldX, float groundY, float worldZ,
                              float width, float height, Color color) {
        drawBillboard(worldX, groundY, worldZ, width, height, color, 0f);
    }

    /**
     * Queues one camera-facing quad rotated by {@code roll} radians around the
     * view axis (spinning rune seals, tumbling cards).
     */
    public void drawBillboard(float worldX, float groundY, float worldZ,
                              float width, float height, Color color, float roll) {
        if (color == null || quadCount >= MAX_DYNAMIC_QUADS) {
            return;
        }
        float halfWidth = width * 0.5f;
        float halfHeight = height * 0.5f;
        float centerY = groundY + halfHeight;

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

        vertexNormal.set(cameraPosition.x - worldX,
            cameraPosition.y - centerY, cameraPosition.z - worldZ);
        if (vertexNormal.len2() <= 0.0001f) {
            vertexNormal.set(0f, 0f, 1f);
        } else {
            vertexNormal.nor();
        }

        int base = quadCount * VERTICES_PER_QUAD * FLOATS_PER_VERTEX;
        writeVertex(base,
            worldX - axisRightX * halfWidth - axisUpX * halfHeight,
            centerY - axisRightY * halfWidth - axisUpY * halfHeight,
            worldZ - axisRightZ * halfWidth - axisUpZ * halfHeight, color);
        writeVertex(base + FLOATS_PER_VERTEX,
            worldX + axisRightX * halfWidth - axisUpX * halfHeight,
            centerY + axisRightY * halfWidth - axisUpY * halfHeight,
            worldZ + axisRightZ * halfWidth - axisUpZ * halfHeight, color);
        writeVertex(base + FLOATS_PER_VERTEX * 2,
            worldX + axisRightX * halfWidth + axisUpX * halfHeight,
            centerY + axisRightY * halfWidth + axisUpY * halfHeight,
            worldZ + axisRightZ * halfWidth + axisUpZ * halfHeight, color);
        writeVertex(base + FLOATS_PER_VERTEX * 3,
            worldX - axisRightX * halfWidth + axisUpX * halfHeight,
            centerY - axisRightY * halfWidth + axisUpY * halfHeight,
            worldZ - axisRightZ * halfWidth + axisUpZ * halfHeight, color);
        quadCount++;
    }

    /**
     * Queues a flat, terrain-hugging quad (Hanko seals and other ground decals).
     * The four corners sample the heightfield so the decal follows the surface.
     */
    public void drawGroundQuad(float worldX, float worldZ, float halfSize, Color color) {
        if (color == null || !(halfSize > 0f) || quadCount >= MAX_DYNAMIC_QUADS) {
            return;
        }
        float minX = worldX - halfSize;
        float maxX = worldX + halfSize;
        float minZ = worldZ - halfSize;
        float maxZ = worldZ + halfSize;
        float lift = 0.035f;

        int base = quadCount * VERTICES_PER_QUAD * FLOATS_PER_VERTEX;
        writeFlatVertex(base, minX, field.getHeight(minX, minZ) + lift, minZ, color);
        writeFlatVertex(base + FLOATS_PER_VERTEX,
            maxX, field.getHeight(maxX, minZ) + lift, minZ, color);
        writeFlatVertex(base + FLOATS_PER_VERTEX * 2,
            maxX, field.getHeight(maxX, maxZ) + lift, maxZ, color);
        writeFlatVertex(base + FLOATS_PER_VERTEX * 3,
            minX, field.getHeight(minX, maxZ) + lift, maxZ, color);
        quadCount++;
    }

    /** Flushes the queued billboards/decals and unbinds the shader. */
    public void end() {
        if (quadCount > 0) {
            int floats = quadCount * VERTICES_PER_QUAD * FLOATS_PER_VERTEX;
            dynamicMesh.setVertices(dynamicVertices, 0, floats);
            dynamicMesh.setIndices(dynamicIndices, 0, quadCount * INDICES_PER_QUAD);
            dynamicMesh.render(shader, GL20.GL_TRIANGLES);
        }
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
        terrainMesh.dispose();
        dynamicMesh.dispose();
        shader.dispose();
    }

    // ------------------------------------------------------------------
    // Mesh construction
    // ------------------------------------------------------------------

    private Mesh buildTerrainMesh(Heightfield heightfield) {
        int perSide = heightfield.getVerticesPerSide();
        int vertexCount = perSide * perSide;
        float[] vertices = new float[vertexCount * FLOATS_PER_VERTEX];
        float step = heightfield.getStep();
        float lowest = heightfield.getMinHeight();
        // Clamp the range so the raised mountain rim does not crush the rolling
        // meadow into a single dark shade.
        float span = Math.max(0.0001f,
            Math.min(heightfield.getMaxHeight() - lowest, 12f));
        int index = 0;

        for (int row = 0; row < perSide; row++) {
            float z = heightfield.getZ(row);
            for (int col = 0; col < perSide; col++) {
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

                // Steep cliff faces turn from moss into grey-green rock.
                float slope = 1f - vertexNormal.y;
                float rock = MathUtils.clamp(slope * 3.2f, 0f, 1f);
                float mossRed = 0.155f * light;
                float mossGreen = 0.335f * light;
                float mossBlue = 0.200f * light;
                float rockRed = 0.340f * light;
                float rockGreen = 0.360f * light;
                float rockBlue = 0.340f * light;
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
            }
        }

        int resolution = heightfield.getResolution();
        short[] indices = new short[resolution * resolution * INDICES_PER_QUAD];
        int cursor = 0;
        for (int row = 0; row < resolution; row++) {
            for (int col = 0; col < resolution; col++) {
                short topLeft = (short) (row * perSide + col);
                short topRight = (short) (topLeft + 1);
                short bottomLeft = (short) (topLeft + perSide);
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

    private void writeVertex(int offset, float x, float y, float z, Color color) {
        dynamicVertices[offset] = x;
        dynamicVertices[offset + 1] = y;
        dynamicVertices[offset + 2] = z;
        dynamicVertices[offset + 3] = vertexNormal.x;
        dynamicVertices[offset + 4] = vertexNormal.y;
        dynamicVertices[offset + 5] = vertexNormal.z;
        dynamicVertices[offset + 6] = color.toFloatBits();
    }

    private void writeFlatVertex(int offset, float x, float y, float z, Color color) {
        dynamicVertices[offset] = x;
        dynamicVertices[offset + 1] = y;
        dynamicVertices[offset + 2] = z;
        dynamicVertices[offset + 3] = 0f;
        dynamicVertices[offset + 4] = 1f;
        dynamicVertices[offset + 5] = 0f;
        dynamicVertices[offset + 6] = color.toFloatBits();
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
