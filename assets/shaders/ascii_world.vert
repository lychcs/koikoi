#ifdef GL_ES
precision mediump float;
#endif

attribute vec3 a_position;
attribute vec3 a_normal;
attribute vec4 a_color;

uniform mat4 u_projView;

varying vec4 v_color;
varying vec3 v_normal;
varying vec3 v_world;

void main() {
    v_color = a_color;
    v_normal = a_normal;
    v_world = a_position;
    gl_Position = u_projView * vec4(a_position, 1.0);
}
