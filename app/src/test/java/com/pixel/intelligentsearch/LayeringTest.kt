package com.pixel.intelligentsearch

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app is layered: `core` holds what every screen, widget and service shares and `feature` builds on it. Core
 * code never reaches up into a feature, so a feature can change (or a settings page move) without breaking the
 * search engine, the glance sources or the widgets underneath it.
 */
class LayeringTest {

    @Test
    fun coreNeverDependsOnAFeature() {
        val core = File("src/main/java/com/pixel/intelligentsearch/core")
        val sources = core.walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue("found no core sources under ${core.absolutePath}", sources.size > 50)
        val offenders = sources.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                val code = line.substringBefore("//").trim()
                // KDoc and block comments may name a feature class; code may not, imported or fully qualified.
                val inComment = code.startsWith("*") || code.startsWith("/*")
                if (!inComment && code.contains("com.pixel.intelligentsearch.feature")) {
                    "${file.relativeTo(core)}:${index + 1}: $code"
                } else null
            }
        }
        assertTrue("core depends on feature code:\n${offenders.joinToString("\n")}", offenders.isEmpty())
    }
}
