package com.lychcs.koikoi.render;

import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;

/**
 * Over-the-shoulder third-person yaw/pitch rig of the overworld camera.
 *
 * <p>The controller owns the look angles and the complete camera geometry; it
 * never touches the ECS or the physics body. Mouse deltas rotate the view, WASD
 * is converted into a unit ground direction relative to the yaw (so {@code W}
 * walks where the view points and {@code S} backpedals), and the camera orbits
 * behind a shoulder pivot {@link #DEFAULT_HEIGHT_OFFSET} units above the feet.
 * A subtle right-shoulder offset keeps the centre crosshair - and therefore the
 * card aim ray - clear of the visible player avatar.</p>
 *
 * <p>The boom is clamped against solid scenery supplied by an
 * {@link ObstacleQuery}; ground rising above the pivot shortens the boom as
 * well, and a final lift keeps the camera above the {@link TerrainSampler}
 * surface when the player looks up. {@link #resolveAimPoint} walks the exact
 * camera ray into the same terrain/scenery so the {@code +} crosshair always
 * names a real 3D world point. Both queries are pure functions, which keeps all
 * geometry unit-testable without a GL context.</p>
 *
 * <p>World mapping: the ASCII world uses a 2D {@code (x, y)} ground plane. In
 * 3D that plane becomes {@code (x, -y, z)}: 2D north ({@code +y}) maps to 3D
 * {@code -z}, so a yaw of {@code 0} looks north by default. All movement
 * helpers return 2D world vectors that can be handed straight to the combat
 * systems.</p>
 */
public final class ThirdPersonCameraController {

    /** Degrees of rotation per mouse pixel. */
    public static final float DEFAULT_SENSITIVITY = 0.11f;

    /** Maximum absolute pitch; keeping it below 90 avoids a degenerate up vector. */
    public static final float MAX_PITCH_DEGREES = 75f;

    /** Default distance from the shoulder pivot to the camera in world units. */
    public static final float DEFAULT_DISTANCE = 5.0f;

    /** Closest distance the collision clamp may pull the camera in. */
    public static final float MIN_DISTANCE = 1.2f;

    /** Default height of the shoulder pivot above the player's feet. */
    public static final float DEFAULT_HEIGHT_OFFSET = 1.8f;

    /** Default right-shoulder offset that frees the centre crosshair. */
    public static final float DEFAULT_SHOULDER_OFFSET = 0.65f;

    /** The camera never sinks closer than this to the terrain surface. */
    public static final float TERRAIN_CLEARANCE = 0.35f;

    /** Samples used to sweep the camera boom against obstacles. */
    public static final int BOOM_STEPS = 16;

    /** March step of the crosshair ray in world units. */
    public static final float AIM_STEP = 0.5f;

    /** Bisection passes that refine the crosshair ray hit. */
    private static final int AIM_REFINE_STEPS = 8;

    /** Ground height sampler of the procedural heightfield. */
    @FunctionalInterface
    public interface TerrainSampler {
        float height(float x, float z);
    }

    /** Point test of solid scenery (fences, torii, lanterns, temple). */
    @FunctionalInterface
    public interface ObstacleQuery {
        boolean isBlocked(float x, float y, float z);
    }

    private float yawDegrees;
    private float pitchDegrees;
    private float sensitivity = DEFAULT_SENSITIVITY;
    private float distance = DEFAULT_DISTANCE;
    private float heightOffset = DEFAULT_HEIGHT_OFFSET;
    private float shoulderOffset = DEFAULT_SHOULDER_OFFSET;

    private final Vector3 forward = new Vector3();
    private final Vector2 groundForward = new Vector2(0f, 1f);
    private final Vector2 groundRight = new Vector2(1f, 0f);
    private final Vector3 cameraPosition = new Vector3();

    /** Resets the view to look north with a level horizon. */
    public void reset() {
        yawDegrees = 0f;
        pitchDegrees = 0f;
    }

    /**
     * Applies one frame of mouse movement.
     *
     * @param deltaX horizontal mouse delta in pixels (right is positive)
     * @param deltaY vertical mouse delta in pixels (down is positive)
     */
    public void applyLook(float deltaX, float deltaY) {
        yawDegrees = wrapDegrees(yawDegrees - deltaX * sensitivity);
        // Screen Y grows downward, so moving the mouse up must look up.
        pitchDegrees = MathUtils.clamp(
            pitchDegrees - deltaY * sensitivity, -MAX_PITCH_DEGREES, MAX_PITCH_DEGREES);
    }

    /** Current yaw in degrees in {@code (-180, 180]}. */
    public float getYawDegrees() {
        return yawDegrees;
    }

    /** Current pitch in degrees, positive looking up. */
    public float getPitchDegrees() {
        return pitchDegrees;
    }

    public void setYawDegrees(float yawDegrees) {
        this.yawDegrees = wrapDegrees(yawDegrees);
    }

    public void setPitchDegrees(float pitchDegrees) {
        this.pitchDegrees = MathUtils.clamp(
            pitchDegrees, -MAX_PITCH_DEGREES, MAX_PITCH_DEGREES);
    }

    public void setSensitivity(float sensitivity) {
        this.sensitivity = Math.max(0.001f, sensitivity);
    }

    public float getSensitivity() {
        return sensitivity;
    }

    /** Orbit distance in world units. */
    public float getDistance() {
        return distance;
    }

    public void setDistance(float distance) {
        this.distance = MathUtils.clamp(distance, MIN_DISTANCE, 20f);
    }

    /** Height of the shoulder pivot above the player's feet. */
    public float getHeightOffset() {
        return heightOffset;
    }

    public void setHeightOffset(float heightOffset) {
        this.heightOffset = MathUtils.clamp(heightOffset, 0.5f, 4f);
    }

    /** Right-shoulder boom offset in world units. */
    public float getShoulderOffset() {
        return shoulderOffset;
    }

    public void setShoulderOffset(float shoulderOffset) {
        this.shoulderOffset = MathUtils.clamp(shoulderOffset, -2f, 2f);
    }

    /** True view direction including pitch, written into {@code out}. */
    public Vector3 getForward(Vector3 out) {
        float yaw = yawDegrees * MathUtils.degreesToRadians;
        float pitch = pitchDegrees * MathUtils.degreesToRadians;
        float cosPitch = MathUtils.cos(pitch);
        return out.set(
            -MathUtils.sin(yaw) * cosPitch,
            MathUtils.sin(pitch),
            -MathUtils.cos(yaw) * cosPitch);
    }

    /** Ground-plane forward direction in 2D ASCII world coordinates. */
    public Vector2 getGroundForward(Vector2 out) {
        float yaw = yawDegrees * MathUtils.degreesToRadians;
        return out.set(-MathUtils.sin(yaw), MathUtils.cos(yaw));
    }

    /** Ground-plane right direction in 2D ASCII world coordinates. */
    public Vector2 getGroundRight(Vector2 out) {
        float yaw = yawDegrees * MathUtils.degreesToRadians;
        return out.set(MathUtils.cos(yaw), MathUtils.sin(yaw));
    }

    /**
     * Converts WASD axes into a unit 2D world direction relative to the view.
     *
     * @param axisX {@code +1} = D/right, {@code -1} = A/left
     * @param axisY {@code +1} = W/forward, {@code -1} = S/backward
     * @param out   receives the normalized direction, or zero without input
     */
    public Vector2 moveDirection(float axisX, float axisY, Vector2 out) {
        getGroundForward(groundForward);
        getGroundRight(groundRight);

        out.set(groundRight.x * axisX + groundForward.x * axisY,
            groundRight.y * axisX + groundForward.y * axisY);

        float length2 = out.len2();
        if (length2 > 1f) {
            out.scl(1f / (float) Math.sqrt(length2));
        }
        return out;
    }

    /**
     * Shoulder pivot of the rig: the player's feet plus
     * {@link #getHeightOffset()} world units.
     */
    public Vector3 getTarget(float playerX, float playerGroundY, float playerZ, Vector3 out) {
        if (out == null) {
            throw new IllegalArgumentException("out must not be null");
        }
        return out.set(playerX, playerGroundY + heightOffset, playerZ);
    }

    /**
     * Collision-clamped camera position of the orbit boom.
     *
     * <p>The boom starts at the shoulder pivot, sweeps towards the ideal
     * orbit position (behind the view direction plus the right-shoulder
     * offset) and stops before the first sample blocked by scenery or by
     * ground that rises above the pivot. The result is finally lifted above
     * the terrain so an upward look never buries the camera.</p>
     */
    public Vector3 computeCameraPosition(Vector3 target, TerrainSampler terrain,
                                         ObstacleQuery obstacles, Vector3 out) {
        if (target == null) {
            throw new IllegalArgumentException("target must not be null");
        }
        if (terrain == null) {
            throw new IllegalArgumentException("terrain must not be null");
        }
        if (obstacles == null) {
            throw new IllegalArgumentException("obstacles must not be null");
        }
        if (out == null) {
            throw new IllegalArgumentException("out must not be null");
        }

        getForward(forward);
        float yaw = yawDegrees * MathUtils.degreesToRadians;
        float rightX = MathUtils.cos(yaw);
        float rightZ = -MathUtils.sin(yaw);

        // Ideal orbit: backwards along the view ray, pushed onto the shoulder.
        float boomX = -forward.x * distance + rightX * shoulderOffset;
        float boomY = -forward.y * distance;
        float boomZ = -forward.z * distance + rightZ * shoulderOffset;
        float boomLength = (float) Math.sqrt(boomX * boomX + boomY * boomY + boomZ * boomZ);
        if (boomLength <= 0.0001f) {
            return out.set(target);
        }

        float safeT = 1f;
        for (int step = 1; step <= BOOM_STEPS; step++) {
            float t = step / (float) BOOM_STEPS;
            float x = target.x + boomX * t;
            float y = target.y + boomY * t;
            float z = target.z + boomZ * t;
            if (obstacles.isBlocked(x, y, z) || terrain.height(x, z) > target.y) {
                safeT = (step - 1) / (float) BOOM_STEPS;
                break;
            }
        }

        // Never collapse fully into the pivot: keep a readable minimum boom.
        float minT = Math.min(1f, MIN_DISTANCE / boomLength);
        float t = Math.max(minT, safeT);
        out.set(target.x + boomX * t, target.y + boomY * t, target.z + boomZ * t);

        float floor = terrain.height(out.x, out.z) + TERRAIN_CLEARANCE;
        if (out.y < floor) {
            out.y = floor;
        }
        return out;
    }

    /**
     * Places the perspective camera at the collision-clamped boom position and
     * orients it along the current look angles.
     */
    public void applyTo(PerspectiveCamera camera, Vector3 target,
                        TerrainSampler terrain, ObstacleQuery obstacles) {
        if (camera == null) {
            throw new IllegalArgumentException("camera must not be null");
        }
        computeCameraPosition(target, terrain, obstacles, cameraPosition);
        camera.position.set(cameraPosition);
        camera.direction.set(getForward(forward));
        camera.up.set(Vector3.Y);
        camera.update();
    }

    /**
     * Marches the crosshair ray into the world and writes the first terrain or
     * scenery hit into {@code out}.
     *
     * @param origin      ray origin (camera position) in world units
     * @param direction   unit ray direction (the camera forward)
     * @param terrain     ground height sampler
     * @param obstacles   solid scenery test
     * @param maxDistance furthest distance the crosshair resolves to
     * @param out         receives the world point under the crosshair
     * @return true when the ray hit terrain or scenery, false for a pure sky ray
     */
    public boolean resolveAimPoint(Vector3 origin, Vector3 direction, TerrainSampler terrain,
                                   ObstacleQuery obstacles, float maxDistance, Vector3 out) {
        if (origin == null || direction == null) {
            throw new IllegalArgumentException("origin and direction must not be null");
        }
        if (terrain == null || obstacles == null) {
            throw new IllegalArgumentException("terrain and obstacles must not be null");
        }
        if (out == null) {
            throw new IllegalArgumentException("out must not be null");
        }
        if (!(maxDistance > 0f)) {
            throw new IllegalArgumentException("maxDistance must be > 0 but was " + maxDistance);
        }

        float previousT = 0f;
        float t = 0f;
        while (t < maxDistance) {
            t = Math.min(maxDistance, t + AIM_STEP);
            float x = origin.x + direction.x * t;
            float y = origin.y + direction.y * t;
            float z = origin.z + direction.z * t;
            if (y <= terrain.height(x, z) || obstacles.isBlocked(x, y, z)) {
                float low = previousT;
                float high = t;
                for (int i = 0; i < AIM_REFINE_STEPS; i++) {
                    float mid = (low + high) * 0.5f;
                    float mx = origin.x + direction.x * mid;
                    float my = origin.y + direction.y * mid;
                    float mz = origin.z + direction.z * mid;
                    if (my <= terrain.height(mx, mz) || obstacles.isBlocked(mx, my, mz)) {
                        high = mid;
                    } else {
                        low = mid;
                    }
                }
                setOnRay(origin, direction, high, out);
                return true;
            }
            previousT = t;
        }
        setOnRay(origin, direction, maxDistance, out);
        return false;
    }

    private static void setOnRay(Vector3 origin, Vector3 direction, float rayDistance,
                                 Vector3 out) {
        out.set(origin.x + direction.x * rayDistance,
            origin.y + direction.y * rayDistance,
            origin.z + direction.z * rayDistance);
    }

    /** Wraps an angle into {@code (-180, 180]}. */
    public static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360f;
        if (wrapped <= -180f) {
            wrapped += 360f;
        } else if (wrapped > 180f) {
            wrapped -= 360f;
        }
        return wrapped;
    }
}
