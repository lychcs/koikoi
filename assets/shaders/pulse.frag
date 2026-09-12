#ifdef GL_ES
precision mediump float;
#endif

varying vec4 v_color;
varying vec2 v_texCoords;
uniform sampler2D u_texture;
uniform float u_time;
uniform vec3 u_glowColor; // Einstellbare Farbe je nach Siegel

void main() {
    vec4 texColor = texture2D(u_texture, v_texCoords);

    if (texColor.a < 0.05) {
        gl_FragColor = vec4(0.0);
        return;
    }

    // Sinuswelle für weiches Ein- und Ausatmen (Pulse)
    float pulse = 0.75 + 0.25 * sin(u_time * 4.0);

    vec3 finalRgb = mix(texColor.rgb, u_glowColor, 0.45) * pulse * 1.2;
    gl_FragColor = vec4(finalRgb, texColor.a) * v_color;
}
