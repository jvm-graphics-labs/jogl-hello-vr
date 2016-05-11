#version 410 core

#include semantic.glsl

uniform sampler2D texture_;

in vec2 uvCoords_;

layout (location = FRAG_COLOR) out vec4 outputColor;

void main()
{
   outputColor = texture(texture_, uvCoords_);
}