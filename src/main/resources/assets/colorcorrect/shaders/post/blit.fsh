#version 330

/* Simple pass-through copy used to write the graded image back to "main". */

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

out vec4 fragColor;

void main() {
    fragColor = vec4(texture(InSampler, texCoord).rgb, 1.0);
}
