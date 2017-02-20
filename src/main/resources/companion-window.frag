#version 330 core

#include semantic.glsl

layout(location = FRAG_COLOR) out vec4 outColor;

uniform sampler2D myTexture;

noperspective in vec2 uv;

void main()
{
	outColor = texture(myTexture, uv);
}