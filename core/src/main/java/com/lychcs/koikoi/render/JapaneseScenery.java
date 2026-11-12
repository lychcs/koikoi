package com.lychcs.koikoi.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.NumberUtils;

/**
 * Static 3D mesh of the procedural Japanese scenery.
 *
 * <p>Converts a {@link SceneryPlan} into one non-indexed mesh: every beam,
 * post, roof layer, bamboo stalk and blossom cluster becomes a box with six
 * shaded faces, and every dirt-path tile becomes a quad that follows the
 * heightfield. Woven bamboo fences, mossy stone wall blocks, torii gates,
 * lanterns and temple structures all arrive through the plan, so collision and
 * visuals share the same layout. Drawn with the same forward shader as the
 * terrain, the ASCII pass receives readable silhouettes.</p>
 *
 * <p>Owns its {@link Mesh}; the renderer disposes it with the screen.</p>
 */
public final class JapaneseScenery implements Disposable {

    private static final int FLOATS_PER_VERTEX = 7;

    private final SceneryPlan plan;
    private final Mesh mesh;

    public JapaneseScenery(SceneryPlan plan, Heightfield field) {
        if (plan == null) {
            throw new IllegalArgumentException("plan must not be null");
        }
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        this.plan = plan;
        this.mesh = buildMesh(plan, field);
    }

    /** Draws the scenery with the already-bound world shader. */
    public void render(ShaderProgram shader) {
        mesh.render(shader, GL20.GL_TRIANGLES);
    }

    public SceneryPlan getPlan() {
        return plan;
    }

    @Override
    public void dispose() {
        mesh.dispose();
    }

    // ------------------------------------------------------------------
    // Mesh construction
    // ------------------------------------------------------------------

    private static Mesh buildMesh(SceneryPlan plan, Heightfield field) {
        FloatArray vertices = new FloatArray(
            plan.getBoxes().size() * 36 * FLOATS_PER_VERTEX
                + plan.getPathPlates().size() * 6 * FLOATS_PER_VERTEX);

        for (SceneryPlan.Box box : plan.getBoxes()) {
            appendBox(vertices, box);
        }
        for (SceneryPlan.PathPlate plate : plan.getPathPlates()) {
            appendPlate(vertices, plate, field);
        }

        float[] data = vertices.toArray();
        int vertexCount = data.length / FLOATS_PER_VERTEX;
        Mesh mesh = new Mesh(false, Math.max(1, vertexCount), 0,
            HeightmapTerrainRenderer.VERTEX_ATTRIBUTES);
        if (vertexCount > 0) {
            mesh.setVertices(data);
        }
        return mesh;
    }

    private static void appendBox(FloatArray out, SceneryPlan.Box box) {
        float halfX = box.sx() * 0.5f;
        float halfY = box.sy() * 0.5f;
        float halfZ = box.sz() * 0.5f;
        float cos = MathUtils.cos(box.yaw());
        float sin = MathUtils.sin(box.yaw());

        // +X / -X
        appendQuad(out, box, cos, sin, 1f, 0f, 0f, 0.97f,
            halfX, -halfY, -halfZ, halfX, -halfY, halfZ,
            halfX, halfY, halfZ, halfX, halfY, -halfZ);
        appendQuad(out, box, cos, sin, -1f, 0f, 0f, 0.90f,
            -halfX, -halfY, halfZ, -halfX, -halfY, -halfZ,
            -halfX, halfY, -halfZ, -halfX, halfY, halfZ);
        // +Y / -Y
        appendQuad(out, box, cos, sin, 0f, 1f, 0f, 1.12f,
            -halfX, halfY, -halfZ, halfX, halfY, -halfZ,
            halfX, halfY, halfZ, -halfX, halfY, halfZ);
        appendQuad(out, box, cos, sin, 0f, -1f, 0f, 0.55f,
            -halfX, -halfY, halfZ, halfX, -halfY, halfZ,
            halfX, -halfY, -halfZ, -halfX, -halfY, -halfZ);
        // +Z / -Z
        appendQuad(out, box, cos, sin, 0f, 0f, 1f, 1.00f,
            -halfX, -halfY, halfZ, -halfX, halfY, halfZ,
            halfX, halfY, halfZ, halfX, -halfY, halfZ);
        appendQuad(out, box, cos, sin, 0f, 0f, -1f, 0.84f,
            halfX, -halfY, -halfZ, halfX, halfY, -halfZ,
            -halfX, halfY, -halfZ, -halfX, -halfY, -halfZ);
    }

    private static void appendQuad(FloatArray out, SceneryPlan.Box box,
                                   float cos, float sin,
                                   float normalX, float normalY, float normalZ,
                                   float shade,
                                   float x1, float y1, float z1,
                                   float x2, float y2, float z2,
                                   float x3, float y3, float z3,
                                   float x4, float y4, float z4) {
        float red = clamp(box.red() * shade);
        float green = clamp(box.green() * shade);
        float blue = clamp(box.blue() * shade);
        float color = NumberUtils.intToFloatColor(Color.rgba8888(red, green, blue, 1f));

        // Rotation around Y also rotates the face normal.
        float worldNormalX = normalX * cos + normalZ * sin;
        float worldNormalZ = -normalX * sin + normalZ * cos;

        appendVertex(out, box, cos, sin, x1, y1, z1,
            worldNormalX, normalY, worldNormalZ, color);
        appendVertex(out, box, cos, sin, x2, y2, z2,
            worldNormalX, normalY, worldNormalZ, color);
        appendVertex(out, box, cos, sin, x3, y3, z3,
            worldNormalX, normalY, worldNormalZ, color);

        appendVertex(out, box, cos, sin, x1, y1, z1,
            worldNormalX, normalY, worldNormalZ, color);
        appendVertex(out, box, cos, sin, x3, y3, z3,
            worldNormalX, normalY, worldNormalZ, color);
        appendVertex(out, box, cos, sin, x4, y4, z4,
            worldNormalX, normalY, worldNormalZ, color);
    }

    private static void appendVertex(FloatArray out, SceneryPlan.Box box,
                                     float cos, float sin,
                                     float localX, float localY, float localZ,
                                     float normalX, float normalY, float normalZ,
                                     float color) {
        out.add(box.x() + localX * cos + localZ * sin);
        out.add(box.y() + localY);
        out.add(box.z() - localX * sin + localZ * cos);
        out.add(normalX);
        out.add(normalY);
        out.add(normalZ);
        out.add(color);
    }

    /** A dirt plate follows the heightfield at all four corners. */
    private static void appendPlate(FloatArray out, SceneryPlan.PathPlate plate,
                                    Heightfield field) {
        float half = plate.size() * 0.5f;
        float minX = plate.x() - half;
        float maxX = plate.x() + half;
        float minZ = plate.z() - half;
        float maxZ = plate.z() + half;
        float lift = 0.025f;
        float color = NumberUtils.intToFloatColor(
            Color.rgba8888(plate.red(), plate.green(), plate.blue(), 1f));

        float x1 = minX;
        float z1 = minZ;
        float y1 = field.getHeight(x1, z1) + lift;
        float x2 = minX;
        float z2 = maxZ;
        float y2 = field.getHeight(x2, z2) + lift;
        float x3 = maxX;
        float z3 = maxZ;
        float y3 = field.getHeight(x3, z3) + lift;
        float x4 = maxX;
        float z4 = minZ;
        float y4 = field.getHeight(x4, z4) + lift;

        appendFlatVertex(out, x1, y1, z1, color);
        appendFlatVertex(out, x2, y2, z2, color);
        appendFlatVertex(out, x3, y3, z3, color);
        appendFlatVertex(out, x1, y1, z1, color);
        appendFlatVertex(out, x3, y3, z3, color);
        appendFlatVertex(out, x4, y4, z4, color);
    }

    private static void appendFlatVertex(FloatArray out, float x, float y, float z,
                                         float color) {
        out.add(x);
        out.add(y);
        out.add(z);
        out.add(0f);
        out.add(1f);
        out.add(0f);
        out.add(color);
    }

    private static float clamp(float value) {
        return value < 0f ? 0f : Math.min(value, 1f);
    }
}
