package com.pixel.intelligentsearch.core.currency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

/** Saved rates come back exactly; a missing, damaged or tampered file counts as none. */
class CurrencyRateStoreTest {

    @get:Rule val folder = TemporaryFolder()

    private val file: File get() = File(folder.root, "rates.properties")
    private val rates = CurrencyRates("eur", mapOf("eur" to 1.0, "usd" to 1.0812, "jpy" to 162.5), LocalDate.of(2026, 10, 19))

    @Test
    fun savedRatesLoadBack() {
        val store = CurrencyRateStore(file)
        assertFalse(store.exists())
        assertTrue(store.save(rates))
        assertTrue(store.exists())
        assertEquals(rates, CurrencyRateStore(file).load())
        assertFalse("no temporary file is left behind", File(folder.root, "rates.properties.tmp").exists())
    }

    @Test
    fun aNewerTableReplacesTheOldOne() {
        val store = CurrencyRateStore(file)
        store.save(rates)
        val next = rates.copy(perBase = mapOf("eur" to 1.0, "usd" to 1.09), date = LocalDate.of(2026, 10, 20))
        assertTrue(store.save(next))
        assertEquals(next, store.load())
    }

    @Test
    fun nothingSavedLoadsAsNone() {
        assertNull(CurrencyRateStore(file).load())
    }

    @Test
    fun theBuiltInTableIsNeverSaved() {
        assertFalse(CurrencyRateStore(file).save(CurrencyRates.BUILT_IN))
        assertFalse(file.exists())
    }

    @Test
    fun anUnreadableFileLoadsAsNone() {
        file.writeBytes(ByteArray(512) { (it * 7).toByte() })
        assertNull(CurrencyRateStore(file).load())
        file.writeText("")
        assertNull(CurrencyRateStore(file).load())
        file.writeText("base=eur\ndate=yesterday\nrate.eur=1.0\nrate.usd=1.08\n")
        assertNull(CurrencyRateStore(file).load())
        file.writeText("base=eur\ndate=2026-10-19\nrate.eur=1.0\nrate.usd=lots\n")
        assertNull(CurrencyRateStore(file).load())
        file.writeText("x".repeat(100 * 1024))
        assertNull(CurrencyRateStore(file).load())
    }

    @Test
    fun valuesTheFeedCouldNeverHaveHadAreRefused() {
        fun stored(lines: String): CurrencyRates? {
            file.writeText(lines)
            return CurrencyRateStore(file).load()
        }
        assertEquals(LocalDate.of(2026, 10, 19), stored("base=eur\ndate=2026-10-19\nrate.eur=1.0\nrate.usd=1.08\n")?.date)
        assertNull(stored("base=eur\ndate=2026-10-19\nrate.eur=1.0\nrate.usd=-1.08\n"))
        assertNull(stored("base=eur\ndate=2026-10-19\nrate.eur=1.0\nrate.usd=NaN\n"))
        assertNull(stored("base=eur\ndate=2026-10-19\nrate.eur=1.0\nrate.usd=Infinity\n"))
        assertNull(stored("base=eur\ndate=2026-10-19\nrate.eur=1.0\nrate.USD=1.08\n"))
        assertNull(stored("base=eur\ndate=2026-10-19\nrate.eur=1.0\nrate.km=1.08\n"))
        assertNull(stored("base=eur\ndate=2026-10-19\nrate.eur=2.0\nrate.usd=1.08\n"))
        assertNull(stored("date=2026-10-19\nrate.eur=1.0\nrate.usd=1.08\n"))
    }

    @Test
    fun aDateTheEcbCouldNeverHaveSetRatesOnIsRefused() {
        val today = Clock.fixed(LocalDate.of(2026, 10, 20).atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        fun dated(date: String): CurrencyRates? {
            file.writeText("base=eur\ndate=$date\nrate.eur=1.0\nrate.usd=1.08\n")
            return CurrencyRateStore(file, today).load()
        }
        // From the ECB's first day to two days ahead (time zones and a clock a little behind), as a fetched table may be.
        for (date in listOf("1999-01-04", "2026-10-19", "2026-10-20", "2026-10-22")) {
            assertEquals(date, LocalDate.parse(date), dated(date)?.date)
        }
        for (date in listOf("9999-12-31", "2027-01-01", "2026-10-23", "1999-01-03", "1970-01-01", "0001-01-01")) {
            assertNull(date, dated(date))
        }
        // Read back on the day it is saved, a table saved through the store is always in range.
        assertTrue(CurrencyRateStore(file, today).save(rates))
        assertEquals(rates, CurrencyRateStore(file, today).load())
    }

    @Test
    fun ratesAreCheckedAsStrictlyAsTheFeedChecksThem() {
        fun usd(rate: String): CurrencyRates? {
            file.writeText("base=eur\ndate=2026-10-19\nrate.eur=1.0\nrate.usd=$rate\n")
            return CurrencyRateStore(file).load()
        }
        assertEquals(1.08, usd("1.08")!!.perBase.getValue("usd"), 0.0)
        assertEquals(0.000001, usd("0.000001")!!.perBase.getValue("usd"), 0.0)
        assertEquals(10_000_000.0, usd("10000000")!!.perBase.getValue("usd"), 0.0)
        // Only plain digits, as the feed writes them, and only plausible values.
        for (rate in listOf("1e-300", "1e300", "0x1p3", "1.08e0", "1.08E0", "+1.08", "1.08d", "1.08f", "1,08", ".5", "5.", "0.0000009", "10000001", "0", "0.0")) {
            assertNull(rate, usd(rate))
        }
    }

    @Test
    fun smallAndLargeRatesAreSavedAsPlainDigitsAndLoadBack() {
        val extremes = rates.copy(perBase = mapOf("eur" to 1.0, "aaa" to 0.000123, "bbb" to 0.000001, "ccc" to 9_876_543.21))
        assertTrue(CurrencyRateStore(file).save(extremes))
        val saved = file.readLines().filter { it.startsWith("rate.") }
        assertTrue(saved.toString(), saved.all { it.substringAfter("=").all { c -> c.isDigit() || c == '.' } })
        assertEquals(extremes, CurrencyRateStore(file).load())
    }

    @Test
    fun aWriterNeverUsesAnotherWritersTemporaryFile() {
        // What another write in progress would hold: the temporary name a fixed scheme would use.
        File(folder.root, "rates.properties.tmp").mkdirs()
        assertTrue(CurrencyRateStore(file).save(rates))
        assertEquals(rates, CurrencyRateStore(file).load())
    }

    @Test
    fun concurrentSavesEachLeaveACompleteTable() {
        val tables = (1..8).map { day -> rates.copy(perBase = mapOf("eur" to 1.0, "usd" to 1.0 + day / 100.0), date = LocalDate.of(2026, 10, day)) }
        val pool = Executors.newFixedThreadPool(tables.size)
        try {
            repeat(20) {
                val start = CountDownLatch(1)
                val saves = tables.map { table -> pool.submit<Boolean> { start.await(); CurrencyRateStore(file).save(table) } }
                start.countDown()
                assertTrue(saves.all { it.get() })
                assertTrue(CurrencyRateStore(file).load() in tables)
            }
        } finally {
            pool.shutdownNow()
        }
        assertEquals("only the rates file is left", listOf("rates.properties"), folder.root.list()!!.toList())
    }
}
