package com.lychcs.koikoi.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

/**
 * Procedural Y2K / anime streetwear player avatar made only of code-defined
 * 3D boxes. No external model or texture is needed: the parts go through the
 * world cel shader and the normal/depth edge prepass, so the character gets the
 * same painted outlines as the scenery.
 *
 * <p>Rig: sage/teal ponytail with spring physics, cream crop top, slate cargo
 * pants, white sneakers and a small gold belt accent. Animation is procedural:
 * a sinusoidal walk bounce with alternating leg/arm swing, a smoothed yaw
 * toward the movement/aim direction, a sharp dash lean, a two-frame ghost
 * trail, and five fluttering Ofuda paper talismans along the dash arc.</p>
 *
 * <p>{@link #update} is pure CPU state and safe to unit test; {@link #render}
 * queues boxes into {@link HeightmapTerrainRenderer}.</p>
 */
public final class PlayerAvatarRenderer {

    /** Yaw approach rates in radians per second. */
    public static final float TURN_RATE_IDLE = 8f;
    public static final float TURN_RATE_MOVE = 13f;
    public static final float TURN_RATE_DASH = 24f;

    /** Exponential rates of the dash lean (rise is sharp, release is softer). */
    public static final float DASH_LEAN_RISE = 30f;
    public static final float DASH_LEAN_FALL = 8.5f;

    /** Walk cycle: phase multiplier, vertical bounce and leg swing amplitude. */
    public static final float WALK_RATE = 9f;
    public static final float WALK_BOUNCE = 0.040f;
    public static final float WALK_LEG_SWING = 0.55f;

    /** Ponytail spring constants (subtle trailing lag, slightly under-damped). */
    public static final float TAIL_STIFFNESS = 55f;
    public static final float TAIL_DAMPING = 11f;

    /** Number of recorded dash poses (the requested two-frame ghost trail). */
    public static final int GHOST_COUNT = 2;

    /** Forward-somersault pivot height and mid-roll crouch depth. */
    public static final float TUMBLE_PIVOT_HEIGHT = 0.95f;
    public static final float TUMBLE_DIP = 0.20f;

    // Grounded Y2K streetwear palette.
    private static final Color SKIN = new Color(0.88f, 0.72f, 0.60f, 1f);
    private static final Color HAIR = new Color(0.34f, 0.64f, 0.56f, 1f);
    private static final Color HAIR_DARK = new Color(0.22f, 0.45f, 0.40f, 1f);
    private static final Color CREAM = new Color(0.93f, 0.88f, 0.72f, 1f);
    private static final Color SLATE = new Color(0.30f, 0.33f, 0.40f, 1f);
    private static final Color SLATE_DARK = new Color(0.20f, 0.22f, 0.28f, 1f);
    private static final Color SNEAKER = new Color(0.94f, 0.96f, 0.93f, 1f);
    private static final Color SOLE = new Color(0.14f, 0.16f, 0.20f, 1f);
    private static final Color GOLD = new Color(0.86f, 0.70f, 0.34f, 1f);
    private static final Color SHADOW = new Color(0.035f, 0.055f, 0.040f, 1f);
    private static final Color GHOST_NEAR = new Color(0.62f, 0.78f, 0.74f, 1f);
    private static final Color GHOST_FAR = new Color(0.42f, 0.60f, 0.58f, 1f);
    private static final Color OFUDA_INK = new Color(0.78f, 0.18f, 0.14f, 1f);

    /** Snapshot of one avatar pose for the dash ghost trail. */
    static final class Pose {
        float x;
        float z;
        float groundY;
        float yaw;
        float leanPitch;
        float leanRoll;
        float bounce;
        float tumble;
        float dip;

        void set(float x, float z, float groundY, float yaw,
                 float leanPitch, float leanRoll, float bounce,
                 float tumble, float dip) {
            this.x = x;
            this.z = z;
            this.groundY = groundY;
            this.yaw = yaw;
            this.leanPitch = leanPitch;
            this.leanRoll = leanRoll;
            this.bounce = bounce;
            this.tumble = tumble;
            this.dip = dip;
        }

        void set(Pose other) {
            set(other.x, other.z, other.groundY, other.yaw,
                other.leanPitch, other.leanRoll, other.bounce,
                other.tumble, other.dip);
        }
    }

    private final Pose[] ghosts = {new Pose(), new Pose()};
    private final Pose lastPose = new Pose();
    private int ghostFrames;
    private boolean ghostTrail;
    private boolean wasDashing;
    private boolean initialized;

    private float yaw;
    private float dashLean;
    private float leanPitch;
    private float leanRoll;
    private float bounce;
    private float legSwing;
    private float armSwing;

    /** Full-roll tumble, mid-roll crouch and throw-arm flick state. */
    private float tumbleAngle;
    private float tumbleDip;
    private float throwFlick;
    private float torsoTwist;

    private float tailSway;
    private float tailSwayVelocity;
    private float tailPitch;
    private float tailPitchVelocity;

    /** Flutter clock for the Ofuda talismans spawned by the dash. */
    private float ofudaClock;

    private float worldX;
    private float worldZ;
    private float groundY;

    private final Vector3 root = new Vector3();
    private final Vector3 right = new Vector3(1f, 0f, 0f);
    private final Vector3 up = new Vector3(0f, 1f, 0f);
    private final Vector3 forward = new Vector3(0f, 0f, -1f);

    private final Vector3 partRight = new Vector3();
    private final Vector3 partUp = new Vector3();
    private final Vector3 partForward = new Vector3();

    private final Vector3 torsoRight = new Vector3(1f, 0f, 0f);
    private final Vector3 torsoUp = new Vector3(0f, 1f, 0f);
    private final Vector3 torsoForward = new Vector3(0f, 0f, -1f);

    /** Final draw-time tumble around the body's right axis. */
    private float renderTumble;
    private final Vector3 tumbleAxis = new Vector3(1f, 0f, 0f);
    private final Vector3 tumblePivot = new Vector3();
    private final Vector3 tCenter = new Vector3();
    private final Vector3 tRight = new Vector3();
    private final Vector3 tUp = new Vector3();
    private final Vector3 tForward = new Vector3();

    /** World-space right hand, refreshed by the last render (throw origin). */
    private final Vector3 handWorld = new Vector3();
    private boolean handValid;

    private final Vector3 tailRight = new Vector3();
    private final Vector3 tailUp = new Vector3();
    private final Vector3 tailForward = new Vector3();

    private final Vector3 center = new Vector3();
    private final Vector3 hip = new Vector3();
    private final Vector3 shoulder = new Vector3();
    private final Vector3 tailPivot = new Vector3();

    private final Vector3 ofudaCenter = new Vector3();
    private final Vector3 ofudaRight = new Vector3();
    private final Vector3 ofudaUp = new Vector3();

    /**
     * Advances the procedural animation.
     *
     * @param delta       frame time in seconds (clamped to 50 ms)
     * @param worldX      player root X in world units
     * @param worldZ      player root Z in world units
     * @param groundY     smoothed terrain height under the feet
     * @param faceX       desired facing X (movement, dash or camera aim)
     * @param faceZ       desired facing Z
     * @param moving      true while walking (gates the bounce)
     * @param walkPhase   walk-cycle phase in seconds
     * @param dashing     true while the dash timer is active
     * @param dashX       dash direction X (2D facing mapped to 3D)
     * @param dashZ       dash direction Z
     * @param velocityX   body velocity X in world units per second
     * @param velocityZ   body velocity Z in world units per second
     * @param dashProgress dash completion 0..1 (0 when not dashing)
     * @param throwProgress throw animation completion 0..1 (0 when idle)
     */
    public void update(float delta, float worldX, float worldZ, float groundY,
                       float faceX, float faceZ, boolean moving, float walkPhase,
                       boolean dashing, float dashX, float dashZ,
                       float velocityX, float velocityZ,
                       float dashProgress, float throwProgress) {
        float dt = MathUtils.clamp(delta, 0f, 0.05f);
        this.worldX = worldX;
        this.worldZ = worldZ;
        this.groundY = groundY;
        ofudaClock += dt;

        // Smoothly face the movement/dash direction; idle faces the aim.
        float desiredYaw = (float) Math.atan2(-faceX, -faceZ);
        if (!initialized) {
            yaw = wrapAngle(desiredYaw);
            initialized = true;
        }
        float turnRate = dashing ? TURN_RATE_DASH : (moving ? TURN_RATE_MOVE : TURN_RATE_IDLE);
        yaw = stepAngle(yaw, desiredYaw, turnRate * dt);

        // Sine walk: vertical bounce plus alternating legs and counter-swing arms.
        float targetBounce = moving
            ? MathUtils.sin(walkPhase * WALK_RATE) * WALK_BOUNCE : 0f;
        float targetLeg = moving
            ? MathUtils.sin(walkPhase * WALK_RATE) * WALK_LEG_SWING : 0f;
        float smooth = 1f - (float) Math.exp(-dt * 18f);
        bounce = MathUtils.lerp(bounce, targetBounce, smooth);
        legSwing = MathUtils.lerp(legSwing, targetLeg, smooth);
        armSwing = -legSwing * 0.7f;

        // Sharp dash lean: forward pitch plus a roll into the dash direction.
        float leanRate = dashing ? DASH_LEAN_RISE : DASH_LEAN_FALL;
        dashLean += ((dashing ? 1f : 0f) - dashLean)
            * (1f - (float) Math.exp(-leanRate * dt));

        // Full 360-degree tumble with a mid-roll crouch, plus the snappy throw
        // flick/torso twist. Both are driven by the component timers.
        float rollProgress = MathUtils.clamp(dashProgress, 0f, 1f);
        float easedRoll = rollProgress * rollProgress * (3f - 2f * rollProgress);
        tumbleAngle = easedRoll * MathUtils.PI2;
        tumbleDip = (float) Math.sin(rollProgress * Math.PI) * TUMBLE_DIP;
        float flickProgress = MathUtils.clamp(throwProgress, 0f, 1f);
        throwFlick = (float) Math.sin(flickProgress * Math.PI);
        torsoTwist = throwFlick * 0.30f;

        setBasisFromYaw(yaw);
        float lateralDash = dashX * right.x + dashZ * right.z;
        float lateralVelocity = velocityX * right.x + velocityZ * right.z;
        float speed = (float) Math.sqrt(velocityX * velocityX + velocityZ * velocityZ);
        // Forward weight + a subtle bank into strafes/turns (Witcher-style).
        leanPitch = dashLean * 0.30f
            + MathUtils.clamp(speed * 0.004f, 0f, 0.08f);
        float moveBank = MathUtils.clamp(-lateralVelocity * 0.012f, -0.12f, 0.12f);
        leanRoll = MathUtils.clamp(
            -lateralDash * dashLean * 0.55f + moveBank, -0.60f, 0.60f);

        // Ponytail: speed/dash pitch with a lateral velocity spring.
        float targetSway = -lateralVelocity * 0.09f - lateralDash * dashLean * 0.25f;
        float targetPitch = MathUtils.clamp(
            speed * 0.035f + dashLean * 0.75f, 0f, 1.0f);
        integrateTail(dt, targetSway, targetPitch);

        // Two-frame ghost trail along the dash path. The current pose is parked
        // in lastPose first, so ghosts[] only ever contain *previous* frames and
        // never z-fight with the live avatar at dash start.
        if (dashing) {
            if (!wasDashing) {
                lastPose.set(worldX, worldZ, groundY, yaw, leanPitch, leanRoll, bounce,
                    tumbleAngle, tumbleDip);
                ghostFrames = 0;
                ghostTrail = false;
            } else {
                ghosts[1].set(ghosts[0]);
                ghosts[0].set(lastPose);
                lastPose.set(worldX, worldZ, groundY, yaw, leanPitch, leanRoll, bounce,
                    tumbleAngle, tumbleDip);
                ghostFrames = Math.min(GHOST_COUNT, ghostFrames + 1);
                ghostTrail = ghostFrames > 0;
            }
        } else if (dashLean < 0.06f) {
            ghostTrail = false;
        }
        wasDashing = dashing;
    }

    // ------------------------------------------------------------------
    // Procedural basis math (no GL, fully unit-testable)
    // ------------------------------------------------------------------

    /** Builds the yaw basis: forward is the facing direction, right is east of it. */
    private void setBasisFromYaw(float yawValue) {
        float sin = MathUtils.sin(yawValue);
        float cos = MathUtils.cos(yawValue);
        forward.set(-sin, 0f, -cos);
        right.set(cos, 0f, -sin);
        up.set(0f, 1f, 0f);
    }

    /** Rotates up/forward around the avatar's right axis (forward/back pitch). */
    private void rotateAroundRight(float angle) {
        float cos = MathUtils.cos(angle);
        float sin = MathUtils.sin(angle);
        float ux = up.x;
        float uy = up.y;
        float uz = up.z;
        float fx = forward.x;
        float fy = forward.y;
        float fz = forward.z;
        up.set(ux * cos + fx * sin, uy * cos + fy * sin, uz * cos + fz * sin);
        forward.set(fx * cos - ux * sin, fy * cos - uy * sin, fz * cos - uz * sin);
    }

    /** Rotates right/up around the avatar's forward axis (side roll). */
    private void rotateAroundForward(float angle) {
        float cos = MathUtils.cos(angle);
        float sin = MathUtils.sin(angle);
        float rx = right.x;
        float ry = right.y;
        float rz = right.z;
        float ux = up.x;
        float uy = up.y;
        float uz = up.z;
        right.set(rx * cos + ux * sin, ry * cos + uy * sin, rz * cos + uz * sin);
        up.set(ux * cos - rx * sin, uy * cos - ry * sin, uz * cos - rz * sin);
    }

    /** Upper-body basis twisted around the avatar's up axis for the throw. */
    private void setTorsoBasis() {
        if (Math.abs(torsoTwist) < 0.0001f) {
            torsoRight.set(right);
            torsoUp.set(up);
            torsoForward.set(forward);
            return;
        }
        float cos = MathUtils.cos(torsoTwist);
        float sin = MathUtils.sin(torsoTwist);
        torsoRight.set(right.x * cos + forward.x * sin,
            right.y * cos + forward.y * sin,
            right.z * cos + forward.z * sin);
        torsoForward.set(forward.x * cos - right.x * sin,
            forward.y * cos - right.y * sin,
            forward.z * cos - right.z * sin);
        torsoUp.set(up);
    }

    /** Arm basis: the torso basis rotated around the torso's right axis. */
    private void setArmBasisRotatedAroundRight(float angle) {
        float cos = MathUtils.cos(angle);
        float sin = MathUtils.sin(angle);
        partRight.set(torsoRight);
        partUp.set(torsoUp.x * cos + torsoForward.x * sin,
            torsoUp.y * cos + torsoForward.y * sin,
            torsoUp.z * cos + torsoForward.z * sin);
        partForward.set(torsoForward.x * cos - torsoUp.x * sin,
            torsoForward.y * cos - torsoUp.y * sin,
            torsoForward.z * cos - torsoUp.z * sin);
    }

    /** Rodrigues rotation of a vector around a unit axis, in place. */
    private static void rotateVector(Vector3 v, Vector3 axis, float angle) {
        float cos = MathUtils.cos(angle);
        float sin = MathUtils.sin(angle);
        float x = v.x;
        float y = v.y;
        float z = v.z;
        float dot = axis.x * x + axis.y * y + axis.z * z;
        float cx = axis.y * z - axis.z * y;
        float cy = axis.z * x - axis.x * z;
        float cz = axis.x * y - axis.y * x;
        v.set(x * cos + cx * sin + axis.x * dot * (1f - cos),
            y * cos + cy * sin + axis.y * dot * (1f - cos),
            z * cos + cz * sin + axis.z * dot * (1f - cos));
    }

    /** Rotates a point around the tumble pivot (used for centers and hands). */
    private void tumbledPoint(Vector3 in, Vector3 out) {
        if (Math.abs(renderTumble) < 0.0001f) {
            out.set(in);
            return;
        }
        out.set(in).sub(tumblePivot);
        rotateVector(out, tumbleAxis, renderTumble);
        out.add(tumblePivot);
    }

    /** Draws one box with the draw-time tumble applied to center and basis. */
    private void drawTumbledBox(HeightmapTerrainRenderer renderer, Vector3 boxCenter,
                                Vector3 basisRight, Vector3 basisUp, Vector3 basisForward,
                                float sx, float sy, float sz, Color color) {
        drawTumbledBox(renderer, boxCenter, basisRight, basisUp, basisForward,
            sx, sy, sz, color, false);
    }

    /** Draws one spirit-ink box for the ASCII dash glyph trail. */
    private void drawTumbledMagicBox(HeightmapTerrainRenderer renderer, Vector3 boxCenter,
                                     Vector3 basisRight, Vector3 basisUp, Vector3 basisForward,
                                     float sx, float sy, float sz, Color color) {
        drawTumbledBox(renderer, boxCenter, basisRight, basisUp, basisForward,
            sx, sy, sz, color, true);
    }

    private void drawTumbledBox(HeightmapTerrainRenderer renderer, Vector3 boxCenter,
                                Vector3 basisRight, Vector3 basisUp, Vector3 basisForward,
                                float sx, float sy, float sz, Color color, boolean magic) {
        if (Math.abs(renderTumble) < 0.0001f) {
            if (magic) {
                renderer.drawMagicBox(boxCenter, basisRight, basisUp, basisForward,
                    sx, sy, sz, color);
            } else {
                renderer.drawBox(boxCenter, basisRight, basisUp, basisForward,
                    sx, sy, sz, color);
            }
            return;
        }
        tumbledPoint(boxCenter, tCenter);
        tRight.set(basisRight);
        tUp.set(basisUp);
        tForward.set(basisForward);
        rotateVector(tRight, tumbleAxis, renderTumble);
        rotateVector(tUp, tumbleAxis, renderTumble);
        rotateVector(tForward, tumbleAxis, renderTumble);
        if (magic) {
            renderer.drawMagicBox(tCenter, tRight, tUp, tForward, sx, sy, sz, color);
        } else {
            renderer.drawBox(tCenter, tRight, tUp, tForward, sx, sy, sz, color);
        }
    }

    /** Local limb basis rotated around the avatar's right axis (leg swing). */
    private void setPartBasisRotatedAroundRight(float angle) {
        float cos = MathUtils.cos(angle);
        float sin = MathUtils.sin(angle);
        partRight.set(right);
        partUp.set(up.x * cos + forward.x * sin,
            up.y * cos + forward.y * sin,
            up.z * cos + forward.z * sin);
        partForward.set(forward.x * cos - up.x * sin,
            forward.y * cos - up.y * sin,
            forward.z * cos - up.z * sin);
    }

    /** Tail basis: trailing pitch around right, then lateral sway around forward. */
    private void setTailBasis() {
        tailRight.set(torsoRight);
        tailUp.set(torsoUp);
        tailForward.set(torsoForward);

        float pitchCos = MathUtils.cos(tailPitch);
        float pitchSin = MathUtils.sin(tailPitch);
        float ux = tailUp.x;
        float uy = tailUp.y;
        float uz = tailUp.z;
        float fx = tailForward.x;
        float fy = tailForward.y;
        float fz = tailForward.z;
        tailUp.set(ux * pitchCos + fx * pitchSin,
            uy * pitchCos + fy * pitchSin,
            uz * pitchCos + fz * pitchSin);
        tailForward.set(fx * pitchCos - ux * pitchSin,
            fy * pitchCos - uy * pitchSin,
            fz * pitchCos - uz * pitchSin);

        float swayCos = MathUtils.cos(tailSway);
        float swaySin = MathUtils.sin(tailSway);
        float rx = tailRight.x;
        float ry = tailRight.y;
        float rz = tailRight.z;
        float tx = tailUp.x;
        float ty = tailUp.y;
        float tz = tailUp.z;
        tailRight.set(rx * swayCos + tx * swaySin,
            ry * swayCos + ty * swaySin,
            rz * swayCos + tz * swaySin);
        tailUp.set(tx * swayCos - rx * swaySin,
            ty * swayCos - ry * swaySin,
            tz * swayCos - rz * swaySin);
    }

    /** Critically-ish damped spring for the ponytail sway/pitch. */
    private void integrateTail(float dt, float targetSway, float targetPitch) {
        tailSwayVelocity += ((targetSway - tailSway) * TAIL_STIFFNESS
            - tailSwayVelocity * TAIL_DAMPING) * dt;
        tailSway = MathUtils.clamp(tailSway + tailSwayVelocity * dt, -1.15f, 1.15f);

        tailPitchVelocity += ((targetPitch - tailPitch) * TAIL_STIFFNESS
            - tailPitchVelocity * TAIL_DAMPING) * dt;
        tailPitch = MathUtils.clamp(tailPitch + tailPitchVelocity * dt, -0.25f, 1.25f);
    }

    /** Wraps radians into {@code (-PI, PI]}. */
    static float wrapAngle(float radians) {
        float wrapped = radians % MathUtils.PI2;
        if (wrapped <= -MathUtils.PI) {
            wrapped += MathUtils.PI2;
        } else if (wrapped > MathUtils.PI) {
            wrapped -= MathUtils.PI2;
        }
        return wrapped;
    }

    /** Shortest signed angle from {@code from} to {@code to}. */
    static float shortestAngleDelta(float from, float to) {
        return wrapAngle(to - from);
    }

    /** Moves {@code from} toward {@code to} by at most {@code maxStep} radians. */
    static float stepAngle(float from, float to, float maxStep) {
        float diff = shortestAngleDelta(from, to);
        if (Math.abs(diff) <= maxStep) {
            return wrapAngle(to);
        }
        return wrapAngle(from + Math.signum(diff) * maxStep);
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    /** Queues the two-frame dash ghost trail (if any) and the current avatar. */
    public void render(HeightmapTerrainRenderer renderer) {
        if (renderer == null) {
            return;
        }
        renderer.drawCrispGroundQuad(worldX, worldZ, 0.42f, SHADOW);

        if (ghostTrail) {
            if (ghostFrames >= 2) {
                drawGhost(renderer, ghosts[1], GHOST_FAR);
            }
            if (ghostFrames >= 1) {
                drawGhost(renderer, ghosts[0], GHOST_NEAR);
            }
            renderOfudaTalismans(renderer);
        }

        setBasisFromYaw(yaw);
        rotateAroundForward(leanRoll);
        rotateAroundRight(leanPitch);
        setTorsoBasis();
        renderTumble = tumbleAngle;
        tumbleAxis.set(right);
        float visualY = groundY + bounce - tumbleDip;
        root.set(worldX, visualY, worldZ);
        tumblePivot.set(worldX, visualY + TUMBLE_PIVOT_HEIGHT, worldZ);
        drawBody(renderer);
    }

    /**
     * Scatters five Ofuda paper talismans along the dash arc. The strips flutter
     * around their own axes and use the procedural parchment/red-kanji shader.
     */
    private void renderOfudaTalismans(HeightmapTerrainRenderer renderer) {
        if (ghostFrames < 1) {
            return;
        }
        Pose tail = ghostFrames >= 2 ? ghosts[1] : ghosts[0];
        float dx = worldX - tail.x;
        float dz = worldZ - tail.z;
        float length = (float) Math.sqrt(dx * dx + dz * dz);
        if (length < 0.0001f) {
            dx = forward.x;
            dz = forward.z;
            length = (float) Math.sqrt(dx * dx + dz * dz);
        }
        if (length < 0.0001f) {
            return;
        }
        dx /= length;
        dz /= length;
        float sideX = -dz;
        float sideZ = dx;

        for (int i = 0; i < 5; i++) {
            float t = (i + 1) / 6f;
            float lateral = (i % 2 == 0 ? 1f : -1f)
                * (0.11f + 0.035f * MathUtils.sin(ofudaClock * 3.1f + i));
            float x = MathUtils.lerp(tail.x, worldX, t * 0.88f) + sideX * lateral;
            float z = MathUtils.lerp(tail.z, worldZ, t * 0.88f) + sideZ * lateral;
            float ground = MathUtils.lerp(tail.groundY, groundY, t * 0.88f);

            float flutter = MathUtils.sin(ofudaClock * 8.5f + i * 1.7f);
            float tilt = flutter * 0.55f + lateral * 1.4f;
            float cos = MathUtils.cos(tilt);
            float sin = MathUtils.sin(tilt);
            ofudaRight.set(sideX * cos, 0.16f * flutter, sideZ * cos);
            ofudaRight.nor();
            ofudaUp.set(-sideX * 0.28f * flutter, 1f, -sideZ * 0.28f * flutter);
            ofudaUp.nor();
            ofudaCenter.set(x, ground + 0.28f + 0.14f * Math.abs(flutter)
                + 0.04f * sin, z);
            renderer.drawOfuda(ofudaCenter, ofudaRight, ofudaUp,
                0.105f, 0.30f, OFUDA_INK);
        }
    }

    /** Simplified solid afterimage of one recorded dash pose. */
    private void drawGhost(HeightmapTerrainRenderer renderer, Pose pose, Color color) {
        setBasisFromYaw(pose.yaw);
        rotateAroundForward(pose.leanRoll);
        rotateAroundRight(pose.leanPitch);
        renderTumble = pose.tumble;
        tumbleAxis.set(right);
        float visualY = pose.groundY + pose.bounce - pose.dip;
        root.set(pose.x, visualY, pose.z);
        tumblePivot.set(pose.x, visualY + TUMBLE_PIVOT_HEIGHT, pose.z);

        magicBox(renderer, 0f, 0.44f, 0f, 0.34f, 0.70f, 0.22f, color);
        magicBox(renderer, 0f, 1.08f, 0f, 0.36f, 0.46f, 0.26f, color);
        magicBox(renderer, 0f, 1.48f, 0f, 0.26f, 0.26f, 0.24f, color);
        magicBox(renderer, 0f, 1.42f, -0.20f, 0.10f, 0.32f, 0.10f, color);
    }

    /** The current avatar: torso, hair, ponytail, legs and arms. */
    private void drawBody(HeightmapTerrainRenderer renderer) {
        // Slate cargo waist with a near-black belt and a tiny Y2K gold buckle.
        box(renderer, 0f, 0.80f, 0f, 0.40f, 0.22f, 0.28f, SLATE_DARK);
        box(renderer, 0f, 0.91f, 0f, 0.42f, 0.05f, 0.30f, SOLE);
        box(renderer, 0f, 0.91f, 0.155f, 0.08f, 0.06f, 0.03f, GOLD);

        // Midriff and cream crop top (upper body follows the torso twist).
        boxTorso(renderer, 0f, 1.00f, 0f, 0.30f, 0.14f, 0.23f, SKIN);
        boxTorso(renderer, 0f, 1.17f, 0f, 0.36f, 0.28f, 0.26f, CREAM);

        // Neck, head and sage/teal hair shell.
        boxTorso(renderer, 0f, 1.34f, 0f, 0.10f, 0.08f, 0.10f, SKIN);
        boxTorso(renderer, 0f, 1.48f, 0.01f, 0.26f, 0.26f, 0.24f, SKIN);
        boxTorso(renderer, 0f, 1.58f, -0.03f, 0.30f, 0.16f, 0.28f, HAIR);
        boxTorso(renderer, 0f, 1.52f, 0.12f, 0.28f, 0.12f, 0.05f, HAIR_DARK);
        boxTorso(renderer, -0.15f, 1.44f, -0.02f, 0.05f, 0.24f, 0.22f, HAIR_DARK);
        boxTorso(renderer, 0.15f, 1.44f, -0.02f, 0.05f, 0.24f, 0.22f, HAIR_DARK);

        drawPonytail(renderer);

        // Wide cargo legs with white sneakers; alternating sine swing.
        drawLeg(renderer, -0.11f, legSwing);
        drawLeg(renderer, 0.11f, -legSwing);

        // Arms counter-swing the legs; the right arm snaps forward on a throw.
        drawArm(renderer, -0.235f, -legSwing * 0.7f);
        drawArm(renderer, 0.235f, legSwing * 0.7f - throwFlick * 1.9f);
    }

    /** Two-segment sage ponytail with a gold tie. */
    private void drawPonytail(HeightmapTerrainRenderer renderer) {
        tailPivot.set(
            root.x + torsoUp.x * 1.51f + torsoForward.x * -0.155f,
            root.y + torsoUp.y * 1.51f + torsoForward.y * -0.155f,
            root.z + torsoUp.z * 1.51f + torsoForward.z * -0.155f);
        boxTorso(renderer, 0f, 1.51f, -0.155f, 0.06f, 0.06f, 0.06f, GOLD);

        setTailBasis();
        boxFrom(renderer, tailPivot, tailRight, tailUp, tailForward,
            0f, -0.12f, 0f, 0.10f, 0.22f, 0.10f, HAIR);
        boxFrom(renderer, tailPivot, tailRight, tailUp, tailForward,
            0.01f, -0.31f, 0f, 0.08f, 0.18f, 0.08f, HAIR_DARK);
    }

    /** One cargo leg and its white sneaker, rotated around the hip. */
    private void drawLeg(HeightmapTerrainRenderer renderer, float localX, float swing) {
        setPartBasisRotatedAroundRight(swing);
        hip.set(
            root.x + right.x * localX + up.x * 0.78f,
            root.y + up.y * 0.78f,
            root.z + right.z * localX + up.z * 0.78f);
        boxFrom(renderer, hip, partRight, partUp, partForward,
            0f, -0.31f, 0f, 0.17f, 0.62f, 0.21f, SLATE);
        boxFrom(renderer, hip, partRight, partUp, partForward,
            0f, -0.68f, 0.03f, 0.19f, 0.12f, 0.28f, SNEAKER);
        boxFrom(renderer, hip, partRight, partUp, partForward,
            0f, -0.755f, 0.03f, 0.20f, 0.05f, 0.30f, SOLE);
    }

    /** One sleeved arm and hand, rotated around the shoulder. */
    private void drawArm(HeightmapTerrainRenderer renderer, float localX, float swing) {
        setArmBasisRotatedAroundRight(swing);
        shoulder.set(
            root.x + torsoRight.x * localX + torsoUp.x * 1.27f,
            root.y + torsoUp.y * 1.27f,
            root.z + torsoRight.z * localX + torsoUp.z * 1.27f);
        boxFrom(renderer, shoulder, partRight, partUp, partForward,
            0f, -0.18f, 0f, 0.11f, 0.36f, 0.13f, CREAM);
        boxFrom(renderer, shoulder, partRight, partUp, partForward,
            0f, -0.40f, 0f, 0.09f, 0.12f, 0.11f, SKIN);
        if (localX > 0f) {
            // The hand (in the right arm's current pose) is the throw origin.
            tumbledPoint(center, handWorld);
            handValid = true;
        }
    }

    /** Queues a part in the current avatar basis. */
    private void box(HeightmapTerrainRenderer renderer,
                     float localX, float localY, float localZ,
                     float sizeX, float sizeY, float sizeZ, Color color) {
        boxFrom(renderer, root, right, up, forward,
            localX, localY, localZ, sizeX, sizeY, sizeZ, color);
    }

    /** Queues a spirit-ink part in the current avatar basis (dash ghosts). */
    private void magicBox(HeightmapTerrainRenderer renderer,
                          float localX, float localY, float localZ,
                          float sizeX, float sizeY, float sizeZ, Color color) {
        magicBoxFrom(renderer, root, right, up, forward,
            localX, localY, localZ, sizeX, sizeY, sizeZ, color);
    }

    /** Queues an upper-body part in the twisted torso basis. */
    private void boxTorso(HeightmapTerrainRenderer renderer,
                          float localX, float localY, float localZ,
                          float sizeX, float sizeY, float sizeZ, Color color) {
        boxFrom(renderer, root, torsoRight, torsoUp, torsoForward,
            localX, localY, localZ, sizeX, sizeY, sizeZ, color);
    }

    /** Queues a part relative to an arbitrary origin/basis. */
    private void boxFrom(HeightmapTerrainRenderer renderer, Vector3 origin,
                         Vector3 basisRight, Vector3 basisUp, Vector3 basisForward,
                         float localX, float localY, float localZ,
                         float sizeX, float sizeY, float sizeZ, Color color) {
        center.set(
            origin.x + basisRight.x * localX + basisUp.x * localY + basisForward.x * localZ,
            origin.y + basisRight.y * localX + basisUp.y * localY + basisForward.y * localZ,
            origin.z + basisRight.z * localX + basisUp.z * localY + basisForward.z * localZ);
        drawTumbledBox(renderer, center, basisRight, basisUp, basisForward,
            sizeX, sizeY, sizeZ, color);
    }

    /** Queues a spirit-ink part relative to an arbitrary origin/basis. */
    private void magicBoxFrom(HeightmapTerrainRenderer renderer, Vector3 origin,
                              Vector3 basisRight, Vector3 basisUp, Vector3 basisForward,
                              float localX, float localY, float localZ,
                              float sizeX, float sizeY, float sizeZ, Color color) {
        center.set(
            origin.x + basisRight.x * localX + basisUp.x * localY + basisForward.x * localZ,
            origin.y + basisRight.y * localX + basisUp.y * localY + basisForward.y * localZ,
            origin.z + basisRight.z * localX + basisUp.z * localY + basisForward.z * localZ);
        drawTumbledMagicBox(renderer, center, basisRight, basisUp, basisForward,
            sizeX, sizeY, sizeZ, color);
    }

    // ------------------------------------------------------------------
    // Test/inspection accessors
    // ------------------------------------------------------------------

    /** Current avatar yaw in degrees (0 = north, positive turns west). */
    public float getYawDegrees() {
        return yaw * MathUtils.radiansToDegrees;
    }

    public float getDashLean() { return dashLean; }
    public float getLeanPitch() { return leanPitch; }
    public float getLeanRoll() { return leanRoll; }
    public float getBounce() { return bounce; }
    public float getLegSwing() { return legSwing; }
    public float getArmSwing() { return armSwing; }
    public float getTailSway() { return tailSway; }
    public float getTailPitch() { return tailPitch; }
    public float getTumbleAngleDegrees() { return tumbleAngle * MathUtils.radiansToDegrees; }
    public float getTumbleDip() { return tumbleDip; }
    public float getThrowFlick() { return throwFlick; }
    public float getTorsoTwist() { return torsoTwist; }
    public boolean hasGhostTrail() { return ghostTrail; }
    /** Number of valid ghost frames currently recorded (0..{@link #GHOST_COUNT}). */
    public int getGhostCount() { return ghostFrames; }

    public float getGhostX(int index) {
        return ghosts[clampGhostIndex(index)].x;
    }

    public float getGhostZ(int index) {
        return ghosts[clampGhostIndex(index)].z;
    }

    public float getGhostYawDegrees(int index) {
        return ghosts[clampGhostIndex(index)].yaw * MathUtils.radiansToDegrees;
    }

    private static int clampGhostIndex(int index) {
        return Math.max(0, Math.min(GHOST_COUNT - 1, index));
    }

    /**
     * World position of the right hand used as the card throw origin. The value
     * comes from the last rendered pose (one frame of latency); before the first
     * render a rest-pose fallback based on the current yaw is returned.
     */
    public Vector3 getHandPosition(Vector3 out) {
        if (out == null) {
            throw new IllegalArgumentException("out must not be null");
        }
        if (handValid) {
            return out.set(handWorld);
        }
        float sin = MathUtils.sin(yaw);
        float cos = MathUtils.cos(yaw);
        return out.set(
            worldX + cos * 0.235f - sin * 0.12f,
            groundY + 0.92f,
            worldZ - sin * 0.235f - cos * 0.12f);
    }
}
