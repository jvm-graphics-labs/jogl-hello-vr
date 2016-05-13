#version 410 core

#include semantic.glsl

uniform sampler2D myTexture;

noperspective in vec2 uvRed_;
noperspective in vec2 uvGreen_;
noperspective in vec2 uvBlue_;

layout (location = FRAG_COLOR) out vec4 outputColor;

void main()
{
    vec2 lt = vec2(lessThan(uvGreen_, vec2(0.05)));
    vec2 gt = vec2(greaterThan(uvGreen_, vec2(0.95)));
    float boundsCheck = dot(lt, vec2(1.0)) + dot(gt, vec2(1.0));
    if(boundsCheck > 1.0 )
        outputColor = vec4(0, 0, 0, 1.0);
    else
    {
        float red = texture(myTexture, uvRed_).x;
        float green = texture(myTexture, uvGreen_).y;
        float blue = texture(myTexture, uvBlue_).z;
        outputColor = vec4(red, green, blue, 1.0); 
    }    
}