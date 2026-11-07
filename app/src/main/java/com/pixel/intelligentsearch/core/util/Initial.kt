package com.pixel.intelligentsearch.core.util

import java.text.BreakIterator
import java.util.Locale

/**
 * The first character a person sees in [text], in capitals, for a circle that stands in for a picture: a whole emoji,
 * flag or accented letter, never half of one. Empty for blank text.
 */
fun initialOf(text: String, locale: Locale): String {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return ""
    val characters = BreakIterator.getCharacterInstance(locale)
    characters.setText(trimmed)
    val end = characters.next().takeIf { it != BreakIterator.DONE } ?: trimmed.length
    return trimmed.substring(0, end).uppercase(locale)
}
