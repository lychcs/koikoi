#ifdef GL_ES
precision mediump float;
#endif

varying vec4 v_color;
varying vec2 v_texCoords;
uniform sampler2D u_texture;
uniform float u_time;

// Hilfsfunktion: Konvertiert HSV zu RGB für weiche Regenbogenübergänge
vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

void main() {
    vec4 texColor = texture2D(u_texture, v_texCoords);

    // Verhindert Berechnung auf transparenten Pixeln
    if (texColor.a < 0.05) {
        gl_FragColor = vec4(0.0);
        return;
    }

    // Regenbogen läuft diagonal über die Textur basierend auf Zeit
    float hue = fract((v_texCoords.x + v_texCoords.y) * 0.8 - (u_time * 0.4));
    vec3 rainbow = hsv2rgb(vec3(hue, 0.85, 1.0));

    // Kombiniert Textur-Luminanz mit dem Regenbogeneffekt
    gl_FragColor = vec4(rainbow * texColor.rgb * 1.3, texColor.a) * v_color;
}
