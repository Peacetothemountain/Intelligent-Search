package com.pixel.intelligentsearch.feature.search

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * File Search's Show Thumbnails switch ("Show Images and Video Thumbnails.") is about the user's own files: it used to
 * hide the pictures of people, songs and videos beside web suggestions and in the detail pane too. Every place the
 * overlay reads it now draws a file's thumbnail, and no web picture reads it.
 */
class FileThumbnailsScopeTest {

    private val sources = File("src/main/java/com/pixel/intelligentsearch/feature/search").walk().filter { it.extension == "kt" }

    @Test
    fun webPicturesDontFollowTheFileThumbnailsSwitch() {
        val readers = sources.flatMap { file ->
            file.readLines().withIndex()
                .filter { (_, line) -> "filesThumbnails" in line }
                .map { (i, line) -> Triple(file.name, i + 1, line.trim()) }
        }.toList()
        assertTrue("the switch is still read for files", readers.isNotEmpty())
        val webReaders = readers.filter { (_, _, line) -> "imageUrl" in line || "showThumbnail" in line || "Web(" in line }
        assertTrue(
            "Web pictures read File Search's Show Thumbnails:\n" + webReaders.joinToString("\n") { "${it.first}:${it.second}: ${it.third}" },
            webReaders.isEmpty()
        )
    }
}
