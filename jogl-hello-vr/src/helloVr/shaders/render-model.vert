#version 410

#include semantic.glsl

layout(location = POSITION) in vec4 position;
layout(location = NORMAL) in vec3 normal;
layout(location = TEX_COORD) in vec2 texCoord;

uniform mat4 matrix;

out vec2 texCoord_;

void main()
{
    texCoord_ = texCoord;
    gl_Position = matrix * vec4(position.xyz, 1.0);
}