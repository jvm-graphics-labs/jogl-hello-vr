// Attributes
#define POSITION    0
#define UV_COORDS   1
#define NORMAL      2

// Outputs
#define FRAG_COLOR  0

precision highp float;
precision highp int;
layout(std140, column_major) uniform;
layout(std430, column_major) buffer;