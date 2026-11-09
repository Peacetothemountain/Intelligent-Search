package com.pixel.intelligentsearch.feature.search.today.cards.widget

import android.annotation.SuppressLint
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.os.RemoteException
import androidx.compose.ui.graphics.asImageBitmap
import com.pixel.intelligentsearch.feature.search.today.cards.AppCardData
import com.pixel.intelligentsearch.feature.search.today.cards.AppCardState
import com.pixel.intelligentsearch.feature.search.today.cards.WidgetCards
import com.pixel.intelligentsearch.feature.search.today.cards.WidgetChoice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executor

/*
 * NG Designs
 */

/** The longest a read keeps the reader process alive. */
private const val READ_CAP_MS = 8_000L

/** How long the reader stays after every card has a trusted read, for a widget's quick second update. */
private const val GRACE_MS = 600L

/** The longest the Add card list waits for the reader to say which widgets are only a picture. */
private const val CHOICES_WAIT_MS = 3_000L

/** Why a provider is left off the Add card list after reading one of its widgets. */
private const val STATIC = "static"
private const val PICTURE = "picture"

@SuppressLint("StaticFieldLeak") // the application context, which lives as long as the process
@Volatile
private var instance: AndroidWidgetCards? = null

/** Discover's app cards: one instance for the whole process. */
internal fun widgetCards(context: Context): WidgetCards = androidWidgetCards(context)

/** The same instance, with what the add and setup flows need beyond [WidgetCards]. */
internal fun androidWidgetCards(context: Context): AndroidWidgetCards =
    instance ?: synchronized(AndroidWidgetCards::class.java) {
        instance ?: AndroidWidgetCards(context.applicationContext).also { instance = it }
    }

/**
 * Discover's app cards on Android. Widgets are bound to [host] ([WIDGET_HOST_ID]), which nothing shows, and are read in
 * the reader process ([WidgetReaderService]), bound only while a read is on (until every card has a trusted read, or
 * [READ_CAP_MS] at most). What comes back is judged by [applyRead] and kept in a few KB of JSON in app-private,
 * no-backup storage, so a card shows its last good values at once. All state lives on one background thread, so nothing
 * here touches the main thread but the [cards] flow.
 */
internal class AndroidWidgetCards(private val app: Context) : WidgetCards {
    private val thread = HandlerThread("discover-widgets", Process.THREAD_PRIORITY_BACKGROUND).apply { start() }
    private val worker = Handler(thread.looper)
    private val workerDispatcher = worker.asCoroutineDispatcher()
    private val workerExecutor = Executor { worker.post(it) }
    private val cacheFile by lazy { File(app.noBackupFilesDir, "discover_app_cards.json") }

    /** Discover's widget host in this process: for adding and removing cards only. It never listens or shows a widget. */
    val host: AppWidgetHost by lazy { AppWidgetHost(app, WIDGET_HOST_ID) }

    private val manager: AppWidgetManager get() = AppWidgetManager.getInstance(app)

    private val state = MutableStateFlow<Map<Int, AppCardData>>(emptyMap())
    override val cards: StateFlow<Map<Int, AppCardData>> = state.asStateFlow()

    // Everything below is touched on the worker thread only.
    private val records = HashMap<Int, CardRecord>()
    private val verdicts = HashMap<String, String>()
    private var connection: ServiceConnection? = null
    private var reader: Messenger? = null
    private val awaiting = LinkedHashSet<Int>()
    private val choiceRequests = HashMap<Int, Pair<List<String>, CompletableDeferred<Set<String>>>>()
    private var nextRequest = 1
    private val replies = Messenger(Handler(thread.looper) { msg -> onReply(msg); true })
    private val cap = Runnable { unbind() }
    private val grace = Runnable { unbind() }
    private val save = Runnable { writeCache() }

    init {
        worker.post { readCache() }
    }

    override fun refresh(widgetIds: Collection<Int>) {
        val ids = widgetIds.toList()
        if (ids.isNotEmpty()) worker.post { refreshNow(ids) }
    }

    override suspend fun choices(): List<WidgetChoice> {
        val left = withContext(workerDispatcher) { verdicts.keys.toHashSet() }
        val candidates = withContext(Dispatchers.IO) {
            val pm = app.packageManager
            val launchable = HashMap<String, Boolean>()
            runCatching { manager.getInstalledProvidersForProfile(Process.myUserHandle()) }.getOrDefault(emptyList())
                .filter { info ->
                    info.provider.flattenToString() !in left && offered(info) &&
                        launchable.getOrPut(info.provider.packageName) { pm.getLaunchIntentForPackage(info.provider.packageName) != null }
                }
        }
        val picture = pictureOnly(candidates.map { it.provider.flattenToString() })
        return withContext(Dispatchers.IO) {
            val pm = app.packageManager
            val looks = HashMap<String, Pair<Int, androidx.compose.ui.graphics.ImageBitmap?>>()
            candidates.filter { it.provider.flattenToString() !in picture }.map { info ->
                val pkg = info.provider.packageName
                val (accent, icon) = looks.getOrPut(pkg) {
                    val bitmap = iconBitmap(app, pkg)
                    (bitmap?.let(::iconAccent) ?: NEUTRAL_ACCENT) to bitmap?.asImageBitmap()
                }
                WidgetChoice(
                    provider = info.provider,
                    packageName = pkg,
                    appLabel = appLabel(pm, pkg),
                    widgetLabel = cleanText(runCatching { info.loadLabel(pm) }.getOrNull()),
                    accent = accent,
                    icon = icon
                )
            }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, WidgetChoice::appLabel).thenBy(String.CASE_INSENSITIVE_ORDER, WidgetChoice::widgetLabel))
        }
    }

    /** Widget ids allocated for an add that hasn't finished: [keepOnly] leaves them be. */
    private val adding = java.util.Collections.synchronizedSet(HashSet<Int>())

    /** Allocates a widget id for an add, held from [keepOnly] until [added] or [remove]. Blocking. */
    fun allocateForAdd(): Int = host.allocateAppWidgetId().also { adding += it }

    /** Holds [widgetId], an add's that a recreated screen is finishing, from [keepOnly]. */
    fun holdForAdd(widgetId: Int) {
        adding += widgetId
    }

    override fun keepOnly(widgetIds: Set<Int>) {
        worker.post {
            val held = synchronized(adding) { adding.toHashSet() }
            val bound = runCatching { host.appWidgetIds.toList() }.getOrDefault(emptyList())
            for (id in bound) {
                if (id !in widgetIds && id !in held) runCatching { host.deleteAppWidgetId(id) }
            }
            if (records.keys.removeAll { it !in widgetIds && it !in held }) publish()
        }
    }

    override fun remove(widgetId: Int) {
        adding -= widgetId
        worker.post {
            records.remove(widgetId)
            awaiting.remove(widgetId)
            publish()
            runCatching { host.deleteAppWidgetId(widgetId) }
        }
    }

    /**
     * A card was just added for [widgetId]: shows it as updating (or waiting for setup when [needsSetup], when it isn't
     * known whether its setup was finished) and reads it.
     */
    fun added(widgetId: Int, needsSetup: Boolean) {
        adding -= widgetId
        worker.post {
            runCatching { manager.getAppWidgetInfo(widgetId) }.getOrNull()?.let { info ->
                records[widgetId] = newRecord(widgetId, info).let { r ->
                    if (needsSetup) r.copy(needsSetup = true, data = r.data.copy(state = AppCardState.SETUP)) else r
                }
            }
            refreshNow(listOf(widgetId))
        }
    }

    /** The widget's setup was finished in its app: no longer waiting for it, and read again. */
    fun setupDone(widgetId: Int) {
        worker.post {
            records[widgetId]?.let { r ->
                val state = if (r.data.model != null) AppCardState.STALE else AppCardState.UPDATING
                records[widgetId] = r.copy(needsSetup = false, data = r.data.copy(state = state))
                publish()
            }
            refreshNow(listOf(widgetId))
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Reading

    /**
     * Whether [info] can become a card before anything is read from it: a home-screen widget of another app that
     * isn't a search bar or a row of shortcuts (these never change, so there is nothing to read).
     */
    private fun offered(info: AppWidgetProviderInfo): Boolean {
        if (info.provider.packageName == app.packageName) return false
        if (info.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN == 0) return false
        if (info.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_SEARCHBOX != 0) return false
        val simpleName = info.provider.className.substringAfterLast('.')
        if (SEARCH_OR_SHORTCUT_CLASS.containsMatchIn(simpleName)) return false
        val label = runCatching { info.loadLabel(app.packageManager) }.getOrNull().orEmpty()
        return !SEARCH_OR_SHORTCUT_LABEL.containsMatchIn(label)
    }

    private fun refreshNow(ids: List<Int>) {
        val pm = app.packageManager
        val toRead = ArrayList<Int>()
        for (id in ids) {
            val info = runCatching { manager.getAppWidgetInfo(id) }.getOrNull()
            val installed = info != null && runCatching { pm.getApplicationInfo(info.provider.packageName, 0) }.isSuccess
            val record = records[id]
            if (info == null || !installed) {
                // The app is gone (or the widget with it): forget what was read, and let the card offer to remove itself.
                val gone = record ?: CardRecord(
                    AppCardData(id, info?.provider?.packageName.orEmpty(), "", "", NEUTRAL_ACCENT, AppCardState.REMOVED, null, 0),
                    info?.provider?.flattenToString().orEmpty()
                )
                records[id] = applyRead(gone, SnapCandidate(ReadVerdict.REMOVED), System.currentTimeMillis()) ?: gone
                continue
            }
            if (record == null) records[id] = newRecord(id, info)
            toRead += id
        }
        publish()
        if (toRead.isEmpty()) return
        awaiting += toRead
        worker.removeCallbacks(grace)
        restartCap()
        val r = reader
        if (r != null) sendRead(r, toRead) else bind()
    }

    private fun newRecord(id: Int, info: AppWidgetProviderInfo): CardRecord {
        val pm = app.packageManager
        val pkg = info.provider.packageName
        return CardRecord(
            AppCardData(
                widgetId = id,
                packageName = pkg,
                appLabel = appLabel(pm, pkg),
                widgetLabel = cleanText(runCatching { info.loadLabel(pm) }.getOrNull()),
                accent = appAccent(app, pkg),
                state = AppCardState.UPDATING,
                model = null,
                updatedAt = 0
            ),
            provider = info.provider.flattenToString()
        )
    }

    private fun onReply(msg: Message) {
        val data: Bundle = msg.data
        when (msg.what) {
            MSG_CARD -> {
                val id = data.getInt(KEY_ID)
                val verdict = runCatching { ReadVerdict.valueOf(data.getString(KEY_VERDICT).orEmpty()) }.getOrNull() ?: return
                val read = SnapCandidate(verdict, SnapCodec.decode(data.getString(KEY_MODEL)), data.getString(KEY_FP).orEmpty().take(16))
                val record = records[id] ?: return
                learn(record, read)
                applyRead(record, read, System.currentTimeMillis())?.let {
                    records[id] = it
                    publish()
                }
                // A trusted answer (or a final one) ends the wait for this card; anything else waits for a better read.
                if (verdict == ReadVerdict.GOOD || verdict == ReadVerdict.SETUP || verdict == ReadVerdict.REMOVED) awaiting.remove(id)
            }
            MSG_CHOICES_DONE -> {
                val request = choiceRequests.remove(data.getInt(KEY_REQUEST)) ?: return
                request.second.complete(data.getStringArrayList(KEY_PROVIDERS).orEmpty().toSet())
            }
        }
        maybeDone()
    }

    /** Learns from [read] which providers to leave off the Add card list: those that never show more than a picture or their placeholder. */
    private fun learn(record: CardRecord, read: SnapCandidate) {
        val provider = record.provider
        val before = verdicts[provider]
        when {
            read.verdict == ReadVerdict.GOOD -> verdicts.remove(provider)
            record.data.model != null -> Unit
            read.verdict == ReadVerdict.INITIAL -> verdicts[provider] = STATIC
            read.verdict == ReadVerdict.PICTURE -> verdicts[provider] = PICTURE
        }
        if (verdicts[provider] != before) scheduleSave()
    }

    /** Which of [providers] are only a picture, asked of the reader; none when it doesn't answer in time. */
    private suspend fun pictureOnly(providers: List<String>): Set<String> {
        if (providers.isEmpty()) return emptySet()
        val answer = CompletableDeferred<Set<String>>()
        val request = withContext(workerDispatcher) {
            val request = nextRequest++
            choiceRequests[request] = providers to answer
            worker.removeCallbacks(grace)
            restartCap()
            val r = reader
            if (r != null) sendChoices(r, request, providers) else bind()
            request
        }
        val result = withTimeoutOrNull(CHOICES_WAIT_MS) { answer.await() }
        if (result == null) {
            withContext(workerDispatcher) {
                choiceRequests.remove(request)
                maybeDone()
            }
        }
        return result.orEmpty()
    }

    private fun sendRead(to: Messenger, ids: Collection<Int>) {
        send(to, Message.obtain(null, MSG_READ).apply { data = Bundle().apply { putIntArray(KEY_IDS, ids.toIntArray()) } })
    }

    private fun sendChoices(to: Messenger, request: Int, providers: List<String>) {
        send(to, Message.obtain(null, MSG_CHOICES).apply {
            data = Bundle().apply {
                putInt(KEY_REQUEST, request)
                putStringArrayList(KEY_PROVIDERS, ArrayList(providers))
            }
        })
    }

    private fun send(to: Messenger, message: Message) {
        message.replyTo = replies
        try {
            to.send(message)
        } catch (_: RemoteException) {
            unbind()
        }
    }

    private fun bind() {
        if (connection != null) return
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                if (connection !== this || service == null) return
                val r = Messenger(service)
                reader = r
                // Everything asked for so far, including after the reader process restarted.
                if (awaiting.isNotEmpty()) sendRead(r, awaiting)
                choiceRequests.forEach { (request, value) -> sendChoices(r, request, value.first) }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                if (connection === this) reader = null
            }

            override fun onBindingDied(name: ComponentName?) {
                if (connection === this) unbind()
            }

            override fun onNullBinding(name: ComponentName?) {
                if (connection === this) unbind()
            }
        }
        connection = conn
        val bound = runCatching {
            app.bindService(Intent(app, WidgetReaderService::class.java), Context.BIND_AUTO_CREATE, workerExecutor, conn)
        }.getOrDefault(false)
        if (!bound) unbind()
    }

    private fun restartCap() {
        worker.removeCallbacks(cap)
        worker.postDelayed(cap, READ_CAP_MS)
    }

    private fun maybeDone() {
        if (connection != null && awaiting.isEmpty() && choiceRequests.isEmpty()) {
            worker.removeCallbacks(grace)
            worker.postDelayed(grace, GRACE_MS)
        }
    }

    /** Lets the reader go; its process ends with it. Cards that never got a trusted read keep what they show. */
    private fun unbind() {
        worker.removeCallbacks(cap)
        worker.removeCallbacks(grace)
        val conn = connection ?: return
        connection = null
        reader = null
        awaiting.clear()
        choiceRequests.values.forEach { it.second.complete(emptySet()) }
        choiceRequests.clear()
        runCatching { app.unbindService(conn) }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // The cache: app-private, no-backup storage, a few KB.

    private fun publish() {
        state.value = records.mapValues { it.value.data }
        scheduleSave()
    }

    private fun scheduleSave() {
        worker.removeCallbacks(save)
        worker.postDelayed(save, 300)
    }

    private fun readCache() {
        val json = runCatching { if (cacheFile.exists()) JSONObject(cacheFile.readText()) else null }.getOrNull() ?: return
        json.optJSONArray("cards")?.let { list ->
            for (i in 0 until list.length()) {
                val o = list.optJSONObject(i) ?: continue
                val record = decodeRecord(o) ?: continue
                records.putIfAbsent(record.data.widgetId, record)
            }
        }
        json.optJSONObject("verdicts")?.let { v -> v.keys().forEach { k -> v.optString(k).takeIf { it == STATIC || it == PICTURE }?.let { verdicts[k] = it } } }
        // What was read from an app uninstalled since goes now, not when Discover next opens.
        val pm = app.packageManager
        var dropped = false
        for ((id, r) in records.toList()) {
            val pkg = r.data.packageName
            if (r.data.model != null && pkg.isNotEmpty() && runCatching { pm.getApplicationInfo(pkg, 0) }.isFailure) {
                records[id] = applyRead(r, SnapCandidate(ReadVerdict.REMOVED), System.currentTimeMillis()) ?: r
                dropped = true
            }
        }
        state.value = records.mapValues { it.value.data }
        if (dropped) scheduleSave()
    }

    private fun writeCache() {
        val json = JSONObject()
            .put("v", 1)
            .put("cards", JSONArray().apply { records.values.forEach { put(encodeRecord(it)) } })
            .put("verdicts", JSONObject().apply { verdicts.forEach { (k, v) -> put(k, v) } })
        val tmp = File(cacheFile.parentFile, cacheFile.name + ".tmp")
        try {
            tmp.writeText(json.toString())
            if (!tmp.renameTo(cacheFile)) tmp.delete()
        } catch (_: Exception) {
            tmp.delete()
        }
    }

    private fun encodeRecord(r: CardRecord): JSONObject = JSONObject().apply {
        val d = r.data
        put("id", d.widgetId)
        put("provider", r.provider)
        put("package", d.packageName)
        put("app", d.appLabel)
        put("widget", d.widgetLabel)
        put("accent", d.accent)
        put("state", d.state.name)
        d.model?.let { put("model", SnapCodec.encode(it, withFrom = false)) }
        put("at", d.updatedAt)
        if (r.fingerprint.isNotEmpty()) put("fp", r.fingerprint)
        r.pendingKey?.let { put("pendingKey", it); put("pendingAt", r.pendingAt) }
        if (r.needsSetup) put("setup", true)
    }

    private fun decodeRecord(o: JSONObject): CardRecord? {
        val id = o.optInt("id", 0).takeIf { it != 0 } ?: return null
        val model = SnapCodec.decode(o.optJSONObject("model"))
        var state = runCatching { AppCardState.valueOf(o.optString("state")) }.getOrDefault(AppCardState.UPDATING)
        if (model == null && (state == AppCardState.READY || state == AppCardState.STALE)) state = AppCardState.UPDATING
        return CardRecord(
            data = AppCardData(
                widgetId = id,
                packageName = o.optString("package").take(255),
                appLabel = cleanText(o.optString("app")),
                widgetLabel = cleanText(o.optString("widget")),
                accent = o.optInt("accent", NEUTRAL_ACCENT),
                state = state,
                model = model,
                updatedAt = o.optLong("at")
            ),
            provider = o.optString("provider").take(512),
            fingerprint = o.optString("fp").take(16),
            pendingKey = if (o.has("pendingKey")) o.optString("pendingKey").take(256) else null,
            pendingAt = o.optLong("pendingAt"),
            needsSetup = o.optBoolean("setup")
        )
    }

    private companion object {
        fun appLabel(pm: PackageManager, pkg: String): String = cleanText(
            runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)) }.getOrNull() ?: pkg
        )

        val SEARCH_OR_SHORTCUT_LABEL = Regex("(?i)\\b(search|shortcuts?)\\b")
        val SEARCH_OR_SHORTCUT_CLASS = Regex("Search|Shortcut|QuickAction")
    }
}
