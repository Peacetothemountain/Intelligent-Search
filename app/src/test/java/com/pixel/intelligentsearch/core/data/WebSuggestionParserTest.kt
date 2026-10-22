package com.pixel.intelligentsearch.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Suggestion responses recorded from the engines (test resources under `websuggest/`, fetched in October 2026):
 * Google's `mobile-gws-wiz-hp` answers with entity names, descriptions, thumbnails and bold marks, the other engines
 * with plain OpenSearch lists.
 */
@RunWith(RobolectricTestRunner::class)
class WebSuggestionParserTest {

    private fun sample(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("websuggest/$name")) { "missing sample $name" }.readText()

    private fun WebSuggestion.bold(): List<String> = boldRanges.map { query.substring(it.first, it.last + 1) }

    @Test
    fun anEntityKeepsItsNameDescriptionAndPicture() {
        val suggestions = WebSuggestionParser.parse(sample("google_entity_obama.txt"))
        val obama = suggestions.first()
        assertEquals("obama", obama.query)
        assertEquals("Barack Obama", obama.title)
        assertEquals("44th U.S. President", obama.subtitle)
        assertTrue(obama.imageUrl!!.startsWith("https://encrypted-tbn0.gstatic.com/images?q=tbn:"))
        assertTrue(obama.isEntity)
        assertEquals(8, suggestions.size)
    }

    @Test
    fun plainQueriesHaveTheCompletionBoldAndNoEntityData() {
        val suggestions = WebSuggestionParser.parse(sample("google_entity_obama.txt"))
        val center = suggestions[1]
        assertEquals("obama presidential center", center.query)
        assertEquals(listOf(" presidential center"), center.bold())
        assertNull(center.title)
        assertNull(center.imageUrl)
        assertFalse(center.isEntity)
    }

    @Test
    fun songsAndPeopleCarryTheirDescriptions() {
        val song = WebSuggestionParser.parse(sample("google_song_never_gonna.txt")).first()
        assertEquals("never gonna give you up", song.query)
        assertEquals("Never Gonna Give You Up", song.title)
        assertEquals("Song by Rick Astley", song.subtitle)

        val creator = WebSuggestionParser.parse(sample("google_wrapped_mr_beast.txt")).first()
        assertEquals("MrBeast", creator.title)
        assertEquals("American YouTuber", creator.subtitle)
        assertTrue(creator.imageUrl!!.startsWith("https://"))
    }

    @Test
    fun theJavascriptWrappedAnswerReadsTheSameAsTheXssiOne() {
        val wrapped = WebSuggestionParser.parse(sample("google_wrapped_mr_beast.txt"))
        assertEquals("mr beast net worth", wrapped[1].query)
        assertEquals(listOf(" net worth"), wrapped[1].bold())
    }

    @Test
    fun aCorrectedWordIsBoldAsWellAsTheCompletion() {
        // Typed "teylor swi": Google fixes the first word and bolds both the fix and the completion.
        val wedding = WebSuggestionParser.parse(sample("google_corrected_teylor_swi.txt"))[1]
        assertEquals("taylor swift wedding", wedding.query)
        assertEquals(listOf("taylor", "ft wedding"), wedding.bold())
    }

    @Test
    fun htmlEntitiesAreDecoded() {
        val dont = WebSuggestionParser.parse(sample("google_html_entities_dont_st.txt"))
        assertEquals("don't stop believing", dont[0].query)
        assertEquals("Don't Stop Believin'", dont[0].title)
        assertEquals("Song by Journey", dont[0].subtitle)
        // "don<b>&#39;</b>t st<b>op til...</b>": the apostrophe Google added is bold, after decoding.
        assertEquals("don't stop til you get enough lyrics", dont[1].query)
        assertEquals(listOf("'", "op til you get enough lyrics"), dont[1].bold())

        val att = WebSuggestionParser.parse(sample("google_html_entities_att.txt"))
        assertEquals("at&t", att[0].query)
        assertEquals("Stadium · 1 AT&T Way, Arlington, TX, USA", att[1].subtitle)
    }

    @Test
    fun anEntityWithAnEmptyDescriptionStillHasItsPicture() {
        // "taylor sw" lists Taylor Swift with a picture; rows without "zi" have no subtitle rather than a blank one.
        val swift = WebSuggestionParser.parse(sample("google_taylor_sw.txt")).first()
        assertEquals("American singer-songwriter", swift.subtitle)
        assertTrue(WebSuggestionParser.parse(sample("google_taylor_sw.txt")).drop(1).all { it.subtitle == null })
    }

    @Test
    fun trendingSearchesParseLikeAnyOtherAnswer() {
        val trending = WebSuggestionParser.parse(sample("google_zero_prefix_trending.txt"))
        assertTrue(trending.size >= 10)
        assertTrue(trending.all { it.query.isNotBlank() && it.boldRanges.isEmpty() })
        // An entity among them may carry its picture inline.
        assertTrue(trending.any { it.imageUrl?.startsWith("data:image/") == true })
    }

    @Test
    fun openSearchEnginesGiveTheirPlainLists() {
        val ddg = WebSuggestionParser.parse(sample("duckduckgo_obama.json"))
        assertEquals("obama presidential center", ddg[1].query)
        val bing = WebSuggestionParser.parse(sample("bing_obama.json"))
        assertEquals(12, bing.size)
        val chrome = WebSuggestionParser.parse(sample("google_chrome_client_obama.json"))
        assertEquals("obama medal meme", chrome[1].query)
        assertTrue((ddg + bing + chrome).none { it.isEntity || it.boldRanges.isNotEmpty() })
    }

    @Test
    fun anythingElseGivesNoSuggestions() {
        assertTrue(WebSuggestionParser.parse("").isEmpty())
        assertTrue(WebSuggestionParser.parse("<html>Too many requests</html>").isEmpty())
        assertTrue(WebSuggestionParser.parse(")]}'\n{\"error\":1}").isEmpty())
    }

    @Test
    fun onlyHttpsAndInlineImagesAreKept() {
        val body = """)]}'
            [[["a",46,[512],{"zs":"http://example.com/a.jpg","zi":"Plain http"}],["b",46,[512],{"zs":"javascript:alert(1)"}],["c",46,[512],{"zs":"data:image/png;base64,AAAA"}]],{}]"""
        val (a, b, c) = WebSuggestionParser.parse(body)
        assertNull(a.imageUrl)
        assertNull(b.imageUrl)
        assertEquals("data:image/png;base64,AAAA", c.imageUrl)
    }

    @Test
    fun enginesWithoutMarksGetTheCompletionBoldFromWhatWasTyped() {
        assertEquals(listOf(5 until 13), WebSuggestion("obama library").highlightFor("obama"))
        assertEquals(emptyList<IntRange>(), WebSuggestion("barack obama").highlightFor("obama"))
        assertEquals(emptyList<IntRange>(), WebSuggestion("obama").highlightFor("obama"))
        // The engine's own marks win.
        assertEquals(listOf(0..1), WebSuggestion("obama", boldRanges = listOf(0..1)).highlightFor("obama"))
    }
}
