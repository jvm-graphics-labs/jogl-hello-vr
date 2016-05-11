#version 410

#include semantic.glsl

layout(location = POSITION) in vec4 position;
layout(location = UV_COORDS) in vec2 uvCoords;
layout(location = NORMAL) in vec3 normalIn;

uniform mat4 matrix;

out vec2 uvCoords_;

void main()
{
    uvCoords_ = uvCoords;
    gl_Position = matrix * position;
}