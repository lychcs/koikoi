#ifdef GL_ES
precision highp float;
#endif

varying vec4 v_color;
varying vec2 v_texCoords;

uniform sampler2D u_texture;
uniform float u_time;
uniform float u_strength;

// Atlas region of the companion frame: distortion is clamped to it so no
// neighbouring sprites leak into the ghost.
uniform vec2 u_uvMin;
uniform vec2 u_uvMax;

// 128-colour palette shared with the environment (palette-swap pass).
uniform sampler2D u_palette;
uniform float u_paletteSize;

vec3 snapToPalette(vec3 rgb) {
    vec3 best = rgb;
    float bestDistance = 1e9;

    for (int i = 0; i < 128; i++) {
        vec3 candidate = texture2D(
            u_palette,
            vec2((float(i) + 0.5) / u_paletteSize, 0.5)
        ).rgb;

        vec3 delta = rgb - candidate;
        float distance = dot(delta, delta);

        if (distance < bestDistance) {
            bestDistance = distance;
            best = candidate;
        }
    }

    return best;
}

void main() {
    vec2 span = max(u_uvMax - u_uvMin, vec2(0.000001));
    vec2 local = (v_texCoords - u_uvMin) / span;

    // Sine displacement: a slow horizontal ghost sway plus a fast heat wobble.
    vec2 wobble = vec2(
        sin(local.y * 14.0 + u_time * 6.0) * 0.030,
        sin(local.x * 11.0 - u_time * 4.5) * 0.018
    ) * u_strength;

    vec2 uv = clamp(v_texCoords + wobble * span, u_uvMin, u_uvMax);
    vec4 tex = texture2D(u_texture, uv);

    // Ethereal glow: lift the sprite colour and add a soft alpha flicker.
    float flicker = 0.86 + 0.14 * sin(u_time * 9.0 + local.y * 20.0);
    vec3 ghost = tex.rgb * (0.72 + 0.55 * tex.a);
    vec3 glow = mix(ghost, vec3(0.75, 0.95, 1.0), 0.18);

    vec3 paletted = snapToPalette(glow * v_color.rgb);
    gl_FragColor = vec4(paletted, tex.a * flicker * v_color.a);
}
