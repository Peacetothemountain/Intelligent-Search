package com.pixel.intelligentsearch.core.currency

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.util.Properties

/**
 * The last rates fetched, kept in a file in the app's private storage so conversions work offline. A new table is
 * written beside the old one and renamed over it, so an interrupted write never leaves half a table behind. A file
 * that can't be read back, or whose values fail the checks the feed itself must pass, counts as no file.
 */
class CurrencyRateStore(private val file: File) {

    fun exists(): Boolean = file.exists()

    /** The saved rates, or null when none were saved or the file is unreadable. */
    fun load(): CurrencyRates? = try {
        if (!file.isFile || file.length() > MAX_FILE_BYTES) null else {
            val props = Properties().apply { file.reader(Charsets.UTF_8).use { load(it) } }
            val base = props.getProperty(KEY_BASE)
            val date = props.getProperty(KEY_DATE)?.let(LocalDate::parse)
            val rates = props.stringPropertyNames()
                .filter { it.startsWith(RATE_PREFIX) }
                .associate { it.removePrefix(RATE_PREFIX) to props.getProperty(it).toDouble() }
            val valid = base != null && CODE.matches(base) && date != null && rates.isNotEmpty() &&
                rates.size <= MAX_RATES && rates[base] == 1.0 &&
                rates.all { (code, rate) -> CODE.matches(code) && rate.isFinite() && rate > 0.0 }
            if (valid) CurrencyRates(base, rates, date) else null
        }
    } catch (_: Exception) {
        null
    }

    /** Saves [rates] (a fetched, dated table) in place of the old ones; false if it couldn't be written. */
    fun save(rates: CurrencyRates): Boolean {
        val date = rates.date ?: return false
        val temp = File(file.parentFile, file.name + ".tmp")
        return try {
            val props = Properties()
            props.setProperty(KEY_BASE, rates.base)
            props.setProperty(KEY_DATE, date.toString())
            for ((code, rate) in rates.perBase) props.setProperty(RATE_PREFIX + code, rate.toString())
            file.parentFile?.mkdirs()
            temp.writer(Charsets.UTF_8).use { props.store(it, null) }
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            true
        } catch (_: Exception) {
            temp.delete()
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
    }
}
