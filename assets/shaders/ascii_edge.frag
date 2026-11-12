#ifdef GL_ES
precision highp float;
#endif

// Depth/normal prepass for the cel outline.
//
// The scene is re-rendered into an RGBA8 target with the same world shader
// vertex stage:
//   RG = view-space normal.xy packed to 0..1 (z is reconstructed in the post
//        pass; visible surfaces face the camera, so z is positive),
//   B  = low byte of a 16-bit linear view depth / far,
//   A  = high byte of that depth.
// The sky is the cleared (0,0,0,0) texel. The ASCII post pass compares
// neighbouring texels; depth jumps and normal creases become stable ink lines.
// Pure GL2 / GLES2 friendly: no MRT, no depth texture, no GL30 calls.

varying vec3 v_normal;
varying vec3 v_world;

uniform mat4 u_view;
uniform float u_far;

void main() {
    vec3 worldNormal = normalize(v_normal);
    vec3 viewNormal = normalize((u_view * vec4(worldNormal, 0.0)).xyz);

    // Positive distance along the camera forward axis.
    vec4 viewPosition = u_view * vec4(v_world, 1.0);
    float depth = clamp(-viewPosition.z / max(u_far, 0.001), 0.0, 1.0);

    float packed = depth * 65535.0;
    float high = floor(packed / 256.0);
    float low = packed - high * 256.0;

    gl_FragColor = vec4(
        viewNormal.xy * 0.5 + 0.5,
        low / 255.0,
        high / 255.0);
}
