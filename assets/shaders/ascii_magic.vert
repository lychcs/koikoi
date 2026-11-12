#ifdef GL_ES
precision mediump float;
#endif

attribute vec3 a_position;
attribute vec2 a_texCoord0;
attribute vec4 a_color;

uniform mat4 u_projView;

varying vec2 v_texCoords;
varying vec4 v_color;
varying vec3 v_world;

void main() {
    v_texCoords = a_texCoord0;
    v_color = a_color;
    v_world = a_position;
    gl_Position = u_projView * vec4(a_position, 1.0);
}
