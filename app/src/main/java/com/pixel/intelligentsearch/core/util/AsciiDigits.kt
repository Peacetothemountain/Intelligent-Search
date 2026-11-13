package com.pixel.intelligentsearch.core.util

/** [text] with other scripts' decimal digits (Arabic-Indic, Persian, Devanagari, full-width...) written in ASCII. */
fun asciiDigits(text: String): String = buildString(text.length) {
    for (c in text) {
        val digit = if (c.isDigit()) Character.digit(c, 10) else -1
        append(if (digit in 0..9) '0' + digit else c)
    }
}
