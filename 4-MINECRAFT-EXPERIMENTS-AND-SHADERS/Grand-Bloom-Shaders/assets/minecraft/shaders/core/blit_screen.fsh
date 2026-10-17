#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

void main() {
    vec4 base = texture(InSampler, texCoord);
    vec2 texel = 1.0 / vec2(textureSize(InSampler, 0));

    // 60Hz Anti-Tear & Anti-Judder Horizontal Micro-Reconstruction
    // Softens discrete raster scanline tear seams caused by 400-600 FPS on a 60Hz display
    vec3 leftSample = texture(InSampler, texCoord - vec2(texel.x * 0.75, 0.0)).rgb;
    vec3 rightSample = texture(InSampler, texCoord + vec2(texel.x * 0.75, 0.0)).rgb;
    vec3 smoothBase = base.rgb * 0.70 + (leftSample + rightSample) * 0.15;

    // 9-tap Gaussian Bloom Filter on bright highlights (sun, torches, lanterns, lava)
    vec3 bloom = vec3(0.0);
    float totalWeight = 0.0;

    for (int x = -2; x <= 2; x++) {
        for (int y = -2; y <= 2; y++) {
            vec2 offset = vec2(float(x), float(y)) * texel * 2.2;
            vec3 s = texture(InSampler, texCoord + offset).rgb;
            float lum = dot(s, vec3(0.2126, 0.7152, 0.0722));
            if (lum > 0.62) {
                float w = exp(-float(x*x + y*y) * 0.25);
                bloom += (s - 0.62) * w;
                totalWeight += w;
            }
        }
    }

    if (totalWeight > 0.0) {
        bloom /= totalWeight;
    }

    // Blend HDR bloom glow
    vec3 color = smoothBase + bloom * 0.40;

    // Cinematic Color Grading: Subtle Contrast & Rich Vibrance
    color = (color - 0.5) * 1.06 + 0.5;
    float gray = dot(color, vec3(0.299, 0.587, 0.114));
    color = mix(vec3(gray), color, 1.12);

    fragColor = vec4(clamp(color, 0.0, 1.0), base.a);
}
