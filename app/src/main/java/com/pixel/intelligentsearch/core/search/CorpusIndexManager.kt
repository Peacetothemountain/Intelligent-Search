package com.pixel.intelligentsearch.core.search

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
 * 4. Local Files (via MediaStore)
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
    private val contactsMutex = Mutex()

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
                    val filesDeferred = async { reindexFilesInternal() }
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

    fun reindexShortcuts() {
        scope.launch {
            reindexShortcutsInternal()
        }
    }

    fun reindexFiles() {
        scope.launch {
            reindexFilesInternal()
        }
    }

    private suspend fun reindexAppsInternal(forceRefresh: Boolean = false) {
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

    private fun reindexShortcutsInternal() {
        try {
            val shortcuts = ShortcutProvider.getAllShortcuts(context)
            searchCoordinator.replaceShortcuts(shortcuts)
        } catch (e: Exception) {
            Log.e(TAG, "Failed indexing shortcuts", e)
        }
    }

    private suspend fun reindexContactsInternal() {
        contactsMutex.withLock {
            try {
                val contacts = SystemDataProvider.getAllContacts(context)
                // Atomic swap: searches never observe an empty or partially rebuilt contact index.
                searchCoordinator.replaceContacts(contacts)
                Log.d(TAG, "Indexed ${contacts.size} contacts into memory")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed indexing contacts", e)
            }
        }
    }

    private suspend fun reindexFilesInternal() {
        try {
            val files = SystemDataProvider.getAllRecentFiles(context, limit = 300)
            searchCoordinator.replaceFiles(files)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed indexing files", e)
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
        scope.coroutineContext.cancelChildren()
        synchronized(observerLock) {
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
