package com.pixel.intelligentsearch.core.currency

import java.io.File
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Clock
import java.time.LocalDate
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

/**
 * The last rates fetched, kept in a file in the app's private storage so conversions work offline. A new table is
 * written beside the old one, in a temporary file of its own so two writers never share one, and renamed over it,
 * so an interrupted write never leaves half a table behind; writes in this process take turns. A file that can't be read back, or whose values fail the
 * checks the feed itself must pass, counts as no file. So does one dated before the ECB's first rates or more than
 * [EcbRatesParser.MAX_DAYS_AHEAD] days after today, which [clock] says.
 */
class CurrencyRateStore(private val file: File, private val clock: Clock = Clock.systemDefaultZone()) {

    fun exists(): Boolean = file.exists()

    /** The saved rates, or null when none were saved or the file is unreadable. */
    fun load(): CurrencyRates? = try {
        if (!file.isFile || file.length() > MAX_FILE_BYTES) null else {
            val props = Properties().apply { file.reader(Charsets.UTF_8).use { load(it) } }
            val base = props.getProperty(KEY_BASE)
            // A day the ECB could have set rates on: not before its first, nor further ahead than a fetched table may be.
            val date = props.getProperty(KEY_DATE)?.let(LocalDate::parse)
                ?.takeIf { it in FIRST_ECB_DAY..LocalDate.now(clock).plusDays(EcbRatesParser.MAX_DAYS_AHEAD) }
            val rateTexts = props.stringPropertyNames()
                .filter { it.startsWith(RATE_PREFIX) }
                .associate { it.removePrefix(RATE_PREFIX) to props.getProperty(it) }
            // The rate pattern and range the feed must pass: no sign, exponent, hex or extreme values.
            val rates = rateTexts.mapNotNull { (code, text) -> EcbRatesParser.rateOrNull(text)?.let { code to it } }.toMap()
            val valid = base != null && CODE.matches(base) && date != null && rates.isNotEmpty() &&
                rates.size == rateTexts.size && rates.size <= MAX_RATES && rates[base] == 1.0 &&
                rates.keys.all { CODE.matches(it) }
            if (valid) CurrencyRates(base, rates, date) else null
        }
    } catch (_: Exception) {
        null
    }

    /** Saves [rates] (a fetched, dated table) in place of the old ones; false if it couldn't be written. */
    fun save(rates: CurrencyRates): Boolean {
        val date = rates.date ?: return false
        return synchronized(lockFor(file)) { write(rates, date) }
    }

    private fun write(rates: CurrencyRates, date: LocalDate): Boolean {
        var temp: File? = null
        return try {
            val props = Properties()
            props.setProperty(KEY_BASE, rates.base)
            props.setProperty(KEY_DATE, date.toString())
            // Plain digits, as the feed writes them: "0.0001", never "1.0E-4", which loading would refuse.
            for ((code, rate) in rates.perBase) props.setProperty(RATE_PREFIX + code, BigDecimal.valueOf(rate).toPlainString())
            file.parentFile?.mkdirs()
            temp = File.createTempFile(file.name, ".tmp", file.parentFile)
            temp.writer(Charsets.UTF_8).use { props.store(it, null) }
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            true
        } catch (_: Exception) {
            temp?.delete()
            false
        }
    }

    private companion object {
        const val KEY_BASE = "base"
        const val KEY_DATE = "date"
        const val RATE_PREFIX = "rate."
        const val MAX_RATES = 200
        const val MAX_FILE_BYTES = 64L * 1024
        val CODE = Regex("^[a-z]{3}$")

        /** The first day the ECB set euro reference rates; no table of them is older. */
        val FIRST_ECB_DAY: LocalDate = LocalDate.of(1999, 1, 4)

        /** One lock per file, shared by every store of it in this process, so writes to it take turns. */
        private val LOCKS = ConcurrentHashMap<String, Any>()

        fun lockFor(file: File): Any = LOCKS.getOrPut(file.absolutePath) { Any() }
    }
}
