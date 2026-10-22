package com.pixel.intelligentsearch.core.data

import com.pixel.intelligentsearch.core.search.MathematicalExpressionEngine
import com.pixel.intelligentsearch.core.search.NaturalLanguageIntentParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** How files are sorted into types, and how a query names one ("pdf invoice"). */
class FileTypesTest {

    @Test
    fun theExtensionDecidesWhenItIsKnown() {
        assertEquals(FileTypeGroup.PDF, FileTypes.groupOf("Tax Return.PDF", "application/octet-stream"))
        assertEquals(FileTypeGroup.IMAGES, FileTypes.groupOf("beach.heic", ""))
        assertEquals(FileTypeGroup.VIDEO, FileTypes.groupOf("clip.MKV", "*/*"))
        assertEquals(FileTypeGroup.AUDIO, FileTypes.groupOf("song.flac", "application/octet-stream"))
        assertEquals(FileTypeGroup.DOCUMENTS, FileTypes.groupOf("notes.md", "text/plain"))
        assertEquals(FileTypeGroup.DOCUMENTS, FileTypes.groupOf("letter.docx", "application/octet-stream"))
        assertEquals(FileTypeGroup.DOCUMENTS, FileTypes.groupOf("readme.txt", "application/octet-stream"))
        // A provider may call a CSV plain text; people go by the extension.
        assertEquals(FileTypeGroup.SPREADSHEETS, FileTypes.groupOf("budget.csv", "text/plain"))
        assertEquals(FileTypeGroup.SPREADSHEETS, FileTypes.groupOf("budget.xlsx", "application/zip"))
        assertEquals(FileTypeGroup.PRESENTATIONS, FileTypes.groupOf("talk.pptx", "application/zip"))
        assertEquals(FileTypeGroup.ARCHIVES, FileTypes.groupOf("backup.tar.gz", ""))
        assertEquals(FileTypeGroup.APKS, FileTypes.groupOf("app-release.apk", "application/zip"))
    }

    @Test
    fun theMimeTypeDecidesWithoutAKnownExtension() {
        assertEquals(FileTypeGroup.PDF, FileTypes.groupOf("scan", "application/pdf"))
        assertEquals(FileTypeGroup.IMAGES, FileTypes.groupOf("IMG_0001", "image/x-adobe-dng"))
        assertEquals(FileTypeGroup.VIDEO, FileTypes.groupOf("recording", "video/3gpp"))
        assertEquals(FileTypeGroup.AUDIO, FileTypes.groupOf("memo", "audio/AMR"))
        assertEquals(FileTypeGroup.DOCUMENTS, FileTypes.groupOf("Report", "application/vnd.google-apps.document"))
        assertEquals(FileTypeGroup.SPREADSHEETS, FileTypes.groupOf("Budget", "application/vnd.google-apps.spreadsheet"))
        assertEquals(FileTypeGroup.PRESENTATIONS, FileTypes.groupOf("Deck", "application/vnd.ms-powerpoint"))
        assertEquals(FileTypeGroup.ARCHIVES, FileTypes.groupOf("bundle", "application/x-7z-compressed"))
        assertEquals(FileTypeGroup.APKS, FileTypes.groupOf("installer", "application/vnd.android.package-archive"))
        assertEquals(FileTypeGroup.DOCUMENTS, FileTypes.groupOf("readme", "text/plain; charset=utf-8"))
    }

    @Test
    fun anythingElseIsOther() {
        assertEquals(FileTypeGroup.OTHER, FileTypes.groupOf("data.bin", "application/octet-stream"))
        assertEquals(FileTypeGroup.OTHER, FileTypes.groupOf("config", "application/json"))
        assertEquals(FileTypeGroup.OTHER, FileTypes.groupOf(".nomedia", ""))
        assertEquals(FileTypeGroup.OTHER, FileTypes.groupOf("trailing.", "*/*"))
    }

    @Test
    fun aFileWithAGenericTypeOpensAsItsExtensionSays() {
        assertEquals("application/pdf", FileTypes.mimeForOpening("scan.pdf", "application/octet-stream"))
        assertEquals("text/csv", FileTypes.mimeForOpening("budget.csv", ""))
        // A real type is kept, and an unknown extension keeps the generic one.
        assertEquals("image/png", FileTypes.mimeForOpening("photo.jpg", "image/png"))
        assertEquals("application/octet-stream", FileTypes.mimeForOpening("data.bin", "application/octet-stream"))
        assertEquals("*/*", FileTypes.mimeForOpening("data.bin", ""))
    }

    @Test
    fun everyGroupHasAStableIdAndATypeToFindAppsWith() {
        assertEquals(FileTypeGroup.entries.size, FileTypeGroup.entries.map { it.id }.toSet().size)
        FileTypeGroup.entries.forEach {
            assertEquals(it, FileTypeGroup.fromId(it.id))
            assert(it.queryMimes.isNotEmpty())
        }
        assertNull(FileTypeGroup.fromId("movies"))
    }

    @Test
    fun aTypeWordStartingTheQueryLimitsFilesToThatType() {
        fun check(query: String, group: FileTypeGroup, rest: String) {
            val parsed = FileTypeQuery.parse(query)
            assertNotNull("\"$query\" should name a type", parsed)
            assertEquals(group, parsed!!.group)
            assertEquals(rest, parsed.rest)
        }
        check("pdf invoice", FileTypeGroup.PDF, "invoice")
        check("PDFs  tax 2025 ", FileTypeGroup.PDF, "tax 2025")
        check("photos beach", FileTypeGroup.IMAGES, "beach")
        check("image cat", FileTypeGroup.IMAGES, "cat")
        check("Pictures", FileTypeGroup.IMAGES, "")
        check("videos birthday", FileTypeGroup.VIDEO, "birthday")
        check("song wonderwall", FileTypeGroup.AUDIO, "wonderwall")
        check("music", FileTypeGroup.AUDIO, "")
        check("audio memo", FileTypeGroup.AUDIO, "memo")
        check("docs resume", FileTypeGroup.DOCUMENTS, "resume")
        check("document lease", FileTypeGroup.DOCUMENTS, "lease")
        check("spreadsheet budget", FileTypeGroup.SPREADSHEETS, "budget")
        check("sheets budget", FileTypeGroup.SPREADSHEETS, "budget")
        check("slides launch", FileTypeGroup.PRESENTATIONS, "launch")
        check("presentation q3", FileTypeGroup.PRESENTATIONS, "q3")
        check("  pdf\tinvoice", FileTypeGroup.PDF, "invoice")
    }

    @Test
    fun onlyAWholeFirstWordNamesAType() {
        for (query in listOf(
            "", "  ", "photography tips", "pdf2go", "docker compose", "songbird", "invoice pdf", "my photos",
            "spreadsheets.google.com", "slide", "pd"
        )) {
            assertNull("\"$query\" should not name a type", FileTypeQuery.parse(query))
        }
    }

    @Test
    fun typeWordsDontClashWithBangsSumsOrActions() {
        // Bangs and sums never start with a type word...
        for (query in listOf("!pdf invoice", "!g photos", "12*4+7", "100 usd to eur", "call mom", "text alex hi",
            "remind me to print the pdf", "navigate to the music hall", "timer 5m")) {
            assertNull("\"$query\" should not name a type", FileTypeQuery.parse(query))
        }
        // ...and a query naming a type is no action or sum.
        for (query in listOf("pdf invoice", "photos beach", "music", "docs 2024", "sheets 5")) {
            assertNull("\"$query\" should not be an action", NaturalLanguageIntentParser.parse(query))
            assertNull("\"$query\" should not be a sum", MathematicalExpressionEngine.evaluate(query))
        }
    }
}
