package com.pixel.intelligentsearch.core.search

import com.pixel.intelligentsearch.di.ApplicationScope
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.get
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
import com.pixel.intelligentsearch.core.data.UserGrantedFiles
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
 * 3. Local Files (folders and photos the user chose, plus MediaStore entries this app can see)
 *
 * Guarantees that keystroke queries execute 100% in-memory without ContentProvider Binder IPC stalls.
 */
@Singleton
class CorpusIndexManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val sources: com.pixel.intelligentsearch.core.data.DeviceSearchSources,
    private val searchCoordinator: UnifiedSearchCoordinator,
    @param:ApplicationScope private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "CorpusIndexManager"
        private const val SETTINGS_PREFS = SettingsManager.PREFS_NAME
        private const val FILES_REFRESH_INTERVAL_MS = 2 * 60 * 1000L
        private const val APP_USAGE_REFRESH_INTERVAL_MS = 5 * 60 * 1000L
        private const val CONTACTS_REFRESH_INTERVAL_MS = 60 * 1000L
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private val _isIndexReady = MutableStateFlow(false)
    val isIndexReady: StateFlow<Boolean> = _isIndexReady.asStateFlow()
    private val isInitialized = java.util.concurrent.atomic.AtomicBoolean(false)

    // Observer registration runs on the IO scope and is serialized on observerLock, so passes that overlap
    // never register an observer twice. The debounce jobs are touched from the main-thread observer
    // callbacks and therefore stay volatile.
    private val observerLock = Any()
    private var contactsObserver: ContentObserver? = null
    private var launcherCallback: android.content.pm.LauncherApps.Callback? = null
    /** Coalesces a burst of package events (an update fires several) into one re-scan. */
    private var pendingAppReindex: kotlinx.coroutines.Job? = null
    @Volatile
    private var contactsDebounceJob: Job? = null
    private val appsMutex = Mutex()
    private val contactsMutex = Mutex()
    private val filesMutex = Mutex()

    // Folder and photo-picker choices live in settings prefs; any change there rebuilds the file index.
    // SharedPreferences keeps listeners weakly, so the listener must stay referenced from this field.
    private var filesPrefListener: android.content.SharedPreferences.OnSharedPreferenceChangeListener? = null
    @Volatile
    private var filesDebounceJob: Job? = null
    @Volatile
    private var lastFilesIndexAtMs = 0L
    @Volatile
    private var lastContactsIndexAtMs = 0L
    @Volatile
    private var lastAppUsageAtMs = 0L

    /**
     * Bootstraps eager background indexing of all corpuses.
     */
    fun initialize() {
        if (isInitialized.getAndSet(true)) {
            return
        }
        scope.launch(Dispatchers.IO) {
            try {
                val startMs = System.currentTimeMillis()

                // Concurrently index all corpuses using Kotlin coroutines for instant readiness
                coroutineScope {
                    val appsDeferred = async { reindexAppsInternal() }
                    refreshAppUsageIfStale()
                    val contactsDeferred = async { reindexContactsInternal() }
                    val filesDeferred = async { reindexFilesInternal(onlyIfOlderThanMs = FILES_REFRESH_INTERVAL_MS) }
                    awaitAll(appsDeferred, contactsDeferred, filesDeferred)
                }

                _isIndexReady.value = true
                val totalMs = System.currentTimeMillis() - startMs
                Log.d(TAG, "Corpus indexing completed in ${totalMs}ms")

                // Register live system listeners
                registerObservers()
            } catch (e: CancellationException) {
                // Cancelled with its scope: allow a later initialize() to start over.
                isInitialized.set(false)
                throw e
            } catch (e: Exception) {
                isInitialized.set(false)
                Log.e(TAG, "Error during corpus indexing initialization", e)
            }
        }
    }

    /** A full re-scan (rather than dropping the package by name) keeps copies of the same app in other profiles. */
    private fun appsChanged() {
        pendingAppReindex?.cancel()
        pendingAppReindex = scope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(300)
            reindexAppsInternal(forceRefresh = true)
        }
    }

    fun reindexApps(forceRefresh: Boolean = true) {
        scope.launch(Dispatchers.IO) {
            reindexAppsInternal(forceRefresh)
        }
    }

    fun reindexContacts() {
        scope.launch(Dispatchers.IO) {
            reindexContactsInternal()
        }
    }

    /**
     * Re-reads contacts only if the last full read is older than [CONTACTS_REFRESH_INTERVAL_MS]. The
     * ContentObserver already picks up edits, so overlay opens must not re-read the whole provider.
     */
    fun reindexContactsIfStale() {
        scope.launch(Dispatchers.IO) {
            reindexContactsInternal(onlyIfOlderThanMs = CONTACTS_REFRESH_INTERVAL_MS)
        }
    }

    /**
     * Picks up files added to the user's chosen folders since the last pass. Folder providers send no
     * reliable change notifications, so the search overlay calls this each time it opens.
     */
    fun reindexFilesIfStale() {
        scope.launch(Dispatchers.IO) {
            reindexFilesInternal(onlyIfOlderThanMs = FILES_REFRESH_INTERVAL_MS)
        }
    }

    /** Re-reads app usage for ranking at most every [APP_USAGE_REFRESH_INTERVAL_MS]; one cheap system call. */
    fun refreshAppUsageIfStale() {
        val now = android.os.SystemClock.elapsedRealtime()
        if (lastAppUsageAtMs != 0L && now - lastAppUsageAtMs < APP_USAGE_REFRESH_INTERVAL_MS) return
        lastAppUsageAtMs = now
        scope.launch(Dispatchers.IO) {
            searchCoordinator.updateAppUsage(sources.appUsage())
        }
    }

    private suspend fun reindexAppsInternal(forceRefresh: Boolean = false) {
        appsMutex.withLock {
            try {
                if (forceRefresh) {
                    sources.invalidateAppsCache()
                }
                searchCoordinator.bulkIndexApps(sources.allApps(forceRefresh = forceRefresh))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed indexing apps", e)
            }
        }
    }

    private suspend fun reindexContactsInternal(onlyIfOlderThanMs: Long? = null) {
        contactsMutex.withLock {
            // 0 means never read; the uptime clock is itself small just after boot, so it must not count as recent.
            if (onlyIfOlderThanMs != null && lastContactsIndexAtMs != 0L &&
                android.os.SystemClock.elapsedRealtime() - lastContactsIndexAtMs < onlyIfOlderThanMs
            ) return
            try {
                // With contact search off nothing is indexed, so no surface (global search included) can show them.
                val contactsOn = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)[SettingsManager.SEARCH_CONTACTS]
                val contacts = if (contactsOn) sources.allContacts() else emptyList()
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
                Log.e(TAG, "Failed indexing contacts: ${e.javaClass.simpleName}") // messages can name contacts
            }
        }
    }

    private suspend fun reindexFilesInternal(onlyIfOlderThanMs: Long? = null) {
        filesMutex.withLock {
            // 0 means never indexed; the uptime clock is itself small just after boot, so it must not count as recent.
            if (onlyIfOlderThanMs != null && lastFilesIndexAtMs != 0L &&
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
                    val ownMedia = sources.recentFiles(limit = 300, includeHidden = UserGrantedFiles.includeHiddenFiles(prefs))
                        .filter(UserGrantedFiles.keepsOwnMedia(prefs))
                    val files = (granted + ownMedia).distinctBy { it.uri }
                    searchCoordinator.replaceFiles(files)
                    Log.d(TAG, "Indexed ${files.size} files (${granted.size} from chosen folders and photos)")
                }
                lastFilesIndexAtMs = android.os.SystemClock.elapsedRealtime()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed indexing files: ${e.javaClass.simpleName}") // messages can name files
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
                        contactsDebounceJob = scope.launch(Dispatchers.IO) {
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
                        filesDebounceJob = scope.launch(Dispatchers.IO) {
                            delay(500L)
                            reindexFilesInternal()
                        }
                    }
                }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                filesPrefListener = listener
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not watch file search settings", e)
        }

        // App changes in every profile (personal, work, private space): installs, removals, updates, apps disabled
        // or suspended, and profiles becoming available. Package broadcasts only cover the
        // personal profile and miss disabling, so LauncherApps is the one source.
        try {
            if (launcherCallback == null) {
                val callback = object : android.content.pm.LauncherApps.Callback() {
                    override fun onPackageRemoved(packageName: String, user: android.os.UserHandle) = appsChanged()
                    override fun onPackageAdded(packageName: String, user: android.os.UserHandle) = appsChanged()
                    override fun onPackageChanged(packageName: String, user: android.os.UserHandle) = appsChanged()
                    override fun onPackagesAvailable(packageNames: Array<out String>, user: android.os.UserHandle, replacing: Boolean) = appsChanged()
                    override fun onPackagesUnavailable(packageNames: Array<out String>, user: android.os.UserHandle, replacing: Boolean) = appsChanged()
                    override fun onPackagesSuspended(packageNames: Array<out String>, user: android.os.UserHandle) = appsChanged()
                    override fun onPackagesUnsuspended(packageNames: Array<out String>, user: android.os.UserHandle) = appsChanged()
                }
                context.getSystemService(android.content.pm.LauncherApps::class.java)?.registerCallback(callback, mainHandler)
                launcherCallback = callback
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not watch app changes", e)
        }
    }

}
