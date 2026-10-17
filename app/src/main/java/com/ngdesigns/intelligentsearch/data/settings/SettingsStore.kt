package com.ngdesigns.intelligentsearch.data.settings

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** The JSON form of [Settings], shared by the store and backups. Tolerant of fields added or removed over time. */
object SettingsJson {
    val format = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
        explicitNulls = false
    }

    fun encode(settings: Settings): String = format.encodeToString(Settings.serializer(), settings)

    /** The settings in [text], sanitized, or null if it isn't a settings document at all. */
    fun decode(text: String): Settings? = try {
        format.decodeFromString(Settings.serializer(), text).sanitized()
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}

internal object SettingsSerializer : Serializer<Settings> {
    override val defaultValue = Settings()
    override suspend fun readFrom(input: InputStream): Settings =
        SettingsJson.decode(input.readBytes().decodeToString()) ?: throw CorruptionException("Unreadable settings")
    override suspend fun writeTo(t: Settings, output: OutputStream) = output.write(SettingsJson.encode(t).encodeToByteArray())
}

/**
 * The app's settings: read as a flow that always holds sanitized values, changed atomically with [update]. An
 * unreadable file is replaced by defaults rather than crashing the app.
 */
class SettingsStore(private val store: DataStore<Settings>) {

    val settings: Flow<Settings> = store.data.map { it.sanitized() }.distinctUntilChanged()

    suspend fun current(): Settings = settings.first()

    suspend fun update(transform: (Settings) -> Settings) {
        store.updateData { transform(it).sanitized() }
    }

    companion object {
        fun create(context: Context): SettingsStore = SettingsStore(
            DataStoreFactory.create(
                serializer = SettingsSerializer,
                corruptionHandler = ReplaceFileCorruptionHandler { Settings() },
                produceFile = { context.dataStoreFile("settings.json") },
            ),
        )
    }
}
