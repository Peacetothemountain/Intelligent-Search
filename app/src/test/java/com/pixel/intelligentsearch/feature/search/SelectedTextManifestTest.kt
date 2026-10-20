package com.pixel.intelligentsearch.feature.search

import com.pixel.intelligentsearch.feature.widget.WidgetActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The manifest entry for the text selection menu and share sheet. Other apps start it, so how it is declared is part of
 * keeping it safe: it must show no window, start in no task of its own that stays behind, and be reachable only
 * through its two plain-text filters.
 */
class SelectedTextManifestTest {

    private val androidNs = "http://schemas.android.com/apk/res/android"

    private val activities: List<Element> by lazy {
        // Unit tests run from the module directory; the repo root is tried too.
        val manifest = listOf(File("src/main/AndroidManifest.xml"), File("app/src/main/AndroidManifest.xml")).first { it.isFile }
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val nodes = factory.newDocumentBuilder().parse(manifest).getElementsByTagName("activity")
        (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun activity(name: String): Element = activities.single { it.getAttributeNS(androidNs, "name") == name }

    private fun Element.android(attribute: String): String? =
        if (hasAttributeNS(androidNs, attribute)) getAttributeNS(androidNs, attribute) else null

    private fun Element.children(tag: String): List<Element> {
        val nodes = getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    @Test
    fun theEntryShowsNoWindowAndLeavesNoTaskBehind() {
        val entry = activity(SelectedTextSearchActivity::class.java.name)
        assertEquals("@android:style/Theme.NoDisplay", entry.android("theme"))
        // An empty affinity keeps it out of the calling app's task and out of the overlay's.
        assertEquals("", entry.android("taskAffinity"))
        assertEquals("true", entry.android("excludeFromRecents"))
        assertEquals("true", entry.android("noHistory"))
    }

    @Test
    fun theEntryIsExportedForPlainTextSelectionAndSharingOnly() {
        val entry = activity(SelectedTextSearchActivity::class.java.name)
        assertEquals("true", entry.android("exported"))

        val filters = entry.children("intent-filter").map { filter ->
            Triple(
                filter.children("action").map { it.android("name") },
                filter.children("category").map { it.android("name") },
                filter.children("data").map { it.android("mimeType") to it.attributes.length }
            )
        }
        assertEquals(
            listOf(
                Triple(listOf("android.intent.action.PROCESS_TEXT"), listOf("android.intent.category.DEFAULT"), listOf("text/plain" to 1)),
                Triple(listOf("android.intent.action.SEND"), listOf("android.intent.category.DEFAULT"), listOf("text/plain" to 1))
            ),
            filters
        )
    }

    @Test
    fun theEntryIsOnByDefaultLikeItsSetting() {
        // The Search from Selected Text setting defaults to on; the component's own default must match, and the
        // setting alone turns it off (an "enabled" here would fight the state the app sets at run time).
        val entry = activity(SelectedTextSearchActivity::class.java.name)
        assertFalse(entry.hasAttributeNS(androidNs, "enabled"))
    }

    @Test
    fun theOverlayStaysPrivate() {
        assertEquals("false", activity(WidgetActivity::class.java.name).android("exported"))
    }
}
