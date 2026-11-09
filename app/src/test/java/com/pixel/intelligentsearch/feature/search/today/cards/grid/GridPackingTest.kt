package com.pixel.intelligentsearch.feature.search.today.cards.grid

import androidx.compose.ui.unit.IntRect
import com.pixel.intelligentsearch.feature.search.today.cards.CardKind
import com.pixel.intelligentsearch.feature.search.today.cards.CardSize
import com.pixel.intelligentsearch.feature.search.today.cards.CardSize.Cells
import com.pixel.intelligentsearch.feature.search.today.cards.CardSize.Natural
import com.pixel.intelligentsearch.feature.search.today.cards.CardSpec
import com.pixel.intelligentsearch.feature.search.today.cards.DiscoverLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Discover's packing: cards go in reading order with no backfill, narrow natural cards share a band, a resized card
 * keeps its row, and the default layout is the page's plain column.
 */
class GridPackingTest {

    /** 4 cells of 100 px with 10 px gaps: 430 px across. */
    private val metrics = GridMetrics(width = 430, gap = 10)

    private fun spots(sizes: List<CardSize?>) = packSlots(sizes).map { it?.let { s -> Triple(s.row, s.column, s.line) } }

    @Test
    fun cellCardsGoInLineAndNeverBackfill() {
        // A 2x2, a 3x1 that can't fit beside it, then a 1x1 that would fit in the gap beside the 2x2 but comes after.
        val slots = spots(listOf(Cells(2, 2), Cells(3, 1), Cells(1, 1)))
        assertEquals(Triple(0, 0, 0), slots[0])
        assertEquals(Triple(2, 0, 2), slots[1])
        assertEquals(Triple(2, 3, 2), slots[2])
    }

    @Test
    fun aTallCardIsStackedBesideInLine() {
        // 2x2 then two 1x1: the second 1x1 goes on the next row, beside the 2x2, after the first.
        val slots = spots(listOf(Cells(2, 2), Cells(1, 1), Cells(1, 1), Cells(2, 1)))
        assertEquals(Triple(0, 2, 0), slots[1])
        assertEquals(Triple(0, 3, 0), slots[2])
        // The 2x1 is after the second 1x1 (row 0, column 3), so row 1 columns 2-3.
        assertEquals(Triple(1, 2, 1), slots[3])
    }

    @Test
    fun openCellsBeforeTheLastCardAreDropSpots() {
        val placement = placeSlots(packSlots(listOf(Cells(2, 2), Cells(3, 1))), { 0 }, metrics)
        // Row 0 and 1, columns 2-3 are open before the 3x1 on row 2; each drops in before the 3x1 (index 1).
        assertEquals(4, placement.spots.size)
        assertEquals(setOf(1), placement.spots.map { it.insertAt }.toSet())
        assertEquals(IntRect(220, 0, 320, 100), placement.spots.first().rect)
    }

    @Test
    fun narrowNaturalCardsShareABand() {
        val sizes = listOf(Natural(2), Natural(2), Natural(3), Natural(4))
        val slots = packSlots(sizes)
        assertEquals(slots[0]!!.line, slots[1]!!.line)
        assertEquals(2, slots[1]!!.column)
        assertEquals(slots[1]!!.line + 1, slots[2]!!.line)
        val heights = listOf(50, 80, 40, 30)
        val placement = placeSlots(slots, { heights[it] }, metrics)
        // Top-aligned, as tall as the taller one, then a gap.
        assertEquals(IntRect(0, 0, 210, 50), placement.rects[0])
        assertEquals(IntRect(220, 0, 430, 80), placement.rects[1])
        assertEquals(IntRect(0, 90, 320, 130), placement.rects[2])
        assertEquals(IntRect(0, 140, 430, 170), placement.rects[3])
        assertEquals(170, placement.height)
    }

    @Test
    fun aFullWidthNaturalCardClosesTheCells() {
        val placement = placeSlots(packSlots(listOf(Cells(1, 1), Natural(4), Cells(1, 1))), { 60 }, metrics)
        assertEquals(IntRect(0, 0, 100, 100), placement.rects[0])
        assertEquals(IntRect(0, 110, 430, 170), placement.rects[1])
        // A new block of cells starts under it, at its own first row.
        assertEquals(IntRect(0, 180, 100, 280), placement.rects[2])
    }

    @Test
    fun theDefaultLayoutIsAPlainColumn() {
        val cards = DiscoverLayout.DEFAULT.cards
        val heights = cards.indices.map { if (it == 3) 0 else 40 + it * 7 }
        // One part isn't on the page; another has nothing to draw yet.
        val sizes = cards.mapIndexed { i, card -> if (i == 5) null else card.size }
        val placement = placeSlots(packSlots(sizes), { heights[it] }, metrics)
        var y = 0
        var first = true
        cards.indices.forEach { i ->
            if (i == 5) {
                assertNull(placement.rects[i])
                return@forEach
            }
            val h = heights[i]
            if (h == 0) {
                assertEquals(0, placement.rects[i]!!.height)
                return@forEach
            }
            if (!first) y += 10
            first = false
            assertEquals("card $i", IntRect(0, y, 430, y + h), placement.rects[i])
            y += h
        }
        assertEquals(y, placement.height)
        assertEquals(emptyList<Spot>(), placement.spots)
    }

    @Test
    fun aResizedCardKeepsItsRow() {
        val a = CardSpec("a", CardKind.APP, Cells(1, 1), widgetId = 1)
        val b = CardSpec("b", CardKind.APP, Cells(1, 1), widgetId = 2)
        val c = CardSpec("c", CardKind.APP, Cells(2, 1), widgetId = 3)
        val layout = DiscoverLayout(listOf(a, b, c))
        // c grows to 3 across: no longer fits after a and b, so it takes the row's start and they shift on.
        val grown = layout.resized(2, Cells(3, 1)) { true }
        assertEquals(listOf("c", "a", "b"), grown.cards.map { it.id })
        assertEquals(Cells(3, 1), grown.cards[0].size)
        // Still fitting on its row, it stays where it is.
        val wider = DiscoverLayout(listOf(a, c)).resized(1, Cells(3, 1)) { true }
        assertEquals(listOf("a", "c"), wider.cards.map { it.id })
    }

    @Test
    fun builtInSizesTurnShapedOnlyWhenAboutSquare() {
        assertEquals(Cells(2, 2), candidateSize(CardKind.WEATHER, 2, 2))
        assertEquals(Natural(3), candidateSize(CardKind.WEATHER, 3, 2))
        assertEquals(Natural(2), candidateSize(CardKind.UP_NEXT, 2, 2))
        assertEquals(Cells(3, 1), candidateSize(CardKind.SITE, 3, 1))
    }
}
