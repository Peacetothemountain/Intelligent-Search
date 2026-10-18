package com.pixel.intelligentsearch.feature.voice

/**
 * Tidies dictated words into what the user meant to search, the way Gboard's Rambler does: filler sounds dropped
 * ("um", "uh"), stutters and repeats collapsed ("the the", "Okay. Okay. Okay."), and spoken corrections applied
 * ("timer for 5 minutes, no, 10 minutes" becomes "timer for 10 minutes"; "scratch that" starts over). Runs on the
 * phone, instantly, with no network.
 */
object SpeechCleanup {
    private val fillers = setOf("um", "umm", "uh", "uhh", "uhm", "er", "erm", "ah", "hmm", "hm", "mm", "mhm")

    /** Phrases after which the user restates what they meant; the restated words replace as many words before. */
    private val corrections = listOf("no wait", "no no", "sorry i mean", "i mean", "no sorry", "actually no", "no", "sorry", "actually", "wait")

    /** Phrases that throw away everything said before them. */
    private val restarts = listOf("scratch that", "start over", "never mind", "nevermind", "forget that", "delete that")

    fun clean(segments: List<String>): String = clean(segments.joinToString(" "))

    fun clean(raw: String): String {
        var words = tokenize(raw)
        words = applyRestarts(words)
        words = dropFillers(words)
        words = applyCorrections(words)
        words = collapseRepeats(words)
        return words.joinToString(" ") { it.text }.trim().trimEnd('.', ',', ';', ':', '!').trim()
    }

    private data class Word(val text: String) {
        val bare: String = text.lowercase().trim { !it.isLetterOrDigit() && it != '\'' }
    }

    private fun tokenize(raw: String): List<Word> =
        raw.replace(Regex("\\s+"), " ").trim().split(' ').filter { it.isNotBlank() }.map(::Word)

    /** Index where [phrase] (lower case, space separated) starts in [words] at or after [from], or -1. */
    private fun find(words: List<Word>, phrase: String, from: Int = 0): Int {
        val parts = phrase.split(' ')
        outer@ for (i in from..words.size - parts.size) {
            for (j in parts.indices) if (words[i + j].bare != parts[j]) continue@outer
            return i
        }
        return -1
    }

    /** Removes filler sounds; a filler set off by commas ("in, uhm, Chicago") takes its commas with it. */
    private fun dropFillers(words: List<Word>): List<Word> {
        val out = ArrayList<Word>(words.size)
        for (w in words) {
            if (w.bare in fillers) {
                val prev = out.lastOrNull()
                if (prev != null && prev.text.endsWith(",") && w.text.endsWith(",")) out[out.lastIndex] = Word(prev.text.dropLast(1))
            } else {
                out += w
            }
        }
        return out
    }

    private fun applyRestarts(words: List<Word>): List<Word> {
        var result = words
        for (phrase in restarts) {
            while (true) {
                val at = find(result, phrase)
                if (at < 0) break
                result = result.drop(at + phrase.split(' ').size)
            }
        }
        return result
    }

    /**
     * "X Y no Z": a correction marker followed by a restatement replaces the same number of words before the marker
     * ("weather in Boston no Chicago" -> "weather in Chicago"). A marker at the very start or end is just a word.
     */
    private fun applyCorrections(words: List<Word>): List<Word> {
        var result = words
        var guard = 0
        loop@ while (guard++ < 10) {
            for (phrase in corrections) {
                val size = phrase.split(' ').size
                var at = find(result, phrase)
                while (at >= 0) {
                    val before = result.subList(0, at)
                    val after = result.subList(at + size, result.size)
                    // Only a correction when the speaker paused (comma or full stop before the marker), or the marker is
                    // a multi-word phrase; otherwise "no" and "actually" stay as ordinary words ("no sugar", "actually good").
                    val paused = before.lastOrNull()?.text?.lastOrNull()?.let { it == ',' || it == '.' } == true ||
                        result[at].text.firstOrNull()?.isUpperCase() == true && phrase == "no"
                    if (before.isNotEmpty() && after.isNotEmpty() && (paused || size > 1)) {
                        val restated = after.takeWhile { it.text.lastOrNull() != '.' }.ifEmpty { after.take(1) }
                        val drop = restated.size.coerceAtMost(before.size)
                        result = before.dropLast(drop).map { Word(it.text.trimEnd(',', '.')) } + after
                        continue@loop
                    }
                    at = find(result, phrase, at + 1)
                }
            }
            break
        }
        return result
    }

    /** Collapses a word or phrase (up to four words) said again straight away: "the the", "Okay. Okay. Okay.". */
    private fun collapseRepeats(words: List<Word>): List<Word> {
        val result = words.toMutableList()
        for (n in 4 downTo 1) {
            var i = 0
            while (i + 2 * n <= result.size) {
                val a = result.subList(i, i + n).map { it.bare }
                val b = result.subList(i + n, i + 2 * n).map { it.bare }
                if (a == b && a.all { it.isNotEmpty() }) {
                    repeat(n) { result.removeAt(i + n) }
                } else {
                    i++
                }
            }
        }
        return result
    }
}
