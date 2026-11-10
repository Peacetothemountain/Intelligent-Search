package com.pixel.intelligentsearch.feature.search.today.cards.grid

import androidx.compose.ui.unit.IntRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Discover's freeform page: cards fall up against the cards they sit under, never overlap, and leave no holes. */
class FreeFlowTest {

    private val page = 400

    @Test
    fun fullWidthCardsStackInOrder() {
        val flow = flowCards(listOf(FlowCard("a", 0, page, 100), FlowCard("b", 0, page, 50)))
        assertEquals(IntRect(0, 0, page, 100), flow.rects["a"])
        assertEquals(IntRect(0, 100, page, 150), flow.rects["b"])
        assertEquals(150, flow.height)
    }

    @Test
    fun halvesSitSideBySideAndTheNextCardFallsUnderTheTaller() {
        val flow = flowCards(
            listOf(FlowCard("l", 0, 200, 120), FlowCard("r", 200, 200, 60), FlowCard("under", 0, page, 40))
        )
        assertEquals(0, flow.rects.getValue("r").top)
        assertEquals(120, flow.rects.getValue("under").top)
    }

    @Test
    fun aCardOnlyFallsAgainstCardsItSitsUnder() {
        val flow = flowCards(listOf(FlowCard("l", 0, 200, 300), FlowCard("r1", 200, 200, 60), FlowCard("r2", 200, 200, 60)))
        assertEquals(60, flow.rects.getValue("r2").top)
    }

    @Test
    fun aCardKeepsItsGapAndAHiddenCardLeavesNoHole() {
        val all = listOf(FlowCard("a", 0, page, 100), FlowCard("gone", 0, page, 200), FlowCard("b", 0, page, 50, gap = 30))
        val withoutGone = flowCards(all.filter { it.id != "gone" })
        assertEquals(130, withoutGone.rects.getValue("b").top)
    }

    @Test
    fun cardsNeverOverlap() {
        val cards = listOf(
            FlowCard("a", 0, 260, 90), FlowCard("b", 120, 280, 70), FlowCard("c", 0, 150, 150),
            FlowCard("d", 150, 250, 40, gap = 12), FlowCard("e", 50, 300, 60)
        )
        val rects = flowCards(cards).rects.values.toList()
        for (i in rects.indices) for (j in i + 1 until rects.size) {
            assertTrue("${rects[i]} overlaps ${rects[j]}", !rects[i].overlaps(rects[j]))
        }
    }

    @Test
    fun aMovedCardGoesBeforeTheFirstCardItSitsOverWhoseMiddleIsLower() {
        val cards = listOf(FlowCard("a", 0, page, 100), FlowCard("b", 0, page, 100), FlowCard("m", 0, page, 80))
        // Held over the top half of b: it goes between a and b, and b makes room.
        val arranged = arrange(cards, "m", activeTop = 105, snap = 20)
        assertEquals(listOf("a", "m", "b"), arranged.order)
        assertEquals(100, arranged.rects.getValue("m").top)
        assertEquals(180, arranged.rects.getValue("b").top)
        assertEquals(0, arranged.activeGap)
    }

    @Test
    fun aMovedCardLetGoFarBelowKeepsTheSpace() {
        val cards = listOf(FlowCard("a", 0, page, 100), FlowCard("m", 0, 200, 80))
        val arranged = arrange(cards, "m", activeTop = 160, snap = 20)
        assertEquals(160, arranged.rects.getValue("m").top)
        assertEquals(60, arranged.activeGap)
    }

    @Test
    fun aCardMovedSidewaysStaysAboveTheCardUnderIt() {
        val cards = listOf(FlowCard("a", 0, page, 100), FlowCard("m", 200, 200, 150), FlowCard("b", 0, page, 100))
        val picked = flowCards(listOf(FlowCard("a", 0, page, 100), FlowCard("m", 0, 200, 150), FlowCard("b", 0, page, 100))).rects
        val arranged = arrange(cards, "m", activeTop = 100, snap = 20, reference = picked)
        assertEquals(listOf("a", "m", "b"), arranged.order)
        assertEquals(250, arranged.rects.getValue("b").top)
    }

    @Test
    fun cardsNotOnThePageStayAfterTheCardBeforeThem() {
        val tops = mapOf("a" to 200, "b" to 0)
        val sorted = sortedDown(listOf("a", "hidden", "b")) { tops[it] }
        assertEquals(listOf("b", "a", "hidden"), sorted)
    }

    @Test
    fun edgesLineUpWithThePageAndOtherCards() {
        assertEquals(0, snapLeft(7, 100, page, emptyList(), 10).value)
        assertEquals(page - 100, snapLeft(295, 100, page, emptyList(), 10).value)
        assertEquals(120, snapLeft(126, 80, page, listOf(IntRect(120, 0, 300, 50)), 10).value)
        assertEquals(53, snapLeft(53, 80, page, listOf(IntRect(120, 0, 300, 50)), 10).value)
    }

    @Test
    fun widthsLineUpWithHalvesButNeverGoUnderTheLeast() {
        assertEquals(200, snapWidth(206, page, emptyList(), 10, 0))
        assertEquals(195, snapWidth(195, page, emptyList(), 4, 0))
        assertEquals(page / 4 + 20, snapWidth(page / 4 + 20, page, emptyList(), 10, page / 4 + 10))
    }
}
