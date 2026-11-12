#ifdef GL_ES
precision mediump float;
#endif

#define MAX_LIGHTS 12

varying vec4 v_color;
varying vec3 v_normal;
varying vec3 v_world;

uniform vec3 u_cameraPos;
uniform vec3 u_lightDir;
uniform vec3 u_fogColor;
uniform vec3 u_valleyFogColor;
uniform float u_fogStart;
uniform float u_fogEnd;
uniform float u_fogBaseHeight;
uniform float u_fogHeightSpan;
uniform float u_heightFogStrength;
uniform float u_time;

uniform int u_lightCount;
uniform vec3 u_lightPos[MAX_LIGHTS];
uniform vec3 u_lightColor[MAX_LIGHTS];
uniform float u_lightRange[MAX_LIGHTS];

void main() {
    vec3 normal = normalize(v_normal);
    vec3 lightDir = normalize(u_lightDir);

    // Soft single-key moon light plus a wide ambient floor: the ASCII pass
    // turns the resulting luminance into glyph density, so flat shading must
    // stay readable instead of crushing the ground into pure black.
    float lambert = max(dot(normal, lightDir), 0.0);
    float ambient = 0.46;
    vec3 lit = v_color.rgb * (ambient + 0.92 * lambert);

    // Warm Tōrō/temple point lights: golden ink pools on nearby terrain and beams.
    vec3 warm = vec3(0.0);
    for (int i = 0; i < MAX_LIGHTS; i++) {
        if (i >= u_lightCount) break;
        vec3 toLight = u_lightPos[i] - v_world;
        float range = max(u_lightRange[i], 0.001);
        float attenuation = clamp(1.0 - dot(toLight, toLight) / (range * range), 0.0, 1.0);
        attenuation *= attenuation;
        warm += u_lightColor[i] * attenuation;
    }
    lit += v_color.rgb * warm * 1.6 + warm * 0.10;

    // Very slow moss shimmer breaks large flat faces into slightly different
    // glyph shades, which reads as wind over the meadow.
    float shimmer = 0.95 + 0.05 * sin(u_time * 0.6 + v_world.x * 2.7 + v_world.z * 3.1);
    lit *= shimmer;

    // Distance fog fades the far hills into the terminal background; valley
    // height fog additionally pools cool mist in low-lying terrain.
    float depth = distance(v_world, u_cameraPos);
    float distanceFog = clamp((depth - u_fogStart) / max(1.0, u_fogEnd - u_fogStart), 0.0, 0.85);
    float valley = 1.0 - smoothstep(u_fogBaseHeight,
        u_fogBaseHeight + u_fogHeightSpan, v_world.y);
    float heightFog = valley * u_heightFogStrength * smoothstep(6.0, 40.0, depth);
    float fog = clamp(distanceFog + heightFog, 0.0, 0.94);
    vec3 fogColor = mix(u_fogColor, u_valleyFogColor, valley * 0.85);
    lit = mix(lit, fogColor, fog);

    gl_FragColor = vec4(lit, v_color.a);
}
