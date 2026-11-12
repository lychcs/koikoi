package com.lychcs.koikoi.render;

import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;

/**
 * First-person yaw/pitch controller of the overworld camera.
 *
 * <p>The controller owns the look angles only; it never touches the ECS or the
 * physics body. Mouse deltas rotate the view (yaw around the world Y axis,
 * pitch clamped to {@link #MAX_PITCH_DEGREES}), and WASD is converted into a
 * unit direction on the ground plane relative to the current yaw, so
 * {@code W} always walks where the player looks.</p>
 *
 * <p>World mapping: the ASCII world uses a 2D {@code (x, y)} ground plane. In
 * 3D that plane becomes {@code (x, -y, z)}: 2D north ({@code +y}) maps to 3D
 * {@code -z}, so a yaw of {@code 0} looks north by default. All helpers return
 * 2D world vectors that can be handed straight to the combat systems.</p>
 */
public final class FirstPersonCameraController {

    /** Degrees of rotation per mouse pixel. */
    public static final float DEFAULT_SENSITIVITY = 0.11f;

    /** Maximum absolute pitch; keeping it below 90 avoids a degenerate up vector. */
    public static final float MAX_PITCH_DEGREES = 85f;

    private float yawDegrees;
    private float pitchDegrees;
    private float sensitivity = DEFAULT_SENSITIVITY;

    private final Vector3 forward = new Vector3();
    private final Vector2 groundForward = new Vector2(0f, 1f);
    private final Vector2 groundRight = new Vector2(1f, 0f);

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

    public void setSensitivity(float sensitivity) {
        this.sensitivity = Math.max(0.001f, sensitivity);
    }

    public float getSensitivity() {
        return sensitivity;
    }

    public void setYawDegrees(float yawDegrees) {
        this.yawDegrees = wrapDegrees(yawDegrees);
    }

    public void setPitchDegrees(float pitchDegrees) {
        this.pitchDegrees = MathUtils.clamp(
            pitchDegrees, -MAX_PITCH_DEGREES, MAX_PITCH_DEGREES);
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
     * Places the perspective camera at the given eye position and orients it
     * along the current look angles.
     */
    public void applyTo(PerspectiveCamera camera, float eyeX, float eyeY, float eyeZ) {
        if (camera == null) {
            throw new IllegalArgumentException("camera must not be null");
        }
        camera.position.set(eyeX, eyeY, eyeZ);
        camera.direction.set(getForward(forward));
        camera.up.set(Vector3.Y);
        camera.update();
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
