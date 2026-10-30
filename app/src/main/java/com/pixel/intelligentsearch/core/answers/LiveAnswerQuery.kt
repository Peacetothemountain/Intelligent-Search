package com.pixel.intelligentsearch.core.answers

import java.util.Locale

/** Which day a weather question is about. */
enum class ForecastDay { TODAY, TOMORROW }

/**
 * A question the overlay can answer inline, read from the query by [LiveAnswerParser]: a word to define, the
 * weather somewhere, text to translate, or today's screen time.
 */
sealed interface LiveAnswerQuery {
    /**
     * Define [word]. [explicit] when the query asked for a definition in so many words ("define x", "x meaning"); a
     * bare "word?" isn't, so it gets no tap-through card when no definition can be shown.
     */
    data class Define(val word: String, val explicit: Boolean) : LiveAnswerQuery

    /**
     * The forecast for [day] in [place], or at the user's home location when [place] is null, in the [unit] the query
     * named ("weather in celsius"); null uses the scale of the phone's region.
     */
    data class Weather(val place: String?, val day: ForecastDay, val unit: TemperatureUnit? = null) : LiveAnswerQuery

    /**
     * Translate [text] into [targetLanguage], one of [TranslationLanguages.CODES] (null: the phone's language), from
     * [sourceLanguage] (null: let Google Translate tell).
     */
    data class Translate(val text: String, val targetLanguage: String?, val sourceLanguage: String?) : LiveAnswerQuery

    /** Today's screen time and the apps that took it. */
    data object ScreenTime : LiveAnswerQuery
}

/**
 * Reads instant-answer questions out of a query. Pure and cheap: it runs on every keystroke, so every pattern is
 * compiled once. Order matters where phrasings overlap: screen time, then "define ..." in so many words, then
 * translations ("weather in french" is a translation, not a place), then weather, then the looser definition forms.
 */
object LiveAnswerParser {

    private val WHITESPACE = Regex("\\s+")

    /** Quotes people put around a word or phrase: straight and curly, single and double. */
    private val QUOTES = charArrayOf('"', '\'', '“', '”', '‘', '’')

    // ---------------------------------------------------------------------------------------------
    // SCREEN TIME
    // ---------------------------------------------------------------------------------------------

    private val SCREEN_TIME_PATTERNS = listOf(
        Regex("^(?:what(?:'s| is) )?(?:my |today'?s |todays )?screen ?time(?: today)?$"),
        Regex("^how (?:long|much(?: time)?) (?:have i (?:been|spent) |did i spend |i spent )?(?:on|using|used) (?:my |the )?phone(?: today)?$"),
        Regex("^(?:my )?phone usage(?: today)?$")
    )

    // ---------------------------------------------------------------------------------------------
    // DEFINITIONS
    // ---------------------------------------------------------------------------------------------

    private val DEFINE_PREFIXES = listOf(
        "define ", "definition of ", "meaning of ", "what is the meaning of ", "what's the meaning of ",
        "whats the meaning of "
    )
    private val DEFINE_SUFFIX = Regex("^(.+?) (?:meaning|definition|define|defined)$")
    private val WHAT_DOES_MEAN = Regex("^what (?:does|do) (.+?) mean$")
    private val WHATS_MEAN = Regex("^what(?:'s| is) (.+?) mean$")
    private val BARE_QUESTION_WORD = Regex("^([a-z][a-z'-]{1,29})\\?$")

    /** An English word or short phrase: letters, with inner apostrophes, hyphens and spaces. */
    private val DEFINABLE = Regex("^[\\p{L}][\\p{L}'\\- ]{0,39}$")

    /** Words a "word?" query or "what does X mean" is far more often a question with than a word to look up. */
    private val NOT_DEFINABLE = setOf(
        "who", "what", "why", "when", "where", "how", "which", "huh", "ok", "okay", "yes", "no", "hi", "hey", "hello",
        "it", "this", "that", "he", "she", "they", "you", "we", "me", "us", "them", "him", "her", "one", "anything"
    )

    // ---------------------------------------------------------------------------------------------
    // TRANSLATION
    // ---------------------------------------------------------------------------------------------

    private val TRANSLATE_FROM_TO = Regex("^translate (.+?) from ([\\p{L} ]+?) (?:to|into) ([\\p{L} ]+)$")
    private val TRANSLATE_TO = Regex("^translate (.+?) (?:to|into|in) ([\\p{L} ]+)$")
    private val TRANSLATE_ONLY = Regex("^translate (.+)$")
    private val HOW_DO_YOU_SAY = Regex("^how (?:do|would|can) (?:you|i) say (.+?) in ([\\p{L} ]+)$")
    private val MEANING_IN = Regex("^(.+?) (?:meaning|translation) in ([\\p{L} ]+)$")
    private val WHAT_IS_IN = Regex("^what(?:'s| is) (.+?) in ([\\p{L} ]+)$")
    private val BARE_IN = Regex("^(.+?) in ([\\p{L} ]+)$")

    /**
     * Words that make "X in <language>" a search for things in that language ("songs in spanish", "learn python in
     * english", "news in hindi") rather than words to translate.
     */
    private val NOT_TRANSLATABLE = setOf(
        "song", "songs", "music", "lyrics", "movie", "movies", "film", "films", "show", "shows", "series", "tv", "news",
        "video", "videos", "book", "books", "novel", "novels", "story", "stories", "poem", "poems", "podcast",
        "podcasts", "radio", "channel", "channels", "cartoon", "cartoons", "anime", "episode", "episodes", "dubbed",
        "subtitles", "learn", "learning", "course", "courses", "class", "classes", "lesson", "lessons", "tutorial",
        "tutorials", "typing", "keyboard", "app", "apps", "game", "games", "jobs", "website", "websites"
    )

    /**
     * Words that mark a phrase someone wants said in another language: people, questions, greetings and courtesies
     * ("thank you", "where is the station", "good morning"). A run of other words is more often a name or a title
     * ("harry potter in hindi"), so the bare "X in <language>" form needs one of these when X is longer than a word.
     */
    private val PHRASE_WORDS = setOf(
        "i", "i'm", "me", "my", "you", "your", "he", "she", "it", "it's", "we", "they", "him", "her", "us", "them",
        "our", "their", "this", "that", "is", "are", "am", "was", "were", "be", "do", "does", "did", "don't", "can",
        "could", "would", "will", "have", "has", "not", "what", "what's", "where", "where's", "when", "why", "who",
        "how", "which", "let's", "please", "thank", "thanks", "sorry", "excuse", "hello", "hi", "goodbye", "bye",
        "good", "happy", "merry", "welcome", "love", "cheers", "congratulations", "yes", "no"
    )

    /** Longest text a query offers to translate; anything longer is better pasted into a translator. */
    const val MAX_TRANSLATE_CHARS = 500

    // ---------------------------------------------------------------------------------------------
    // WEATHER
    // ---------------------------------------------------------------------------------------------

    /** When a forecast is for: the day, with the part of it people add ("tomorrow morning", "this evening"). */
    private val DAY_WORD = "((?:today|tomorrow)(?: (?:morning|afternoon|evening|night))?|tonight|now|right now|" +
        "this (?:morning|afternoon|evening)|later(?: today)?)"
    private val WEATHER_LEAD = Regex("^(?:what(?:'s| is|s) the |how(?:'s| is|s) the |the )")
    private val WEATHER_WORD = Regex("\\b(?:weather|forecast)\\b")

    /**
     * Words that mean where the user is, not a place to look up: "near me", "here", "outside", "in my area", "at my
     * location", "nearby". Geocoding them finds villages called "Me" and suburbs called "Outside".
     */
    private val WEATHER_HERE = Regex(
        "\\b(?:(?:near|around|at|in|for|close to) (?:me|here|my (?:current )?(?:area|location))|(?:my )?current location|" +
            "my (?:area|location)|right here|here|outside|nearby|near by|close by|where i am)\\b"
    )

    /** A scale the query asks the temperatures in. */
    private val WEATHER_UNIT = Regex("\\b(?:in )?(celsius|centigrade|fahrenheit)\\b")

    /**
     * A stretch of days ("10 day forecast", "this weekend", "next 7 days"): more than the card's one day shows, so
     * those stay searches, as "weather 10 day" always has, rather than being read as a place.
     */
    private val WEATHER_SPAN = Regex(
        "\\b(?:(?:\\d+|three|five|seven|ten|fourteen)[ -]?days?|next (?:few |couple of )?days|weeks?|weekends?|weekly)\\b"
    )

    /** "for" or "on" before the day ("weather for tomorrow"), which would otherwise read as a place called "tomorrow". */
    private val WEATHER_FOR_DAY = Regex("\\b(?:for|on) (?=$DAY_WORD$)")
    private val WEATHER_LIKE = Regex("\\bweather like\\b")
    private val WEATHER_ALONE = Regex("^(?:weather|forecast|weather forecast)(?: $DAY_WORD)?$")
    private val WEATHER_OWNED = Regex("^(today'?s|todays|tonight'?s|tomorrow'?s|tomorrows|current|local|my) (?:weather|forecast|weather forecast)$")
    private val WEATHER_IN = Regex("^(?:weather|forecast|weather forecast) (?:in|for|at|near) (.+?)(?: $DAY_WORD)?$")
    private val WEATHER_DAY_IN = Regex("^(?:weather|forecast|weather forecast) $DAY_WORD (?:in|for|at|near) (.+)$")
    private val WEATHER_PLACE_FIRST = Regex("^(.+?) (?:weather|forecast|weather forecast)(?: $DAY_WORD)?$")
    private val WEATHER_BARE_PLACE = Regex("^(?:weather|forecast) (.+)$")

    /** A place name: letters, digits (postcodes), spaces and the punctuation place names use. */
    private val PLACE = Regex("^[\\p{L}\\p{N}][\\p{L}\\p{N} .,'\\-]{0,59}$")

    /** Words after or before "weather" that name something other than a place ("weather radar", "the weather channel"). */
    private val NOT_A_PLACE = setOf(
        "radar", "channel", "app", "apps", "map", "maps", "alert", "alerts", "warning", "warnings", "report", "news",
        "underground", "network", "station", "widget", "forecast", "today", "tomorrow", "tonight", "now", "live",
        "update", "updates", "hourly", "weekly", "10 day", "10-day", "7 day", "7-day", "this week", "next week",
        "weekend", "this weekend", "the", "a", "bad", "good", "nice", "cold", "hot", "the weather channel"
    )

    /** What [rawQuery] asks to be answered inline, or null for an ordinary search. */
    fun parse(rawQuery: String): LiveAnswerQuery? {
        val trimmed = rawQuery.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_TRANSLATE_CHARS + 40) return null
        val lower = trimmed.lowercase(Locale.ROOT).replace(WHITESPACE, " ")
        // A trailing question mark or full stop is punctuation, except on a bare "word?", which it marks.
        val sentence = lower.trimEnd('?', '.', '!').trim()
        if (sentence.isEmpty()) return null

        if (SCREEN_TIME_PATTERNS.any { it.matches(sentence) }) return LiveAnswerQuery.ScreenTime

        DEFINE_PREFIXES.firstOrNull { sentence.startsWith(it) }?.let { prefix ->
            return definition(sentence.removePrefix(prefix), explicit = true, strict = false)
        }
        // "weather meaning" asks what a word means, so these come before the weather forms.
        DEFINE_SUFFIX.matchEntire(sentence)?.let { return definition(it.groupValues[1], explicit = true, strict = false) }
        (WHAT_DOES_MEAN.matchEntire(sentence) ?: WHATS_MEAN.matchEntire(sentence))?.let { match ->
            definition(match.groupValues[1], explicit = true, strict = true)?.let { return it }
        }

        parseTranslation(trimmed, sentence)?.let { return it }
        parseWeather(sentence)?.let { return it }

        BARE_QUESTION_WORD.matchEntire(lower)?.let { match ->
            return definition(match.groupValues[1], explicit = false, strict = true)
        }
        return null
    }

    /** [raw] as a word to define. [strict] leaves out words that more likely start a question ("what does it mean"). */
    private fun definition(raw: String, explicit: Boolean, strict: Boolean): LiveAnswerQuery.Define? {
        val word = raw.trim().trim(*QUOTES).trim()
        if (word.isEmpty() || !DEFINABLE.matches(word) || word.split(' ').size > 3) return null
        if (strict && word in NOT_DEFINABLE) return null
        return LiveAnswerQuery.Define(word, explicit)
    }

    // ---------------------------------------------------------------------------------------------
    // TRANSLATION
    // ---------------------------------------------------------------------------------------------

    private fun parseTranslation(original: String, sentence: String): LiveAnswerQuery.Translate? {
        TRANSLATE_FROM_TO.matchEntire(sentence)?.let { match ->
            val source = TranslationLanguages.codeFor(match.groupValues[2])
            val target = TranslationLanguages.codeFor(match.groupValues[3])
            if (source != null && target != null) return translation(original, match.groups[1]!!.range, target, source)
        }
        TRANSLATE_TO.matchEntire(sentence)?.let { match ->
            val target = TranslationLanguages.codeFor(match.groupValues[2])
            if (target != null) return translation(original, match.groups[1]!!.range, target, null)
        }
        TRANSLATE_ONLY.matchEntire(sentence)?.let { match ->
            return translation(original, match.groups[1]!!.range, null, null)
        }
        HOW_DO_YOU_SAY.matchEntire(sentence)?.let { match ->
            val target = TranslationLanguages.codeFor(match.groupValues[2])
            if (target != null) return translation(original, match.groups[1]!!.range, target, null)
        }
        (MEANING_IN.matchEntire(sentence) ?: WHAT_IS_IN.matchEntire(sentence))?.let { match ->
            val target = TranslationLanguages.codeFor(match.groupValues[2])
            if (target != null) return translation(original, match.groups[1]!!.range, target, null)
        }
        BARE_IN.matchEntire(sentence)?.let { match ->
            val target = TranslationLanguages.codeFor(match.groupValues[2])
            if (target != null && readsAsPhrase(match.groupValues[1])) {
                return translation(original, match.groups[1]!!.range, target, null)
            }
        }
        return null
    }

    /**
     * Whether [text], from a bare "X in <language>" with nothing else asking for a translation, reads as words to
     * translate: a single word ("hola in english") or a phrase with a person, question or courtesy in it, and never a
     * search for songs, films or lessons in that language.
     */
    private fun readsAsPhrase(text: String): Boolean {
        val words = text.trim().trim(*QUOTES).split(' ').map { it.trim(*QUOTES, ',', '.', '!', '?') }
        if (words.any { it in NOT_TRANSLATABLE }) return false
        return words.size == 1 || words.any { it in PHRASE_WORDS }
    }

    /**
     * The text to translate, cut from [original] at [range] of its lowercased form so the user's own capitals stay.
     * Lowercasing and collapsing spaces can move characters, so the cut is only trusted when it reads the same.
     */
    private fun translation(original: String, range: IntRange, target: String?, source: String?): LiveAnswerQuery.Translate? {
        val collapsed = original.trim().replace(WHITESPACE, " ")
        val lowered = collapsed.lowercase(Locale.ROOT)
        val sentence = lowered.trimEnd('?', '.', '!').trim()
        val fromOriginal = if (lowered.length == collapsed.length && range.last < collapsed.length) {
            collapsed.substring(range)
        } else null
        val text = (fromOriginal?.takeIf { it.lowercase(Locale.ROOT) == sentence.substring(range) } ?: sentence.substring(range))
            .trim().trim(*QUOTES).trim()
        if (text.isEmpty() || text.length > MAX_TRANSLATE_CHARS) return null
        // "translate english" or "spanish in english" name languages, not text.
        if (target == null && TranslationLanguages.codeFor(text) != null) return null
        return LiveAnswerQuery.Translate(text, target, source)
    }

    // ---------------------------------------------------------------------------------------------
    // WEATHER
    // ---------------------------------------------------------------------------------------------

    private fun parseWeather(sentence: String): LiveAnswerQuery.Weather? {
        val asked = sentence.replace(WEATHER_LEAD, "").replace(WEATHER_LIKE, "weather")
        if (!WEATHER_WORD.containsMatchIn(asked) || WEATHER_SPAN.containsMatchIn(asked)) return null
        val unit = WEATHER_UNIT.find(asked)?.let {
            if (it.groupValues[1] == "fahrenheit") TemperatureUnit.FAHRENHEIT else TemperatureUnit.CELSIUS
        }
        // What's left once the scale and "near me" are out is the plain question: "weather near me tomorrow" is
        // "weather tomorrow", here.
        val q = asked.replace(WEATHER_UNIT, " ").replace(WEATHER_HERE, " ").replace(WHITESPACE, " ").trim()
            .replace(WEATHER_FOR_DAY, "")
        WEATHER_ALONE.matchEntire(q)?.let { return LiveAnswerQuery.Weather(null, dayOf(it.groupValues[1]), unit) }
        WEATHER_OWNED.matchEntire(q)?.let { match ->
            return LiveAnswerQuery.Weather(null, dayOf(match.groupValues[1]), unit)
        }
        WEATHER_DAY_IN.matchEntire(q)?.let { match ->
            return place(match.groupValues[2])?.let { LiveAnswerQuery.Weather(it, dayOf(match.groupValues[1]), unit) }
        }
        WEATHER_IN.matchEntire(q)?.let { match ->
            return place(match.groupValues[1])?.let { LiveAnswerQuery.Weather(it, dayOf(match.groupValues[2]), unit) }
        }
        WEATHER_PLACE_FIRST.matchEntire(q)?.let { match ->
            return place(match.groupValues[1])?.let { LiveAnswerQuery.Weather(it, dayOf(match.groupValues[2]), unit) }
        }
        WEATHER_BARE_PLACE.matchEntire(q)?.let { match ->
            return place(match.groupValues[1])?.let { LiveAnswerQuery.Weather(it, ForecastDay.TODAY, unit) }
        }
        return null
    }

    /** "tomorrow", "tomorrow morning", "tomorrow's": tomorrow; every other day word is today. */
    private fun dayOf(word: String): ForecastDay = if (word.startsWith("tomorrow")) ForecastDay.TOMORROW else ForecastDay.TODAY

    private fun place(raw: String): String? {
        val place = raw.trim().trim(',', '.').trim()
        if (place.isEmpty() || place in NOT_A_PLACE || !PLACE.matches(place) || place.split(' ').size > 5) return null
        return place
    }
}

/**
 * The languages a translation query can name, by the names people type for them ("spanish", "español"), and their
 * BCP-47 codes. Google Translate takes every one of them ([GoogleTranslate.googleCode] gives its own spelling).
 */
object TranslationLanguages {
    private val BY_NAME: Map<String, String> = buildMap {
        fun add(code: String, vararg names: String) = names.forEach { put(it, code) }
        add("af", "afrikaans"); add("ar", "arabic"); add("be", "belarusian"); add("bg", "bulgarian")
        add("bn", "bengali", "bangla"); add("ca", "catalan"); add("cs", "czech"); add("cy", "welsh")
        add("da", "danish"); add("de", "german", "deutsch"); add("el", "greek"); add("en", "english")
        add("eo", "esperanto"); add("es", "spanish", "español", "espanol", "castilian"); add("et", "estonian")
        add("fa", "persian", "farsi"); add("fi", "finnish"); add("fr", "french", "français", "francais")
        add("ga", "irish", "irish gaelic"); add("gl", "galician"); add("gu", "gujarati"); add("he", "hebrew")
        add("hi", "hindi"); add("hr", "croatian"); add("ht", "haitian", "haitian creole"); add("hu", "hungarian")
        add("id", "indonesian"); add("is", "icelandic"); add("it", "italian", "italiano"); add("ja", "japanese")
        add("ka", "georgian"); add("kn", "kannada"); add("ko", "korean"); add("lt", "lithuanian")
        add("lv", "latvian"); add("mk", "macedonian"); add("mr", "marathi"); add("ms", "malay")
        add("mt", "maltese"); add("nl", "dutch", "nederlands", "flemish"); add("no", "norwegian")
        add("pl", "polish"); add("pt", "portuguese", "português", "portugues"); add("ro", "romanian")
        add("ru", "russian"); add("sk", "slovak"); add("sl", "slovenian", "slovene"); add("sq", "albanian")
        add("sv", "swedish"); add("sw", "swahili"); add("ta", "tamil"); add("te", "telugu"); add("th", "thai")
        add("tl", "tagalog", "filipino"); add("tr", "turkish"); add("uk", "ukrainian"); add("ur", "urdu")
        add("vi", "vietnamese"); add("zh", "chinese", "mandarin", "simplified chinese")
    }

    /** Every language code a query can name. */
    val CODES: Set<String> = BY_NAME.values.toSet()

    /** The code for a typed language name ("Spanish", "spanish language"), or null if it isn't one of [CODES]. */
    fun codeFor(name: String): String? {
        val key = name.trim().lowercase(Locale.ROOT).removeSuffix(" language").trim()
        return BY_NAME[key]
    }

    /**
     * The code for a [locale]'s language, or null when it isn't one of [CODES]. Java still reports Hebrew,
     * Indonesian and Yiddish under their old codes on some phones, and Norwegian Bokmål as "nb".
     */
    fun codeFor(locale: Locale): String? {
        val code = when (val language = locale.language) {
            "iw" -> "he"
            "in" -> "id"
            "nb", "nn" -> "no"
            "fil" -> "tl"
            else -> language
        }
        return code.takeIf { it in CODES }
    }

    /** [code]'s name in [displayLocale] with a capital letter: "Spanish", "Español". */
    fun displayName(code: String, displayLocale: Locale = Locale.getDefault()): String =
        Locale.forLanguageTag(code).getDisplayLanguage(displayLocale).replaceFirstChar { it.titlecase(displayLocale) }
            .ifEmpty { code }
}
