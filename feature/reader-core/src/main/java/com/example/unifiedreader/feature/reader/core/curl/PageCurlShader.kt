package com.example.unifiedreader.feature.reader.core.curl

/**
 * Cylinder page curl. The leaf lies flat where `x = dot(p - origin, dir) <= 0`, wraps around a
 * cylinder of `radius` on `0 < x < radius`, and continues flat on top, back face up, beyond it.
 * Inputs are canvas-sized composites in canvas coordinates:
 *  - sheetFront: the turning leaf's front; sheetBack: its back (read mirrored about the spine when
 *    `backMirrored` is set, otherwise `backTint`, the paper colour, with the front bleeding through);
 *  - revealed: what the lifted leaf uncovers inside its own paper;
 *  - outside: everything off the leaf (the facing page, margins).
 * Rects are (left, top, right, bottom); the leaf is only ever drawn inside `book`. Shadows scale
 * with `maxRadius`, the roll's radius away from the spine.
 */
const val PageCurlAgsl = """
uniform shader sheetFront;
uniform shader sheetBack;
uniform shader revealed;
uniform shader outside;
uniform float4 paper;
uniform float4 book;
uniform float2 origin;
uniform float2 dir;
uniform float radius;
uniform float maxRadius;
uniform float hingeX;
uniform float backMirrored;
uniform float4 backTint;

const float PI = 3.141592653589793;
const half BleedThrough = 0.18;
const float OverhangShadow = 0.2;
const float RollShadow = 0.36;

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
        c = mix(half3(backTint.rgb), sheetFront.eval(pp).rgb, BleedThrough);
    }
    return half4(c * half(light), 1.0);
}

// Shade from the turned-over part of the leaf hanging above the page next to the fold. [overhang] is
// how far p is from that part's free edge as if it lay flat on top; the inside of the roll uses the
// same measure, so the page darkens continuously as it curls up under the edge.
float overhangShade(float overhang, float r) {
    float k = 1.0 - clamp(overhang / (0.15 * maxRadius + 0.35 * r), 0.0, 1.0);
    return 1.0 - OverhangShadow * k * k;
}

// Distance along dir from q to where that line leaves the paper.
float paperExit(float2 q) {
    float2 d = float2(
        abs(dir.x) < 0.0001 ? 0.0001 : dir.x,
        abs(dir.y) < 0.0001 ? 0.0001 : dir.y
    );
    float2 exits = max((paper.xy - q) / d, (paper.zw - q) / d);
    return min(exits.x, exits.y);
}

half4 main(float2 p) {
    if (!inRect(p, book)) {
        return outside.eval(p);
    }
    float r = max(radius, 0.5);
    float x = dot(p - origin, dir);
    float2 flap = p + dir * (PI * r - 2.0 * x);
    float overhang = overhangShade(rectDistance(flap, paper), r);

    if (x <= 0.0) {
        // Flat region: the folded-over flap lies on top where it reaches.
        if (inRect(flap, paper)) {
            return backFace(flap, 1.0);
        }
        half4 base = inRect(p, paper) ? sheetFront.eval(p) : outside.eval(p);
        return shaded(base, overhang);
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
        // Lower half: the front face curving up, turned from the light and under the free edge.
        float2 lower = p + dir * (theta * r - x);
        if (inRect(lower, paper)) {
            return shaded(sheetFront.eval(lower), (1.0 - 0.45 * pow(t, 1.6)) * overhang);
        }
    }

    // Uncovered: the roll on this line casts a shadow beyond its silhouette that grows as it rises.
    half4 base = inRect(p, paper) ? revealed.eval(p) : outside.eval(p);
    float2 fold = p - dir * x;
    float lifted = clamp(paperExit(fold), 0.0, PI * r);
    float height = r * (1.0 - cos(lifted / r));
    float silhouette = r * sin(min(lifted, 0.5 * PI * r) / r);
    float width = 0.25 * maxRadius + 0.6 * height;
    float presence = 1.0 - smoothstep(0.0, 0.5 * width, rectDistance(fold + dir * (0.5 * lifted), paper));
    float fade = 1.0 - clamp((x - silhouette) / width, 0.0, 1.0);
    float shadow = RollShadow * presence * smoothstep(0.0, r, height) * fade * fade;
    return shaded(base, 1.0 - shadow);
}
"""
