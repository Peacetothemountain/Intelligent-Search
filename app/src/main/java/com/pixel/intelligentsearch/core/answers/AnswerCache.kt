package com.pixel.intelligentsearch.core.answers

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Answers fetched from the web, kept for [ttlMs]: the last [memoryEntries] in memory (least recently used dropped
 * first) and, when [disk] is given, the raw responses on disk too, so a definition looked up yesterday shows at once
 * and offline. [now] says what time it is, so tests can move it.
 */
class AnswerCache<V : Any>(
    private val ttlMs: Long,
    private val memoryEntries: Int,
    private val disk: DiskAnswerCache? = null,
    private val now: () -> Long = System::currentTimeMillis
) {
    private class Entry<V>(val value: V, val savedAtMs: Long)

    private val memory = object : LinkedHashMap<String, Entry<V>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry<V>>?): Boolean = size > memoryEntries
    }

    /** The value saved under [key] in memory while it is fresh; never touches the disk, so it is safe on any thread. */
    fun peek(key: String): V? = synchronized(memory) {
        val entry = memory[key] ?: return null
        if (now() - entry.savedAtMs !in 0 until ttlMs) {
            memory.remove(key)
            return null
        }
        entry.value
    }

    /**
     * The fresh value under [key]: from memory, else read from disk with [decode] (off the main thread only), else
     * null. A disk copy that no longer decodes is dropped.
     */
    fun get(key: String, decode: (String) -> V?): V? {
        peek(key)?.let { return it }
        val stored = disk?.read(key, ttlMs, now()) ?: return null
        val value = decode(stored.body)
        if (value == null) {
            disk.remove(key)
            return null
        }
        synchronized(memory) { memory[key] = Entry(value, stored.savedAtMs) }
        return value
    }

    /** Keeps [value] under [key], with the [body] it was decoded from on disk when there is a disk cache. */
    fun put(key: String, value: V, body: String? = null) {
        val at = now()
        synchronized(memory) { memory[key] = Entry(value, at) }
        if (body != null) disk?.write(key, body, at)
    }

    /** Forgets everything kept, in memory and on disk. Touches the disk: call off the main thread. */
    fun clear() {
        synchronized(memory) { memory.clear() }
        disk?.clear()
    }

    /** Keeps [value] in memory only: a "nothing found" that shouldn't be asked again while typing. */
    fun putInMemory(key: String, value: V) {
        synchronized(memory) { memory[key] = Entry(value, now()) }
    }
}

/**
 * Small text responses in their own folder, one file per key (named by a hash of it, so what was looked up isn't in
 * the file name), each starting with the time it was saved. At most [maxEntries] files are kept, oldest removed
 * first. Writes go to a temporary file renamed over the old one, so a reader never sees half a response.
 */
class DiskAnswerCache(private val dir: File, private val maxEntries: Int, private val maxBytes: Int = 256 * 1024) {

    class Stored(val body: String, val savedAtMs: Long)

    fun read(key: String, ttlMs: Long, nowMs: Long): Stored? = try {
        val file = fileFor(key)
        if (!file.isFile || file.length() > maxBytes) null else {
            val text = file.readText(Charsets.UTF_8)
            val savedAt = text.substringBefore('\n').toLongOrNull()
            val body = text.substringAfter('\n', "")
            if (savedAt == null || nowMs - savedAt !in 0 until ttlMs || body.isEmpty()) {
                file.delete()
                null
            } else Stored(body, savedAt)
        }
    } catch (_: Exception) {
        null
    }

    fun write(key: String, body: String, nowMs: Long) {
        if (body.length > maxBytes) return
        var temp: File? = null
        try {
            dir.mkdirs()
            temp = File.createTempFile("answer", ".tmp", dir)
            temp.writeText("$nowMs\n$body", Charsets.UTF_8)
            Files.move(temp.toPath(), fileFor(key).toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            prune()
        } catch (_: Exception) {
            temp?.delete()
        }
    }

    fun remove(key: String) {
        runCatching { fileFor(key).delete() }
    }

    /** Everything saved, for when the user turns the feature off. */
    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun prune() {
        val files = dir.listFiles { file -> file.name.endsWith(SUFFIX) } ?: return
        if (files.size <= maxEntries) return
        files.sortedBy { it.lastModified() }.take(files.size - maxEntries).forEach { it.delete() }
    }

    private fun fileFor(key: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        return File(dir, digest.joinToString("") { "%02x".format(it) }.take(40) + SUFFIX)
    }

    private companion object {
        const val SUFFIX = ".answer"
    }
}
