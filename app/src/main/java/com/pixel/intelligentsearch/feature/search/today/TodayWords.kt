package com.pixel.intelligentsearch.feature.search.today

import java.time.LocalDate

/** A word for Word of the Day: what kind of word it is and what it means, in plain words. */
internal data class TodayWord(val word: String, val kind: String, val meaning: String)

/**
 * Word of the Day's words, kept in the app so the card works offline and asks no server. One a day, in turn through the
 * year ([wordOfTheDay]).
 */
internal val TODAY_WORDS = listOf(
    TodayWord("serendipity", "noun", "Finding something good without looking for it."),
    TodayWord("ephemeral", "adjective", "Lasting for a very short time."),
    TodayWord("luminous", "adjective", "Full of light; glowing softly."),
    TodayWord("resilient", "adjective", "Able to recover quickly after something hard."),
    TodayWord("halcyon", "adjective", "Calm, peaceful and happy, often of a time remembered."),
    TodayWord("wanderlust", "noun", "A strong wish to travel and see the world."),
    TodayWord("petrichor", "noun", "The pleasant smell of earth after rain."),
    TodayWord("sonder", "noun", "The sense that every passerby has a life as full as your own."),
    TodayWord("mellifluous", "adjective", "Sweet and smooth to hear."),
    TodayWord("quixotic", "adjective", "Hopeful and idealistic to the point of being impractical."),
    TodayWord("sanguine", "adjective", "Cheerfully confident, even in a hard situation."),
    TodayWord("eloquent", "adjective", "Able to say things clearly and movingly."),
    TodayWord("ineffable", "adjective", "Too great or beautiful to be put into words."),
    TodayWord("panacea", "noun", "A cure for every problem."),
    TodayWord("zenith", "noun", "The highest point something reaches."),
    TodayWord("candor", "noun", "Being open and honest in what you say."),
    TodayWord("alacrity", "noun", "Brisk, cheerful readiness."),
    TodayWord("verdant", "adjective", "Green with growing plants."),
    TodayWord("gregarious", "adjective", "Fond of company; sociable."),
    TodayWord("tenacious", "adjective", "Holding on firmly; not giving up."),
    TodayWord("ebullient", "adjective", "Overflowing with energy and good cheer."),
    TodayWord("solace", "noun", "Comfort in a time of sadness."),
    TodayWord("felicity", "noun", "Great happiness, or a well-chosen way of saying something."),
    TodayWord("labyrinth", "noun", "A maze of winding paths."),
    TodayWord("nostalgia", "noun", "A fond longing for the past."),
    TodayWord("cadence", "noun", "The rise and fall of a voice or a beat."),
    TodayWord("effervescent", "adjective", "Bubbly, lively and enthusiastic."),
    TodayWord("vellichor", "noun", "The wistful charm of a secondhand bookstore."),
    TodayWord("aplomb", "noun", "Calm self-assurance, especially under pressure."),
    TodayWord("idyllic", "adjective", "Peaceful and lovely, as if from a story."),
    TodayWord("fortitude", "noun", "Courage that lasts through pain or trouble."),
    TodayWord("whimsical", "adjective", "Playfully unusual."),
    TodayWord("lucid", "adjective", "Clear and easy to understand."),
    TodayWord("perennial", "adjective", "Lasting a long time, or coming back again and again."),
    TodayWord("reverie", "noun", "A pleasant daydream."),
    TodayWord("synergy", "noun", "Working together to make more than the parts alone."),
    TodayWord("ubiquitous", "adjective", "Seemingly everywhere at once."),
    TodayWord("cogent", "adjective", "Clear and convincing."),
    TodayWord("gossamer", "noun", "Something very light, thin and delicate."),
    TodayWord("jubilant", "adjective", "Full of joy, especially after a success."),
    TodayWord("kindred", "adjective", "Alike in nature or feeling."),
    TodayWord("meander", "verb", "To wander slowly along a winding path."),
    TodayWord("nascent", "adjective", "Just beginning to grow."),
    TodayWord("opulent", "adjective", "Rich and lavish."),
    TodayWord("placid", "adjective", "Calm and not easily upset."),
    TodayWord("savor", "verb", "To enjoy something slowly and fully."),
    TodayWord("tranquil", "adjective", "Free from noise and worry."),
    TodayWord("vivacious", "adjective", "Lively and full of spirit."),
    TodayWord("wistful", "adjective", "Gently sad and longing."),
    TodayWord("zeal", "noun", "Great energy for a cause or goal.")
)

/** The word for [date]: the next one in the list each day, starting over when it runs out. */
internal fun wordOfTheDay(date: LocalDate): TodayWord = TODAY_WORDS[Math.floorMod(date.toEpochDay(), TODAY_WORDS.size.toLong()).toInt()]
