package com.ngdesigns.intelligentsearch.engine

import java.text.Normalizer

/**
 * The form every name and query is compared in: lower case, accents removed ("Café Müller" -> "cafe muller"),
 * punctuation turned into spaces, and runs of spaces collapsed. Matching then never depends on how a name
 * happens to be written.
 */
fun normalize(text: String): String = fold(text, splitCamelCase = false)

/**
 * [normalize], but also splitting joined words at their capitals ("YouTube" -> "you tube", "WhatsApp" -> "whats
 * app"), so a name's word starts and acronym ("yt", "wa") can be found. Runs of capitals stay together ("BBC News"
 * -> "bbc news").
 */
fun nameWords(text: String): List<String> = words(fold(text, splitCamelCase = true))

/** The words of an already [normalize]d string. */
fun words(normalized: String): List<String> =
    if (normalized.isEmpty()) emptyList() else normalized.split(' ')

private fun fold(text: String, splitCamelCase: Boolean): String {
    if (text.isEmpty()) return text
    val decomposed = Normalizer.normalize(text, Normalizer.Form.NFKD)
    val out = StringBuilder(decomposed.length)
    var pendingSpace = false
    var previous = ' '
    for (ch in decomposed) {
        when {
            Character.getType(ch) == Character.NON_SPACING_MARK.toInt() -> Unit
            ch.isLetterOrDigit() -> {
                val camelBreak = splitCamelCase && ch.isUpperCase() && previous.isLowerCase()
                if ((pendingSpace || camelBreak) && out.isNotEmpty()) out.append(' ')
                pendingSpace = false
                out.append(ch.lowercaseChar())
                previous = ch
            }
            // Apostrophes join a word ("Domino's" -> "dominos") rather than splitting it.
            ch == '\'' || ch == '’' -> Unit
            else -> { pendingSpace = true; previous = ' ' }
        }
    }
    return out.toString()
}
