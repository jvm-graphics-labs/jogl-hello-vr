#version 410

#include semantic.glsl

layout (location = POSITION) in vec3 position;
layout (location = TEXT_COORD) in vec2 texCoord;

uniform mat4 matrix;

out vec2 uvCoord;

void main()
{
    uvCoord = texCoord;
    gl_Position = matrix * vec4(position, 1);
}