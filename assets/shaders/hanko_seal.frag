#ifdef GL_ES
precision mediump float;
#endif

varying vec4 v_color;
varying vec2 v_texCoords;

uniform float u_time;
uniform float u_progress;
uniform vec3 u_sealColor;

// Procedural radial Hanko seal: glowing border, inner ring and rotating runes.
void main() {
    // The quad is drawn in a -1..1 square; everything outside the circle is cut.
    vec2 uv = v_texCoords * 2.0 - 1.0;
    float distanceFromCenter = length(uv);
    if (distanceFromCenter > 1.0) {
        discard;
    }

    float pulse = 0.72 + 0.28 * sin(u_time * 7.0);

    float border = smoothstep(0.90, 0.97, distanceFromCenter);
    float innerRing = smoothstep(0.62, 0.66, distanceFromCenter)
                    * (1.0 - smoothstep(0.70, 0.74, distanceFromCenter));

    float angle = atan(uv.y, uv.x);
    float runes = step(0.55, sin(angle * 16.0 + u_time * 1.8))
                * smoothstep(0.74, 0.78, distanceFromCenter)
                * (1.0 - smoothstep(0.88, 0.92, distanceFromCenter));

    float core = (1.0 - smoothstep(0.0, 0.45, distanceFromCenter)) * 0.35;

    float alpha = clamp(border + innerRing * 0.8 + runes * 0.7 + core, 0.0, 1.0);
    alpha *= clamp(u_progress, 0.0, 1.0);

    vec3 color = u_sealColor * (0.85 + 0.5 * pulse);
    gl_FragColor = vec4(color, alpha) * v_color;
}
