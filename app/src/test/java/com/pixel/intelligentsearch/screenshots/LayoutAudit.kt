package com.pixel.intelligentsearch.screenshots

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult

/**
 * Finds layout faults in a rendered screen from its semantics tree: text that is clipped or cut short, content
 * hidden by its container or pushed past the screen edge, text drawn over other text, and touch targets smaller
 * than 48 dp. Used by [ResponsiveLayoutAuditTest] to check every screen at many device and text sizes.
 */
object LayoutAudit {

    enum class Kind(val serious: Boolean) {
        CLIPPED_TEXT(true),
        MID_WORD_BREAK(true),
        HIDDEN_BY_CONTAINER(true),
        OFF_SCREEN(true),
        OVERLAPPING_TEXT(true),
        ELLIPSIZED_TEXT(false),
        SMALL_TOUCH_TARGET(false)
    }

    data class Issue(val kind: Kind, val label: String, val bounds: Rect)

    fun inspect(root: SemanticsNode, density: Float): List<Issue> {
        val window = root.boundsInRoot
        val issues = mutableListOf<Issue>()
        val texts = mutableListOf<Pair<String, Rect>>()
        fun visit(node: SemanticsNode) {
            val label = labelOf(node)
            val clipped = node.boundsInRoot
            val unclipped = Rect(node.positionInRoot, androidx.compose.ui.geometry.Size(node.size.width.toFloat(), node.size.height.toFloat()))
            val hasArea = node.size.width > 1 && node.size.height > 1

            val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
            if (text.isNotBlank() && hasArea) {
                layoutOf(node)?.let { layout ->
                    // Measured from the laid-out lines: hasVisualOverflow (and the paragraph width) also trip on text that
                    // fits. Text is cut when a line is wider than its box, or its last line ends a quarter line below it.
                    val lines = layout.lineCount
                    val lineHeight = if (lines > 0) layout.multiParagraph.height / lines else 0f
                    val widest = (0 until lines).maxOfOrNull { layout.getLineRight(it) - layout.getLineLeft(it) } ?: 0f
                    val bottom = if (lines > 0) layout.getLineBottom(lines - 1) else 0f
                    if (widest > layout.size.width + 1f || bottom > layout.size.height + lineHeight * 0.25f) {
                        issues += Issue(Kind.CLIPPED_TEXT, text, clipped)
                    }
                    // A word split across lines without a hyphen ("Customizatio" / "n"). With hyphenation on,
                    // Android draws a hyphen at such a break, so only unhyphenated text counts.
                    val laidOut = layout.layoutInput.text.text
                    val hyphenated = layout.layoutInput.style.hyphens == androidx.compose.ui.text.style.Hyphens.Auto
                    val splitWord = !hyphenated && (0 until lines - 1).any { line ->
                        val end = layout.getLineEnd(line)
                        end in 1 until laidOut.length && laidOut[end - 1].isLetterOrDigit() && laidOut[end].isLetterOrDigit()
                    }
                    if (splitWord) issues += Issue(Kind.MID_WORD_BREAK, text, clipped)
                    else if (layout.lineCount > 0 && layout.isLineEllipsized(layout.lineCount - 1)) issues += Issue(Kind.ELLIPSIZED_TEXT, text, clipped)
                }
                if (clipped.width > 0f && clipped.height > 0f) texts += text to clipped
            }

            if (hasArea && label.isNotBlank()) {
                val horizontallyScrolled = ancestors(node).any { it.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange) != null }
                val scrollers = ancestors(node).filter {
                    it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) != null ||
                        it.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange) != null
                }
                val pastEdge = unclipped.left < window.left - 1f || unclipped.right > window.right + 1f
                if (pastEdge && !horizontallyScrolled) {
                    issues += Issue(Kind.OFF_SCREEN, label, unclipped)
                } else if (!pastEdge && clipped.width * clipped.height < 0.97f * unclipped.width * unclipped.height) {
                    // Cut by an ancestor's clip. A scrolling list cutting an item at its own edge is normal.
                    val atScrollEdge = scrollers.any { s ->
                        val b = s.boundsInRoot
                        kotlin.math.abs(clipped.top - b.top) < 1.5f || kotlin.math.abs(clipped.bottom - b.bottom) < 1.5f ||
                            kotlin.math.abs(clipped.left - b.left) < 1.5f || kotlin.math.abs(clipped.right - b.right) < 1.5f
                    }
                    val atWindowEdge = kotlin.math.abs(clipped.bottom - window.bottom) < 1.5f || kotlin.math.abs(clipped.top - window.top) < 1.5f
                    if (!atScrollEdge && !atWindowEdge && clipped.width > 0f && clipped.height > 0f) {
                        issues += Issue(Kind.HIDDEN_BY_CONTAINER, label, unclipped)
                    }
                }
            }

            val clickable = node.config.getOrNull(SemanticsActions.OnClick) != null
            if (clickable && hasArea) {
                val minPx = 48f * density
                if (node.size.width < minPx - 1f || node.size.height < minPx - 1f) issues += Issue(Kind.SMALL_TOUCH_TARGET, label, unclipped)
            }
            node.children.forEach(::visit)
        }
        visit(root)

        // Text over text: two text boxes sharing more than a third of the smaller one.
        for (i in texts.indices) for (j in i + 1 until texts.size) {
            val (ta, a) = texts[i]
            val (tb, b) = texts[j]
            val overlap = a.intersect(b)
            if (overlap.width <= 0f || overlap.height <= 0f) continue
            val smaller = minOf(a.width * a.height, b.width * b.height)
            if (smaller > 0f && overlap.width * overlap.height > smaller / 3f) {
                issues += Issue(Kind.OVERLAPPING_TEXT, "\"$ta\" over \"$tb\"", overlap)
            }
        }
        return issues
    }

    private fun layoutOf(node: SemanticsNode): TextLayoutResult? {
        val action = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action ?: return null
        val results = mutableListOf<TextLayoutResult>()
        return if (action(results)) results.firstOrNull() else null
    }

    private fun ancestors(node: SemanticsNode): Sequence<SemanticsNode> = generateSequence(node.parent) { it.parent }

    /** A short human label: the node's text, else its content description, else its test tag. */
    private fun labelOf(node: SemanticsNode): String {
        node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }?.takeIf { it.isNotBlank() }?.let { return it }
        node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")?.takeIf { it.isNotBlank() }?.let { return "[$it]" }
        node.config.getOrNull(SemanticsProperties.TestTag)?.let { return "#$it" }
        return ""
    }
}
