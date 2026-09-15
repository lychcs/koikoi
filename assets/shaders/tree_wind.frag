#ifdef GL_ES
precision mediump float;
#endif

varying vec4 v_color;
varying vec2 v_texCoords;

uniform sampler2D u_texture;
uniform float u_time;

uniform vec4 u_region;        // x = u, y = v, z = u2 - u, w = v2 - v
uniform vec2 u_regionSizePx;

uniform float u_swayStrength;     // Schwungstärke (z.B. 2.5)
uniform float u_swaySpeed;        // Schwunggeschwindigkeit (z.B. 2.0)
uniform float u_swayHeightStart;  // Ab welcher Höhe der Schwung beginnt (0.0 bis 1.0)

uniform float u_windStrength;     // Blätter-Zittern (z.B. 0.8)
uniform float u_windScale;        // Noise-Skalierung (z.B. 8.0)
uniform float u_windSpeed;        // Noise-Geschwindigkeit (z.B. 2.0)
uniform float u_windHeightStart;  // Ab welcher Höhe Blätter zittern

vec2 random(vec2 uv) {
    uv = vec2(dot(uv, vec2(127.1, 311.7)), dot(uv, vec2(269.5, 183.3)));
    return -1.0 + 2.0 * fract(sin(uv) * 43758.5453123);
}

float noise(vec2 uv) {
    vec2 uv_index = floor(uv);
    vec2 uv_fract = fract(uv);
    vec2 blur = smoothstep(0.0, 1.0, uv_fract);

    return mix(
        mix(dot(random(uv_index + vec2(0.0, 0.0)), uv_fract - vec2(0.0, 0.0)),
            dot(random(uv_index + vec2(1.0, 0.0)), uv_fract - vec2(1.0, 0.0)), blur.x),
        mix(dot(random(uv_index + vec2(0.0, 1.0)), uv_fract - vec2(0.0, 1.0)),
            dot(random(uv_index + vec2(1.0, 1.0)), uv_fract - vec2(1.0, 1.0)), blur.x),
        blur.y
    ) + 0.5;
}

void main() {
    vec2 local_uv = (v_texCoords - u_region.xy) / u_region.zw;
    vec2 pixelated_local_uv = floor(local_uv * u_regionSizePx) / u_regionSizePx;

    // 0.0 = Stamm am Boden, 1.0 = Baumkrone
    float heightFromGround = clamp(1.0 - pixelated_local_uv.y, 0.0, 1.0);

    // 1. SWAY: Unterhalb von u_swayHeightStart ist der Faktor garantiert 0.0
    float swayMask = step(u_swayHeightStart, heightFromGround);
    float bendProgress = (heightFromGround - u_swayHeightStart) / max(0.001, (1.0 - u_swayHeightStart));
    float swayFactor = pow(clamp(bendProgress, 0.0, 1.0), 2.0) * swayMask;

    float swayOffsetPx = swayFactor * sin(u_time * u_swaySpeed) * u_swayStrength;

    // 2. WIND (Blätterzittern)
    float windMask = step(u_windHeightStart, heightFromGround);
    float windNoise = noise(pixelated_local_uv * u_windScale + vec2(u_time * u_windSpeed, 0.0)) * 2.0 - 1.0;
    float windOffsetPx = windNoise * u_windStrength * windMask;

    // Gesamtverschiebung
    float totalOffsetUvX = (swayOffsetPx + windOffsetPx) / u_regionSizePx.x;
    pixelated_local_uv.x += totalOffsetUvX;

    vec2 transformed_uv = pixelated_local_uv * u_region.zw + u_region.xy;
    transformed_uv = clamp(transformed_uv, u_region.xy, u_region.xy + u_region.zw);

    gl_FragColor = v_color * texture2D(u_texture, transformed_uv);
}
