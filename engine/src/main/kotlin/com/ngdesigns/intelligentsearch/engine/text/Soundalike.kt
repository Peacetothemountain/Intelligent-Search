package com.ngdesigns.intelligentsearch.engine.text

/**
 * A sound-alike key for English names, so spellings that are said the same match: Steven and Stephen, Catherine and
 * Kathryn, Jon and John, Philip and Filip, Sean and Shawn. Vowels after the first letter are dropped, letters that
 * sound alike share one symbol, silent letters disappear and doubled sounds collapse. Input must be [normalize]d
 * (lower case ASCII letters); anything else is skipped.
 */
object Soundalike {

    fun key(word: String): String {
        if (word.isEmpty()) return ""
        val w = word.filter { it in 'a'..'z' }
        if (w.isEmpty()) return ""
        val out = StringBuilder(w.length)
        var i = 0
        fun at(k: Int) = w.getOrNull(k) ?: ' '
        fun emit(c: Char) { if (out.isEmpty() || out.last() != c) out.append(c) }
        // Silent or alternative starts.
        when {
            w.startsWith("kn") || w.startsWith("gn") || w.startsWith("pn") || w.startsWith("wr") -> i = 1
            w.startsWith("x") -> { emit('S'); i = 1 }
            w.startsWith("wh") -> { emit('W'); i = 2 }
        }
        while (i < w.length) {
            val c = w[i]
            val next = at(i + 1)
            when (c) {
                'a', 'e', 'i', 'o', 'u', 'y' -> if (out.isEmpty()) emit('A')
                'b' -> if (!(i == w.length - 1 && at(i - 1) == 'm')) emit('P')
                'p' -> if (next == 'h') { emit('F'); i++ } else emit('P')
                'f', 'v' -> emit('F')
                'c' -> when {
                    next == 'h' -> { emit('X'); i++ }
                    next == 'i' || next == 'e' || next == 'y' -> emit('S')
                    next == 'k' -> { emit('K'); i++ }
                    else -> emit('K')
                }
                'k', 'q' -> emit('K')
                'g' -> when {
                    next == 'h' -> i++ // "gh" is silent or an f in names (Hugh, Vaughan): drop it
                    next == 'n' && i + 2 == w.length -> Unit
                    next == 'i' || next == 'e' || next == 'y' -> emit('J')
                    else -> emit('K')
                }
                'j' -> emit('J')
                's' -> when {
                    next == 'h' -> { emit('X'); i++ }
                    next == 'c' && at(i + 2) == 'h' -> { emit('S'); emit('K'); i += 2 }
                    else -> emit('S')
                }
                'z' -> emit('S')
                'x' -> { emit('K'); emit('S') }
                't' -> when {
                    next == 'h' -> { emit('0'); i++ }
                    next == 'i' && (at(i + 2) == 'o' || at(i + 2) == 'a') -> emit('X')
                    else -> emit('T')
                }
                'd' -> emit('T')
                'h' -> if (out.isEmpty() && next in "aeiouy") emit('H')
                'w' -> if (next in "aeiouy") emit('W')
                'l', 'm', 'n', 'r' -> emit(c.uppercaseChar())
            }
            i++
        }
        return out.toString()
    }
}
