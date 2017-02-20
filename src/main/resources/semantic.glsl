// Attributes
#define COLOR       0
#define NORMAL      1
#define POSITION    2
#define TEX_COORD   3

// Outputs
#define FRAG_COLOR  0

precision highp float;
precision highp int;
layout (std140, column_major) uniform;
//layout (std430, column_major) buffer; // no ssbo in <4.3