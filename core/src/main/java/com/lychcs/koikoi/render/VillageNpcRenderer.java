package com.lychcs.koikoi.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

/**
 * Friendly shrine-maiden / Hanafuda merchant rig near the temple entrance.
 *
 * <p>The body is a set of solid Mokuhanga boxes, so it shares the world cel
 * shading and ink outline with the avatar, Yokai and scenery. Breathing is a
 * subtle sine offset; the head smoothly tracks the player when they are nearby,
 * clamped to a natural neck angle.</p>
 */
public final class VillageNpcRenderer {

    public static final float HEAD_TRACK_RADIUS = 5.5f;
    public static final float MAX_HEAD_YAW = 0.68f;
    public static final float HEAD_TURN_RATE = 7.5f;

    private static final Color SKIN = new Color(0.88f, 0.72f, 0.60f, 1f);
    private static final Color WHITE_HAORI = new Color(0.94f, 0.91f, 0.82f, 1f);
    private static final Color HAORI_SHADOW = new Color(0.76f, 0.74f, 0.66f, 1f);
    private static final Color VERMILLION = new Color(0.76f, 0.18f, 0.16f, 1f);
    private static final Color DARK_HAIR = new Color(0.12f, 0.11f, 0.13f, 1f);
    private static final Color GOLD = new Color(0.88f, 0.70f, 0.30f, 1f);
    private static final Color INK = new Color(0.08f, 0.075f, 0.09f, 1f);
    private static final Color SHADOW = new Color(0.035f, 0.045f, 0.050f, 1f);
    private static final Color CARD_RED = new Color(0.90f, 0.30f, 0.22f, 1f);

    private final Vector3 bodyRight = new Vector3(1f, 0f, 0f);
    private final Vector3 bodyUp = new Vector3(0f, 1f, 0f);
    private final Vector3 bodyForward = new Vector3(0f, 0f, -1f);
    private final Vector3 headRight = new Vector3();
    private final Vector3 headUp = new Vector3();
    private final Vector3 headForward = new Vector3();
    private final Vector3 partCenter = new Vector3();
    private final Vector3 headCenter = new Vector3();
    private final Vector3 cardCenter = new Vector3();
    private final Vector3 ofudaCenter = new Vector3();
    private final Vector3 ofudaRight = new Vector3();
    private final Vector3 ofudaUp = new Vector3();
    private final Color sealColor = new Color();

    private float headYaw;
    private boolean initialized;

    /**
     * Relative head yaw toward the player, clamped to the neck range. The
     * result is relative to the fixed body yaw.
     */
    public static float headYawTarget(float bodyYaw, float npcX, float npcZ,
                                      float playerX, float playerZ, float distance) {
        if (distance > HEAD_TRACK_RADIUS) {
            return 0f;
        }
        float desired = (float) Math.atan2(-(playerX - npcX), -(playerZ - npcZ));
        float delta = PlayerAvatarRenderer.shortestAngleDelta(bodyYaw, desired);
        return MathUtils.clamp(delta, -MAX_HEAD_YAW, MAX_HEAD_YAW);
    }

    /** Breathing offset in world units (also useful for head bobbing). */
    public static float breathOffset(float time) {
        return MathUtils.sin(time * 1.7f) * 0.018f;
    }

    /** Renders the NPC; the caller supplies the fixed body yaw. */
    public void render(HeightmapTerrainRenderer renderer,
                       float worldX, float worldZ, float ground,
                       float bodyYaw, float playerX, float playerZ,
                       float playerDistance, float delta, float time) {
        if (renderer == null) {
            return;
        }
        setBodyBasis(bodyYaw);
        float breath = breathOffset(time);

        if (!initialized) {
            headYaw = 0f;
            initialized = true;
        }
        float targetHead = headYawTarget(bodyYaw, worldX, worldZ,
            playerX, playerZ, playerDistance);
        headYaw = PlayerAvatarRenderer.stepAngle(headYaw, targetHead,
            HEAD_TURN_RATE * MathUtils.clamp(delta, 0f, 0.05f));

        renderer.drawCrispGroundQuad(worldX, worldZ, 0.34f, SHADOW);
        sealColor.set(GOLD);
        renderer.drawKamonSeal(worldX, worldZ, 0.30f, sealColor, time * 0.10f);

        // Hakama skirt and white haori.
        drawBox(renderer, worldX, ground + breath, worldZ, -0.12f, 0.26f, 0f,
            0.22f, 0.54f, 0.24f, VERMILLION);
        drawBox(renderer, worldX, ground + breath, worldZ, 0.12f, 0.26f, 0f,
            0.22f, 0.54f, 0.24f, VERMILLION);
        drawBox(renderer, worldX, ground + breath, worldZ, 0f, 0.54f, 0f,
            0.48f, 0.18f, 0.30f, VERMILLION);
        drawBox(renderer, worldX, ground + breath, worldZ, 0f, 0.95f, 0f,
            0.42f, 0.52f, 0.26f, WHITE_HAORI);
        drawBox(renderer, worldX, ground + breath, worldZ, 0f, 0.98f, 0.14f,
            0.10f, 0.44f, 0.025f, VERMILLION);
        drawBox(renderer, worldX, ground + breath, worldZ, 0f, 1.18f, 0f,
            0.50f, 0.10f, 0.32f, HAORI_SHADOW);

        // Sleeves and hands.
        drawBox(renderer, worldX, ground + breath, worldZ, -0.29f, 0.97f, 0f,
            0.16f, 0.38f, 0.24f, WHITE_HAORI);
        drawBox(renderer, worldX, ground + breath, worldZ, 0.29f, 0.97f, 0f,
            0.16f, 0.38f, 0.24f, WHITE_HAORI);
        drawBox(renderer, worldX, ground + breath, worldZ, -0.30f, 0.76f, 0.08f,
            0.12f, 0.12f, 0.12f, SKIN);
        drawBox(renderer, worldX, ground + breath, worldZ, 0.30f, 0.76f, 0.08f,
            0.12f, 0.12f, 0.12f, SKIN);

    // Head follows the player around the neck, while the body stays put.
        setHeadBasis(bodyYaw, headYaw);
        float headY = ground + 1.36f + breath;
        headCenter.set(worldX, headY, worldZ);
        drawHeadBox(renderer, headCenter, 0f, 0f, 0f,
            0.28f, 0.28f, 0.26f, SKIN);
        drawHeadBox(renderer, headCenter, 0f, 0.13f, -0.07f,
            0.30f, 0.13f, 0.24f, DARK_HAIR);
        drawHeadBox(renderer, headCenter, 0f, 0.04f, -0.16f,
            0.24f, 0.26f, 0.10f, DARK_HAIR);
        drawHeadBox(renderer, headCenter, -0.16f, 0.08f, -0.03f,
            0.045f, 0.18f, 0.045f, GOLD);
        drawHeadBox(renderer, headCenter, 0.08f, 0.02f, 0.135f,
            0.035f, 0.035f, 0.015f, INK);
        drawHeadBox(renderer, headCenter, -0.08f, 0.02f, 0.135f,
            0.035f, 0.035f, 0.015f, INK);

        // A Hanafuda card held in the right hand.
        cardCenter.set(worldX, ground + 1.00f + breath, worldZ)
            .mulAdd(bodyRight, 0.30f)
            .mulAdd(bodyForward, 0.23f);
        renderer.drawHanafudaCard(cardCenter, bodyRight, bodyUp,
            0.15f, 0.22f, CARD_RED);

        // Small paper talisman accent at the belt.
        ofudaCenter.set(worldX, ground + 0.70f + breath, worldZ)
            .mulAdd(bodyForward, 0.17f)
            .mulAdd(bodyRight, -0.18f);
        ofudaRight.set(bodyRight);
        ofudaUp.set(bodyUp);
        renderer.drawOfuda(ofudaCenter, ofudaRight, ofudaUp,
            0.07f, 0.16f, VERMILLION);
    }

    private void setBodyBasis(float yaw) {
        float sin = MathUtils.sin(yaw);
        float cos = MathUtils.cos(yaw);
        bodyForward.set(-sin, 0f, -cos);
        bodyRight.set(cos, 0f, -sin);
        bodyUp.set(0f, 1f, 0f);
    }

    private void setHeadBasis(float bodyYaw, float relativeYaw) {
        setBodyBasis(bodyYaw);
        headRight.set(bodyRight);
        headUp.set(bodyUp);
        headForward.set(bodyForward);
        rotateAroundUp(headRight, headForward, relativeYaw);
    }

    private static void rotateAroundUp(Vector3 right, Vector3 forward, float angle) {
        if (Math.abs(angle) < 0.0001f) {
            return;
        }
        float cos = MathUtils.cos(angle);
        float sin = MathUtils.sin(angle);
        float rx = right.x;
        float rz = right.z;
        float fx = forward.x;
        float fz = forward.z;
        right.set(rx * cos + fx * sin, 0f, rz * cos + fz * sin);
        forward.set(fx * cos - rx * sin, 0f, fz * cos - rz * sin);
    }

    private void drawBox(HeightmapTerrainRenderer renderer,
                         float baseX, float baseY, float baseZ,
                         float localX, float localY, float localZ,
                         float sizeX, float sizeY, float sizeZ, Color color) {
        partCenter.set(baseX, baseY, baseZ)
            .mulAdd(bodyRight, localX)
            .mulAdd(bodyUp, localY)
            .mulAdd(bodyForward, localZ);
        renderer.drawBox(partCenter, bodyRight, bodyUp, bodyForward,
            sizeX, sizeY, sizeZ, color);
    }

    private void drawHeadBox(HeightmapTerrainRenderer renderer, Vector3 headCenter,
                             float localX, float localY, float localZ,
                             float sizeX, float sizeY, float sizeZ, Color color) {
        partCenter.set(headCenter)
            .mulAdd(headRight, localX)
            .mulAdd(headUp, localY)
            .mulAdd(headForward, localZ);
        renderer.drawBox(partCenter, headRight, headUp, headForward,
            sizeX, sizeY, sizeZ, color);
    }
}

