package com.pixel.intelligentsearch.core.currency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate

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
}
