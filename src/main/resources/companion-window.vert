#version 330 core

#include semantic.glsl

layout(location = POSITION) in vec2 position;
layout(location = TEX_COORD) in vec2 texCoord;

noperspective out vec2 uv;

void main()
{
    uv = texCoord;
	gl_Position = vec4(position, 0, 1);
}