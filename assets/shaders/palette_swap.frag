#ifdef GL_ES
precision highp float;
#endif

varying vec4 v_color;
varying vec2 v_texCoords;

uniform sampler2D u_texture;
uniform sampler2D u_palette;
uniform float u_paletteSize;

// Snaps the sprite colour to the nearest entry of the 128-colour palette.
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
    vec4 tex = texture2D(u_texture, v_texCoords);

    if (tex.a < 0.04) {
        discard;
    }

    vec3 color = snapToPalette(tex.rgb * v_color.rgb);
    gl_FragColor = vec4(color, tex.a * v_color.a);
}
