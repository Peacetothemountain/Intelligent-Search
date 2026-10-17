package com.pixel.intelligentsearch.feature.search

import com.pixel.intelligentsearch.core.data.ContactItem
import com.pixel.intelligentsearch.core.data.FileItem
import java.util.Locale

/**
 * Stand-in data for the debug menu's Mock Large Dataset and Mock Trending Queries switches. Contacts use the
 * 555-0101 to 555-0150 numbers reserved for fiction, so tapping one only opens the dialer; the files point at
 * an address no app serves, which the file opener already handles.
 */
internal object DebugMocks {

    const val MOCK_COUNT = 50

    val contacts: List<ContactItem> = (1..MOCK_COUNT).map { i ->
        ContactItem(
            name = String.format(Locale.ROOT, "Mock Contact %02d", i),
            phoneNumber = String.format(Locale.ROOT, "+1 555-01%02d", i),
            lookupUri = ""
        )
    }

    val files: List<FileItem> = (1..MOCK_COUNT).map { i ->
        val name = String.format(Locale.ROOT, "Mock File %02d.txt", i)
        FileItem(
            name = name,
            path = "/Mock/$name",
            mimeType = "text/plain",
            uri = String.format(Locale.ROOT, "content://com.pixel.intelligentsearch.mock/file/%02d", i)
        )
    }

    val trendingQueries: List<String> = listOf(
        "Weather this weekend",
        "Northern lights tonight",
        "Easy weeknight dinners",
        "Pixel camera tips",
        "Best hiking trails near me",
        "How to make cold brew"
    )
}
