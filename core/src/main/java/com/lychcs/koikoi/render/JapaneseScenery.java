package com.lychcs.koikoi.render;

import com.badlogic.gdx.graphics.Camera;
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
 * <p>Converts a {@link SceneryPlan} into one 32-tile chunk mesh each: temple
 * beams and roof layers remain authored box primitives, while bamboo culms,
 * torii gates,
 * stone lanterns and torches are built from tapered round/faceted cylinders,
 * node rings and low-poly ellipsoids. Every dirt-path tile becomes a quad that
 * follows the heightfield. Woven bamboo fences, mossy stone wall blocks, torii
 * gates, lanterns and temple structures all arrive through the plan, so
 * collision and visuals share the same layout. Drawn with the same forward
 * shader as the terrain, the ASCII pass receives readable silhouettes.</p>
 *
 * <p>Owns its {@link Mesh}; the renderer disposes it with the screen.</p>
 */
public final class JapaneseScenery implements Disposable {

    private static final int FLOATS_PER_VERTEX = 8;
    /** Distance cull radius of one scenery chunk in world units. */
    private static final float CHUNK_CULL_RADIUS = 150f;

    private final SceneryPlan plan;
    /** One mesh per occupied 32-tile chunk, plus their culling bounds. */
    private final Mesh[] chunkMeshes;
    private final int[] chunkIndices;
    private final float[] chunkMinY;
    private final float[] chunkMaxY;
    private final WorldSeasonMap.ChunkGrid chunkGrid;

    public JapaneseScenery(SceneryPlan plan, Heightfield field) {
        if (plan == null) {
            throw new IllegalArgumentException("plan must not be null");
        }
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        this.plan = plan;
        this.chunkGrid = sceneryChunkGrid(field);
        this.chunkMeshes = new Mesh[chunkGrid.count()];
        this.chunkIndices = new int[chunkGrid.count()];
        this.chunkMinY = new float[chunkGrid.count()];
        this.chunkMaxY = new float[chunkGrid.count()];
        for (int i = 0; i < chunkMeshes.length; i++) {
            chunkIndices[i] = -1;
            chunkMinY[i] = Float.MAX_VALUE;
            chunkMaxY[i] = -Float.MAX_VALUE;
        }
        buildMeshes(plan, field);
    }

    /** 32-tile culling grid aligned to the world origin, covering the field. */
    private static WorldSeasonMap.ChunkGrid sceneryChunkGrid(Heightfield field) {
        float chunk = WorldSeasonMap.CHUNK_SIZE_TILES;
        float originX = (float) Math.floor(field.getOriginX() / chunk) * chunk;
        float originZ = (float) Math.floor(field.getOriginZ() / chunk) * chunk;
        float offset = Math.max(field.getOriginX() - originX,
            field.getOriginZ() - originZ);
        float span = (float) Math.ceil((field.getSize() + offset) / chunk) * chunk;
        return new WorldSeasonMap.ChunkGrid(originX, originZ, span, chunk);
    }

    /** Draws all scenery chunks with the already-bound world shader. */
    public void render(ShaderProgram shader) {
        for (Mesh mesh : chunkMeshes) {
            if (mesh != null) {
                mesh.render(shader, GL20.GL_TRIANGLES);
            }
        }
    }

    /**
     * Draws only the scenery chunks inside the camera frustum and within the
     * distance cull radius.
     */
    public void render(ShaderProgram shader, Camera camera) {
        if (camera == null) {
            render(shader);
            return;
        }
        float cameraX = camera.position.x;
        float cameraZ = camera.position.z;
        float half = chunkGrid.getChunkSize() * 0.5f;
        for (int slot = 0; slot < chunkMeshes.length; slot++) {
            Mesh mesh = chunkMeshes[slot];
            if (mesh == null) {
                continue;
            }
            int index = chunkIndices[slot];
            if (!chunkGrid.isVisible(index, cameraX, cameraZ, CHUNK_CULL_RADIUS)) {
                continue;
            }
            float centerY = (chunkMinY[slot] + chunkMaxY[slot]) * 0.5f;
            float halfHeight = (chunkMaxY[slot] - chunkMinY[slot]) * 0.5f + 0.5f;
            if (!camera.frustum.boundsInFrustum(chunkGrid.chunkCenterX(index),
                centerY, chunkGrid.chunkCenterZ(index), half, halfHeight, half)) {
                continue;
            }
            mesh.render(shader, GL20.GL_TRIANGLES);
        }
    }

    public SceneryPlan getPlan() {
        return plan;
    }

    /** Number of occupied 32-tile scenery chunks. */
    public int getChunkCount() {
        int count = 0;
        for (Mesh mesh : chunkMeshes) {
            if (mesh != null) {
                count++;
            }
        }
        return count;
    }

    @Override
    public void dispose() {
        for (Mesh mesh : chunkMeshes) {
            if (mesh != null) {
                mesh.dispose();
            }
        }
    }

    // ------------------------------------------------------------------
    // Mesh construction
    // ------------------------------------------------------------------

    /**
     * Buckets every box, cylinder, sphere and path plate into its 32-tile
     * chunk and uploads one mesh per occupied chunk.
     */
    private void buildMeshes(SceneryPlan plan, Heightfield field) {
        FloatArray[] bins = new FloatArray[chunkMeshes.length];
        for (SceneryPlan.Box box : plan.getBoxes()) {
            appendBox(binFor(bins, box.x(), box.z()), box);
        }
        for (SceneryPlan.Cylinder cylinder : plan.getCylinders()) {
            float x = (cylinder.x1() + cylinder.x2()) * 0.5f;
            float z = (cylinder.z1() + cylinder.z2()) * 0.5f;
            appendCylinder(binFor(bins, x, z), cylinder);
        }
        for (SceneryPlan.Sphere sphere : plan.getSpheres()) {
            appendSphere(binFor(bins, sphere.x(), sphere.z()), sphere);
        }
        for (SceneryPlan.PathPlate plate : plan.getPathPlates()) {
            appendPlate(binFor(bins, plate.x(), plate.z()), plate, field);
        }

        for (int slot = 0; slot < bins.length; slot++) {
            FloatArray vertices = bins[slot];
            if (vertices == null || vertices.size == 0) {
                continue;
            }
            float[] data = vertices.toArray();
            int vertexCount = data.length / FLOATS_PER_VERTEX;
            Mesh mesh = new Mesh(false, Math.max(1, vertexCount), 0,
                HeightmapTerrainRenderer.VERTEX_ATTRIBUTES);
            mesh.setVertices(data);
            chunkMeshes[slot] = mesh;
            chunkIndices[slot] = slot;
            chunkMinY[slot] = field.getMinHeight() - 1f;
            chunkMaxY[slot] = field.getMaxHeight() + 8f;
        }
    }

    private FloatArray binFor(FloatArray[] bins, float x, float z) {
        int index = chunkGrid.chunkOf(x, z);
        FloatArray bin = bins[index];
        if (bin == null) {
            bin = new FloatArray(1024);
            bins[index] = bin;
        }
        return bin;
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
    /**
     * Builds a tapered cylinder/frustum between two world points. Every side
     * gets its radial normal; caps are optional so bamboo nodes can stay open.
     */
    private static void appendCylinder(FloatArray out, SceneryPlan.Cylinder c) {
        int sides = Math.max(3, c.sides());
        float ax = c.x2() - c.x1();
        float ay = c.y2() - c.y1();
        float az = c.z2() - c.z1();
        float length = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        if (length < 0.0001f) {
            return;
        }
        ax /= length;
        ay /= length;
        az /= length;

        // Stable orthonormal basis around the cylinder axis.
        float refX = 0f;
        float refY = 0f;
        float refZ = 1f;
        if (Math.abs(az) > 0.9f) {
            refX = 1f;
            refZ = 0f;
        }
        float ux = ay * refZ - az * refY;
        float uy = az * refX - ax * refZ;
        float uz = ax * refY - ay * refX;
        float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= ul;
        uy /= ul;
        uz /= ul;
        float vx = ay * uz - az * uy;
        float vy = az * ux - ax * uz;
        float vz = ax * uy - ay * ux;

        float color = NumberUtils.intToFloatColor(
            opaqueColor(c.red(), c.green(), c.blue()));
        float material = c.material();

        for (int i = 0; i < sides; i++) {
            float a0 = (float) (Math.PI * 2.0 * i / sides);
            float a1 = (float) (Math.PI * 2.0 * (i + 1) / sides);
            float c0 = MathUtils.cos(a0);
            float s0 = MathUtils.sin(a0);
            float c1 = MathUtils.cos(a1);
            float s1 = MathUtils.sin(a1);

            float n0x = ux * c0 + vx * s0;
            float n0y = uy * c0 + vy * s0;
            float n0z = uz * c0 + vz * s0;
            float n1x = ux * c1 + vx * s1;
            float n1y = uy * c1 + vy * s1;
            float n1z = uz * c1 + vz * s1;

            float b0x = c.x1() + n0x * c.radius1();
            float b0y = c.y1() + n0y * c.radius1();
            float b0z = c.z1() + n0z * c.radius1();
            float b1x = c.x1() + n1x * c.radius1();
            float b1y = c.y1() + n1y * c.radius1();
            float b1z = c.z1() + n1z * c.radius1();
            float t0x = c.x2() + n0x * c.radius2();
            float t0y = c.y2() + n0y * c.radius2();
            float t0z = c.z2() + n0z * c.radius2();
            float t1x = c.x2() + n1x * c.radius2();
            float t1y = c.y2() + n1y * c.radius2();
            float t1z = c.z2() + n1z * c.radius2();

            appendRaw(out, b0x, b0y, b0z, n0x, n0y, n0z, color, material);
            appendRaw(out, b1x, b1y, b1z, n1x, n1y, n1z, color, material);
            appendRaw(out, t1x, t1y, t1z, n1x, n1y, n1z, color, material);

            appendRaw(out, b0x, b0y, b0z, n0x, n0y, n0z, color, material);
            appendRaw(out, t1x, t1y, t1z, n1x, n1y, n1z, color, material);
            appendRaw(out, t0x, t0y, t0z, n0x, n0y, n0z, color, material);
        }

        if (!c.caps()) {
            return;
        }
        for (int i = 0; i < sides; i++) {
            float a0 = (float) (Math.PI * 2.0 * i / sides);
            float a1 = (float) (Math.PI * 2.0 * (i + 1) / sides);
            float c0 = MathUtils.cos(a0);
            float s0 = MathUtils.sin(a0);
            float c1 = MathUtils.cos(a1);
            float s1 = MathUtils.sin(a1);
            float n0x = ux * c0 + vx * s0;
            float n0y = uy * c0 + vy * s0;
            float n0z = uz * c0 + vz * s0;
            float n1x = ux * c1 + vx * s1;
            float n1y = uy * c1 + vy * s1;
            float n1z = uz * c1 + vz * s1;

            appendRaw(out, c.x1(), c.y1(), c.z1(), -ax, -ay, -az, color, material);
            appendRaw(out, c.x1() + n1x * c.radius1(), c.y1() + n1y * c.radius1(),
                c.z1() + n1z * c.radius1(), -ax, -ay, -az, color, material);
            appendRaw(out, c.x1() + n0x * c.radius1(), c.y1() + n0y * c.radius1(),
                c.z1() + n0z * c.radius1(), -ax, -ay, -az, color, material);

            appendRaw(out, c.x2(), c.y2(), c.z2(), ax, ay, az, color, material);
            appendRaw(out, c.x2() + n0x * c.radius2(), c.y2() + n0y * c.radius2(),
                c.z2() + n0z * c.radius2(), ax, ay, az, color, material);
            appendRaw(out, c.x2() + n1x * c.radius2(), c.y2() + n1y * c.radius2(),
                c.z2() + n1z * c.radius2(), ax, ay, az, color, material);
        }
    }

    /** Builds a low-poly ellipsoid with smooth radial normals. */
    private static void appendSphere(FloatArray out, SceneryPlan.Sphere s) {
        int slices = Math.max(3, s.slices());
        int stacks = Math.max(2, s.stacks());
        float rx = Math.max(0.0001f, s.radiusX());
        float ry = Math.max(0.0001f, s.radiusY());
        float rz = Math.max(0.0001f, s.radiusZ());
        float color = NumberUtils.intToFloatColor(
            opaqueColor(s.red(), s.green(), s.blue()));
        float material = s.material();

        for (int stack = 0; stack < stacks; stack++) {
            float lat0 = (float) (-Math.PI * 0.5 + Math.PI * stack / stacks);
            float lat1 = (float) (-Math.PI * 0.5 + Math.PI * (stack + 1) / stacks);
            float cy0 = MathUtils.cos(lat0);
            float sy0 = MathUtils.sin(lat0);
            float cy1 = MathUtils.cos(lat1);
            float sy1 = MathUtils.sin(lat1);
            for (int slice = 0; slice < slices; slice++) {
                float lon0 = (float) (Math.PI * 2.0 * slice / slices);
                float lon1 = (float) (Math.PI * 2.0 * (slice + 1) / slices);
                float cx0 = MathUtils.cos(lon0);
                float sx0 = MathUtils.sin(lon0);
                float cx1 = MathUtils.cos(lon1);
                float sx1 = MathUtils.sin(lon1);

                float ax = s.x() + cx0 * cy0 * rx;
                float ay = s.y() + sy0 * ry;
                float az = s.z() + sx0 * cy0 * rz;
                float bx = s.x() + cx1 * cy0 * rx;
                float by = s.y() + sy0 * ry;
                float bz = s.z() + sx1 * cy0 * rz;
                float cx = s.x() + cx1 * cy1 * rx;
                float cy = s.y() + sy1 * ry;
                float cz = s.z() + sx1 * cy1 * rz;
                float dx = s.x() + cx0 * cy1 * rx;
                float dy = s.y() + sy1 * ry;
                float dz = s.z() + sx0 * cy1 * rz;

                appendEllipsoidVertex(out, s, ax, ay, az, rx, ry, rz, color, material);
                appendEllipsoidVertex(out, s, bx, by, bz, rx, ry, rz, color, material);
                appendEllipsoidVertex(out, s, cx, cy, cz, rx, ry, rz, color, material);

                appendEllipsoidVertex(out, s, ax, ay, az, rx, ry, rz, color, material);
                appendEllipsoidVertex(out, s, cx, cy, cz, rx, ry, rz, color, material);
                appendEllipsoidVertex(out, s, dx, dy, dz, rx, ry, rz, color, material);
            }
        }
    }

    private static void appendEllipsoidVertex(FloatArray out, SceneryPlan.Sphere sphere,
                                              float x, float y, float z,
                                              float rx, float ry, float rz,
                                              float color, float material) {
        float nx = (x - sphere.x()) / (rx * rx);
        float ny = (y - sphere.y()) / (ry * ry);
        float nz = (z - sphere.z()) / (rz * rz);
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length > 0.0001f) {
            nx /= length;
            ny /= length;
            nz /= length;
        } else {
            nx = 0f;
            ny = 1f;
            nz = 0f;
        }
        appendRaw(out, x, y, z, nx, ny, nz, color, material);
    }


    /** Writes one already world-space vertex in the shared 8-float layout. */
    private static void appendRaw(FloatArray out,
                                  float x, float y, float z,
                                  float nx, float ny, float nz,
                                  float color, float material) {
        out.add(x);
        out.add(y);
        out.add(z);
        out.add(nx);
        out.add(ny);
        out.add(nz);
        out.add(color);
        out.add(material);
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
        float color = NumberUtils.intToFloatColor(opaqueColor(red, green, blue));

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

    /**
     * Packs one opaque scenery vertex colour. Static torii / fences / temple /
     * rocks / trees must always carry exactly 1.0 alpha; magic glyphs are a
     * separate 3D layer and never use scenery vertices.
     */
    static int opaqueColor(float red, float green, float blue) {
        return Color.rgba8888(red, green, blue, 1f);
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
        out.add(box.material());
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
            opaqueColor(plate.red(), plate.green(), plate.blue()));

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
        out.add(SceneryPlan.MATERIAL_STONE_PATH);
    }

    private static float clamp(float value) {
        return value < 0f ? 0f : Math.min(value, 1f);
    }
}
