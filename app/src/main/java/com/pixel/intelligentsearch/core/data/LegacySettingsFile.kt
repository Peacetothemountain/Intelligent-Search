package com.pixel.intelligentsearch.core.data

import android.content.Context
import java.io.File

/**
 * Where versions before the single-store migration kept their settings: the file DataStore's
 * `preferencesDataStoreFile(name)` names, under the application context's files directory.
 */
internal fun legacySettingsFile(context: Context): File =
    File(context.applicationContext.filesDir, "datastore/${SettingsManager.LEGACY_DATASTORE_NAME}.preferences_pb")

/**
 * Reads that file, a `PreferenceMap` from DataStore's `preferences.proto` (map field 1 of name to `Value`; `Value`
 * is a oneof of boolean 1, float 2, int32 3, int64 4, string 5, `StringSet` 6, double 7, bytes 8). Settings only
 * ever used Boolean, Int, String and Set<String>, so those are returned and the other types are left out, as the
 * import skipped them before. Like DataStore, it throws on a malformed file or a value with no type set.
 */
internal fun readLegacySettings(bytes: ByteArray): Map<String, Any> {
    val settings = HashMap<String, Any>()
    val map = ProtoReader(bytes, 0, bytes.size)
    while (map.hasMore()) {
        val tag = map.varint().toInt()
        if (tag != (1 shl 3 or LENGTH_DELIMITED)) { map.skip(tag); continue }
        val entry = map.message()
        var name = ""
        var value: LegacyValue = LegacyValue.NotSet
        while (entry.hasMore()) {
            when (val entryTag = entry.varint().toInt()) {
                1 shl 3 or LENGTH_DELIMITED -> name = entry.string()
                2 shl 3 or LENGTH_DELIMITED -> value = readValue(entry.message())
                else -> entry.skip(entryTag)
            }
        }
        when (value) {
            LegacyValue.NotSet -> throw IllegalArgumentException("Value not set for $name")
            LegacyValue.OtherType -> settings.remove(name)
            is LegacyValue.Known -> settings[name] = value.value
        }
    }
    return settings
}

private sealed interface LegacyValue {
    data object NotSet : LegacyValue
    data object OtherType : LegacyValue
    data class Known(val value: Any) : LegacyValue
}

/** One `Value`; as in any oneof, the last field read wins. */
private fun readValue(reader: ProtoReader): LegacyValue {
    var value: LegacyValue = LegacyValue.NotSet
    while (reader.hasMore()) {
        val tag = reader.varint().toInt()
        value = when (tag) {
            1 shl 3 or VARINT -> LegacyValue.Known(reader.varint() != 0L)
            3 shl 3 or VARINT -> LegacyValue.Known(reader.varint().toInt())
            5 shl 3 or LENGTH_DELIMITED -> LegacyValue.Known(reader.string())
            6 shl 3 or LENGTH_DELIMITED -> {
                val strings = reader.message()
                val set = LinkedHashSet<String>()
                while (strings.hasMore()) {
                    val stringTag = strings.varint().toInt()
                    if (stringTag == (1 shl 3 or LENGTH_DELIMITED)) set += strings.string() else strings.skip(stringTag)
                }
                LegacyValue.Known(set)
            }
            else -> {
                reader.skip(tag)
                if (tag ushr 3 in 1..8) LegacyValue.OtherType else value
            }
        }
    }
    return value
}

private const val VARINT = 0
private const val FIXED64 = 1
private const val LENGTH_DELIMITED = 2
private const val FIXED32 = 5

/** Protocol buffer wire format over [bytes] from [pos] up to [end]. */
private class ProtoReader(private val bytes: ByteArray, private var pos: Int, private val end: Int) {

    fun hasMore() = pos < end

    fun varint(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            require(pos < end && shift < 64) { "Malformed varint" }
            val byte = bytes[pos++].toInt()
            result = result or ((byte and 0x7F).toLong() shl shift)
            if (byte and 0x80 == 0) return result
            shift += 7
        }
    }

    fun message(): ProtoReader {
        val length = varint()
        require(length in 0..(end - pos).toLong()) { "Malformed length" }
        val start = pos
        pos += length.toInt()
        return ProtoReader(bytes, start, pos)
    }

    fun string(): String = message().let { String(bytes, it.pos, it.end - it.pos, Charsets.UTF_8) }

    fun skip(tag: Int) {
        when (tag and 7) {
            VARINT -> varint()
            FIXED64 -> advance(8)
            LENGTH_DELIMITED -> message()
            FIXED32 -> advance(4)
            else -> throw IllegalArgumentException("Unsupported wire type in tag $tag")
        }
    }

    private fun advance(count: Int) {
        require(count <= end - pos) { "Malformed field" }
        pos += count
    }
}
