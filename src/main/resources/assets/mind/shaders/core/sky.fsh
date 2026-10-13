#version 150

layout(std140) uniform SkyData {
    vec4 screenTimePreset;
    vec4 worldWeather;
    vec4 effectCloud;
    vec4 shootingCamera;
    mat4 inverseView;
    mat4 inverseProjection;
};


in vec2 skyUv;
out vec4 fragColor;

const float PI = 3.14159265358979323846;
const float TAU = 6.28318530717958647692;

float saturate(float v) {
    return clamp(v, 0.0, 1.0);
}

float hash11(float p) {
    p = fract(p * 0.1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
}

float hash21(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float hash31(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}

float noise3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);

    float n000 = hash31(i + vec3(0.0, 0.0, 0.0));
    float n100 = hash31(i + vec3(1.0, 0.0, 0.0));
    float n010 = hash31(i + vec3(0.0, 1.0, 0.0));
    float n110 = hash31(i + vec3(1.0, 1.0, 0.0));
    float n001 = hash31(i + vec3(0.0, 0.0, 1.0));
    float n101 = hash31(i + vec3(1.0, 0.0, 1.0));
    float n011 = hash31(i + vec3(0.0, 1.0, 1.0));
    float n111 = hash31(i + vec3(1.0, 1.0, 1.0));

    float nx00 = mix(n000, n100, f.x);
    float nx10 = mix(n010, n110, f.x);
    float nx01 = mix(n001, n101, f.x);
    float nx11 = mix(n011, n111, f.x);
    float nxy0 = mix(nx00, nx10, f.y);
    float nxy1 = mix(nx01, nx11, f.y);
    return mix(nxy0, nxy1, f.z);
}

float fbm3(vec3 p) {
    float value = 0.0;
    float amplitude = 0.55;
    for (int i = 0; i < 3; i++) {
        value += noise3(p) * amplitude;
        p = p * 2.07 + vec3(7.7, 3.1, 5.9);
        amplitude *= 0.48;
    }
    return value;
}

float fbm5(vec3 p) {
    float value = 0.0;
    float amplitude = 0.54;
    for (int i = 0; i < 5; i++) {
        value += noise3(p) * amplitude;
        p = p * 2.03 + vec3(4.3, 8.1, 2.6);
        amplitude *= 0.49;
    }
    return value;
}

vec3 getWorldRay(vec2 uv) {
    vec2 ndc = uv * 2.0 - 1.0;

    vec4 viewFar = inverseProjection * vec4(ndc, 1.0, 1.0);
    float safeW = abs(viewFar.w) < 0.000001
        ? (viewFar.w < 0.0 ? -0.000001 : 0.000001)
        : viewFar.w;
    vec3 viewDirection = normalize(viewFar.xyz / safeW);

    return normalize((inverseView * vec4(viewDirection, 0.0)).xyz);
}

vec3 getSunDirection(float worldTime) {
    float phase = ((worldTime - 6000.0) / 24000.0) * TAU;
    return normalize(vec3(-0.18, cos(phase), sin(phase)));
}

float rayleighPhase(float mu) {
    return 0.75 * (1.0 + mu * mu);
}

float miePhase(float mu, float g) {
    float g2 = g * g;
    float denom = max(1.0 + g2 - 2.0 * g * mu, 0.001);
    return (1.0 - g2) / (4.0 * PI * pow(denom, 1.5));
}

vec3 atmosphere(vec3 ray, vec3 sunDir, int preset, float rain) {
    float sunHeight = sunDir.y;
    float day = smoothstep(-0.10, 0.055, sunHeight);
    float night = 1.0 - smoothstep(-0.16, 0.03, sunHeight);
    float sunset = exp(-pow(sunHeight / 0.12, 2.0));

    float up = max(ray.y, 0.0);
    float horizon = exp(-max(ray.y, 0.0) * 5.2);
    float below = smoothstep(0.02, -0.32, ray.y);
    float mu = dot(ray, sunDir);

    vec3 nightZenith = vec3(0.0022, 0.0055, 0.020);
    vec3 nightHorizon = vec3(0.014, 0.027, 0.060);
    vec3 dayZenith = vec3(0.035, 0.17, 0.46);
    vec3 dayHorizon = vec3(0.34, 0.57, 0.86);

    if (preset == 3) {
        nightZenith = vec3(0.0015, 0.009, 0.025);
        nightHorizon = vec3(0.008, 0.060, 0.075);
    } else if (preset == 4) {
        nightZenith = vec3(0.0008, 0.0012, 0.0050);
        nightHorizon = vec3(0.006, 0.010, 0.024);
        dayZenith *= 0.22;
        dayHorizon *= 0.28;
    }

    vec3 baseNight = mix(nightHorizon, nightZenith, pow(up, 0.42));
    vec3 baseDay = mix(dayHorizon, dayZenith, pow(up, 0.34));
    vec3 color = mix(baseNight, baseDay, day);

    vec3 betaR = vec3(0.16, 0.36, 0.82);
    vec3 rayleigh = betaR * rayleighPhase(mu) * (0.10 + 0.33 * horizon) * day;
    vec3 mieColor = vec3(1.0, 0.62, 0.30);
    float mie = miePhase(mu, 0.76) * (0.032 + 0.16 * sunset) * day;
    color += rayleigh + mieColor * mie;

    vec3 sunsetColor = vec3(1.10, 0.22, 0.055);
    float sunsetBand = sunset * horizon * pow(max(mu * 0.5 + 0.5, 0.0), 1.7);
    color += sunsetColor * sunsetBand * 0.72;

    float sunDisc = smoothstep(0.99990, 0.999985, mu);
    float sunGlow = pow(max(mu, 0.0), 220.0);
    color += vec3(1.0, 0.76, 0.40) * (sunDisc * 10.0 + sunGlow * 0.9) * day;

    vec3 moonDir = -sunDir;
    float moonMu = dot(ray, moonDir);
    float moonDisc = smoothstep(0.99972, 0.99992, moonMu);
    float moonGlow = pow(max(moonMu, 0.0), 180.0);
    color += vec3(0.50, 0.64, 0.90) * (moonDisc * 1.6 + moonGlow * 0.17) * night;

    color = mix(color, vec3(0.060, 0.075, 0.095), rain * 0.42);
    color *= 1.0 - below * 0.68;
    return color;
}

float starPlane(vec2 p, float scale, float threshold, float seed, float time, out float temperature) {
    vec2 grid = p * scale;
    vec2 cell = floor(grid);
    vec2 local = fract(grid) - 0.5;

    float random = hash21(cell + seed);
    temperature = hash21(cell + seed + 43.17);
    if (random < threshold) return 0.0;

    vec2 offset = vec2(
        hash21(cell + seed + 11.7),
        hash21(cell + seed + 29.3)
    ) - 0.5;
    local -= offset * 0.72;

    float dist = length(local);
    float sizeRnd = mix(0.026, 0.065, hash21(cell + seed + 71.0));
    float core = smoothstep(sizeRnd, 0.0, dist);
    float halo = smoothstep(sizeRnd * 3.4, 0.0, dist) * 0.10;
    float twinkle = 0.90 + 0.10 * sin(time * mix(1.2, 2.7, random) + random * 90.0);
    return (core + halo) * twinkle;
}

vec3 starField(vec3 ray, float intensity, float night, float rain, float time) {
    if (intensity <= 0.001 || night <= 0.001 || ray.y < -0.15) return vec3(0.0);

    vec3 weights = pow(abs(ray), vec3(8.0));
    weights /= max(weights.x + weights.y + weights.z, 0.0001);

    float tx1, tx2, ty1, ty2, tz1, tz2;
    float sx = starPlane(ray.yz, 720.0, 0.9925, 7.0, time, tx1)
             + starPlane(ray.yz + vec2(0.031, 0.067), 390.0, 0.9955, 31.0, time * 0.8, tx2) * 1.55;
    float sy = starPlane(ray.xz, 720.0, 0.9925, 83.0, time, ty1)
             + starPlane(ray.xz + vec2(0.071, 0.019), 390.0, 0.9955, 127.0, time * 0.8, ty2) * 1.55;
    float sz = starPlane(ray.xy, 720.0, 0.9925, 173.0, time, tz1)
             + starPlane(ray.xy + vec2(0.047, 0.091), 390.0, 0.9955, 229.0, time * 0.8, tz2) * 1.55;

    float s = sx * weights.x + sy * weights.y + sz * weights.z;
    float temp = (tx1 + tx2 + ty1 + ty2 + tz1 + tz2) / 6.0;
    vec3 color = mix(vec3(0.55, 0.72, 1.0), vec3(1.0, 0.78, 0.58), temp * 0.42);

    float horizonFade = smoothstep(-0.08, 0.20, ray.y);
    return color * s * intensity * night * horizonFade * (1.0 - rain * 0.90);
}

vec3 milkyWay(vec3 ray, float intensity, float night, float time, int preset) {
    if (intensity <= 0.001 || night <= 0.001) return vec3(0.0);

    vec3 axis = normalize(vec3(-0.31, 0.88, 0.36));
    float bandDistance = abs(dot(ray, axis));
    float band = exp(-pow(bandDistance * 3.5, 1.55));

    vec3 p = ray * 5.0 + vec3(time * 0.0032, -time * 0.0017, time * 0.0021);
    float n1 = fbm5(p);
    float n2 = fbm3(ray * 12.0 - vec3(time * 0.002, 0.0, -time * 0.0014));
    float dust = smoothstep(0.47, 0.83, n1 * 0.72 + n2 * 0.30 + band * 0.32);
    float core = smoothstep(0.08, 0.88, band * (0.40 + n1));

    vec3 c1 = vec3(0.035, 0.10, 0.28);
    vec3 c2 = vec3(0.18, 0.055, 0.30);
    vec3 c3 = vec3(0.025, 0.23, 0.38);

    if (preset == 2) {
        c1 = vec3(0.035, 0.16, 0.55);
        c2 = vec3(0.34, 0.055, 0.48);
        c3 = vec3(0.025, 0.38, 0.64);
    } else if (preset == 4) {
        c1 = vec3(0.055, 0.018, 0.22);
        c2 = vec3(0.16, 0.025, 0.34);
        c3 = vec3(0.018, 0.065, 0.28);
    }

    vec3 color = c1 * dust + c2 * core * (0.35 + n2 * 0.8);
    color += c3 * pow(max(dust * core, 0.0), 1.45) * 0.65;
    return color * intensity * night * (0.30 + band * 0.75);
}

vec3 aurora(vec3 ray, float intensity, float night, float time) {
    if (intensity <= 0.001 || night <= 0.001 || ray.y < 0.0) return vec3(0.0);

    float azimuth = atan(ray.z, ray.x);
    float heightMask = smoothstep(0.02, 0.19, ray.y) * (1.0 - smoothstep(0.54, 0.90, ray.y));
    float noise = fbm3(ray * 5.2 + vec3(time * 0.008, 0.0, -time * 0.004));

    float waveA = sin(azimuth * 7.0 + noise * 7.0 + time * 0.055);
    float waveB = sin(azimuth * 13.0 - noise * 4.5 - time * 0.041);
    float curtain = pow(saturate(waveA * 0.33 + waveB * 0.17 + 0.56), 5.0);
    curtain *= heightMask;

    float veil = smoothstep(0.52, 0.78, noise) * heightMask;
    vec3 color = vec3(0.025, 0.68, 0.48) * curtain;
    color += vec3(0.018, 0.18, 0.66) * veil * 0.56;
    color += vec3(0.29, 0.045, 0.42) * pow(curtain, 1.7) * 0.38;
    return color * intensity * night;
}

float cloudShape(vec3 worldPos, float time, float amount) {
    vec3 p = worldPos * vec3(0.00155, 0.0065, 0.00155);
    vec3 wind = vec3(time * 0.0055, 0.0, -time * 0.0031);

    float warpA = fbm3(p * 0.62 + wind * 0.7);
    float warpB = fbm3(p * 0.58 + vec3(17.0, 3.0, 9.0) - wind * 0.45);
    p.xz += (vec2(warpA, warpB) - 0.5) * 0.85;

    float macro = fbm5(p + wind);
    float detail = fbm3(p * 3.3 - wind * 0.45);
    float erosion = fbm3(p * 7.8 + vec3(4.0, 11.0, 2.0));

    float coverage = mix(0.70, 0.43, saturate(amount * 0.55));
    float density = macro * 0.82 + detail * 0.24 - erosion * 0.095 - coverage;
    return saturate(density * 3.6);
}

vec4 volumetricClouds(vec3 ray, vec3 sunDir, float amount, float quality, float rain, float time, vec3 skyColor) {
    if (amount <= 0.001) return vec4(0.0);

    float cloudCenter = 340.0;
    float halfThickness = 66.0;
    if (abs(ray.y) < 0.006) return vec4(0.0);

    float tBottom = (cloudCenter - halfThickness - shootingCamera.z) / ray.y;
    float tTop = (cloudCenter + halfThickness - shootingCamera.z) / ray.y;
    float t0 = max(min(tBottom, tTop), 0.0);
    float t1 = max(tBottom, tTop);
    if (t1 <= 0.0 || t1 <= t0) return vec4(0.0);

    t1 = min(t1, 6200.0);
    if (t1 <= t0) return vec4(0.0);

    int steps = 12;
    if (quality < 0.5) steps = 8;
    if (quality > 1.5) steps = 18;

    float segment = (t1 - t0) / float(steps);
    float jitter = hash21(gl_FragCoord.xy) - 0.5;
    float t = t0 + segment * (0.5 + jitter * 0.35);

    vec3 accum = vec3(0.0);
    float alpha = 0.0;
    float sunAbove = saturate(sunDir.y * 5.0 + 0.35);
    vec3 sunLight = mix(vec3(0.18, 0.23, 0.34), vec3(1.0, 0.80, 0.57), sunAbove);
    vec3 ambient = mix(vec3(0.030, 0.045, 0.075), skyColor * 0.70 + vec3(0.025), 0.72);

    for (int i = 0; i < 18; i++) {
        if (i >= steps) break;

        vec3 wp = shootingCamera.yzw + ray * t;
        float heightNorm = (wp.y - (cloudCenter - halfThickness)) / (halfThickness * 2.0);
        float heightProfile = smoothstep(0.0, 0.18, heightNorm) * (1.0 - smoothstep(0.72, 1.0, heightNorm));
        heightProfile *= 0.78 + 0.22 * sin(heightNorm * PI);

        float density = cloudShape(wp, time, amount) * heightProfile;
        density *= mix(1.0, 1.22, rain);

        if (density > 0.005) {
            float lightDensity = cloudShape(wp + sunDir * 52.0, time, amount) * heightProfile;
            float selfShadow = exp(-lightDensity * 2.6);
            float forwardScatter = pow(saturate(dot(ray, sunDir)), 18.0) * 0.36;
            float edge = saturate((density - lightDensity) * 3.0 + 0.45);

            vec3 sampleColor = ambient * (0.56 + edge * 0.24);
            sampleColor += sunLight * selfShadow * (0.28 + edge * 0.72);
            sampleColor += sunLight * forwardScatter * selfShadow;
            sampleColor = mix(sampleColor, vec3(0.075, 0.085, 0.105), rain * 0.58);

            float sampleAlpha = 1.0 - exp(-density * segment * 0.020);
            sampleAlpha = clamp(sampleAlpha, 0.0, 0.32);

            accum += sampleColor * sampleAlpha * (1.0 - alpha);
            alpha += sampleAlpha * (1.0 - alpha);
            if (alpha > 0.985) break;
        }

        t += segment;
    }

    float horizonFade = smoothstep(0.008, 0.055, abs(ray.y));
    alpha *= horizonFade;
    accum *= horizonFade;
    return vec4(accum, alpha);
}

vec3 shootingStar(vec3 ray, float rate, float brightness, float night, float time, float rain) {
    if (rate <= 0.001 || brightness <= 0.001 || night <= 0.08 || rain > 0.70) return vec3(0.0);



    float slotLength = mix(16.0, 8.0, saturate(rate));
    float slot = floor(time / slotLength);
    float localTime = fract(time / slotLength);
    float spawnChance = saturate(rate * 0.72);
    if (hash11(slot * 13.71 + 4.2) > spawnChance) return vec3(0.0);

    float begin = 0.16 + hash11(slot * 5.13 + 9.1) * 0.40;
    float duration = 0.055 + hash11(slot * 8.37 + 2.0) * 0.045;
    float life = (localTime - begin) / duration;
    if (life < 0.0 || life > 1.0) return vec3(0.0);

    float azimuth = hash11(slot * 2.93 + 17.0) * TAU;
    float elevation = mix(0.34, 1.02, hash11(slot * 6.27 + 3.0));
    vec3 center = normalize(vec3(cos(azimuth) * cos(elevation), sin(elevation), sin(azimuth) * cos(elevation)));

    vec3 tangentX = normalize(cross(abs(center.y) > 0.92 ? vec3(1.0, 0.0, 0.0) : vec3(0.0, 1.0, 0.0), center));
    vec3 tangentY = normalize(cross(center, tangentX));

    float angle = mix(-0.65, 0.65, hash11(slot * 4.11 + 22.0));
    vec2 travel2 = normalize(vec2(cos(angle), sin(angle) * 0.58 - 0.42));
    vec3 travel = normalize(tangentX * travel2.x + tangentY * travel2.y);

    float travelAngle = mix(0.030, 0.070, hash11(slot * 9.17 + 41.0));
    float easedLife = life * life * (3.0 - 2.0 * life);
    vec3 head = normalize(center + travel * ((easedLife - 0.42) * travelAngle));


    float rx = dot(ray, travel);
    vec3 side = normalize(cross(head, travel));
    float ry = dot(ray, side);
    float forward = dot(ray, head);

    float headAngular = acos(clamp(forward, -1.0, 1.0));
    float headCore = exp(-headAngular * headAngular / 0.0000017);
    float headGlow = exp(-headAngular * headAngular / 0.000022) * 0.16;


    float tailLength = 0.040;
    float behind = clamp(-rx / tailLength, 0.0, 1.0);
    float tailWidth = mix(0.00125, 0.00030, behind);
    float line = exp(-(ry * ry) / max(tailWidth * tailWidth, 0.0000001));
    float angularGate = exp(-pow(max(headAngular - behind * tailLength, 0.0) / 0.018, 2.0));
    float tail = line * behind * behind * angularGate * 0.72;

    float envelope = smoothstep(0.0, 0.12, life) * (1.0 - smoothstep(0.62, 1.0, life));
    float warm = hash11(slot * 21.7 + 1.0);
    vec3 tint = mix(vec3(0.52, 0.72, 1.0), vec3(1.0, 0.88, 0.70), warm * 0.45);

    return tint * (headCore * 2.8 + headGlow + tail) * envelope * brightness * night * (1.0 - rain);
}

void main() {
    vec3 ray = getWorldRay(skyUv);

    float animationTime = screenTimePreset.z;
    int preset = int(screenTimePreset.w + 0.5);
    float worldTime = worldWeather.x;
    float rain = saturate(worldWeather.y);
    float brightness = worldWeather.z;
    float starIntensity = worldWeather.w;
    float effectIntensity = effectCloud.x;
    float cloudAmount = effectCloud.y;
    float cloudQuality = effectCloud.z;
    float shootingRate = effectCloud.w;
    float shootingBrightness = shootingCamera.x;

    vec3 sunDir = getSunDirection(worldTime);
    float day = smoothstep(-0.10, 0.055, sunDir.y);
    float night = 1.0 - smoothstep(-0.16, 0.03, sunDir.y);

    vec3 baseSky = atmosphere(ray, sunDir, preset, rain);
    vec3 color = baseSky;

    if (preset == 1) {
        color += milkyWay(ray, effectIntensity * 0.42, night, animationTime, preset);
        color += aurora(ray, effectIntensity * 0.14, night, animationTime);
    } else if (preset == 2) {
        color += milkyWay(ray, effectIntensity * 1.18, max(night, 0.72), animationTime, preset);
    } else if (preset == 3) {
        color += aurora(ray, effectIntensity * 1.10, max(night, 0.78), animationTime);
        color += milkyWay(ray, effectIntensity * 0.20, night, animationTime, preset);
    } else if (preset == 4) {
        color += milkyWay(ray, effectIntensity * 1.05, 1.0, animationTime, preset);
        night = max(night, 0.88);
        day *= 0.18;
    }

    color += starField(ray, starIntensity, night, rain, animationTime);
    color += shootingStar(ray, shootingRate, shootingBrightness, night, animationTime, rain);

    vec4 clouds = volumetricClouds(ray, sunDir, cloudAmount, cloudQuality, rain, animationTime, baseSky);
    color = color * (1.0 - clouds.a) + clouds.rgb;

    float horizonHaze = exp(-abs(ray.y) * 8.5) * mix(0.025, 0.055, day);
    color += mix(vec3(0.025, 0.035, 0.060), vec3(0.18, 0.28, 0.46), day) * horizonHaze * (1.0 - rain * 0.45);

    color *= brightness;
    color = color / (color + vec3(0.72));
    color = pow(max(color, vec3(0.0)), vec3(0.88));
    fragColor = vec4(color, 1.0);
}
