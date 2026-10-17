package com.ngdesigns.intelligentsearch.engine

import java.text.Normalizer

/**
 * The form every name and query is compared in: lower case, accents removed ("Café Müller" -> "cafe muller"),
 * punctuation turned into spaces, and runs of spaces collapsed. Matching then never depends on how a name
 * happens to be written.
 */
fun normalize(text: String): String {
    if (text.isEmpty()) return text
    val decomposed = Normalizer.normalize(text, Normalizer.Form.NFKD)
    val out = StringBuilder(decomposed.length)
    var pendingSpace = false
    for (ch in decomposed) {
        when {
            Character.getType(ch) == Character.NON_SPACING_MARK.toInt() -> Unit
            ch.isLetterOrDigit() -> {
                if (pendingSpace && out.isNotEmpty()) out.append(' ')
                pendingSpace = false
                out.append(ch.lowercaseChar())
            }
            // Apostrophes join a word ("Domino's" -> "dominos") rather than splitting it.
            ch == '\'' || ch == '’' -> Unit
            else -> pendingSpace = true
        }
    }
    return out.toString()
}

/** The words of an already [normalize]d string. */
fun words(normalized: String): List<String> =
    if (normalized.isEmpty()) emptyList() else normalized.split(' ')
