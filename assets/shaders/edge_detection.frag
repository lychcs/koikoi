#ifdef GL_ES
precision mediump float;
#endif

varying vec4 v_color;
varying vec2 v_texCoords;

uniform sampler2D u_texture;
uniform vec2 u_pixelSize;        // (1.0 / Bildbreite, 1.0 / Bildhoehe)

uniform float u_threshold;       // Kanten-Empfindlichkeit (z.B. 0.2 bis 0.5)
uniform vec4 u_lineColor;        // Linienfarbe (z.B. Schwarz: vec4(0.1, 0.1, 0.1, 1.0))
uniform vec4 u_backgroundColor;  // Hintergrundfarbe (z.B. Papier-Weiss: vec4(0.95, 0.93, 0.88, 1.0))
uniform float u_drawOriginal;    // 1.0 = Originaltextur mit Linien, 0.0 = Reiner Skizzen-Stil

// Berechnet die Helligkeit eines Pixels
float getLuminance(vec4 color) {
    return dot(color.rgb, vec3(0.299, 0.587, 0.114)) * color.a;
}

void main() {
    vec4 centerColor = texture2D(u_texture, v_texCoords);

    // Alpha-Test für transparente Sprite-Bereiche
    if (centerColor.a < 0.05) {
        discard;
    }

    // 3x3 Pixel-Kernel abtasten (Luminanz)
    float tl = getLuminance(texture2D(u_texture, v_texCoords + vec2(-u_pixelSize.x,  u_pixelSize.y)));
    float t  = getLuminance(texture2D(u_texture, v_texCoords + vec2(            0.0,  u_pixelSize.y)));
    float tr = getLuminance(texture2D(u_texture, v_texCoords + vec2( u_pixelSize.x,  u_pixelSize.y)));

    float l  = getLuminance(texture2D(u_texture, v_texCoords + vec2(-u_pixelSize.x,            0.0)));
    float r  = getLuminance(texture2D(u_texture, v_texCoords + vec2( u_pixelSize.x,            0.0)));

    float bl = getLuminance(texture2D(u_texture, v_texCoords + vec2(-u_pixelSize.x, -u_pixelSize.y)));
    float b  = getLuminance(texture2D(u_texture, v_texCoords + vec2(            0.0, -u_pixelSize.y)));
    float br = getLuminance(texture2D(u_texture, v_texCoords + vec2( u_pixelSize.x, -u_pixelSize.y)));

    // Sobel-Operatoren (Horizontale & Vertikale Differenzierung)
    float gx = -tl - 2.0 * l - bl + tr + 2.0 * r + br;
    float gy = -tl - 2.0 * t - tr + bl + 2.0 * b + br;

    // Kantenstärke berechnen
    float edge = sqrt(gx * gx + gy * gy);

    if (edge > u_threshold) {
        gl_FragColor = u_lineColor * v_color;
    } else {
        if (u_drawOriginal > 0.5) {
            gl_FragColor = centerColor * v_color;
        } else {
            gl_FragColor = u_backgroundColor * v_color;
        }
    }
}
