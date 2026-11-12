package com.lychcs.koikoi.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.lychcs.koikoi.combat.component.HealthComponent;
import com.lychcs.koikoi.entities.OverworldYokaiEntity;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Procedural 3D Yokai rigs drawn through the Mokuhanga world shader.
 *
 * <p>The renderer owns no GPU resources: every body part is queued as a solid
 * {@link HeightmapTerrainRenderer#drawBox} primitive, so terrain, scenery and
 * Yokai share the same cel lighting and ink-outline prepass. Glowing eyes,
 * spirit-fire and defeat ink splatters use the direct billboard/Kamon/Ofuda
 * effect layers.</p>
 */
public final class YokaiRigRenderer {

    /** Three distinct wild-Yokai silhouettes. */
    public enum Archetype {
        CHOCHIN_OBAKE,
        KITSUNE_BI,
        ONI_GASHIRA
    }

    public static final int TAIL_COUNT = 3;
    public static final float DEFEAT_DURATION = 1.25f;
    public static final int MAX_DEFEAT_BURSTS = 12;

    private static final Color SHADOW = new Color(0.035f, 0.045f, 0.050f, 1f);
    private static final Color INK = new Color(0.055f, 0.060f, 0.075f, 1f);
    private static final Color VERMILLION = new Color(0.88f, 0.18f, 0.14f, 1f);
    private static final Color PAPER = new Color(0.94f, 0.88f, 0.68f, 1f);
    private static final Color PAPER_DARK = new Color(0.72f, 0.62f, 0.43f, 1f);
    private static final Color WOOD = new Color(0.34f, 0.22f, 0.14f, 1f);
    private static final Color WOOD_LIGHT = new Color(0.52f, 0.34f, 0.18f, 1f);
    private static final Color AMBER = new Color(1.00f, 0.62f, 0.18f, 1f);
    private static final Color FOX_WHITE = new Color(0.95f, 0.92f, 0.82f, 1f);
    private static final Color FOX_CREAM = new Color(0.86f, 0.78f, 0.64f, 1f);
    private static final Color FOX_DARK = new Color(0.24f, 0.20f, 0.18f, 1f);
    private static final Color SPIRIT_FIRE = new Color(0.44f, 0.90f, 1.00f, 1f);
    private static final Color SPIRIT_FIRE_WARM = new Color(0.92f, 0.86f, 0.38f, 1f);
    private static final Color IRON = new Color(0.28f, 0.31f, 0.34f, 1f);
    private static final Color IRON_DARK = new Color(0.16f, 0.18f, 0.21f, 1f);
    private static final Color BONE = new Color(0.82f, 0.78f, 0.66f, 1f);
    private static final Color MASK_RED = new Color(0.72f, 0.16f, 0.14f, 1f);
    private static final Color GOLD = new Color(0.82f, 0.64f, 0.26f, 1f);
    private static final Color OFUDA_RED = new Color(0.78f, 0.18f, 0.14f, 1f);

    private static final class RigState {
        boolean initialized;
        float yaw;
        float walkPhase;
        final float[] tailAngle = new float[TAIL_COUNT];
        final float[] tailVelocity = new float[TAIL_COUNT];
    }

    private static final class DefeatBurst {
        float x;
        float z;
        float ground;
        float age;
        int seed;
        Archetype archetype;
    }

    private final Map<OverworldYokaiEntity, RigState> states = new IdentityHashMap<>();
    private final List<DefeatBurst> defeatBursts = new ArrayList<>();

    private final Vector3 bodyRight = new Vector3(1f, 0f, 0f);
    private final Vector3 bodyUp = new Vector3(0f, 1f, 0f);
    private final Vector3 bodyForward = new Vector3(0f, 0f, -1f);
    private final Vector3 partRight = new Vector3();
    private final Vector3 partUp = new Vector3();
    private final Vector3 partForward = new Vector3();
    private final Vector3 partCenter = new Vector3();
    private final Vector3 frameCenter = new Vector3();
    private final Vector3 frameOffset = new Vector3();
    private final Vector3 tailRoot = new Vector3();
    private final Vector3 tailDirection = new Vector3();
    private final Vector3 tailRight = new Vector3();
    private final Vector3 ofudaCenter = new Vector3();
    private final Vector3 ofudaRight = new Vector3();
    private final Vector3 ofudaUp = new Vector3();
    private final Color workColor = new Color();
    private final Color frameColor = new Color();
    private final Color accentColor = new Color();

    /** Chooses the rig archetype from stable species/display metadata. */
    public static Archetype archetypeFor(OverworldYokaiEntity actor) {
        if (actor == null) {
            return Archetype.ONI_GASHIRA;
        }
        return archetypeFor(actor.getSpeciesId(), actor.getDisplayName());
    }

    /** Chooses the rig archetype from stable species/display metadata. */
    public static Archetype archetypeFor(String speciesId, String displayName) {
        String species = speciesId == null ? "" : speciesId.toLowerCase();
        String display = displayName == null ? "" : displayName.toLowerCase();
        if (species.contains("chochin") || species.contains("lantern")
            || display.contains("chochin") || display.contains("chōchin")
            || display.contains("lantern")) {
            return Archetype.CHOCHIN_OBAKE;
        }
        if (species.contains("kitsune") || species.contains("fox")
            || display.contains("kitsune") || display.contains("fox")) {
            return Archetype.KITSUNE_BI;
        }
        if (species.contains("oni") || display.contains("oni")) {
            return Archetype.ONI_GASHIRA;
        }
        return Archetype.ONI_GASHIRA;
    }

    /** Renders one Yokai rig and its hit/health feedback. */
    public void render(HeightmapTerrainRenderer renderer, OverworldYokaiEntity actor,
                       HealthComponent health, float worldX, float worldZ, float ground,
                       float playerX, float playerZ,
                       float cameraRightX, float cameraRightZ,
                       float flash, float delta, float time) {
        if (renderer == null || actor == null) {
            return;
        }
        RigState state = stateFor(actor);
        Archetype archetype = archetypeFor(actor);
        updateState(state, actor, worldX, worldZ, playerX, playerZ, delta, time);

        float knockback = MathUtils.clamp(
            actor.getKnockbackRemaining() / OverworldYokaiEntity.MAX_KNOCKBACK_DISTANCE,
            0f, 1f);
        setBodyBasis(state.yaw, knockback * 0.45f);

        renderer.drawCrispGroundQuad(worldX, worldZ, shadowRadius(archetype), SHADOW);

        if (actor.isAggro()) {
            float pulse = 0.55f + 0.45f * MathUtils.sin(time * 6f);
            workColor.set(VERMILLION.r * pulse, VERMILLION.g * pulse,
                VERMILLION.b * pulse, 1f);
            renderer.drawKamonSeal(worldX, worldZ, aggroRadius(archetype),
                workColor, time * 0.24f);
        }

        switch (archetype) {
            case CHOCHIN_OBAKE -> renderChochin(renderer, state, actor,
                worldX, worldZ, ground, flash, time);
            case KITSUNE_BI -> renderKitsune(renderer, state, actor,
                worldX, worldZ, ground, flash, time);
            case ONI_GASHIRA -> renderOni(renderer, state, actor,
                worldX, worldZ, ground, flash, time);
        }

        if (flash > 0.01f) {
            drawFlashFrame(renderer, worldX, worldZ, ground,
                cameraRightX, cameraRightZ, flash);
        }
        drawHealthBand(renderer, worldX, worldZ, ground, health, archetype);
    }

    /** Removes per-actor animation state after a defeat. */
    public void forget(OverworldYokaiEntity actor) {
        states.remove(actor);
    }

    /** Queues the ink-splatter and Ofuda scatter of a defeated Yokai. */
    public void spawnDefeat(float worldX, float worldZ, float ground, String speciesId) {
        DefeatBurst burst = new DefeatBurst();
        burst.x = worldX;
        burst.z = worldZ;
        burst.ground = ground;
        burst.age = 0f;
        burst.seed = (int) (worldX * 37.0f + worldZ * 91.0f);
        burst.archetype = archetypeFor(speciesId, "");
        defeatBursts.add(burst);
        while (defeatBursts.size() > MAX_DEFEAT_BURSTS) {
            defeatBursts.remove(0);
        }
    }

    /** Advances and draws all active defeat bursts. */
    public void renderDefeatBursts(HeightmapTerrainRenderer renderer, float delta) {
        if (renderer == null || defeatBursts.isEmpty()) {
            return;
        }
        float step = MathUtils.clamp(delta, 0f, 0.05f);
        Iterator<DefeatBurst> iterator = defeatBursts.iterator();
        while (iterator.hasNext()) {
            DefeatBurst burst = iterator.next();
            burst.age += step;
            if (burst.age >= DEFEAT_DURATION) {
                iterator.remove();
                continue;
            }
            drawDefeatBurst(renderer, burst);
        }
    }

    /** Drops all animation and burst state (screen re-init). */
    public void clear() {
        states.clear();
        defeatBursts.clear();
    }

    private RigState stateFor(OverworldYokaiEntity actor) {
        RigState state = states.get(actor);
        if (state == null) {
            state = new RigState();
            states.put(actor, state);
        }
        return state;
    }

    private void updateState(RigState state, OverworldYokaiEntity actor,
                             float worldX, float worldZ, float playerX, float playerZ,
                             float delta, float time) {
        float dt = MathUtils.clamp(delta, 0f, 0.05f);
        float desiredYaw = directionYaw(actor.getDirection());
        float dx = playerX - worldX;
        float dz = playerZ - worldZ;
        float playerDistanceSq = dx * dx + dz * dz;
        if (actor.isAggro() && playerDistanceSq > 0.0001f) {
            desiredYaw = (float) Math.atan2(-dx, -dz);
        }
        if (!state.initialized) {
            state.yaw = PlayerAvatarRenderer.wrapAngle(desiredYaw);
            state.initialized = true;
        } else {
            float rate = actor.isAggro() ? 7f : 3.5f;
            state.yaw = PlayerAvatarRenderer.stepAngle(
                state.yaw, desiredYaw, rate * dt);
        }

        float walkRate = actor.isPaused() ? 2.0f : (actor.isAggro() ? 10f : 7f);
        state.walkPhase += dt * walkRate * actor.getSpeedScale();

        // Three damped tail springs with a slight per-tail phase offset.
        for (int i = 0; i < TAIL_COUNT; i++) {
            float fan = (i - 1) * 0.48f;
            float target = fan + 0.12f * MathUtils.sin(time * 1.35f + i * 1.1f)
                + (actor.isAggro() ? 0.10f : 0f);
            float accel = (target - state.tailAngle[i]) * 22f;
            state.tailVelocity[i] += accel * dt;
            state.tailVelocity[i] *= (float) Math.exp(-5.5f * dt);
            state.tailAngle[i] += state.tailVelocity[i] * dt;
        }
    }


    private static float directionYaw(OverworldYokaiEntity.Direction direction) {
        if (direction == null) {
            return 0f;
        }
        return switch (direction) {
            case UP -> 0f;
            case DOWN -> MathUtils.PI;
            case LEFT -> MathUtils.PI * 0.5f;
            case RIGHT -> -MathUtils.PI * 0.5f;
        };
    }

    private void setBodyBasis(float yaw, float lean) {
        float sin = MathUtils.sin(yaw);
        float cos = MathUtils.cos(yaw);
        bodyForward.set(-sin, 0f, -cos);
        bodyRight.set(cos, 0f, -sin);
        bodyUp.set(0f, 1f, 0f);
        rotateAroundAxis(bodyUp, bodyForward, bodyRight, lean);
    }

    private void setPartBasis(float yawOffset, float pitch, float roll) {
        partRight.set(bodyRight);
        partUp.set(bodyUp);
        partForward.set(bodyForward);
        if (Math.abs(yawOffset) > 0.0001f) {
            rotateAroundAxis(partRight, partForward, bodyUp, yawOffset);
        }
        if (Math.abs(pitch) > 0.0001f) {
            rotateAroundAxis(partUp, partForward, bodyRight, pitch);
        }
        if (Math.abs(roll) > 0.0001f) {
            rotateAroundAxis(partRight, partUp, bodyForward, roll);
        }
    }

    private static void rotateAroundAxis(Vector3 a, Vector3 b, Vector3 axis, float angle) {
        if (Math.abs(angle) < 0.0001f) {
            return;
        }
        float cos = MathUtils.cos(angle);
        float sin = MathUtils.sin(angle);
        float ax = a.x;
        float ay = a.y;
        float az = a.z;
        float bx = b.x;
        float by = b.y;
        float bz = b.z;
        a.set(ax * cos + bx * sin, ay * cos + by * sin, az * cos + bz * sin);
        b.set(bx * cos - ax * sin, by * cos - ay * sin, bz * cos - az * sin);
    }

    private void drawPart(HeightmapTerrainRenderer renderer, float baseX, float baseY,
                          float baseZ, float localX, float localY, float localZ,
                          float sizeX, float sizeY, float sizeZ,
                          Color color, float flash) {
        partCenter.set(baseX, baseY, baseZ)
            .mulAdd(bodyRight, localX)
            .mulAdd(bodyUp, localY)
            .mulAdd(bodyForward, localZ);
        tint(color, flash);
        renderer.drawBox(partCenter, bodyRight, bodyUp, bodyForward,
            sizeX, sizeY, sizeZ, workColor);
    }

    private void drawRotatedPart(HeightmapTerrainRenderer renderer,
                                 float baseX, float baseY, float baseZ,
                                 float localX, float localY, float localZ,
                                 float sizeX, float sizeY, float sizeZ,
                                 float yawOffset, float pitch, float roll,
                                 Color color, float flash) {
        setPartBasis(yawOffset, pitch, roll);
        partCenter.set(baseX, baseY, baseZ)
            .mulAdd(bodyRight, localX)
            .mulAdd(bodyUp, localY)
            .mulAdd(bodyForward, localZ);
        tint(color, flash);
        renderer.drawBox(partCenter, partRight, partUp, partForward,
            sizeX, sizeY, sizeZ, workColor);
    }

    private void tint(Color base, float flash) {
        workColor.set(base);
        if (flash > 0f) {
            if (flash > 0.52f) {
                workColor.lerp(Color.WHITE, (flash - 0.52f) / 0.48f * 0.85f);
            } else {
                workColor.lerp(VERMILLION, (0.52f - flash) / 0.52f * 0.55f);
            }
        }
    }

    private static float shadowRadius(Archetype archetype) {
        return switch (archetype) {
            case CHOCHIN_OBAKE -> 0.38f;
            case KITSUNE_BI -> 0.46f;
            case ONI_GASHIRA -> 0.58f;
        };
    }

    private static float aggroRadius(Archetype archetype) {
        return switch (archetype) {
            case CHOCHIN_OBAKE -> 0.50f;
            case KITSUNE_BI -> 0.58f;
            case ONI_GASHIRA -> 0.74f;
        };
    }

    private void drawFlashFrame(HeightmapTerrainRenderer renderer,
                                float worldX, float worldZ, float ground,
                                float cameraRightX, float cameraRightZ, float flash) {
        frameColor.set(flash > 0.52f ? Color.WHITE : VERMILLION);
        float intensity = 0.45f + 0.55f * flash;
        frameColor.mul(intensity, intensity, intensity, 1f);
        partRight.set(cameraRightX, 0f, cameraRightZ).nor();
        partUp.set(0f, 1f, 0f);
        partForward.set(partRight).crs(partUp).nor();
        frameCenter.set(worldX, ground + 1.02f, worldZ);
        float halfWidth = 0.62f;
        float halfHeight = 0.72f;
        float thickness = 0.035f;
        drawFrameBar(renderer, partRight, -halfWidth, thickness, halfHeight * 2f);
        drawFrameBar(renderer, partRight, halfWidth, thickness, halfHeight * 2f);
        drawFrameBar(renderer, partUp, halfHeight, halfWidth * 2f, thickness);
        drawFrameBar(renderer, partUp, -halfHeight, halfWidth * 2f, thickness);
    }

    private void drawFrameBar(HeightmapTerrainRenderer renderer, Vector3 axis,
                              float offset, float sizeAlongAxis, float sizeAcross) {
        frameOffset.set(frameCenter).mulAdd(axis, offset);
        renderer.drawBox(frameOffset, partRight, partUp, partForward,
            sizeAlongAxis, sizeAcross, 0.035f, frameColor);
    }

    /** Floating wooden-ribbed paper lantern with a slit eye and cloth tongue. */
    private void renderChochin(HeightmapTerrainRenderer renderer, RigState state,
                               OverworldYokaiEntity actor, float worldX, float worldZ,
                               float ground, float flash, float time) {
        float phase = actor.getSpawnX() * 0.013f + actor.getSpawnY() * 0.007f;
        float hover = MathUtils.sin(time * 2.1f + phase) * 0.12f;
        float baseY = ground + hover;

        // Paper body and its wooden ribs.
        drawPart(renderer, worldX, baseY, worldZ, 0f, 0.96f, 0f,
            0.60f, 0.72f, 0.50f, PAPER, flash);
        drawPart(renderer, worldX, baseY, worldZ, 0f, 0.60f, 0f,
            0.54f, 0.16f, 0.44f, PAPER_DARK, flash);
        for (int i = 0; i < 4; i++) {
            drawPart(renderer, worldX, baseY, worldZ, 0f, 0.66f + i * 0.18f, 0f,
                0.66f, 0.045f, 0.56f, WOOD, flash);
        }
        drawPart(renderer, worldX, baseY, worldZ, 0f, 0.55f, 0f,
            0.50f, 0.08f, 0.42f, WOOD_LIGHT, flash);
        drawPart(renderer, worldX, baseY, worldZ, 0f, 1.36f, 0f,
            0.50f, 0.08f, 0.42f, WOOD_LIGHT, flash);
        drawPart(renderer, worldX, baseY, worldZ, 0f, 1.45f, 0f,
            0.12f, 0.18f, 0.12f, WOOD, flash);

        // Dark sockets with a glowing amber slit eye.
        drawPart(renderer, worldX, baseY, worldZ, -0.13f, 1.03f, 0.265f,
            0.17f, 0.095f, 0.035f, INK, flash);
        drawPart(renderer, worldX, baseY, worldZ, 0.13f, 1.03f, 0.265f,
            0.17f, 0.095f, 0.035f, INK, flash);
        drawPart(renderer, worldX, baseY, worldZ, -0.13f, 1.03f, 0.29f,
            0.115f, 0.035f, 0.018f, AMBER, 0f);
        drawPart(renderer, worldX, baseY, worldZ, 0.13f, 1.03f, 0.29f,
            0.115f, 0.035f, 0.018f, AMBER, 0f);

        // Flapping cloth tongue.
        float flap = MathUtils.sin(time * 7.0f + phase) * 0.55f;
        drawRotatedPart(renderer, worldX, baseY, worldZ, 0f, 0.44f, 0.06f,
            0.18f, 0.34f, 0.035f, 0f, flap, 0.12f, VERMILLION, flash);
        drawPart(renderer, worldX, baseY, worldZ, 0f, 0.26f, 0.02f,
            0.06f, 0.08f, 0.04f, GOLD, flash);
    }

    private void drawHealthBand(HeightmapTerrainRenderer renderer,
                                float worldX, float worldZ, float ground,
                                HealthComponent health, Archetype archetype) {
        if (health == null || health.ratio() >= 1f) {
            return;
        }
        float ratio = MathUtils.clamp(health.ratio(), 0f, 1f);
        float height = switch (archetype) {
            case CHOCHIN_OBAKE -> 1.65f;
            case KITSUNE_BI -> 1.55f;
            case ONI_GASHIRA -> 2.35f;
        };
        workColor.set(0.95f, 0.22f, 0.18f, 1f);
        renderer.drawBillboard(worldX, ground + height, worldZ,
            0.90f * ratio + 0.06f, 0.065f, workColor);
    }


    /** Sleek low-poly fox with spring-driven tails and spirit-fire wisps. */
    private void renderKitsune(HeightmapTerrainRenderer renderer, RigState state,
                               OverworldYokaiEntity actor, float worldX, float worldZ,
                               float ground, float flash, float time) {
        float phase = actor.getSpawnX() * 0.009f + actor.getSpawnY() * 0.011f;
        float bob = MathUtils.sin(time * 3.2f + phase) * 0.025f;
        float baseY = ground + bob;

        // Body, chest and low-poly head.
        drawPart(renderer, worldX, baseY, worldZ, 0f, 0.47f, 0f,
            0.50f, 0.32f, 0.78f, FOX_CREAM, flash);
        drawPart(renderer, worldX, baseY, worldZ, 0f, 0.61f, 0.25f,
            0.46f, 0.38f, 0.40f, FOX_WHITE, flash);
        drawPart(renderer, worldX, baseY, worldZ, 0f, 0.84f, 0.48f,
            0.34f, 0.32f, 0.36f, FOX_WHITE, flash);
        drawPart(renderer, worldX, baseY, worldZ, 0f, 0.80f, 0.69f,
            0.16f, 0.15f, 0.28f, FOX_CREAM, flash);
        drawPart(renderer, worldX, baseY, worldZ, 0f, 0.80f, 0.85f,
            0.07f, 0.06f, 0.06f, FOX_DARK, flash);

        // Pointed ears with vermillion inner fur.
        for (int i = 0; i < 2; i++) {
            float side = i == 0 ? -1f : 1f;
            drawRotatedPart(renderer, worldX, baseY, worldZ,
                side * 0.13f, 1.05f, 0.46f, 0.13f, 0.28f, 0.09f,
                0f, 0f, side * 0.22f, FOX_WHITE, flash);
            drawPart(renderer, worldX, baseY, worldZ,
                side * 0.13f, 1.04f, 0.49f, 0.06f, 0.15f, 0.025f,
                VERMILLION, flash);
        }

        // Narrow amber eyes and a white mask mark.
        drawPart(renderer, worldX, baseY, worldZ, -0.09f, 0.88f, 0.64f,
            0.055f, 0.035f, 0.018f, AMBER, 0f);
        drawPart(renderer, worldX, baseY, worldZ, 0.09f, 0.88f, 0.64f,
            0.055f, 0.035f, 0.018f, AMBER, 0f);
        drawPart(renderer, worldX, baseY, worldZ, 0f, 0.78f, 0.655f,
            0.16f, 0.025f, 0.015f, VERMILLION, flash);

        // Four low-poly legs with a quiet alternating stride.
        for (int i = 0; i < 4; i++) {
            float side = (i & 1) == 0 ? -1f : 1f;
            float front = (i & 2) == 0 ? 0.25f : -0.23f;
            float stride = MathUtils.sin(state.walkPhase + (i & 1) * MathUtils.PI) * 0.06f;
            drawPart(renderer, worldX, baseY, worldZ, side * 0.18f, 0.17f + stride, front,
                0.14f, 0.34f, 0.14f, FOX_DARK, flash);
        }

        for (int i = 0; i < TAIL_COUNT; i++) {
            drawFoxTail(renderer, worldX, baseY, worldZ,
                state.tailAngle[i], i, flash, time);
        }

        // Spirit-fire wisps orbit the shoulders and tail tips.
        for (int i = 0; i < 4; i++) {
            float angle = time * 1.8f + i * 1.7f + phase;
            float radius = 0.34f + 0.05f * MathUtils.sin(time * 2.4f + i);
            ofudaCenter.set(worldX, baseY + 1.02f + 0.10f * MathUtils.sin(angle),
                worldZ)
                .mulAdd(bodyRight, MathUtils.cos(angle) * radius)
                .mulAdd(bodyForward, 0.10f + i * 0.10f);
            workColor.set(i % 2 == 0 ? SPIRIT_FIRE : SPIRIT_FIRE_WARM);
            renderer.drawBillboard(ofudaCenter.x, ofudaCenter.y, ofudaCenter.z,
                0.12f, 0.12f, workColor, angle);
        }
    }

    private void drawFoxTail(HeightmapTerrainRenderer renderer,
                             float baseX, float baseY, float baseZ,
                             float angle, int index, float flash, float time) {
        tailRoot.set(baseX, baseY, baseZ)
            .mulAdd(bodyForward, -0.38f)
            .mulAdd(bodyUp, 0.52f);
        float vx = -bodyForward.x;
        float vz = -bodyForward.z;
        float cos = MathUtils.cos(angle);
        float sin = MathUtils.sin(angle);
        tailDirection.set(vx * cos + vz * sin, 0f, vz * cos - vx * sin).nor();
        tailRight.set(-tailDirection.z, 0f, tailDirection.x);

        for (int segment = 0; segment < 3; segment++) {
            float start = 0.10f + segment * 0.24f;
            partCenter.set(tailRoot)
                .mulAdd(tailDirection, start + 0.12f)
                .mulAdd(bodyUp, 0.035f * (segment + 1));
            tint(segment == 2 ? FOX_WHITE : FOX_CREAM, flash);
            float thickness = 0.14f - segment * 0.025f;
            renderer.drawBox(partCenter, tailRight, bodyUp, tailDirection,
                thickness, thickness, 0.30f, workColor);
        }

        partCenter.set(tailRoot)
            .mulAdd(tailDirection, 0.82f)
            .mulAdd(bodyUp, 0.14f + 0.04f * MathUtils.sin(time * 2.3f + index));
        workColor.set(index == 1 ? SPIRIT_FIRE_WARM : SPIRIT_FIRE);
        renderer.drawBillboard(partCenter.x, partCenter.y, partCenter.z,
            0.11f, 0.11f, workColor, angle);
    }


    /** Broad-shouldered stone/iron brute with mask horns and a tetsubo club. */
    private void renderOni(HeightmapTerrainRenderer renderer, RigState state,
                           OverworldYokaiEntity actor, float worldX, float worldZ,
                           float ground, float flash, float time) {
        float stompScale = actor.isPaused() ? 0.25f : 1f;
        float swing = MathUtils.sin(state.walkPhase);
        float stomp = Math.abs(swing) * 0.075f * stompScale;
        float baseY = ground + stomp;

        // Thick legs and grounded geta-like feet.
        for (int i = 0; i < 2; i++) {
            float side = i == 0 ? -1f : 1f;
            float lift = Math.max(0f, swing * side) * 0.11f * stompScale;
            drawPart(renderer, worldX, baseY, worldZ, side * 0.23f, 0.33f + lift, 0f,
                0.27f, 0.62f, 0.28f, IRON_DARK, flash);
            drawPart(renderer, worldX, baseY, worldZ, side * 0.23f, 0.08f + lift, 0.06f,
                0.30f, 0.14f, 0.36f, WOOD, flash);
        }

        // Broad torso, chest plate and waist sash.
        drawPart(renderer, worldX, baseY, worldZ, 0f, 0.93f, 0f,
            0.86f, 0.60f, 0.52f, IRON, flash);
        drawPart(renderer, worldX, baseY, worldZ, 0f, 1.02f, 0.24f,
            0.68f, 0.34f, 0.14f, IRON_DARK, flash);
        drawPart(renderer, worldX, baseY, worldZ, 0f, 0.62f, 0.02f,
            0.74f, 0.18f, 0.46f, VERMILLION, flash);

        // Shoulders and heavy arms.
        drawPart(renderer, worldX, baseY, worldZ, -0.53f, 1.24f, 0f,
            0.38f, 0.32f, 0.46f, IRON, flash);
        drawPart(renderer, worldX, baseY, worldZ, 0.53f, 1.24f, 0f,
            0.38f, 0.32f, 0.46f, IRON, flash);
        drawRotatedPart(renderer, worldX, baseY, worldZ, -0.60f, 0.95f, 0.02f,
            0.24f, 0.58f, 0.24f, 0f, -swing * 0.32f, 0.10f, IRON_DARK, flash);
        drawRotatedPart(renderer, worldX, baseY, worldZ, 0.60f, 0.96f, 0.06f,
            0.22f, 0.54f, 0.22f, 0f, swing * 0.28f, -0.15f, IRON_DARK, flash);

        // Horned mask head.
        drawPart(renderer, worldX, baseY, worldZ, 0f, 1.56f, 0f,
            0.44f, 0.40f, 0.42f, BONE, flash);
        drawPart(renderer, worldX, baseY, worldZ, 0f, 1.55f, 0.23f,
            0.36f, 0.10f, 0.06f, IRON_DARK, flash);
        drawPart(renderer, worldX, baseY, worldZ, -0.12f, 1.62f, 0.245f,
            0.08f, 0.035f, 0.018f, MASK_RED, 0f);
        drawPart(renderer, worldX, baseY, worldZ, 0.12f, 1.62f, 0.245f,
            0.08f, 0.035f, 0.018f, MASK_RED, 0f);
        drawPart(renderer, worldX, baseY, worldZ, 0f, 1.45f, 0.245f,
            0.16f, 0.045f, 0.018f, INK, flash);
        drawRotatedPart(renderer, worldX, baseY, worldZ, -0.18f, 1.86f, 0.02f,
            0.12f, 0.36f, 0.12f, 0f, -0.12f, 0.32f, GOLD, flash);
        drawRotatedPart(renderer, worldX, baseY, worldZ, 0.18f, 1.86f, 0.02f,
            0.12f, 0.36f, 0.12f, 0f, -0.12f, -0.32f, GOLD, flash);

        // Heavy tetsubo in the right fist.
        drawRotatedPart(renderer, worldX, baseY, worldZ, 0.68f, 1.12f, 0.34f,
            0.17f, 0.17f, 1.04f, 0f, -0.38f, 0.12f, WOOD, flash);
        drawRotatedPart(renderer, worldX, baseY, worldZ, 0.68f, 1.47f, 0.76f,
            0.34f, 0.34f, 0.42f, 0f, -0.38f, 0.12f, IRON_DARK, flash);
        for (int i = 0; i < 3; i++) {
            drawRotatedPart(renderer, worldX, baseY, worldZ,
                0.68f, 1.35f + i * 0.10f, 0.58f + i * 0.05f,
                0.07f, 0.07f, 0.07f, 0f, -0.38f, 0.12f, GOLD, flash);
        }

        // Small ember breath at the mask.
        if (MathUtils.sin(time * 2.2f) > 0.4f) {
            ofudaCenter.set(worldX, baseY + 1.48f, worldZ)
                .mulAdd(bodyForward, 0.30f);
            workColor.set(AMBER);
            renderer.drawBillboard(ofudaCenter.x, ofudaCenter.y, ofudaCenter.z,
                0.09f, 0.06f, workColor, 0f);
        }
    }


    private void drawDefeatBurst(HeightmapTerrainRenderer renderer, DefeatBurst burst) {
        float progress = MathUtils.clamp(burst.age / DEFEAT_DURATION, 0f, 1f);
        float fade = 1f - progress;
        setAccent(burst.archetype);

        // A brief white/vermillion ink flash at the moment of exorcism.
        if (progress < 0.22f) {
            float flash = 1f - progress / 0.22f;
            workColor.set(flash > 0.5f ? Color.WHITE : VERMILLION);
            renderer.drawBillboard(burst.x, burst.ground + 0.48f, burst.z,
                0.86f * flash + 0.12f, 0.72f * flash + 0.12f, workColor,
                progress * 5f);
        }

        // Expanding ink crest under the collapsing Yokai.
        workColor.set(INK).lerp(accentColor, 0.45f);
        renderer.drawKamonSeal(burst.x, burst.z, 0.24f + progress * 0.95f,
            workColor, burst.seed * 0.01f + progress * 1.4f);

        // Radial ink splatter.
        for (int i = 0; i < 9; i++) {
            float angle = burst.seed * 0.013f + i * (MathUtils.PI2 / 9f)
                + progress * 0.8f;
            float radius = 0.18f + progress * (0.75f + (i % 3) * 0.12f);
            float px = burst.x + MathUtils.cos(angle) * radius;
            float pz = burst.z + MathUtils.sin(angle) * radius;
            float size = 0.10f + 0.14f * fade;
            workColor.set(INK).lerp(accentColor, 0.28f + (i % 3) * 0.10f);
            renderer.drawBillboard(px, burst.ground + 0.10f + fade * 0.22f, pz,
                size, size, workColor, angle);
        }

        // Scattered Ofuda strips flutter away from the defeat point.
        for (int i = 0; i < 6; i++) {
            float angle = burst.seed * 0.017f + i * (MathUtils.PI2 / 6f);
            float radius = 0.20f + progress * (0.42f + (i % 2) * 0.12f);
            ofudaCenter.set(
                burst.x + MathUtils.cos(angle) * radius,
                burst.ground + 0.18f + MathUtils.sin(progress * 3.2f + i) * 0.12f
                    + progress * 0.35f,
                burst.z + MathUtils.sin(angle) * radius);
            ofudaRight.set(MathUtils.cos(angle), 0.10f * fade,
                MathUtils.sin(angle)).nor();
            ofudaUp.set(-MathUtils.sin(angle) * 0.28f * fade, 1f,
                MathUtils.cos(angle) * 0.28f * fade).nor();
            renderer.drawOfuda(ofudaCenter, ofudaRight, ofudaUp,
                0.075f, 0.23f, OFUDA_RED);
        }
    }

    private void setAccent(Archetype archetype) {
        switch (archetype) {
            case CHOCHIN_OBAKE -> accentColor.set(AMBER);
            case KITSUNE_BI -> accentColor.set(SPIRIT_FIRE);
            case ONI_GASHIRA -> accentColor.set(VERMILLION);
        }
    }
}

