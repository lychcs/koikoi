package com.lychcs.koikoi.render;

import com.badlogic.gdx.graphics.glutils.ShaderProgram;

/**
 * Uploads the four-season world uniforms shared by {@code ascii_world.frag} and
 * {@code nature_model.frag}.
 *
 * <p>The hub centre and the transition window are the Java mirror of
 * {@link WorldSeasonMap#weightsAt(float, float)}; the shaders recompute the
 * per-fragment weights from the interpolated world position so the ground
 * albedo, foliage tint and ambient fog blend without visible seams.</p>
 */
final class SeasonShaderUniforms {

    static final String UNIFORM_HUB = "u_seasonHub";
    static final String UNIFORM_START = "u_seasonStart";
    static final String UNIFORM_WIDTH = "u_seasonWidth";
    static final String UNIFORM_SPRING_GROUND = "u_springGround";
    static final String UNIFORM_SUMMER_GROUND = "u_summerGround";
    static final String UNIFORM_FALL_GROUND = "u_fallGround";
    static final String UNIFORM_WINTER_GROUND = "u_winterGround";
    static final String UNIFORM_SPRING_FOLIAGE = "u_springFoliage";
    static final String UNIFORM_SUMMER_FOLIAGE = "u_summerFoliage";
    static final String UNIFORM_FALL_FOLIAGE = "u_fallFoliage";
    static final String UNIFORM_WINTER_FOLIAGE = "u_winterFoliage";

    /** Disables the blend for maps without a known hub. */
    private static final float DISABLED_START = 1.0e9f;

    private SeasonShaderUniforms() {
    }

    /** Uploads every seasonal uniform that the bound program declares. */
    static void upload(ShaderProgram shader, WorldSeasonMap world) {
        if (shader == null) {
            return;
        }
        float hubX = world == null ? 0f : world.getHubWorldX();
        float hubZ = world == null ? 0f : world.getHubWorldZ();
        float start = world == null
            ? DISABLED_START : WorldSeasonMap.SEASON_BLEND_START_TILES;
        float width = world == null
            ? 1f : WorldSeasonMap.SEASON_BLEND_WIDTH_TILES;

        set2(shader, UNIFORM_HUB, hubX, hubZ);
        set1(shader, UNIFORM_START, start);
        set1(shader, UNIFORM_WIDTH, width);
        set3(shader, UNIFORM_SPRING_GROUND, WorldSeasonMap.SPRING_GROUND);
        set3(shader, UNIFORM_SUMMER_GROUND, WorldSeasonMap.SUMMER_GROUND);
        set3(shader, UNIFORM_FALL_GROUND, WorldSeasonMap.FALL_GROUND);
        set3(shader, UNIFORM_WINTER_GROUND, WorldSeasonMap.WINTER_GROUND);
        set3(shader, UNIFORM_SPRING_FOLIAGE, WorldSeasonMap.SPRING_FOLIAGE);
        set3(shader, UNIFORM_SUMMER_FOLIAGE, WorldSeasonMap.SUMMER_FOLIAGE);
        set3(shader, UNIFORM_FALL_FOLIAGE, WorldSeasonMap.FALL_FOLIAGE);
        set3(shader, UNIFORM_WINTER_FOLIAGE, WorldSeasonMap.WINTER_FOLIAGE);
    }

    private static void set1(ShaderProgram shader, String name, float value) {
        if (shader.hasUniform(name)) {
            shader.setUniformf(name, value);
        }
    }

    private static void set2(ShaderProgram shader, String name, float x, float y) {
        if (shader.hasUniform(name)) {
            shader.setUniformf(name, x, y);
        }
    }

    private static void set3(ShaderProgram shader, String name, float[] rgb) {
        if (shader.hasUniform(name)) {
            shader.setUniformf(name, rgb[0], rgb[1], rgb[2]);
        }
    }
}
