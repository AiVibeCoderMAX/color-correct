#version 330

/*  Color Correct — full-screen color grading shader.

    Follows the conventions of Minecraft's built-in post shaders:
      - input sampler is named after the pipeline input: "In" -> "InSampler"
      - texCoord comes from the vertex stage (screenquad)
      - layout(std140) uniform blocks are bound by the post-effect pipeline

    WARNING: the field order of ColorCorrectConfig must exactly match
    ColorCorrectConfig#uniformValues() in Java.                    */

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform ColorCorrectConfig {
    float uIntensity;         /* 0..100   */
    float uTemperature;       /* -100..100 */
    float uTint;              /* -100..100 */
    float uExposureEV;        /* -4..4    */
    float uContrast;          /* -100..100 */
    float uHighlights;        /* -100..100 */
    float uShadows;           /* -100..100 */
    float uWhites;            /* -100..100 */
    float uBlacks;            /* -100..100 */
    float uSaturation;        /* 0..200   */
    float uVibrance;          /* 0..100   */
    float uFade;              /* 0..100   */
    float uColorSpace;        /* 0 sRGB, 1 Rec.709, 2 DCI-P3, 3 Adobe RGB */
    float uLook;              /* 0 none .. 7 */
    float uFsShadowHue;
    float uFsShadowSat;
    float uFsHighlightHue;
    float uFsHighlightSat;
    float uSharpen;           /* 0..100   */
    float uClarity;           /* 0..100   */
    float uGrain;             /* 0..100   */
    float uVignette;          /* 0..100   */
    float uAberration;        /* 0..100   */
    float uTime;
    float uWheelShadowHue;
    float uWheelShadowSat;
    float uWheelMidHue;
    float uWheelMidSat;
    float uWheelHighlightHue;
    float uWheelHighlightSat;
};

out vec4 fragColor;

const float PI = 3.14159265358979;

vec3 srgbToLinear(vec3 c) {
    return mix(c / 12.92, pow((c + 0.055) / 1.055, vec3(2.4)), step(vec3(0.04045), c));
}

vec3 linearToSrgb(vec3 c) {
    return mix(c * 12.92, 1.055 * pow(c, vec3(1.0 / 2.4)) - 0.055, step(vec3(0.0031308), c));
}

float luma(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

vec3 hue2rgb(float h) {
    float r = clamp(abs(fract(h + 1.0) * 6.0 - 3.0) - 1.0, 0.0, 1.0);
    float g = clamp(abs(fract(h + 0.6666667) * 6.0 - 3.0) - 1.0, 0.0, 1.0);
    float b = clamp(abs(fract(h + 0.3333333) * 6.0 - 3.0) - 1.0, 0.0, 1.0);
    return vec3(r, g, b);
}

/* Convert linear sRGB into another display gamut (Rec.709 == sRGB primaries). */
vec3 convertGamut(vec3 c, float space) {
    if (space < 1.5 || space > 3.5) return c;
    float X = 0.4124564 * c.r + 0.3575761 * c.g + 0.1804375 * c.b;
    float Y = 0.2126729 * c.r + 0.7151522 * c.g + 0.0721750 * c.b;
    float Z = 0.0193339 * c.r + 0.1191920 * c.g + 0.9503041 * c.b;
    if (space < 2.5) {          /* DCI-P3 */
        return vec3( 2.4934969 * X - 0.9313836 * Y - 0.4027108 * Z,
                    -0.8294890 * X + 1.7626641 * Y + 0.0236247 * Z,
                     0.0358458 * X - 0.0761724 * Y + 0.9568845 * Z);
    }
    /* Adobe RGB */
    return vec3( 2.0413690 * X - 0.5649464 * Y - 0.3446944 * Z,
                -0.9692660 * X + 1.8760108 * Y + 0.0415560 * Z,
                 0.0134474 * X - 0.1183897 * Y + 1.0154096 * Z);
}

struct LookParams {
    vec3 gain;
    float satMul;
    float contrast;
    float fade;
    vec3 shadowTint;
    vec3 highlightTint;
};

/* "Creative > Look" presets — applied on top of the user's manual grade. */
LookParams lookParams(float look) {
    LookParams p;
    p.gain = vec3(1.0);
    p.satMul = 1.0;
    p.contrast = 0.0;
    p.fade = 0.0;
    p.shadowTint = vec3(0.0);
    p.highlightTint = vec3(0.0);
    int id = int(look + 0.5);
    if (id == 1) {          /* Warm Sunset */
        p.gain = vec3(1.18, 1.0, 0.85);
        p.satMul = 1.15;
        p.shadowTint = hue2rgb(0.06) * 0.40;
        p.highlightTint = hue2rgb(0.10) * 0.35;
    } else if (id == 2) {   /* Cool Arctic */
        p.gain = vec3(0.90, 1.0, 1.18);
        p.satMul = 1.06;
        p.shadowTint = hue2rgb(0.55) * 0.40;
        p.highlightTint = hue2rgb(0.58) * 0.30;
    } else if (id == 3) {   /* Vintage Film */
        p.gain = vec3(1.06, 0.99, 0.92);
        p.satMul = 0.80;
        p.contrast = -0.28;
        p.fade = 0.85;
        p.shadowTint = hue2rgb(0.09) * 0.35;
    } else if (id == 4) {   /* Noir */
        p.satMul = 0.05;
        p.contrast = 0.55;
        p.gain = vec3(0.96, 0.96, 1.03);
    } else if (id == 5) {   /* Vivid */
        p.satMul = 1.45;
        p.contrast = 0.28;
    } else if (id == 6) {   /* Faded Dream */
        p.satMul = 0.72;
        p.contrast = -0.22;
        p.fade = 1.0;
        p.gain = vec3(1.10, 1.02, 1.12);
    } else if (id == 7) {   /* Cyberpunk */
        p.satMul = 1.28;
        p.contrast = 0.38;
        p.shadowTint = hue2rgb(0.48) * 0.55;
        p.highlightTint = hue2rgb(0.93) * 0.55;
    }
    return p;
}

void main() {
    vec2 uv = texCoord;
    vec3 base = texture(InSampler, uv).rgb;

    /* ---------- gather (lens) ---------- */
    float ab = uAberration / 100.0;
    vec3 inp = base;
    if (ab > 0.001) {
        vec2 off = (uv - 0.5) * dot(uv - 0.5, uv - 0.5) * ab * 0.35;
        inp = vec3(texture(InSampler, uv + off).r,
                   texture(InSampler, uv).g,
                   texture(InSampler, uv - off).b);
    }

    /* ---------- decode ---------- */
    vec3 c = srgbToLinear(inp);

    /* ---------- exposure ---------- */
    c *= exp2(uExposureEV);

    /* ---------- white balance ---------- */
    float temp = uTemperature / 100.0;
    float tintC = uTint / 100.0;
    c.r *= exp2(0.45 * temp + 0.09 * tintC);
    c.b *= exp2(-0.35 * temp + 0.09 * tintC);
    c.g *= exp2(-0.16 * tintC);

    /* ---------- look preset ---------- */
    LookParams look = lookParams(uLook);
    c *= look.gain;

    /* ---------- display gamut ---------- */
    c = convertGamut(c, uColorSpace);

    /* ---------- tonal masks ---------- */
    float l = luma(c);
    float shMask = pow(clamp(1.0 - l, 0.0, 1.0), 2.0);
    float hlMask = pow(clamp(l, 0.0, 1.0), 2.0);
    float midMask = clamp(1.0 - abs(l - 0.5) * 2.0, 0.0, 1.0);

    /* shadows / highlights (neutral) */
    vec3 shadowLift = vec3(0.20 * (uShadows / 100.0));
    vec3 hlLift = vec3(0.16 * (uHighlights / 100.0));

    /* color balance wheels */
    shadowLift += hue2rgb(uWheelShadowHue) * uWheelShadowSat * 0.55;
    vec3 midLift = hue2rgb(uWheelMidHue) * uWheelMidSat * 0.55;
    hlLift += hue2rgb(uWheelHighlightHue) * uWheelHighlightSat * 0.55;

    /* split toning */
    shadowLift += hue2rgb(uFsShadowHue) * uFsShadowSat * 0.55;
    hlLift += hue2rgb(uFsHighlightHue) * uFsHighlightSat * 0.55;

    /* look tints */
    shadowLift += look.shadowTint * 0.6;
    hlLift += look.highlightTint * 0.6;

    c += shadowLift * shMask * 0.55;
    c += midLift * midMask * 0.45;
    c += hlLift * hlMask * 0.55;

    /* blacks / whites */
    float blackAmt = uBlacks / 100.0;
    float whiteAmt = uWhites / 100.0;
    c = c * (1.0 + 0.25 * whiteAmt) + 0.045 * blackAmt;

    /* contrast (pivot on 18% linear gray) */
    float cont = uContrast / 100.0 + look.contrast;
    c = (c - 0.18) * (1.0 + 1.25 * cont) + 0.18;

    /* saturation + vibrance */
    float chroma = max(max(c.r, c.g), c.b) - min(min(c.r, c.g), c.b);
    float vib = (uVibrance / 100.0) * 0.55 * (1.0 - chroma);
    float satFactor = (uSaturation / 100.0) * (1.0 + vib) * look.satMul;
    float grayL = luma(c);
    c = mix(vec3(grayL), c, satFactor);

    /* faded film (lifted, compressed blacks) */
    float fade = clamp(uFade / 100.0 + look.fade * 0.5, 0.0, 1.0);
    c = c * (1.0 - 0.40 * fade) + 0.05 * fade;

    /* ---------- detail: sharpen + clarity ---------- */
    float shAmount = uSharpen / 100.0;
    float clAmount = uClarity / 100.0;
    if (shAmount + clAmount > 0.001) {
        vec2 px = 1.0 / max(InSize, vec2(1.0));
        float s00 = luma(srgbToLinear(texture(InSampler, uv + vec2(px.x, 0.0)).rgb));
        float s10 = luma(srgbToLinear(texture(InSampler, uv - vec2(px.x, 0.0)).rgb));
        float s01 = luma(srgbToLinear(texture(InSampler, uv + vec2(0.0, px.y)).rgb));
        float s11 = luma(srgbToLinear(texture(InSampler, uv - vec2(0.0, px.y)).rgb));
        float sharpAvg = (s00 + s10 + s01 + s11) * 0.25;

        vec2 r3 = px * 3.0;
        float c00 = luma(srgbToLinear(texture(InSampler, uv + vec2(r3.x, r3.y)).rgb));
        float c10 = luma(srgbToLinear(texture(InSampler, uv - vec2(r3.x, r3.y)).rgb));
        float c01 = luma(srgbToLinear(texture(InSampler, uv + vec2(r3.x, -r3.y)).rgb));
        float c11 = luma(srgbToLinear(texture(InSampler, uv - vec2(r3.x, -r3.y)).rgb));
        float clarityAvg = (c00 + c10 + c01 + c11) * 0.25;

        float l2 = luma(c);
        float k = 1.0 + (l2 - sharpAvg) * (shAmount * 0.85)
                      + (l2 - clarityAvg) * (clAmount * 0.55);
        c *= max(k, 0.0);
    }

    /* ---------- film grain ---------- */
    if (uGrain > 0.001) {
        vec2 guv = uv * InSize + vec2(7.13, 3.71);
        float n = fract(sin(dot(guv, vec2(12.9898, 78.233)) + fract(uTime) * 43.7) * 43758.5453);
        c += (n - 0.5) * (uGrain / 100.0) * 0.38 * (0.5 + 1.0 * luma(c));
    }

    /* ---------- vignette ---------- */
    if (uVignette > 0.001) {
        vec2 d = uv - 0.5;
        d.x *= InSize.x / max(InSize.y, 1.0);
        float v = smoothstep(0.35, 0.95, length(d));
        c *= 1.0 - (uVignette / 100.0) * v * 0.85;
    }

    /* ---------- encode (target color space) ---------- */
    vec3 encoded;
    if (uColorSpace < 0.5) {
        encoded = linearToSrgb(clamp(c, 0.0, 1.0));
    } else if (uColorSpace < 1.5) {
        encoded = pow(clamp(c, 0.0, 1.0), vec3(1.0 / 2.2));      /* Rec.709 */
    } else if (uColorSpace < 2.5) {
        encoded = pow(clamp(c, 0.0, 1.0), vec3(1.0 / 2.6));      /* DCI-P3 */
    } else {
        encoded = pow(clamp(c, 0.0, 1.0), vec3(1.0 / 2.2));      /* Adobe RGB */
    }

    /* ---------- intensity blend ---------- */
    vec3 outColor = mix(inp, encoded, clamp(uIntensity / 100.0, 0.0, 1.0));

    fragColor = vec4(clamp(outColor, 0.0, 1.0), 1.0);
}
