package com.pixel.intelligentsearch.feature.settings

import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The doodles icon sits among Material Symbols Outlined icons, so it is drawn on their grid and at their weight. */
class DoodleIconTest {

    private val paths: List<VectorPath> get() = DoodleBrushIcon.root.flatten()

    private fun VectorGroup.flatten(): List<VectorPath> = flatMap {
        when (it) {
            is VectorPath -> listOf(it)
            is VectorGroup -> it.flatten()
        }
    }

    @Test
    fun itIsDrawnOnTheMaterialIconGrid() {
        assertEquals(24.dp, DoodleBrushIcon.defaultWidth)
        assertEquals(24.dp, DoodleBrushIcon.defaultHeight)
        assertEquals(24f, DoodleBrushIcon.viewportWidth)
        assertEquals(24f, DoodleBrushIcon.viewportHeight)
    }

    @Test
    fun itsLinesHaveTheOutlinedIconsWeightAndItsSparklesAreSolid() {
        val (lines, sparkles) = paths.partition { it.stroke != null }
        assertTrue("brush and flourish lines", lines.size >= 3)
        assertEquals("sparkles", 2, sparkles.size)
        lines.forEach {
            assertEquals(2f, it.strokeLineWidth)
            assertNull("lines are outlines, not shapes", it.fill)
        }
        sparkles.forEach { assertNotNull(it.fill) }
    }

    @Test
    fun everythingStaysInsideTheIcon() {
        // Each line is 2 wide, so its points keep 1 from the edges; the sparkles' points may touch them. A curve stays
        // within its control points, so checking every point of every command is enough.
        for (path in paths) {
            val margin = if (path.stroke != null) 1f else 0f
            val points = path.pathData.flatMap { node ->
                when (node) {
                    is PathNode.MoveTo -> listOf(node.x to node.y)
                    is PathNode.LineTo -> listOf(node.x to node.y)
                    is PathNode.QuadTo -> listOf(node.x1 to node.y1, node.x2 to node.y2)
                    is PathNode.CurveTo -> listOf(node.x1 to node.y1, node.x2 to node.y2, node.x3 to node.y3)
                    is PathNode.Close -> emptyList()
                    else -> error("only absolute commands are expected, not $node")
                }
            }
            for ((x, y) in points) assertTrue("($x, $y)", x in margin..24f - margin && y in margin..24f - margin)
        }
    }
}
