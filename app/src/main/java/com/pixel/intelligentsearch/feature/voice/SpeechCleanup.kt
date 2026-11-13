package com.pixel.intelligentsearch.feature.voice

/**
 * Tidies dictated words into what the user meant to search, the way Gboard's Rambler does: filler sounds dropped
 * ("um", "uh"), stutters and repeats collapsed ("the the", "Okay. Okay. Okay."), and spoken corrections applied
 * ("timer for 5 minutes, no, 10 minutes" becomes "timer for 10 minutes"; "scratch that" starts over). Words that
 * only sound like a correction stay what they are: "how to start over after a divorce", "Never Mind the Bollocks",
 * "Bora Bora hotels". Runs on the phone, instantly, with no network.
 */
object SpeechCleanup {
    // Only sounds that are never words: "mm" (millimetres) and "er" (emergency room) stay.
    private val fillers = setOf("um", "umm", "uh", "uhh", "uhm", "erm", "ah", "hmm", "hm", "mhm")

    /** Phrases after which the user restates what they meant; the restated words replace as many words before. */
    private val corrections = listOf("no wait", "no no", "sorry i mean", "i mean", "no sorry", "actually no", "no", "sorry", "actually", "wait")

    /**
     * Phrases that throw away everything said before them. The one word "nevermind" isn't one: it's the album, and a
     * word in titles.
     */
    private val restarts = listOf("scratch that", "start over", "never mind", "forget that", "delete that", "cancel that")

    /** The one restart phrase searches hardly say, so it counts wherever it is said ("how to scratch that itch" aside). */
    private const val CLEAR_RESTART = "scratch that"

    /** The restart phrase people say in one breath before a new request ("weather in Boston never mind what time..."). */
    private const val NEVER_MIND = "never mind"

    /**
     * Words that make a restart phrase after them part of the sentence ("how to start over", "how do I delete that",
     * "don't forget that"), so it is never a restart there unless said after a pause.
     */
    private val bindsRestart = setOf("to", "the", "i", "you", "we", "they", "can", "do", "don't", "dont", "not", "let's", "lets")

    /**
     * Words that make what follows a thing named or quoted ("how do you say never mind in Spanish", "translate never
     * mind", "lyrics start over"): after one of them a restart said in one breath is part of the search.
     */
    private val quotes = setOf("say", "saying", "says", "said", "mean", "means", "meaning", "spell", "spelling",
        "translate", "translation", "define", "definition", "lyrics", "song", "songs", "album", "movie", "film", "show",
        "book", "play", "playing", "quote", "called", "named", "title")

    /** How many words a restart said in one breath needs before it: "nirvana never mind" is a search, not a restart. */
    private const val ONE_BREATH_WORDS_BEFORE = 3

    /**
     * Words said twice in a row only by a stutter ("the the"); any other word said twice is kept ("Bora Bora",
     * "mahi mahi") unless a pause comes between ("Okay. Okay.").
     */
    private val stutters = setOf("the", "a", "an", "i", "to", "and", "of", "in", "on", "at", "for", "is", "it", "my",
        "me", "what", "how", "where", "when", "who", "can", "do", "with", "from", "that", "this")

    /** [segments] are the phrases as heard, split where the speaker paused. */
    fun clean(segments: List<String>): String = cleanWords(segments.flatMap(::tokenize))

    fun clean(raw: String): String = cleanWords(tokenize(raw))

    private fun cleanWords(tokens: List<Word>): String {
        var words = tokens
        words = applyRestarts(words)
        words = dropFillers(words)
        words = applyCorrections(words)
        words = collapseRepeats(words)
        return words.joinToString(" ") { it.text }.trim().trimEnd('.', ',', ';', ':', '!').trim()
    }

    /** A word as heard; [startsPhrase] when the speaker paused just before it (it starts a heard phrase). */
    private data class Word(val text: String, val startsPhrase: Boolean = false) {
        val bare: String = text.lowercase().trim { !it.isLetterOrDigit() && it != '\'' }
        /** Whether the recognizer marked a pause right after this word. */
        val pauseAfter: Boolean get() = text.lastOrNull()?.let { it in ",.?!;" } == true
        /** A spoken word, not a filler sound or stray punctuation. */
        val isSaid: Boolean get() = bare.isNotEmpty() && bare !in fillers
    }

    private fun tokenize(raw: String): List<Word> =
        raw.replace(Regex("\\s+"), " ").trim().split(' ').filter { it.isNotBlank() }
            .mapIndexed { i, text -> Word(text, startsPhrase = i == 0) }

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
                if (prev != null && prev.text.endsWith(",") && w.text.endsWith(",")) out[out.lastIndex] = prev.copy(text = prev.text.dropLast(1))
            } else {
                out += w
            }
        }
        return out
    }

    /**
     * A restart phrase throws away what was said before it when it is plainly said as one: "scratch that" anywhere;
     * any of them after a pause (it starts a heard phrase, or a comma or full stop comes before it) or at the very end
     * ("set a timer for ten minutes never mind"); and "never mind" in one breath before a new request of two words or
     * more ("weather in Boston never mind what time is it in Tokyo"). In one breath it needs three words before it and
     * none that names or quotes what follows ([quotes]): "nirvana never mind" and "how do you say never mind in
     * Spanish" are searches. Bound into the sentence by the word before it
     * ("how to start over", "how to scratch that itch") it is part of the search, and with nothing said before it
     * there is nothing to throw away ("Never Mind the Bollocks"), unless it is all that was said ("never mind").
     * Written as a title ("Never Mind", "Start Over") it is a name, not a restart.
     */
    private fun applyRestarts(words: List<Word>): List<Word> {
        var result = words
        var i = 0
        while (i < result.size) {
            val phrase = restarts.firstOrNull { find(result, it, i) == i }
            if (phrase == null) { i++; continue }
            val size = phrase.split(' ').size
            val before = result.subList(0, i)
            val after = result.subList(i + size, result.size)
            val saidBefore = before.any { it.isSaid }
            val saidAfter = after.filter { it.isSaid }
            val title = (1 until size).any { result[i + it].text.firstOrNull()?.isUpperCase() == true }
            val paused = result[i].startsPhrase || before.lastOrNull()?.pauseAfter == true
            val bound = before.lastOrNull { it.isSaid }?.bare in bindsRestart
            val saidWordsBefore = before.filter { it.isSaid }
            val inOneBreath = !bound && saidWordsBefore.size >= ONE_BREATH_WORDS_BEFORE &&
                saidWordsBefore.none { it.bare in quotes } &&
                (saidAfter.isEmpty() || (phrase == NEVER_MIND && saidAfter.size >= 2 && saidAfter.first().bare != "the"))
            val restart = !title &&
                (if (saidBefore) paused || (!bound && phrase == CLEAR_RESTART) || inOneBreath else saidAfter.isEmpty())
            if (restart) {
                result = after.toList()
                i = 0
            } else {
                i += size
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
                    // Only a correction when the speaker paused (comma or full stop before the marker); otherwise the
                    // words stay what they are ("no sugar", "actually good", "lyrics I mean it", "no no no song").
                    // A capital "No," is the recognizer's own pause mark; a bare capital "No" is just the start of a
                    // segment ("No Man's Sky" after a pause) and stays. A marker of more words starting a heard phrase
                    // ("I mean Sam") is one too, unless written as a title ("No No Song").
                    val title = (1 until size).any { result[at + it].text.firstOrNull()?.isUpperCase() == true }
                    val paused = before.lastOrNull()?.text?.lastOrNull()?.let { it == ',' || it == '.' } == true ||
                        result[at].text == "No," || (size > 1 && result[at].startsPhrase && !title)
                    if (before.isNotEmpty() && after.isNotEmpty() && paused) {
                        // The restatement runs to the end of its sentence, including the word carrying the full stop.
                        val sentenceEnd = after.indexOfFirst { it.text.endsWith('.') }
                        val restated = if (sentenceEnd < 0) after else after.subList(0, sentenceEnd + 1)
                        val drop = restated.size.coerceAtMost(before.size)
                        result = before.dropLast(drop).map { it.copy(text = it.text.trimEnd(',', '.')) } + after
                        continue@loop
                    }
                    at = find(result, phrase, at + 1)
                }
            }
            break
        }
        return result
    }

    /**
     * Collapses a phrase of three or four words said again straight away ("pizza near me pizza near me"), a pair of
     * words said again after a pause ("New York. New York."; "New York New York hotel" stays), and a single word
     * repeated by a stutter ("the the") or after a pause ("Okay. Okay. Okay."); "Bora Bora" stays.
     */
    private fun collapseRepeats(words: List<Word>): List<Word> {
        val result = words.toMutableList()
        for (n in 4 downTo 1) {
            var i = 0
            while (i + 2 * n <= result.size) {
                val a = result.subList(i, i + n).map { it.bare }
                val b = result.subList(i + n, i + 2 * n).map { it.bare }
                val paused = result[i + n - 1].pauseAfter || result[i + n].startsPhrase
                val repeated = n > 2 || (n == 1 && a[0] in stutters) || paused
                if (a == b && a.all { it.isNotEmpty() } && repeated) {
                    repeat(n) { result.removeAt(i + n) }
                } else {
                    i++
                }
            }
        }
        return result
    }
}
