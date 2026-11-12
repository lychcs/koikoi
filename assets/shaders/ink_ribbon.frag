#ifdef GL_ES
precision mediump float;
#endif

varying vec4 v_color;
varying vec2 v_texCoords;
varying vec3 v_world;

/**
 * Tapered sumi-ink brush stroke used for the Hanafuda card trail. The stroke is
 * wide at the middle, thin at the tail and slightly worn at the head; bristle
 * streaks keep it from reading as a flat ribbon.
 */
void main() {
    float u = clamp(v_texCoords.x, 0.0, 1.0);
    float across = abs(v_texCoords.y * 2.0 - 1.0);
    float taper = mix(0.18, 1.0, smoothstep(0.0, 0.22, u))
        * (1.0 - 0.55 * smoothstep(0.72, 1.0, u));
    if (across > taper) {
        discard;
    }
    float bristle = 0.72 + 0.28 * sin(v_texCoords.y * 24.0 + u * 11.0);
    vec3 ink = v_color.rgb * (0.75 + 0.45 * u) * bristle;
    gl_FragColor = vec4(clamp(ink, 0.0, 1.0), 1.0);
}
