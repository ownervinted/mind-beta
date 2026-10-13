#version 150

in vec2 fragCoord;
in vec2 pixelCoord;
in vec2 texCoord;
in vec2 rectSize;
in vec4 cornerRadii;
in vec4 fragColors[4];
in float fragSmoothness;
in float guiScale;

out vec4 fragColor;

uniform sampler2D Sampler0;

float roundedBoxSDF(vec2 p, vec2 b, vec4 r) {
    r.xy = (p.x > 0.0) ? r.yz : r.xw;
    r.x = (p.y > 0.0) ? r.y : r.x;
    vec2 q = abs(p) - b + r.x;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r.x;
}

vec4 sampleGradient(vec2 uv) {
    vec4 top = mix(fragColors[0], fragColors[3], uv.x);
    vec4 bottom = mix(fragColors[1], fragColors[2], uv.x);
    return mix(top, bottom, uv.y);
}

vec4 sampleBlurredTexture(vec2 uv, vec2 stepX, vec2 stepY) {

    float weights[5] = float[](1.0, 4.0, 6.0, 4.0, 1.0);
    vec2 halfTexel = 0.5 / vec2(textureSize(Sampler0, 0));
    vec4 result = vec4(0.0);
    for (int y = 0; y < 5; ++y) {
        for (int x = 0; x < 5; ++x) {
            vec2 sampleUV = uv + float(x - 2) * stepX + float(y - 2) * stepY;
            sampleUV = clamp(sampleUV, halfTexel, vec2(1.0) - halfTexel);
            result += texture(Sampler0, sampleUV) * weights[x] * weights[y];
        }
    }
    return result / 256.0;
}

void main() {
    float blurRadius = max(fragSmoothness, 0.0) * guiScale;
    vec2 stepX = dFdx(texCoord) * blurRadius;
    vec2 stepY = dFdy(texCoord) * blurRadius;
    vec2 halfSize = rectSize * 0.5;
    vec2 center = pixelCoord - halfSize;

    vec4 radii = min(cornerRadii, vec4(min(halfSize.x, halfSize.y)));
    float dist = roundedBoxSDF(center, halfSize, radii);

    float edgeSmoothing = fwidth(dist);
    float alpha = clamp(0.5 - dist / max(edgeSmoothing, 0.0001), 0.0, 1.0);

    if (alpha <= 0.0) {
        discard;
    }
    vec4 texColor = sampleBlurredTexture(texCoord, stepX, stepY);

    vec4 tintColor = sampleGradient(fragCoord);

    vec3 finalColor = texColor.rgb * tintColor.rgb;
    float finalAlpha = texColor.a * tintColor.a * alpha;

    fragColor = vec4(finalColor, finalAlpha);
}
