#ifdef GL_ES
precision mediump float;
#endif

varying vec4 v_color;
varying vec2 v_texCoords;
varying vec3 v_world;

float segment(vec2 p, vec2 a, vec2 b, float width) {
    vec2 pa = p - a;
    vec2 ba = b - a;
    float h = clamp(dot(pa, ba) / max(dot(ba, ba), 0.0001), 0.0, 1.0);
    return 1.0 - smoothstep(width, width * 2.0, length(pa - ba * h));
}

float rectMask(vec2 uv, vec4 bounds) {
    return step(bounds.x, uv.x) * step(uv.x, bounds.y)
        * step(bounds.z, uv.y) * step(uv.y, bounds.w);
}

/**
 * Procedural Ofuda paper strip: ivory parchment, sumi edge and red ink kanji
 * strokes. The effect colour lightly tints the ink so dash talismans follow the
 * active Hanafuda archetype.
 */
void main() {
    vec2 uv = v_texCoords;
    vec3 paper = vec3(0.95, 0.90, 0.76);
    float edge = min(min(uv.x, 1.0 - uv.x), min(uv.y, 1.0 - uv.y));
    paper *= 0.88 + 0.12 * smoothstep(0.0, 0.10, edge);

    float border = 1.0 - smoothstep(0.045, 0.060, edge);
    vec3 sumi = vec3(0.075, 0.065, 0.090);

    float strokes = 0.0;
    strokes = max(strokes, segment(uv, vec2(0.50, 0.16), vec2(0.50, 0.84), 0.025));
    strokes = max(strokes, segment(uv, vec2(0.28, 0.66), vec2(0.72, 0.66), 0.022));
    strokes = max(strokes, segment(uv, vec2(0.32, 0.46), vec2(0.68, 0.46), 0.020));
    strokes = max(strokes, segment(uv, vec2(0.50, 0.46), vec2(0.30, 0.22), 0.018));
    strokes = max(strokes, segment(uv, vec2(0.50, 0.46), vec2(0.72, 0.24), 0.018));

    vec3 red = mix(vec3(0.72, 0.10, 0.09), v_color.rgb * 0.45, 0.25);
    vec3 color = mix(paper, red, strokes);
    color = mix(color, sumi, border);

    float seal = rectMask(uv, vec4(0.68, 0.82, 0.10, 0.23));
    color = mix(color, red * 0.92, seal);

    gl_FragColor = vec4(color, 1.0);
}
