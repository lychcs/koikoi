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

float ring(float r, float radius, float width) {
    return 1.0 - smoothstep(width, width * 2.0, abs(r - radius));
}

float rectMask(vec2 uv, vec4 bounds) {
    return step(bounds.x, uv.x) * step(uv.x, bounds.y)
        * step(bounds.z, uv.y) * step(uv.y, bounds.w);
}

/**
 * Procedural Hanafuda card face: crisp sumi-ink border, inner diamond/petal or
 * wheel motif in the card archetype colour, and a small ink-brush signature.
 */
void main() {
    vec2 uv = v_texCoords;
    vec2 p = (uv - 0.5) * 2.0;
    float r = length(p);
    float a = atan(p.y, p.x + 0.0001);

    vec3 paper = vec3(0.95, 0.91, 0.80);
    float edge = min(min(uv.x, 1.0 - uv.x), min(uv.y, 1.0 - uv.y));
    paper *= 0.90 + 0.10 * smoothstep(0.0, 0.09, edge);

    float fade = clamp(v_color.a, 0.0, 1.0);
    paper = mix(vec3(0.075, 0.080, 0.095), paper, fade);

    float border = 1.0 - smoothstep(0.045, 0.065, edge);
    vec3 sumi = vec3(0.050, 0.055, 0.075);
    vec3 accent = v_color.rgb * (0.45 + 0.55 * fade);

    float diamond = 1.0 - smoothstep(0.030, 0.060,
        abs(abs(p.x) + abs(p.y) - 0.54));
    float petal = ring(r, 0.25 + 0.06 * cos(a * 5.0), 0.010) * step(0.10, r);
    float spokeSegment = fract(a / 6.28318 * 8.0);
    float spokeDistance = min(spokeSegment, 1.0 - spokeSegment);
    float wheel = (1.0 - smoothstep(0.025, 0.060, spokeDistance))
        * step(0.12, r) * step(r, 0.58);
    wheel = max(wheel, ring(r, 0.30, 0.008));

    float wheelMix = smoothstep(-0.04, 0.15, accent.b - accent.r);
    float gold = smoothstep(0.55, 0.75, accent.r)
        * smoothstep(0.45, 0.65, accent.g);
    wheelMix = max(wheelMix, gold * 0.75);
    float motif = max(diamond * 0.75,
        mix(petal, wheel, wheelMix));

    float signature = 0.0;
    signature = max(signature, segment(uv, vec2(0.46, 0.30), vec2(0.46, 0.72), 0.020));
    signature = max(signature, segment(uv, vec2(0.34, 0.58), vec2(0.58, 0.58), 0.018));
    signature = max(signature, segment(uv, vec2(0.46, 0.48), vec2(0.30, 0.34), 0.014));

    vec3 color = mix(paper, accent * 0.82, motif * 0.86);
    color = mix(color, accent * 0.70, signature);
    color = mix(color, sumi, border);

    // Top ribbon band: a small woodblock-style accent.
    float band = rectMask(uv, vec4(0.08, 0.92, 0.80, 0.88));
    color = mix(color, accent * 0.62, band);

    gl_FragColor = vec4(color, 1.0);
}
