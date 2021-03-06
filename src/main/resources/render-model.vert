#version 330

#include semantic.glsl

layout(location = POSITION) in vec3 position;
layout(location = TEX_COORD) in vec2 texCoord;

uniform mat4 matrix;

out vec2 texCoord_;

void main()
{
    texCoord_ = texCoord;
    gl_Position = matrix * vec4(position, 1.0);
}