package com.pixel.intelligentsearch.core.occasion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Rules every doodle's animated vector must follow. The launcher plays them on its render thread, where an animation
 * of a property a shape doesn't have is not skipped but crashes the launcher, so these are checked for every doodle
 * rather than trusted.
 */
class DoodleArtRulesTest {

    private val drawables = File("src/main/res/drawable").listFiles { f -> f.name.startsWith("doodle_") }!!.sortedBy { it.name }

    private fun parse(file: File): Element =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(file).documentElement

    private fun Element.attr(name: String): String? = getAttributeNS(ANDROID, name).takeIf { it.isNotEmpty() }

    private fun Element.descendants(tag: String): List<Element> {
        val nodes = getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    @Test
    fun everyOccasionHasItsArt() {
        assertEquals(Occasion.entries.size, drawables.size)
        for (file in File("src/main/res/layout").listFiles { f -> f.name.startsWith("doodle_") }!!) {
            assertTrue(file.name, file.readText().contains("@drawable/${file.nameWithoutExtension}\""))
        }
    }

    @Test
    fun animationsOnlyTouchWhatTheirShapesHave() {
        for (file in drawables) {
            val root = parse(file)
            val named = HashMap<String, String>()
            for (tag in listOf("group", "path", "clip-path")) {
                for (element in root.descendants(tag)) {
                    val name = element.attr("name") ?: continue
                    assertTrue("${file.name}: '$name' is used twice", named.put(name, tag) == null)
                }
            }
            val targets = root.descendants("target")
            assertTrue("${file.name} has no animation", targets.isNotEmpty())
            for (target in targets) {
                val name = target.attr("name")!!
                val tag = named[name] ?: error("${file.name}: target '$name' names nothing")
                for (animator in target.descendants("objectAnimator")) {
                    val properties = listOfNotNull(animator.attr("propertyName")) +
                        animator.descendants("propertyValuesHolder").map { it.attr("propertyName")!! }
                    for (property in properties) {
                        assertTrue("${file.name}: a $tag can't animate $property ('$name')", property in ALLOWED.getValue(tag))
                    }
                    assertEquals("${file.name}: '$name' must loop", "infinite", animator.attr("repeatCount"))
                    for (holder in animator.descendants("propertyValuesHolder")) {
                        val fractions = holder.descendants("keyframe").map { it.attr("fraction")!!.toFloat() }
                        assertTrue("${file.name}: '$name' keyframes out of order", fractions == fractions.sorted())
                        assertTrue("${file.name}: '$name' keyframes outside 0..1", fractions.all { it in 0f..1f })
                    }
                }
            }
        }
    }

    @Test
    fun morphingPathsHaveMatchingCommands() {
        for (file in drawables) {
            for (animator in parse(file).descendants("objectAnimator").filter { it.attr("propertyName") == "pathData" }) {
                val from = commands(animator.attr("valueFrom")!!)
                val to = commands(animator.attr("valueTo")!!)
                assertEquals("${file.name}: path morph shapes differ", from, to)
            }
        }
    }

    private fun commands(path: String) = path.filter { it.isLetter() }

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
        val ALLOWED = mapOf(
            "group" to setOf("rotation", "pivotX", "pivotY", "scaleX", "scaleY", "translateX", "translateY"),
            "path" to setOf(
                "pathData", "fillColor", "strokeColor", "fillAlpha", "strokeAlpha", "strokeWidth",
                "trimPathStart", "trimPathEnd", "trimPathOffset"
            ),
            "clip-path" to setOf("pathData")
        )
    }
}
