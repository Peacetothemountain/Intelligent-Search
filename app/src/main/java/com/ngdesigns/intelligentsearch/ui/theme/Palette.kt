package com.ngdesigns.intelligentsearch.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The colours of one theme. Two surfaces (ink and paper), one text colour with a dimmed variant, hairlines, and a
 * single accent the user picks. There are no gradients, shadows or tints beyond these.
 */
@Immutable
data class Palette(
    val background: Color,
    val raised: Color,
    val text: Color,
    val dim: Color,
    val line: Color,
    val accent: Color,
    val onAccent: Color,
    val isDark: Boolean,
)

/** The accents a user can choose; each reads on both the ink and the paper background. */
enum class Accent(val dark: Color, val light: Color) {
    Vermilion(Color(0xFFFF5B3A), Color(0xFFD93A1B)),
    Cobalt(Color(0xFF5B8CFF), Color(0xFF2456D6)),
    Signal(Color(0xFF3CCB7F), Color(0xFF13894B)),
    Amber(Color(0xFFFFB020), Color(0xFFB26B00)),
    Bone(Color(0xFFECE8DF), Color(0xFF16161A)),
}

fun inkPalette(accent: Accent = Accent.Vermilion) = Palette(
    background = Color(0xFF0F1012),
    raised = Color(0xFF18191C),
    text = Color(0xFFECE8DF),
    dim = Color(0xFF8B877E),
    line = Color(0xFF26272B),
    accent = accent.dark,
    onAccent = Color(0xFF0F1012),
    isDark = true,
)

fun paperPalette(accent: Accent = Accent.Vermilion) = Palette(
    background = Color(0xFFF3F0E8),
    raised = Color(0xFFE9E5DB),
    text = Color(0xFF16161A),
    dim = Color(0xFF6E695F),
    line = Color(0xFFD9D4C7),
    accent = accent.light,
    onAccent = Color(0xFFF3F0E8),
    isDark = false,
)
