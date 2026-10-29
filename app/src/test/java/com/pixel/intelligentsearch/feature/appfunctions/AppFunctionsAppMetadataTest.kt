package com.pixel.intelligentsearch.feature.appfunctions

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.xmlpull.v1.XmlPullParser
import java.io.File

/**
 * Assistants read what Intelligent Search's functions are for from the app metadata the manifest links, parsed the
 * way the AppFunctions library does: the first element's `instructions` and `displayDescription` attributes.
 */
@RunWith(RobolectricTestRunner::class)
class AppFunctionsAppMetadataTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun metadata(): Pair<String?, String?> {
        val parser = context.resources.getXml(R.xml.app_functions_app_metadata)
        while (parser.eventType != XmlPullParser.START_TAG) parser.next()
        val instructions = parser.getAttributeValue(RES_AUTO, "instructions")
        val display = parser.getAttributeResourceValue(RES_AUTO, "displayDescription", 0)
            .takeIf { it != 0 }?.let(context::getString)
        return instructions to display
    }

    @Test
    fun assistantsAreToldWhatEveryFunctionIsFor() {
        val instructions = metadata().first.orEmpty()
        for (function in listOf("searchApps", "searchContacts", "calculate")) {
            assertTrue("$function isn't described", function in instructions)
        }
    }

    @Test
    fun peopleSeeAShortDescription() {
        assertEquals(context.getString(R.string.app_functions_display_description), metadata().second)
    }

    @Test
    fun theManifestLinksTheMetadata() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(
            Regex(
                """<property\s+android:name="android\.app\.appfunctions\.app_metadata"\s+""" +
                    """android:resource="@xml/app_functions_app_metadata"\s*/>"""
            ).containsMatchIn(manifest)
        )
    }

    private companion object {
        const val RES_AUTO = "http://schemas.android.com/apk/res-auto"
    }
}
