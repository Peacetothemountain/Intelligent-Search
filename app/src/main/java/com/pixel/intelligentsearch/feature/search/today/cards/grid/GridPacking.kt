package com.pixel.intelligentsearch.feature.search.today.cards.grid

import androidx.compose.ui.unit.IntRect
import com.pixel.intelligentsearch.feature.search.today.cards.CardKind
import com.pixel.intelligentsearch.feature.search.today.cards.CardSize
import com.pixel.intelligentsearch.feature.search.today.cards.CardSpec
import com.pixel.intelligentsearch.feature.search.today.cards.DiscoverLayout
import com.pixel.intelligentsearch.feature.search.today.cards.GRID_COLUMNS
import com.pixel.intelligentsearch.feature.search.today.cards.GRID_MAX_ROWS
import kotlin.math.roundToInt

/*
 * Discover's packing, as plain math with no Compose in it: where each card goes in reading order, then where that is
 * on the page in pixels. The page is a run of blocks down the page. A full-width card is a block of its own; built-in
 * cards narrower than the page share a band side by side; cell cards pack on a 4-across grid of square cells. Order
 * is strict: each card takes the first free spot at or after the one before it, never an earlier gap.
 *
 * NG Designs
 */

/** The three kinds of block the page is made of. */
internal enum class BlockType {
    /** One built-in card the page's full width, as tall as its content. */
    FULL,

    /** Built-in cards narrower than the page, side by side and top-aligned, each as tall as its content. */
    BAND,

    /** Cards of whole cells on the grid. */
    CELLS
}

/**
 * Where one card goes in reading order: in [block] (counted down the page) of [type], on visual [line] (each band, each
 * full card and each row of cells is a line, counted down the page), from [column] across [columns] columns, and for
 * cell cards from [row] of its block across [rows] rows ([rows] is 0 for cards as tall as their content).
 */
internal data class Slot(
    val block: Int,
    val type: BlockType,
    val line: Int,
    val column: Int,
    val row: Int,
    val columns: Int,
    val rows: Int
)

/**
 * The slots for cards of [sizes] in reading order; a null size is a card not on the page, which takes no slot. A
 * full-width [CardSize.Natural] card closes whatever is open; narrower natural cards share a band while their columns
 * add up to [GRID_COLUMNS] or less; [CardSize.Cells] cards pack on the open block's cells with the in-line cursor.
 */
internal fun packSlots(sizes: List<CardSize?>): List<Slot?> {
    val out = ArrayList<Slot?>(sizes.size)
    var block = -1
    var nextLine = 0
    var open: BlockType? = null
    var bandLine = 0
    var bandUsed = 0
    val cells = ArrayList<BooleanArray>()
    var cursorRow = 0
    var cursorColumn = 0
    var cellsLine = 0

    fun close() {
        if (open == BlockType.CELLS) nextLine = cellsLine + cells.size
        open = null
    }

    fun free(row: Int, column: Int, width: Int, height: Int): Boolean {
        for (y in row until row + height) {
            val line = cells.getOrNull(y) ?: continue
            for (x in column until column + width) if (line[x]) return false
        }
        return true
    }

    for (size in sizes) {
        when (size) {
            null -> out += null
            is CardSize.Natural -> {
                val width = size.columns.coerceIn(1, GRID_COLUMNS)
                when {
                    width == GRID_COLUMNS -> {
                        close()
                        block++
                        out += Slot(block, BlockType.FULL, nextLine++, 0, 0, GRID_COLUMNS, 0)
                    }
                    open == BlockType.BAND && bandUsed + width <= GRID_COLUMNS -> {
                        out += Slot(block, BlockType.BAND, bandLine, bandUsed, 0, width, 0)
                        bandUsed += width
                    }
                    else -> {
                        close()
                        block++
                        open = BlockType.BAND
                        bandLine = nextLine++
                        bandUsed = width
                        out += Slot(block, BlockType.BAND, bandLine, 0, 0, width, 0)
                    }
                }
            }
            is CardSize.Cells -> {
                val width = size.columns.coerceIn(1, GRID_COLUMNS)
                val height = size.rows.coerceIn(1, GRID_MAX_ROWS)
                if (open != BlockType.CELLS) {
                    close()
                    block++
                    open = BlockType.CELLS
                    cells.clear()
                    cursorRow = 0
                    cursorColumn = 0
                    cellsLine = nextLine
                }
                var row = cursorRow
                var column = cursorColumn
                while (true) {
                    if (column + width > GRID_COLUMNS) {
                        row++
                        column = 0
                        continue
                    }
                    if (free(row, column, width, height)) break
                    column++
                }
                while (cells.size < row + height) cells += BooleanArray(GRID_COLUMNS)
                for (y in row until row + height) for (x in column until column + width) cells[y][x] = true
                cursorRow = row
                cursorColumn = column + width
                out += Slot(block, BlockType.CELLS, cellsLine + row, column, row, width, height)
            }
        }
    }
    return out
}

/**
 * The grid's measures for a page [width] pixels wide with [gap] pixels between cards: [GRID_COLUMNS] square cells
 * across. Positions round to whole pixels the way a column of cards does, so the cards' edges line up.
 */
internal class GridMetrics(val width: Int, val gap: Int) {
    /** One cell's side. */
    val cell: Float = (width - (GRID_COLUMNS - 1) * gap) / GRID_COLUMNS.toFloat()

    /** One cell and the gap after it. */
    val pitch: Float get() = cell + gap

    /** Where cell [index] starts, from the grid's edge. */
    fun start(index: Int): Int = (index * pitch).roundToInt()

    /** How far [count] cells from [from] reach, gaps between them included. */
    fun span(from: Int, count: Int): Int = ((from + count) * pitch - gap).roundToInt() - start(from)

    /** The whole cells a [length] pixels long comes closest to, between 1 and [limit]. */
    fun cellsFor(length: Float, limit: Int): Int = ((length + gap) / pitch).roundToInt().coerceIn(1, limit)
}

/** An open cell before a block's last card, where a card can be dropped: it goes in at layout index [insertAt]. */
internal data class Spot(val rect: IntRect, val insertAt: Int)

/**
 * The cards' places in pixels: [rects] by layout index (null for cards not on the page), the page's [height], and
 * the open [spots] a dragged card can be dropped into.
 */
internal class Placement(val rects: List<IntRect?>, val height: Int, val spots: List<Spot>)

/**
 * Lays [slots] out down the page with [metrics]: [naturalHeight] is how tall the card at a layout index draws when
 * it is as tall as its content. Blocks follow each other with one gap between them, as a column of cards does; a
 * block with nothing to draw (a card whose content isn't there yet) takes no space and no gap.
 */
internal fun placeSlots(slots: List<Slot?>, naturalHeight: (Int) -> Int, metrics: GridMetrics): Placement {
    val rects = arrayOfNulls<IntRect>(slots.size)
    val spots = ArrayList<Spot>()
    var bottom = 0
    var any = false
    var i = 0
    while (i < slots.size) {
        val first = slots[i]
        if (first == null) {
            i++
            continue
        }
        val members = ArrayList<Int>()
        var j = i
        while (j < slots.size) {
            val slot = slots[j]
            if (slot != null) {
                if (slot.block != first.block) break
                members += j
            }
            j++
        }
        val rowsUsed = if (first.type == BlockType.CELLS) members.maxOf { slots[it]!!.row + slots[it]!!.rows } else 0
        val height = when (first.type) {
            BlockType.FULL, BlockType.BAND -> members.maxOf { naturalHeight(it) }
            BlockType.CELLS -> metrics.span(0, rowsUsed)
        }
        val top = if (any && height > 0) bottom + metrics.gap else bottom
        for (m in members) {
            val slot = slots[m]!!
            rects[m] = when (slot.type) {
                BlockType.FULL -> IntRect(0, top, metrics.width, top + naturalHeight(m))
                BlockType.BAND -> {
                    val x = metrics.start(slot.column)
                    IntRect(x, top, x + metrics.span(slot.column, slot.columns), top + naturalHeight(m))
                }
                BlockType.CELLS -> {
                    val x = metrics.start(slot.column)
                    val y = top + metrics.start(slot.row)
                    IntRect(x, y, x + metrics.span(slot.column, slot.columns), y + metrics.span(slot.row, slot.rows))
                }
            }
        }
        if (first.type == BlockType.CELLS) spots += openSpots(slots, members, top, metrics)
        if (height > 0) {
            bottom = top + height
            any = true
        }
        i = j
    }
    return Placement(rects.toList(), bottom, spots)
}

/** The open cells of the cell block of [members] (at [top]) before its last card, each with where a card dropped there goes. */
private fun openSpots(slots: List<Slot?>, members: List<Int>, top: Int, metrics: GridMetrics): List<Spot> {
    val taken = HashSet<Int>()
    for (m in members) {
        val s = slots[m]!!
        for (y in s.row until s.row + s.rows) for (x in s.column until s.column + s.columns) taken += y * GRID_COLUMNS + x
    }
    val starts = members.map { slots[it]!!.row * GRID_COLUMNS + slots[it]!!.column }
    val last = starts.last()
    val out = ArrayList<Spot>()
    for (index in 0 until last) {
        if (index in taken) continue
        val row = index / GRID_COLUMNS
        val column = index % GRID_COLUMNS
        val at = members[starts.indexOfFirst { it > index }]
        val x = metrics.start(column)
        val y = top + metrics.start(row)
        out += Spot(IntRect(x, y, x + metrics.span(column, 1), y + metrics.span(row, 1)), at)
    }
    return out
}

/**
 * The size a card of [kind] takes when resized to [columns] by [rows] cells: a built-in card takes the cells in its
 * shaped design when it has one and they are less than 1.5 times as wide as tall, and is otherwise its own design
 * [columns] wide, as tall as its content; the user's own cards always take the cells.
 */
internal fun candidateSize(kind: CardKind, columns: Int, rows: Int): CardSize {
    val c = columns.coerceIn(1, GRID_COLUMNS)
    val r = rows.coerceIn(1, GRID_MAX_ROWS)
    return when {
        !kind.builtIn -> CardSize.Cells(c, r)
        kind.shaped && c < r * 1.5f -> CardSize.Cells(c, r)
        else -> CardSize.Natural(c)
    }
}

/** [DiscoverLayout.cards] with the card at [from] taken out and put in at [to]; the rest shift one step, in line. */
internal fun DiscoverLayout.moved(from: Int, to: Int): DiscoverLayout {
    if (from == to || from !in cards.indices) return this
    val list = cards.toMutableList()
    val card = list.removeAt(from)
    list.add(to.coerceIn(0, list.size), card)
    return copy(cards = list)
}

/**
 * The layout with the card at [index] at [size], keeping its row as a launcher widget does: if the new size no
 * longer fits on the row it was on, it takes that row's start and the cards before it on the row shift on, in line.
 * [shown] says which cards are on the page.
 */
internal fun DiscoverLayout.resized(index: Int, size: CardSize, shown: (CardSpec) -> Boolean): DiscoverLayout {
    if (index !in cards.indices) return this
    val before = packSlots(cards.map { if (shown(it)) it.size else null })
    val resized = copy(cards = cards.toMutableList().also { it[index] = it[index].copy(size = size) })
    val oldLine = before[index]?.line ?: return resized
    val after = packSlots(resized.cards.map { if (shown(it)) it.size else null })
    val newLine = after[index]?.line ?: return resized
    val rowStart = before.indices.firstOrNull { it != index && before[it]?.line == oldLine } ?: return resized
    return if (newLine > oldLine && rowStart < index) resized.moved(index, rowStart) else resized
}
