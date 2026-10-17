package com.example.unifiedreader.feature.reader.comics

/**
 * Cylinder page curl. The leaf lies flat where `x = dot(p - origin, dir) <= 0`, wraps around a
 * cylinder of `radius` on `0 < x < radius`, and continues flat on top, back face up, beyond it.
 * Inputs are canvas-sized composites in canvas coordinates:
 *  - sheetFront: the turning leaf's front; sheetBack: its back (read mirrored about the spine when
 *    `backMirrored` is set, otherwise a paper tint with the front bleeding through);
 *  - revealed: what the lifted leaf uncovers inside its own paper;
 *  - outside: everything off the leaf (the facing page, margins).
 * Rects are (left, top, right, bottom); the leaf is only ever drawn inside `book`.
 */
internal const val ComicPageCurlAgsl = """
uniform shader sheetFront;
uniform shader sheetBack;
uniform shader revealed;
uniform shader outside;
uniform float4 paper;
uniform float4 book;
uniform float2 origin;
uniform float2 dir;
uniform float radius;
uniform float hingeX;
uniform float backMirrored;

const float PI = 3.141592653589793;
const half3 PaperTint = half3(0.965, 0.955, 0.94);
const half BleedThrough = 0.18;

bool inRect(float2 p, float4 r) {
    return p.x >= r.x && p.y >= r.y && p.x <= r.z && p.y <= r.w;
}

float rectDistance(float2 p, float4 r) {
    float2 d = max(max(r.xy - p, p - r.zw), float2(0.0));
    return length(d);
}

half4 shaded(half4 c, float k) {
    return half4(c.rgb * half(k), c.a);
}

half4 backFace(float2 pp, float light) {
    half3 c;
    if (backMirrored > 0.5) {
        c = sheetBack.eval(float2(2.0 * hingeX - pp.x, pp.y)).rgb;
    } else {
        c = mix(PaperTint, sheetFront.eval(pp).rgb, BleedThrough);
    }
    return half4(c * half(light), 1.0);
}

half4 main(float2 p) {
    if (!inRect(p, book)) {
        return outside.eval(p);
    }
    float r = max(radius, 0.5);
    float shadowWidth = max(r, 6.0);
    float x = dot(p - origin, dir);

    if (x <= 0.0) {
        // Flat region: the folded-over flap lies on top where it reaches.
        float2 flap = p + dir * (PI * r - 2.0 * x);
        if (inRect(flap, paper)) {
            return backFace(flap, 1.0);
        }
        half4 base = inRect(p, paper) ? sheetFront.eval(p) : outside.eval(p);
        float flapShadow = 1.0 - smoothstep(0.0, shadowWidth, rectDistance(flap, paper));
        return shaded(base, 1.0 - 0.22 * flapShadow);
    }

    if (x < r) {
        float t = x / r;
        float theta = asin(t);
        // Upper half of the roll: back face, darkening toward the silhouette, soft highlight.
        float2 upper = p + dir * ((PI - theta) * r - x);
        if (inRect(upper, paper)) {
            float light = 1.0 - 0.42 * pow(t, 2.4) + 0.07 * exp(-pow((t - 0.35) / 0.13, 2.0));
            return backFace(upper, light);
        }
        // Lower half: the front face curving up, in the roll's own shade.
        float2 lower = p + dir * (theta * r - x);
        if (inRect(lower, paper)) {
            return shaded(sheetFront.eval(lower), 1.0 - 0.45 * pow(t, 1.6));
        }
    }

    // Uncovered: the lifted leaf casts a soft shadow just beyond the roll.
    half4 base = inRect(p, paper) ? revealed.eval(p) : outside.eval(p);
    float2 rollTop = p + dir * (PI * r * 0.5 - x);
    float presence = 1.0 - smoothstep(0.0, 6.0, rectDistance(rollTop, paper));
    float s = clamp((x - 0.5 * r) / (1.8 * shadowWidth), 0.0, 1.0);
    return shaded(base, 1.0 - 0.38 * presence * (1.0 - s) * (1.0 - s));
}
"""
