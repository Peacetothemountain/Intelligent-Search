# 🔒 PERMANENT SPECIFICATION: Gemini Search Plate AGSL Shader & Visuals
> **STATUS: LOCKED & FROZEN — NEVER MODIFY OR OVERWRITE**
> **Git Tag:** `gemini-search-plate-golden-v1`
> **Verified Device:** Google Pixel 11 Pro XL

---

## 1. AGSL Shader Code (`DynamicMeshShader.kt`)

```glsl
@Language("AGSL")
private const val AGSL_GEMINI_PLATE = """
    uniform float2 uSize;
    uniform float uRadius;
    uniform float uDp;
    layout(color) uniform half4 uCenterColor;
    layout(color) uniform half4 uOuterColor;
    layout(color) uniform half4 uBottomLiftColor;
    layout(color) uniform half4 uTopRimColor;

    float sdCapsuleRaw(float2 p, float2 halfDims, float r) {
        float2 q = abs(p) - halfDims + r;
        return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
    }

    half4 main(float2 p) {
        float2 hd = uSize * 0.5;
        float2 pos = p - hd;
        float r = min(uRadius, min(hd.x, hd.y));
        float dist = sdCapsuleRaw(pos, hd, r);

        // Anti-aliased outer edge of the stadium pill
        half shapeAlpha = half(clamp(1.0 - dist / (1.0 * uDp), 0.0, 1.0));
        if (shapeAlpha <= 0.0) {
            return half4(0.0);
        }

        // Relative coordinates (0.0 to 1.0)
        float u = clamp(p.x / uSize.x, 0.0, 1.0);
        float v = clamp(p.y / uSize.y, 0.0, 1.0);

        // 1. Natural end-cap ambient glow (diffuses smoothly into the outer ~20% of each end)
        float dxCenter = abs(u - 0.50);
        float capDist = max(0.0, (dxCenter - 0.30) / 0.20);
        float capGlow = pow(capDist, 1.8);

        // 2. Bottom upward glow (starts at v=0.40, smoothly rises toward the bottom edge)
        float tBot = max(0.0, (v - 0.40) / 0.60);
        float botGlow = pow(tBot, 1.7) * 0.70;

        // Subtle glowing lip along the bottom edge (last 14%)
        float tLip = max(0.0, (v - 0.86) / 0.14);
        float botLip = pow(tLip, 1.4) * 0.55;
        float botTotal = min(1.0, botGlow + botLip);

        // 3. Delicate top rim highlight (very top 8%)
        float tTop = max(0.0, (0.08 - v) / 0.08);
        float topGlow = pow(tTop, 1.5) * 0.95;

        // 4. Subtle, smooth organic gradient within the dark purple itself:
        // Deepest purple-black at the core (v ~ 0.35, u ~ 0.50), gently shading into soft dark purple
        float vDark = clamp((0.35 - v) / 0.35, 0.0, 1.0);
        float hDark = clamp(dxCenter / 0.50, 0.0, 1.0);
        float darkGrad = pow(vDark, 1.5) * 0.30 + pow(hDark, 1.8) * 0.20;

        // Base color with internal gradient within the dark purple
        half3 col = mix(uCenterColor.rgb, uOuterColor.rgb, half(darkGrad));

        // Smoothly illuminate with ambient end caps, bottom glow, and top rim
        if (capGlow > 0.0) {
            col = mix(col, uOuterColor.rgb, half(capGlow));
        }
        if (botTotal > 0.0) {
            col = mix(col, uBottomLiftColor.rgb, half(botTotal));
        }
        if (topGlow > 0.0) {
            col = mix(col, uTopRimColor.rgb, half(topGlow));
        }

        return half4(col * shapeAlpha, shapeAlpha);
    }
"""
```

---

## 2. Color Palette Roles (`DynamicMeshShader.kt`)

```kotlin
// 1. Center: deep purple-black
val centerColor = if (isDark) {
    androidx.compose.ui.graphics.lerp(
        Color(0xFF060305),
        scheme.primaryContainer,
        0.025f
    )
} else {
    scheme.surfaceContainerLowest
}

// 2. Outer: soft, dark muted material color
val outerColor = if (isDark) {
    androidx.compose.ui.graphics.lerp(
        Color(0xFF090608),
        scheme.primaryContainer,
        0.24f
    )
} else {
    scheme.surfaceContainerHigh
}

// 3. Bottom lift: velvety upward light along bottom edge
val bottomLiftColor = if (isDark) {
    androidx.compose.ui.graphics.lerp(
        Color(0xFF160E14),
        scheme.primaryContainer,
        0.46f
    )
} else {
    outerColor
}

// 4. Top rim: delicate hairline highlight along top edge
val topRimColor = if (isDark) {
    androidx.compose.ui.graphics.lerp(
        Color(0xFF0B070A),
        scheme.primaryContainer,
        0.32f
    )
} else {
    outerColor
}
```

---

## 3. Outlined Material 3 Three Bars Icon (`ic_three_bars.xml`)

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <!-- Center bar: taller prominent stadium capsule -->
    <path
        android:strokeColor="@android:color/white"
        android:strokeWidth="1.3"
        android:strokeLineCap="round"
        android:fillColor="#00000000"
        android:pathData="M12,4.8 L12,19.2" />
    <!-- Left bar: proportional medium capsule -->
    <path
        android:strokeColor="@android:color/white"
        android:strokeWidth="1.3"
        android:strokeLineCap="round"
        android:fillColor="#00000000"
        android:pathData="M7.2,7.8 L7.2,16.2" />
    <!-- Right bar: proportional short capsule -->
    <path
        android:strokeColor="@android:color/white"
        android:strokeWidth="1.3"
        android:strokeLineCap="round"
        android:fillColor="#00000000"
        android:pathData="M16.8,9.8 L16.8,14.2" />
</vector>
```

---

## 4. Tutorial Spotlight Overlay Integration (`TutorialSpotlightOverlay.kt`)
Step 3 tutorial renders `ic_three_bars` inline within the instructions using `[three_bars]` `InlineTextContent`:
`2 to TutorialStepInfo("Settings", "Tap the settings icon ( [three_bars] ) to customize your search experience. Press OK below to open Settings now.", ...)`
