package com.pixel.intelligentsearch.feature.search.today.cards.site

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.AtomicFile
import android.util.Log
import androidx.compose.ui.graphics.asImageBitmap
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.net.HttpsCall
import com.pixel.intelligentsearch.core.net.withHttpsCall
import com.pixel.intelligentsearch.core.security.NetworkPolicy
import com.pixel.intelligentsearch.feature.search.today.cards.SiteCards
import com.pixel.intelligentsearch.feature.search.today.cards.SiteData
import java.io.File
import java.net.IDN
import java.security.MessageDigest
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/*
 * NG Designs
 */

/** Discover's website cards, one for the whole process (the page and anything else that shows them share its cache). */
internal fun siteCards(context: Context): SiteCards = AndroidSiteCards.get(context)

/**
 * A site card couldn't be added or read. Its [message] is written for the user, in the app's plain words, and says what
 * to do; it never carries a technical cause.
 */
internal class SiteCardException(message: String) : Exception(message)

/**
 * Discover's website cards, read by [SiteFetcher] (https only, address-pinned, header only, within time and size
 * limits) and kept in a small app-private cache: `noBackupFilesDir/discover_sites`, holding `index.json` (each card's
 * text, color and read time) and one WebP picture per card, named by a hash of its url. Nothing there is backed up,
 * and a card's picture is deleted with it.
 *
 * [sites] starts empty and fills from the cache as soon as it has loaded off the main thread. [refresh] re-reads a card
 * once its last good read is a day old; a read that fails keeps what the card had and marks it [SiteData.failed], and
 * isn't tried again for [RETRY_AFTER_MS]. Reads never happen while the phone is offline, nor while Tor is the search
 * engine or Web is off ([NetworkPolicy.siteBlock]). Every change to the cache is
 * made under one lock, so a [remove] can't be undone by a read finishing late.
 */
internal class AndroidSiteCards private constructor(
    private val context: Context,
    private val fetcher: SiteFetcher = SiteFetcher(),
    private val now: () -> Long = System::currentTimeMillis
) : SiteCards {

    /** One card as the index keeps it; [image] is its picture's file name, if it has one. */
    private data class Entry(
        val url: String,
        val domain: String,
        val title: String,
        val description: String,
        val image: String?,
        val accent: Int,
        val readAt: Long,
        val failed: Boolean
    )

    /** What one read found, ready to store: the cleaned text, the picture (decoded and encoded for the cache) and the color. */
    private class PageRead(
        val domain: String,
        val title: String,
        val description: String,
        val bitmap: Bitmap?,
        val encoded: ByteArray?,
        val accent: Int
    )

    // A read that fails in a way nobody foresaw only loses that read, never the app.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, _ -> })
    private val lock = Mutex()
    private val loaded = CompletableDeferred<Unit>()
    private val reads = Semaphore(MAX_PARALLEL_READS)

    /** The cache's folder. Looked up off the main thread: the platform creates it on first use. */
    private val dir: File by lazy { File(context.noBackupFilesDir, DIR).apply { mkdirs() } }
    private val index: AtomicFile by lazy { AtomicFile(File(dir, INDEX)) }

    /** The index as stored, guarded by [lock]. */
    private val entries = LinkedHashMap<String, Entry>()

    private val state = MutableStateFlow<Map<String, SiteData>>(emptyMap())
    override val sites: StateFlow<Map<String, SiteData>> = state.asStateFlow()

    /** Reads in flight by url, and when each url was last tried; guarded by itself. */
    private val running = HashMap<String, Job>()
    private val lastTried = HashMap<String, Long>()

    init {
        scope.launch {
            try {
                lock.withLock { load() }
            } finally {
                loaded.complete(Unit)
            }
        }
    }

    override fun refresh(urls: Collection<String>) {
        val asked = urls.toList()
        scope.launch {
            loaded.await()
            if (blocked() != null || !isOnline()) return@launch
            val time = now()
            for (url in asked.distinct()) {
                val known = state.value[url]
                if (known != null && time - known.readAt < REFRESH_AFTER_MS) continue
                val address = (normalizeSiteAddress(url) as? SiteAddressResult.Ok)?.address ?: continue
                startRead(url, address, time)
            }
        }
    }

    /** Starts a read of the card at [url] unless one is running or it was tried within [RETRY_AFTER_MS]. */
    private fun startRead(url: String, address: SiteAddress, time: Long) {
        val job = synchronized(running) {
            val busy = running[url]?.isActive == true
            val tried = lastTried[url]
            if (busy || (tried != null && time - tried < RETRY_AFTER_MS)) return
            scope.launch(start = CoroutineStart.LAZY) { reads.withPermit { reread(url, address) } }.also {
                running[url] = it
                lastTried[url] = time
            }
        }
        job.invokeOnCompletion { synchronized(running) { if (running[url] === job) running.remove(url) } }
        job.start()
    }

    /** Reads [address] again for the card at [url], storing what it finds or marking the card failed. */
    private suspend fun reread(url: String, address: SiteAddress) {
        // Reads wait their turn; Tor chosen or Web turned off meanwhile stops the ones not yet sent.
        if (blocked() != null) return
        val result = withHttpsCall { call -> safeRead(address, call) }
        lock.withLock {
            // A remove() cancels this read under the same lock, so a late answer can't bring the card back.
            coroutineContext.ensureActive()
            result.fold(onSuccess = { store(url, it) }, onFailure = { markFailed(url, address) })
        }
    }

    override suspend fun add(input: String): Result<SiteData> {
        val address = when (val normalized = normalizeSiteAddress(input)) {
            is SiteAddressResult.Ok -> normalized.address
            is SiteAddressResult.Refused -> return failure(messageFor(normalized.reason))
        }
        loaded.await()
        when (blocked()) {
            NetworkPolicy.Block.TOR -> return failure(TOR_ON)
            NetworkPolicy.Block.WEB_OFF -> return failure(WEB_OFF)
            else -> Unit
        }
        if (!isOnline()) return failure(OFFLINE)
        val read = withHttpsCall { call -> safeRead(address, call) }
        val page = read.getOrElse { return Result.failure(it) }
        // The image and the index are written to disk: never on the caller's (the main) thread.
        val data = withContext(Dispatchers.IO) { lock.withLock { store(address.url, page) } }
        synchronized(running) { lastTried[address.url] = now() }
        return Result.success(data)
    }

    override fun remove(url: String) {
        synchronized(running) {
            running.remove(url)?.cancel()
            lastTried.remove(url)
        }
        state.update { it - url }
        scope.launch {
            loaded.await()
            lock.withLock {
                entries.remove(url)
                runCatching { File(dir, imageName(url)).delete() }
                save()
                state.update { it - url }
            }
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Reading

    /**
     * Reads the page at [address] into a card: its header's title, description and picture, cleaned to plain text, and
     * its color. Blocking (run inside [withHttpsCall]); a failure carries the user's message.
     */
    private fun safeRead(address: SiteAddress, call: HttpsCall): Result<PageRead> =
        try {
            readSite(address, call)
        } catch (e: RuntimeException) {
            // A page the parser can't take (anything it didn't foresee) is a page that couldn't be read.
            failure(UNREACHABLE)
        }

    private fun readSite(address: SiteAddress, call: HttpsCall): Result<PageRead> {
        val page = when (val fetched = fetcher.fetch(address, SiteFetcher.Kind.PAGE, call)) {
            is SiteFetch.Ok -> fetched
            is SiteFetch.Refused -> return failure(messageFor(fetched.reason))
            SiteFetch.Unreachable -> return failure(UNREACHABLE)
        }
        val contentType = page.headers["content-type"]
        val mime = contentType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (mime.isNotEmpty() && mime !in PAGE_TYPES) return failure(NOT_A_PAGE)

        val head = parseSiteHead(decodeSitePage(page.body, contentType))
        val domain = displayDomain(address.host)
        val siteName = cleanSiteText(head.siteName, SITE_TITLE_MAX)
        val title = cleanSiteText(head.title, SITE_TITLE_MAX).ifEmpty { siteName }.ifEmpty { domain }
        val description = cleanSiteText(head.description, SITE_DESCRIPTION_MAX)

        var bitmap: Bitmap? = null
        var encoded: ByteArray? = null
        val imageAddress = head.image?.let { (resolveSiteAddress(page.address, it) as? SiteAddressResult.Ok)?.address }
        if (imageAddress != null && !call.isCancelled) {
            val image = fetcher.fetch(imageAddress, SiteFetcher.Kind.IMAGE, call)
            if (image is SiteFetch.Ok && isImageType(image.headers["content-type"])) {
                bitmap = runCatching { decodeSiteImage(image.body) }.getOrNull()
                encoded = bitmap?.let { runCatching { encodeSiteImage(it) }.getOrNull() }
                if (encoded == null) bitmap = null
            }
        }
        val accent = bitmap?.let(::imageAccent) ?: domainAccent(address.host)
        return Result.success(PageRead(domain, title, description, bitmap, encoded, accent))
    }

    private fun isImageType(contentType: String?): Boolean {
        val mime = contentType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT).orEmpty()
        return mime.isEmpty() || mime.startsWith("image/") || mime == "application/octet-stream"
    }

    /** [host] as the card shows it: international names in their own letters, without "www.". */
    private fun displayDomain(host: String): String {
        val unicode = runCatching { IDN.toUnicode(host, IDN.USE_STD3_ASCII_RULES) }.getOrDefault(host)
        return cleanSiteText(unicode.removePrefix("www."), SITE_TITLE_MAX).ifEmpty { host }
    }

    /** Why no website may be read now (Tor as the engine, or Web off), or null when one may. */
    private fun blocked(): NetworkPolicy.Block? = NetworkPolicy.siteBlock(SettingsManager.storedPrefs(context))

    private fun isOnline(): Boolean = runCatching {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return@runCatching true
        val network = connectivity.activeNetwork ?: return@runCatching false
        connectivity.getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: false
    }.getOrDefault(true)

    // -----------------------------------------------------------------------------------------------------------------
    // The cache. Every function below runs under [lock], off the main thread.

    /** Saves [page] as the card at [url]: its picture file (or none), its index entry, and [sites]. */
    private fun store(url: String, page: PageRead): SiteData {
        val name = imageName(url)
        val file = File(dir, name)
        val image = page.encoded?.let { bytes ->
            runCatching {
                val atomic = AtomicFile(file)
                val out = atomic.startWrite()
                try {
                    out.write(bytes)
                    atomic.finishWrite(out)
                } catch (e: Exception) {
                    atomic.failWrite(out)
                    throw e
                }
                name
            }.getOrNull()
        }
        if (image == null) runCatching { file.delete() }
        val entry = Entry(url, page.domain, page.title, page.description, image, page.accent, now(), failed = false)
        entries[url] = entry
        save()
        val data = entry.toData(page.bitmap.takeIf { image != null })
        state.update { it + (url to data) }
        return data
    }

    /** Marks the card at [url] failed, keeping what it had; a card never read gets a plain one with its domain. */
    private fun markFailed(url: String, address: SiteAddress) {
        val entry = entries[url]?.copy(failed = true)
            ?: Entry(url, displayDomain(address.host), displayDomain(address.host), "", null, domainAccent(address.host), 0L, failed = true)
        entries[url] = entry
        save()
        state.update { current -> current + (url to (current[url]?.copy(failed = true) ?: entry.toData(null))) }
    }

    /** Fills [entries] and [sites] from the index, and deletes picture files no card uses. */
    private fun load() {
        val loadedEntries = runCatching {
            if (!index.baseFile.exists()) return@runCatching emptyList()
            val array = JSONArray(String(index.readFully(), Charsets.UTF_8))
            (0 until array.length()).mapNotNull { array.optJSONObject(it)?.toEntry() }
        }.onFailure { Log.w(TAG, "Couldn't read the site cards' cache; starting fresh") }.getOrDefault(emptyList())

        val data = LinkedHashMap<String, SiteData>()
        for (entry in loadedEntries) {
            val bitmap = entry.image?.let { name -> runCatching { BitmapFactory.decodeFile(File(dir, name).path) }.getOrNull() }
            val kept = if (entry.image != null && bitmap == null) entry.copy(image = null) else entry
            entries[kept.url] = kept
            data[kept.url] = kept.toData(bitmap)
        }
        val used = entries.values.mapNotNullTo(HashSet()) { it.image } + INDEX
        dir.listFiles()?.forEach { file -> if (file.name !in used && !file.name.startsWith(INDEX)) runCatching { file.delete() } }
        state.update { current -> data.filterKeys { it !in current.keys } + current }
    }

    /** Writes [entries] to the index, atomically, so a crash mid-write leaves the old index whole. */
    private fun save() {
        runCatching {
            val array = JSONArray()
            for (entry in entries.values) array.put(entry.toJson())
            val out = index.startWrite()
            try {
                out.write(array.toString().toByteArray(Charsets.UTF_8))
                index.finishWrite(out)
            } catch (e: Exception) {
                index.failWrite(out)
                throw e
            }
        }.onFailure { Log.w(TAG, "Couldn't save the site cards' cache") }
    }

    private fun Entry.toData(bitmap: Bitmap?) = SiteData(
        url = url,
        domain = domain,
        title = title,
        description = description,
        image = bitmap?.asImageBitmap(),
        accent = accent,
        readAt = readAt,
        failed = failed
    )

    private fun Entry.toJson() = JSONObject().apply {
        put("url", url)
        put("domain", domain)
        put("title", title)
        put("description", description)
        if (image != null) put("image", image)
        put("accent", accent)
        put("readAt", readAt)
        put("failed", failed)
    }

    /** An entry from the index, its text cleaned again (the file is ours, but nothing read from disk is trusted). */
    private fun JSONObject.toEntry(): Entry? {
        val url = optString("url").takeIf { normalizeSiteAddress(it) is SiteAddressResult.Ok } ?: return null
        val image = optString("image").takeIf { it == imageName(url) }
        return Entry(
            url = url,
            domain = cleanSiteText(optString("domain"), SITE_TITLE_MAX),
            title = cleanSiteText(optString("title"), SITE_TITLE_MAX),
            description = cleanSiteText(optString("description"), SITE_DESCRIPTION_MAX),
            image = image,
            accent = optInt("accent", 0) or (0xFF shl 24),
            readAt = optLong("readAt", 0L),
            failed = optBoolean("failed", false)
        )
    }

    /** A card's picture file name: a hash of its url, so the name says nothing about the site. */
    private fun imageName(url: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
        return digest.take(16).joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') } + ".webp"
    }

    private fun <T> failure(message: String): Result<T> = Result.failure(SiteCardException(message))

    private fun messageFor(reason: SiteRefusal): String = when (reason) {
        SiteRefusal.NOT_AN_ADDRESS -> NOT_AN_ADDRESS
        SiteRefusal.NOT_SECURE -> NOT_SECURE
        SiteRefusal.NOT_PUBLIC -> NOT_PUBLIC
    }

    companion object {
        private const val TAG = "SiteCards"
        private const val DIR = "discover_sites"
        private const val INDEX = "index.json"

        /** A card is read again once its last good read is this old. */
        const val REFRESH_AFTER_MS = 24 * 60 * 60 * 1000L

        /** A url whose read failed (or just ran) isn't tried again for this long, however often Discover opens. */
        const val RETRY_AFTER_MS = 15 * 60 * 1000L

        private const val MAX_PARALLEL_READS = 3
        private val PAGE_TYPES = setOf("text/html", "application/xhtml+xml")

        // The messages a failed add shows, in the app's plain words.
        const val NOT_AN_ADDRESS = "Type a website address, like example.com"
        const val NOT_SECURE = "Only secure (https) sites can be added."
        const val NOT_PUBLIC = "Only public websites can be added."
        const val UNREACHABLE = "That site couldn't be reached. Check the address and try again."
        const val NOT_A_PAGE = "That address isn't a web page. Try the site's main address."
        const val OFFLINE = "You're offline. Connect to the internet and try again."
        const val TOR_ON = "Website cards can't be added while Tor is your search engine."
        const val WEB_OFF = "Turn on Web in Search Sources to add a website card."

        @SuppressLint("StaticFieldLeak") // the application context, which lives as long as the process
        @Volatile
        private var instance: AndroidSiteCards? = null

        /** The process's one instance; creating it only starts the cache's load in the background. */
        fun get(context: Context): AndroidSiteCards =
            instance ?: synchronized(this) { instance ?: AndroidSiteCards(context.applicationContext ?: context).also { instance = it } }
    }
}
