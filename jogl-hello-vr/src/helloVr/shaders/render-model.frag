#version 410 core

#include semantic.glsl

uniform sampler2D diffuse;

in vec2 texCoord_;

layout (location = FRAG_COLOR) out vec4 outputColor;

void main()
{
   outputColor = texture(diffuse, texCoord_);
}