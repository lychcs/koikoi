#ifdef GL_ES
precision highp float;
#endif

varying vec4 v_color;
varying vec2 v_texCoords;

uniform sampler2D u_texture;
uniform vec2 u_resolution;
uniform float u_time;
uniform float u_bloomStrength;
uniform float u_scanlineStrength;
uniform float u_edgeStrength;

const float PI = 3.14159265;

float luma(vec3 color) {
    return dot(color, vec3(0.2126, 0.7152, 0.0722));
}

vec2 barrelDistort(vec2 uv) {
    vec2 centered = uv * 2.0 - 1.0;
    centered *= 1.0 + 0.022 * dot(centered, centered);
    return centered * 0.5 + 0.5;
}

vec3 scene(vec2 uv) {
    return texture2D(u_texture, clamp(uv, vec2(0.001), vec2(0.999))).rgb;
}

// Bright-pass sample used for the selective bloom (Hanko seals, markers, embers).
vec3 brightSample(vec2 uv) {
    vec3 color = scene(uv);
    // Only the brightest ink glows, so terrain strokes stay crisp text.
    return color * smoothstep(0.70, 0.96, luma(color));
}

void main() {
    vec2 uv = barrelDistort(v_texCoords);
    // Clamp rather than blacking out: the ASCII ink must reach the curved
    // bezel. The old out-of-range branch produced a black frame border.
    uv = clamp(uv, vec2(0.0005), vec2(0.9995));

    vec2 pixel = 1.0 / u_resolution;

    // --- Chromatic aberration (CRT phosphor fringing) -------------------
    // Very small offset: large fringing splits thin ASCII strokes into pixels.
    float aberration = 0.30 * pixel.x;
    vec3 color;
    color.r = scene(uv + vec2(aberration, 0.0)).r;
    color.g = scene(uv).g;
    color.b = scene(uv - vec2(aberration, 0.0)).b;

    // --- Soft warm rim lighting -----------------------------------------
    float leftLuma = luma(scene(uv - vec2(pixel.x, 0.0)));
    float rightLuma = luma(scene(uv + vec2(pixel.x, 0.0)));
    float downLuma = luma(scene(uv - vec2(0.0, pixel.y)));
    float upLuma = luma(scene(uv + vec2(0.0, pixel.y)));
    float edge = clamp(length(vec2(rightLuma - leftLuma, upLuma - downLuma)) * 2.5, 0.0, 1.0);
    color += vec3(0.42, 0.36, 0.26) * edge * u_edgeStrength;

    // --- Selective soft bloom -------------------------------------------
    vec3 bloom = vec3(0.0);
    for (int i = 0; i < 8; i++) {
        float angle = float(i) * (PI * 0.25);
        vec2 direction = vec2(cos(angle), sin(angle));
        bloom += brightSample(uv + direction * pixel * 3.0);
        bloom += brightSample(uv + direction * pixel * 7.0) * 0.6;
    }
    bloom /= 12.8;
    color += bloom * u_bloomStrength;

    // --- Scanlines and phosphor mask ------------------------------------
    // A three-pixel period with a very low amplitude: the terminal grid stays
    // readable while the curved glass keeps a subtle phosphor texture.
    const float TAU_OVER_THREE = 2.0943951;
    float scan = 1.0 - 0.045 * (0.5 + 0.5 * sin(uv.y * u_resolution.y * TAU_OVER_THREE));
    float mask = 1.0 - 0.022 * (0.5 + 0.5 * sin(uv.x * u_resolution.x * TAU_OVER_THREE));
    color *= mix(1.0, scan * mask, u_scanlineStrength * 0.85);

    // --- Vignette and gentle CRT flicker --------------------------------
    vec2 vignetteUv = uv * (1.0 - uv);
    float vignette = pow(clamp(vignetteUv.x * vignetteUv.y * 16.0, 0.0, 1.0), 0.30);
    // Very light falloff: terminal glyphs must stay readable to the screen edge.
    color *= mix(0.94, 1.0, vignette);
    color *= 0.985 + 0.015 * sin(u_time * 11.0);

    // Slight phosphor lift so the darks keep a CRT glow.
    color = pow(max(color, vec3(0.0)), vec3(0.94));

    gl_FragColor = vec4(color, 1.0) * v_color;
}
