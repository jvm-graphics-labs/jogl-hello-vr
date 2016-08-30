#version 410

#include semantic.glsl

layout(location = POSITION) in vec3 position;
layout(location = COLOR) in vec3 color;

uniform mat4 matrix;

out vec4 color_;

void main()
{
    color_ = vec4(color, 1.0);
    gl_Position = matrix * vec4(position, 1.0);
}