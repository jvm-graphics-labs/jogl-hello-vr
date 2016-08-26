#version 410

#include semantic.glsl

layout (location = POSITION) in vec4 position;
layout (location = TEX_COORD) in vec2 texCoord;

uniform mat4 matrix;

out vec2 uvCoord;

void main()
{
    uvCoord = texCoord;
    gl_Position = matrix * position;
}