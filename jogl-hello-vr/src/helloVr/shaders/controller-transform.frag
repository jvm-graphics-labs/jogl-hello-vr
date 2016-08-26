#version 410

#include semantic.glsl

in vec4 color_;

layout (location = FRAG_COLOR) out vec4 outputColor;

void main()
{
   outputColor = color_;
}