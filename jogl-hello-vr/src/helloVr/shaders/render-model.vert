#version 410

#include semantic.glsl

layout(location = POSITION) in vec3 position;
layout(location = NORMAL) in vec3 normal;
layout(location = TEXT_COORD) in vec2 texCoord;

uniform mat4 matrix;

out vec2 texCoord_;

void main()
{
    texCoord_ = texCoord;
    gl_Position = matrix * vec4(position, 1.0);
}