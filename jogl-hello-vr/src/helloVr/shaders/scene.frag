#version 410 core

#include semantic.glsl

uniform sampler2D myTexture;

in vec2 uvCoord;

layout (location = FRAG_COLOR) out vec4 outputColor;

void main()
{
   outputColor = texture(myTexture, uvCoord);
}