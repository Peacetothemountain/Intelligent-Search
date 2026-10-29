package com.pixel.intelligentsearch.screenshots

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult

/**
 * Finds layout faults in a rendered screen from its semantics tree: text that is clipped or cut short, content
 * hidden by its container or pushed past the screen edge, text drawn over other text, touch targets drawn over
 * other touch targets, and touch targets smaller than 48 dp. Used by [ResponsiveLayoutAuditTest] to check every
 * screen at many device and text sizes.
 */
object LayoutAudit {

    /** [serious] faults fail the audit (see [isSerious]); the rest are only reported. */
    enum class Kind(val serious: Boolean) {
        CLIPPED_TEXT(true),
        MID_WORD_BREAK(true),
        HIDDEN_BY_CONTAINER(true),
        OFF_SCREEN(true),
        OVERLAPPING_TEXT(true),
        OVERLAPPING_TARGETS(true),
        SMALL_TOUCH_TARGET(true),
        // A line cut short with "…" on purpose (maxLines): the rest is a tap away, so it is reported, not failed.
        ELLIPSIZED_TEXT(false)
    }

    data class Issue(val kind: Kind, val label: String, val bounds: Rect)

    /** WCAG 1.4.10 Reflow is defined down to 320 CSS px; text may break inside words only on narrower screens. */
    const val REFLOW_MIN_WIDTH_DP = 320

    /** Whether [issue] fails the audit on a screen [widthDp] wide. */
    fun isSerious(issue: Issue, widthDp: Int): Boolean = when (issue.kind) {
        Kind.MID_WORD_BREAK -> widthDp >= REFLOW_MIN_WIDTH_DP
        else -> issue.kind.serious
    }

    fun inspect(root: SemanticsNode, density: Float): List<Issue> {
        val window = root.boundsInRoot
        val issues = mutableListOf<Issue>()
        val texts = mutableListOf<Pair<String, Rect>>()
        val targets = mutableListOf<Target>()
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
                    // An ellipsized line measures as the whole text before the "…" replaced its end, so it is left
                    // out here and reported as ELLIPSIZED_TEXT below.
                    val lines = layout.lineCount
                    val lineHeight = if (lines > 0) layout.multiParagraph.height / lines else 0f
                    val widest = (0 until lines).filterNot { layout.isLineEllipsized(it) }
                        .maxOfOrNull { layout.getLineRight(it) - layout.getLineLeft(it) } ?: 0f
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
                // A target is as big as its touch area: M3's minimumInteractiveComponentSize (IconButton, Checkbox,
                // Switch, a clickable Surface...) pads a smaller node out to 48 dp in the node's own layout chain.
                val touch = touchAreaOf(node)
                val minPx = 48f * density
                if (touch.width < minPx - 1f || touch.height < minPx - 1f) issues += Issue(Kind.SMALL_TOUCH_TARGET, label, touch)
                val visibleTouch = clippedByAncestors(node, touch)
                if (visibleTouch.width > 0f && visibleTouch.height > 0f) targets += Target(node, label, visibleTouch)
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

        // Touch target over touch target: two targets, neither inside the other, whose visible touch areas share
        // more than a 1 dp sliver, so a tap there can land on the wrong one. A button inside a clickable row is
        // nested, not overlapping.
        for (i in targets.indices) for (j in i + 1 until targets.size) {
            val a = targets[i]
            val b = targets[j]
            val overlap = a.touch.intersect(b.touch)
            if (overlap.width <= density || overlap.height <= density) continue
            if (a.node.isAncestorOf(b.node) || b.node.isAncestorOf(a.node)) continue
            issues += Issue(Kind.OVERLAPPING_TARGETS, "${a.label.ifBlank { "#${a.node.id}" }} over ${b.label.ifBlank { "#${b.node.id}" }}", overlap)
        }
        return issues
    }

    private class Target(val node: SemanticsNode, val label: String, val touch: Rect)

    private fun SemanticsNode.isAncestorOf(other: SemanticsNode) = ancestors(other).any { it.id == id }

    /**
     * The node's touch area in root coordinates: its own bounds, grown to the size M3's minimum-interactive modifier
     * gives it when that modifier is in the node's layout chain.
     */
    private fun touchAreaOf(node: SemanticsNode): Rect {
        var area = Rect(node.positionInRoot, Size(node.size.width.toFloat(), node.size.height.toFloat()))
        for (info in node.layoutInfo.getModifierInfo()) {
            if (!info.modifier.javaClass.name.contains("MinimumInteractive")) continue
            val coordinates = info.coordinates
            if (!coordinates.isAttached) continue
            val expanded = Rect(coordinates.positionInRoot(), Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat()))
            area = Rect(
                minOf(area.left, expanded.left), minOf(area.top, expanded.top),
                maxOf(area.right, expanded.right), maxOf(area.bottom, expanded.bottom)
            )
        }
        return area
    }

    /** What of [area] its ancestors' clips leave visible (a scrolled list, a clipped card). */
    private fun clippedByAncestors(node: SemanticsNode, area: Rect): Rect =
        ancestors(node).fold(area) { visible, ancestor -> visible.intersect(ancestor.boundsInRoot) }

    private fun layoutOf(node: SemanticsNode): TextLayoutResult? {
        val action = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action ?: return null
        val results = mutableListOf<TextLayoutResult>()
        return if (action(results)) results.firstOrNull() else null
    }

    private fun ancestors(node: SemanticsNode): Sequence<SemanticsNode> = generateSequence(node.parent) { it.parent }

    /**
     * A short human label: a text field as `field "its text"`, else the node's text, else its content description,
     * else its click label in parentheses, else its test tag.
     */
    private fun labelOf(node: SemanticsNode): String {
        node.config.getOrNull(SemanticsProperties.EditableText)?.let { return "field \"${it.text}\"" }
        node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }?.takeIf { it.isNotBlank() }?.let { return it }
        node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")?.takeIf { it.isNotBlank() }?.let { return "[$it]" }
        node.config.getOrNull(SemanticsActions.OnClick)?.label?.takeIf { it.isNotBlank() }?.let { return "($it)" }
        node.config.getOrNull(SemanticsProperties.TestTag)?.let { return "#$it" }
        return ""
    }
}
