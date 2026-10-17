package com.ngdesigns.intelligentsearch.data.history

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
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@Serializable
data class PastSearch(val query: String, val atMillis: Long)

/** What the user searched for and which apps they opened from search, newest first. Never leaves the app. */
@Serializable
data class Memory(
    val searches: List<PastSearch> = emptyList(),
    /** Launch counts and last launch per app package, the fallback for suggested apps. */
    val launches: Map<String, Launch> = emptyMap(),
)

@Serializable
data class Launch(val count: Int, val lastMillis: Long)

internal object MemorySerializer : Serializer<Memory> {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    override val defaultValue = Memory()
    override suspend fun readFrom(input: InputStream): Memory = try {
        json.decodeFromString(Memory.serializer(), input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Unreadable history", e)
    }
    override suspend fun writeTo(t: Memory, output: OutputStream) = output.write(json.encodeToString(Memory.serializer(), t).encodeToByteArray())
}

class HistoryStore(private val store: DataStore<Memory>, private val clock: () -> Long = System::currentTimeMillis) {

    val memory: Flow<Memory> = store.data

    val searches: Flow<List<PastSearch>> = store.data.map { it.searches }

    /** Records a search run inside the app; repeats move to the top instead of duplicating. */
    suspend fun record(query: String) {
        val q = query.trim()
        if (q.isEmpty() || q.length > 500) return
        store.updateData { m ->
            m.copy(searches = (listOf(PastSearch(q, clock())) + m.searches.filterNot { it.query.equals(q, ignoreCase = true) }).take(MAX))
        }
    }

    suspend fun remove(query: String) = store.updateData { m -> m.copy(searches = m.searches.filterNot { it.query == query }) }

    suspend fun clear() = store.updateData { it.copy(searches = emptyList()) }

    /** Replaces the history with a restored backup's (newest first). */
    suspend fun restore(queries: List<String>) {
        val now = clock()
        store.updateData { m -> m.copy(searches = queries.distinct().take(MAX).mapIndexed { i, q -> PastSearch(q, now - i) }) }
    }

    suspend fun recordLaunch(packageName: String) = store.updateData { m ->
        val old = m.launches[packageName]
        m.copy(launches = m.launches + (packageName to Launch((old?.count ?: 0) + 1, clock())))
    }

    companion object {
        const val MAX = 500

        fun create(context: Context) = HistoryStore(
            DataStoreFactory.create(MemorySerializer, ReplaceFileCorruptionHandler { Memory() }) { context.dataStoreFile("memory.json") },
        )
    }
}
