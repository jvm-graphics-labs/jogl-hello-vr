#version 410 core

#include semantic.glsl

layout(location = POSITION) in vec2 position;
layout(location = UV_RED) in vec2 uvRed;
layout(location = UV_GREEN) in vec2 uvGreen;
layout(location = UV_BLUE) in vec2 uvBlue;

noperspective out vec2 uvRed_;
noperspective out vec2 uvGreen_;
noperspective out vec2 uvBlue_;

void main()
{
    uvRed_ = uvRed;
    uvGreen_ = uvGreen;
    uvBlue_ = uvBlue;
    gl_Position = vec4(position, 0.0, 1.0);
}