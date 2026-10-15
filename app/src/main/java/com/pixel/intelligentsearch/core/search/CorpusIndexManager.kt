package com.pixel.intelligentsearch.core.search

import com.pixel.intelligentsearch.core.data.SettingsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.pixel.intelligentsearch.core.data.ShortcutProvider
import com.pixel.intelligentsearch.core.data.SystemDataProvider
import com.pixel.intelligentsearch.core.data.UserGrantedFiles
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Corpus Index Manager.
 * Orchestrates eager in-memory index construction and real-time delta updates for:
 * 1. Applications (with usage metadata and package add/replace/remove observers)
 * 2. Contacts (with ContactsContract ContentObserver)
 * 3. App Shortcuts (via LauncherApps)
 * 4. Local Files (folders and photos the user chose, plus MediaStore entries this app can see)
 *
 * Guarantees that keystroke queries execute 100% in-memory without ContentProvider Binder IPC stalls.
 */
@Singleton
class CorpusIndexManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val searchCoordinator: UnifiedSearchCoordinator
) {
    companion object {
        private const val TAG = "CorpusIndexManager"
        private const val SETTINGS_PREFS = SettingsManager.PREFS_NAME
        private const val FILES_REFRESH_INTERVAL_MS = 2 * 60 * 1000L
        private const val CONTACTS_REFRESH_INTERVAL_MS = 60 * 1000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _isIndexReady = MutableStateFlow(false)
    val isIndexReady: StateFlow<Boolean> = _isIndexReady.asStateFlow()
    private val isInitialized = java.util.concurrent.atomic.AtomicBoolean(false)

    // Observer registration runs on the IO scope while cleanup() may arrive from the main thread, so
    // both are serialized on observerLock. The debounce job is also touched from the main-thread
    // ContentObserver callback and therefore stays volatile.
    private val observerLock = Any()
    private var contactsObserver: ContentObserver? = null
    private var packageReceiver: BroadcastReceiver? = null
    @Volatile
    private var contactsDebounceJob: Job? = null
    private val appsMutex = Mutex()
    private val shortcutsMutex = Mutex()
    private val contactsMutex = Mutex()
    private val filesMutex = Mutex()

    // Folder and photo-picker choices live in settings prefs; any change there rebuilds the file index.
    // SharedPreferences keeps listeners weakly, so the listener must stay referenced from this field.
    private var filesPrefs: android.content.SharedPreferences? = null
    private var filesPrefListener: android.content.SharedPreferences.OnSharedPreferenceChangeListener? = null
    @Volatile
    private var filesDebounceJob: Job? = null
    @Volatile
    private var lastFilesIndexAtMs = 0L
    @Volatile
    private var lastContactsIndexAtMs = 0L

    /**
     * Bootstraps eager background indexing of all corpuses.
     */
    fun initialize(force: Boolean = false) {
        if (!force && isInitialized.getAndSet(true)) {
            return
        }
        isInitialized.set(true)
        scope.launch {
            try {
                val startMs = System.currentTimeMillis()

                // Concurrently index all corpuses using Kotlin coroutines for instant readiness
                coroutineScope {
                    val appsDeferred = async { reindexAppsInternal(forceRefresh = force) }
                    val shortcutsDeferred = async { reindexShortcutsInternal() }
                    val contactsDeferred = async { reindexContactsInternal() }
                    val filesDeferred = async { reindexFilesInternal(onlyIfOlderThanMs = if (force) null else FILES_REFRESH_INTERVAL_MS) }
                    awaitAll(appsDeferred, shortcutsDeferred, contactsDeferred, filesDeferred)
                }

                _isIndexReady.value = true
                val totalMs = System.currentTimeMillis() - startMs
                Log.d(TAG, "Corpus indexing completed in ${totalMs}ms")

                // Register live system listeners
                registerObservers()
            } catch (e: CancellationException) {
                // Cancelled (e.g. by cleanup()): allow a later initialize() to start over.
                isInitialized.set(false)
                throw e
            } catch (e: Exception) {
                isInitialized.set(false)
                Log.e(TAG, "Error during corpus indexing initialization", e)
            }
        }
    }

    fun reindexApps(forceRefresh: Boolean = true) {
        scope.launch {
            reindexAppsInternal(forceRefresh)
        }
    }

    fun reindexContacts() {
        scope.launch {
            reindexContactsInternal()
        }
    }

    /**
     * Re-reads contacts only if the last full read is older than [CONTACTS_REFRESH_INTERVAL_MS]. The
     * ContentObserver already picks up edits, so overlay opens must not re-read the whole provider.
     */
    fun reindexContactsIfStale() {
        scope.launch {
            reindexContactsInternal(onlyIfOlderThanMs = CONTACTS_REFRESH_INTERVAL_MS)
        }
    }

    fun reindexShortcuts() {
        scope.launch {
            reindexShortcutsInternal()
        }
    }

    /**
     * Picks up files added to the user's chosen folders since the last pass. Folder providers send no
     * reliable change notifications, so the search overlay calls this each time it opens.
     */
    fun reindexFilesIfStale() {
        scope.launch {
            reindexFilesInternal(onlyIfOlderThanMs = FILES_REFRESH_INTERVAL_MS)
        }
    }

    private suspend fun reindexAppsInternal(forceRefresh: Boolean = false) {
        appsMutex.withLock {
            try {
                if (forceRefresh) {
                    SystemDataProvider.invalidateAppsCache()
                }
                searchCoordinator.bulkIndexApps(SystemDataProvider.getAllApps(context, forceRefresh = forceRefresh))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed indexing apps", e)
            }
        }
    }

    private suspend fun reindexShortcutsInternal() {
        shortcutsMutex.withLock {
            try {
                val shortcuts = ShortcutProvider.getAllShortcuts(context)
                searchCoordinator.replaceShortcuts(shortcuts)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed indexing shortcuts", e)
            }
        }
    }

    private suspend fun reindexContactsInternal(onlyIfOlderThanMs: Long? = null) {
        contactsMutex.withLock {
            if (onlyIfOlderThanMs != null &&
                android.os.SystemClock.elapsedRealtime() - lastContactsIndexAtMs < onlyIfOlderThanMs
            ) return
            try {
                val contacts = SystemDataProvider.getAllContacts(context)
                // Atomic swap: searches never observe an empty or partially rebuilt contact index.
                searchCoordinator.replaceContacts(contacts)
                // Only a read made with the permission counts as fresh, so granting it later still re-reads.
                if (context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    lastContactsIndexAtMs = android.os.SystemClock.elapsedRealtime()
                }
                Log.d(TAG, "Indexed ${contacts.size} contacts into memory")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed indexing contacts", e)
            }
        }
    }

    private suspend fun reindexFilesInternal(onlyIfOlderThanMs: Long? = null) {
        filesMutex.withLock {
            if (onlyIfOlderThanMs != null &&
                android.os.SystemClock.elapsedRealtime() - lastFilesIndexAtMs < onlyIfOlderThanMs
            ) return
            try {
                val prefs = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
                if (!UserGrantedFiles.isFileSearchEnabled(prefs)) {
                    // Files search is off: drop the index instead of walking storage for nothing.
                    searchCoordinator.replaceFiles(emptyList())
                } else {
                    // Without broad storage permissions (see UserGrantedFiles) MediaStore only returns
                    // this app's own files; the user's chosen folders and photos are the main source.
                    val granted = UserGrantedFiles.loadAll(context, prefs)
                    val ownMedia = SystemDataProvider.getAllRecentFiles(
                        context,
                        limit = 300,
                        includeHidden = UserGrantedFiles.includeHiddenFiles(prefs)
                    )
                    val files = (granted + ownMedia).distinctBy { it.uri }
                    searchCoordinator.replaceFiles(files)
                    Log.d(TAG, "Indexed ${files.size} files (${granted.size} from chosen folders and photos)")
                }
                lastFilesIndexAtMs = android.os.SystemClock.elapsedRealtime()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed indexing files", e)
            }
        }
    }

    private fun registerObservers() = synchronized(observerLock) {
        // Register ContentObserver for Contacts changes with 1000ms conflated debounce
        try {
            if (contactsObserver == null) {
                contactsObserver = object : ContentObserver(mainHandler) {
                    override fun onChange(selfChange: Boolean, uri: Uri?) {
                        super.onChange(selfChange, uri)
                        contactsDebounceJob?.cancel()
                        contactsDebounceJob = scope.launch {
                            delay(1000L)
                            reindexContactsInternal()
                        }
                    }
                }
                context.contentResolver.registerContentObserver(
                    ContactsContract.Contacts.CONTENT_URI,
                    true,
                    contactsObserver!!
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not register Contacts ContentObserver", e)
        }

        // Rebuild the file index when the user adds or removes folders, picks photos, or toggles Files search.
        try {
            if (filesPrefListener == null) {
                val prefs = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
                val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key in UserGrantedFiles.INDEX_KEYS) {
                        filesDebounceJob?.cancel()
                        filesDebounceJob = scope.launch {
                            delay(500L)
                            reindexFilesInternal()
                        }
                    }
                }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                filesPrefs = prefs
                filesPrefListener = listener
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not watch file search settings", e)
        }

        // Register BroadcastReceiver for Application changes
        try {
            if (packageReceiver == null) {
                packageReceiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        val pkg = intent?.data?.schemeSpecificPart
                        val isReplacing = intent?.getBooleanExtra(Intent.EXTRA_REPLACING, false) ?: false
                        if (intent?.action == Intent.ACTION_PACKAGE_REMOVED && !isReplacing) {
                            if (!pkg.isNullOrBlank()) {
                                searchCoordinator.removePackage(pkg)
                                SystemDataProvider.invalidateAppsCache()
                            }
                        } else {
                            reindexApps(forceRefresh = true)
                            reindexShortcuts()
                        }
                    }
                }
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_PACKAGE_ADDED)
                    addAction(Intent.ACTION_PACKAGE_REMOVED)
                    addAction(Intent.ACTION_PACKAGE_REPLACED)
                    addDataScheme("package")
                }
                try {
                    ContextCompat.registerReceiver(
                        context,
                        packageReceiver!!,
                        filter,
                        ContextCompat.RECEIVER_NOT_EXPORTED
                    )
                } catch (e: Exception) {
                    context.registerReceiver(packageReceiver, filter)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not register package BroadcastReceiver", e)
        }
    }

    /**
     * Unregisters active system listeners and cancels background debounce jobs.
     */
    fun cleanup() {
        contactsDebounceJob?.cancel()
        contactsDebounceJob = null
        filesDebounceJob?.cancel()
        filesDebounceJob = null
        scope.coroutineContext.cancelChildren()
        synchronized(observerLock) {
            filesPrefListener?.let { listener ->
                filesPrefs?.unregisterOnSharedPreferenceChangeListener(listener)
            }
            filesPrefListener = null
            filesPrefs = null
            try {
                contactsObserver?.let {
                    context.contentResolver.unregisterContentObserver(it)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister contacts observer", e)
            }
            contactsObserver = null
            try {
                packageReceiver?.let {
                    context.unregisterReceiver(it)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister package receiver", e)
            }
            packageReceiver = null
        }
        // Observers are gone, so the index will no longer track changes: require a fresh initialize().
        _isIndexReady.value = false
        isInitialized.set(false)
    }
}
