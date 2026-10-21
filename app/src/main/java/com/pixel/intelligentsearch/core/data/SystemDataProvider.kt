package com.pixel.intelligentsearch.core.data
import com.pixel.intelligentsearch.di.appEntryPoint
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

import androidx.compose.runtime.Immutable

@Immutable
data class AppAction(
    val title: String,
    val action: String,
    val dataUri: String? = null
)

enum class ProfileType {
    PERSONAL,
    WORK,
    PRIVATE,
    CLONE
}

@Immutable
data class AppItem(
    val name: String,
    val packageName: String,
    val icon: Drawable,
    val actions: List<AppAction> = emptyList(),
    val userHandle: android.os.UserHandle? = null,
    val profileType: ProfileType = ProfileType.PERSONAL,
    val isPrivateProfile: Boolean = false,
    val isQuietMode: Boolean = false,
    val activityName: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AppItem) return false
        if (packageName != other.packageName) return false
        if (activityName != other.activityName) return false
        if (userHandle != other.userHandle) return false
        if (profileType != other.profileType) return false
        if (isPrivateProfile != other.isPrivateProfile) return false
        if (isQuietMode != other.isQuietMode) return false
        if (name != other.name) return false
        if (actions != other.actions) return false
        return true
    }

    override fun hashCode(): Int {
        var result = packageName.hashCode()
        result = 31 * result + (activityName?.hashCode() ?: 0)
        result = 31 * result + (userHandle?.hashCode() ?: 0)
        result = 31 * result + profileType.hashCode()
        result = 31 * result + isPrivateProfile.hashCode()
        result = 31 * result + isQuietMode.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + actions.hashCode()
        return result
    }
}

/** An upcoming event instance. [startTime] reads "All day" when [allDay]; [beginMillis] is the instance's own start. */
@Immutable
data class CalendarEvent(
    val title: String,
    val startTime: String,
    val eventId: Long = 0L,
    val beginMillis: Long = 0L,
    val allDay: Boolean = false
)

@Immutable
data class ContactItem(
    val name: String,
    val phoneNumber: String,
    val lookupUri: String,
    /** The contact's default email address (else its first), blank when it has none. */
    val email: String = "",
    /**
     * The number the chat buttons use: the contact's default number, else a mobile one, else [phoneNumber]. Blank
     * means [phoneNumber]. [phoneNumber] itself stays the one search found, so matching and ranking don't change.
     */
    val chatNumber: String = ""
)

@Immutable
data class FileItem(
    val name: String,
    val path: String,
    val mimeType: String,
    val uri: String,
    val lastModifiedMs: Long = 0L
)

object SystemDataProvider {

    @Volatile
    private var cachedApps: List<AppItem>? = null

    /** Uses the Hilt singleton so only one LauncherApps callback and profile cache exist per process. */
    private fun getMultiProfileManager(context: Context): com.pixel.intelligentsearch.core.profile.MultiProfileManager =
        context.appEntryPoint.multiProfileManager()

    fun invalidateAppsCache() {
        cachedApps = null
    }

    /** Reads every launchable app from the device. Replaceable in tests, which count how often a scan runs. */
    @VisibleForTesting
    internal var appScanner: suspend (Context, Boolean) -> List<AppItem> = { context, forceRefresh -> scanApps(context, forceRefresh) }

    /** One app scan at a time: callers that arrive together (overlay, index, widget at start-up) share its result. */
    private val appsLock = Mutex()

    suspend fun getAllApps(context: Context, forceRefresh: Boolean = false): List<AppItem> = withContext(Dispatchers.IO) {
        if (!forceRefresh && cachedApps != null) {
            cachedApps?.let { return@withContext it }
        }

        appsLock.withLock {
            // A caller that waited here usually finds the scan it was waiting on already cached.
            if (!forceRefresh) cachedApps?.let { return@withLock it }
            val finalApps = appScanner(context, forceRefresh)
            if (finalApps.isNotEmpty() || cachedApps == null) {
                cachedApps = finalApps
            }
            finalApps
        }
    }

    /**
     * Every launchable app in every profile, by name. When nothing at all can be read, the last list read stands in,
     * so a failed PackageManager query never empties the results.
     */
    private suspend fun scanApps(context: Context, forceRefresh: Boolean): List<AppItem> {
        val mergedMap = LinkedHashMap<String, AppItem>()

        // 1. Discover multi-profile apps (Personal, Work Profile, Private Space, Dual Apps) first via LauncherApps
        try {
            val multiProfileManager = getMultiProfileManager(context)
            val profileApps = multiProfileManager.getAllProfileApps(forceRefresh)
            for (app in profileApps) {
                val key = if (app.profileType != ProfileType.PERSONAL) "${app.packageName}_${app.userHandle}" else app.packageName
                mergedMap[key] = app
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            // Fall through to PackageManager fallback below
        }

        // 2. Only query PackageManager for personal apps if MultiProfileManager returned no personal apps
        val hasPersonalApps = mergedMap.values.any { it.profileType == ProfileType.PERSONAL }
        if (!hasPersonalApps) {
            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.queryIntentActivities(mainIntent, 0)
                }
            } catch (e: Throwable) {
                android.util.Log.w("SystemDataProvider", "IPC binder transaction error querying activities", e)
                if (mergedMap.isEmpty()) {
                    cachedApps?.let { return it }
                }
                emptyList()
            }
            resolveInfos.asSequence()
                .distinctBy { it.activityInfo.packageName }
                .forEach {
                    try {
                        val label = it.loadLabel(pm).toString()
                        val packageName = it.activityInfo.packageName
                        val icon = it.loadIcon(pm)
                        mergedMap[packageName] = AppItem(
                            name = label,
                            packageName = packageName,
                            icon = icon,
                            actions = getAppActions(packageName),
                            activityName = it.activityInfo.name
                        )
                    } catch (_: Throwable) {}
                }
        }

        return mergedMap.values.sortedBy { it.name.lowercase() }
    }

    /** How much an app has been used lately: on how many of the last days, and when it was last in the foreground. */
    data class AppUsage(val daysUsed: Int, val lastUsedMs: Long)

    /**
     * Usage per package over the last [days] from daily usage buckets, for ranking apps people actually open.
     * Empty when usage access isn't granted.
     */
    fun getAppUsage(context: Context, days: Int = 14): Map<String, AppUsage> {
        val usageStatsManager = context.getSystemService(UsageStatsManager::class.java) ?: return emptyMap()
        val now = System.currentTimeMillis()
        val buckets = try {
            usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - days * 24L * 60 * 60 * 1000, now)
        } catch (_: Exception) {
            null
        } ?: return emptyMap()
        return buckets.filter { it.totalTimeInForeground > 0 }
            .groupBy { it.packageName }
            .mapValues { (_, stats) -> AppUsage(stats.size.coerceAtMost(days), stats.maxOf { it.lastTimeUsed }) }
    }

    suspend fun getRecentApps(context: Context, hiddenApps: Set<String> = emptySet(), fallbackToAll: Boolean = true): List<AppItem> = withContext(Dispatchers.IO) {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: run {
                if (!fallbackToAll) return@withContext emptyList()
                return@withContext getAllApps(context).filter { !hiddenApps.contains(it.packageName) }.take(8)
            }
        val time = System.currentTimeMillis()
        val stats = try {
            usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                time - 1000 * 60 * 60 * 24, // Last 24 hours
                time
            ) ?: emptyList()
        } catch (_: Throwable) {
            emptyList()
        }

        val pm = context.packageManager
        // The last 24 hours span two daily buckets, so an app used on both days comes back twice; only its latest
        // use counts, or the duplicates would take the slots of other apps.
        val sortedStats = stats.filter { it.totalTimeInForeground > 0 && !hiddenApps.contains(it.packageName) }
            .sortedByDescending { it.lastTimeUsed }
            .distinctBy { it.packageName }

        val recentApps = mutableListOf<AppItem>()
        for (stat in sortedStats) {
            if (recentApps.size >= 8) break // Max 8 recent apps
            try {
                // Check if it's a launchable app
                val intent = pm.getLaunchIntentForPackage(stat.packageName)
                if (intent != null) {
                    val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getApplicationInfo(stat.packageName, PackageManager.ApplicationInfoFlags.of(0))
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getApplicationInfo(stat.packageName, 0)
                    }
                    val label = pm.getApplicationLabel(appInfo).toString()
                    val packageName = stat.packageName
                    val icon = pm.getApplicationIcon(appInfo)
                    recentApps.add(
                        AppItem(
                            name = label,
                            packageName = packageName,
                            icon = icon,
                            actions = getAppActions(packageName)
                        )
                    )
                }
            } catch (_: Throwable) {
                // Ignore package resolution failures
            }
        }
        
        if (recentApps.isEmpty()) {
            if (!fallbackToAll) return@withContext emptyList()
            return@withContext getAllApps(context).filter { !hiddenApps.contains(it.packageName) }.take(8)
        }
        
        recentApps.distinctBy { it.packageName }
    }

    suspend fun getContextAwareQuickApps(context: Context, hiddenApps: Set<String> = emptySet()): List<AppItem> = withContext(Dispatchers.IO) {
        val recents = getRecentApps(context, hiddenApps)
        if (recents.size >= 6) {
            return@withContext recents.take(6)
        }
        val allApps = getAllApps(context).filter { it.packageName !in hiddenApps }
        val combined = (recents + allApps).distinctBy { it.packageName }
        combined.take(6)
    }

    suspend fun getUpcomingEvents(context: Context): List<CalendarEvent> = withContext(Dispatchers.IO) {
        val events = mutableListOf<CalendarEvent>()
        if (context.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return@withContext events
        }

        try {
            val now = System.currentTimeMillis()
            val tomorrow = now + 1000 * 60 * 60 * 24

            val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
            android.content.ContentUris.appendId(builder, now)
            android.content.ContentUris.appendId(builder, tomorrow)

            val projection = arrayOf(
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.ALL_DAY
            )

            context.contentResolver.query(
                builder.build(),
                projection,
                // Calendars the user hid in their calendar app stay hidden here too.
                "${CalendarContract.Instances.VISIBLE} = 1",
                null,
                "${CalendarContract.Instances.BEGIN} ASC"
            )?.use { cursor ->
                val titleIndex = cursor.getColumnIndex(CalendarContract.Instances.TITLE)
                val dtStartIndex = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
                val eventIdIndex = cursor.getColumnIndex(CalendarContract.Instances.EVENT_ID)
                val allDayIndex = cursor.getColumnIndex(CalendarContract.Instances.ALL_DAY)
                // Honours the user's 12/24-hour preference and locale instead of always printing AM/PM.
                val timeFormat = android.text.format.DateFormat.getTimeFormat(context)

                while (cursor.moveToNext() && events.size < 3) { // Max 3 events
                    val title = if (titleIndex >= 0) cursor.getString(titleIndex) else null
                    val startTimeMillis = if (dtStartIndex >= 0) cursor.getLong(dtStartIndex) else 0L
                    val eventId = if (eventIdIndex >= 0) cursor.getLong(eventIdIndex) else 0L
                    val allDay = allDayIndex >= 0 && cursor.getInt(allDayIndex) != 0
                    // An all-day event starts at midnight UTC, which local time would show as some odd hour.
                    val timeString = if (allDay) "All day" else timeFormat.format(java.util.Date(startTimeMillis))

                    events.add(CalendarEvent(title ?: "Event", timeString, eventId, startTimeMillis, allDay))
                }
            }
        } catch (_: Throwable) {}
        events
    }

    /**
     * Contacts whose name or number matches [query]. [forActions] also reads what the contact buttons need, their
     * email addresses and chat numbers; callers that only want names and numbers skip those queries.
     */
    suspend fun getContacts(context: Context, query: String, forActions: Boolean = true): List<ContactItem> = withContext(Dispatchers.IO) {
        val contacts = mutableListOf<ContactItem>()
        val contactIds = mutableListOf<Long>()
        if (query.isEmpty() || context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return@withContext contacts
        }

        try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI.buildUpon()
                .appendPath(query)
                .build()
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY
            )

            context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val lookupKeyIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)
                val idIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)

                while (cursor.moveToNext() && contacts.size < 15) {
                    val name = if (nameIndex >= 0) cursor.getString(nameIndex) ?: "" else ""
                    val number = if (numberIndex >= 0) cursor.getString(numberIndex) ?: "" else ""
                    val lookupKey = if (lookupKeyIndex >= 0) cursor.getString(lookupKeyIndex) ?: "" else ""
                    val id = if (idIndex >= 0) cursor.getLong(idIndex) else 0L

                    val lookupUri = ContactsContract.Contacts.getLookupUri(id, lookupKey)?.toString() ?: ""

                    if (name.isNotBlank() || number.isNotBlank()) {
                        contacts.add(ContactItem(name, number, lookupUri))
                        contactIds.add(id)
                    }
                }
            }
        } catch (_: Throwable) {}

        val found = if (forActions) {
            withEmails(context, withChatNumbers(context, contacts, contactIds), contactIds, onlyThese = true)
        } else contacts
        found.distinctBy { it.lookupUri.ifEmpty { "${it.name}:${it.phoneNumber}" } }
    }

    /** The most phone rows [withChatNumbers] reads, however many numbers the found contacts have saved. */
    private const val CHAT_NUMBER_ROWS = 300

    /**
     * [contacts] with each one's chat number, matched by [contactIds] (same order). All of those contacts' phone rows
     * are read in a query of their own, so a mobile beyond the search's 15-row cut-off still counts, and the search's
     * own query (which contacts it finds, their order, the number shown) stays as it was.
     */
    private fun withChatNumbers(context: Context, contacts: List<ContactItem>, contactIds: List<Long>): List<ContactItem> {
        if (contacts.isEmpty()) return contacts
        val chatNumbers = ChatNumbers()
        val wanted = contactIds.distinct()
        try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI.buildUpon()
                    .appendQueryParameter(ContactsContract.LIMIT_PARAM_KEY, CHAT_NUMBER_ROWS.toString())
                    .build(),
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    *ChatNumbers.COLUMNS
                ),
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} IN (${wanted.joinToString(",") { "?" }})",
                wanted.map { it.toString() }.toTypedArray(),
                // The numbers marked as default come first, so the row cap never drops them.
                "${ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY} DESC, ${ContactsContract.CommonDataKinds.Phone.IS_PRIMARY} DESC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val numberIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val chatColumns = ChatNumbers.columnsOf(cursor)
                var rows = 0
                while (rows++ < CHAT_NUMBER_ROWS && cursor.moveToNext()) {
                    chatNumbers.offer(cursor, chatColumns, cursor.getLong(idIndex), cursor.getString(numberIndex).orEmpty())
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("IntelligentSearch", "Contact chat numbers unreadable", e)
        }
        return chatNumbers.applyTo(contacts, contactIds)
    }

    /**
     * Each contact's number for the chat buttons, picked from its phone rows as they are read: the number marked as
     * default first, then a mobile, then the first. Only [ContactItem.chatNumber] comes from it; which contacts are
     * found, their order and the number shown stay as the rows came.
     */
    private class ChatNumbers {
        private val best = HashMap<Long, Pair<Int, String>>()

        fun offer(cursor: android.database.Cursor, columns: IntArray, contactId: Long, number: String) {
            if (number.isBlank()) return
            fun int(i: Int) = if (columns[i] >= 0) cursor.getInt(columns[i]) else 0
            val type = int(2)
            val rank = when {
                int(0) != 0 -> 3 // IS_SUPER_PRIMARY: the default the user picked
                int(1) != 0 -> 2 // IS_PRIMARY
                type == ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE ||
                    type == ContactsContract.CommonDataKinds.Phone.TYPE_WORK_MOBILE -> 1
                else -> 0
            }
            val current = best[contactId]
            if (current == null || rank > current.first) best[contactId] = rank to number
        }

        fun applyTo(contacts: List<ContactItem>, contactIds: List<Long>): List<ContactItem> = contacts.mapIndexed { i, contact ->
            val chat = best[contactIds[i]]?.second
            if (chat != null && chat != contact.phoneNumber) contact.copy(chatNumber = chat) else contact
        }

        companion object {
            val COLUMNS = arrayOf(
                ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY,
                ContactsContract.CommonDataKinds.Phone.IS_PRIMARY,
                ContactsContract.CommonDataKinds.Phone.TYPE
            )

            /** Where [COLUMNS] are in [cursor] (-1 when missing). */
            fun columnsOf(cursor: android.database.Cursor): IntArray = IntArray(COLUMNS.size) { cursor.getColumnIndex(COLUMNS[it]) }
        }
    }

    /**
     * [contacts] with each one's email address filled in, matched by [contactIds] (same order). [onlyThese] asks for
     * just those contacts' addresses rather than reading every address on the device.
     */
    private fun withEmails(context: Context, contacts: List<ContactItem>, contactIds: List<Long>, onlyThese: Boolean): List<ContactItem> {
        if (contacts.isEmpty()) return contacts
        val emails = HashMap<Long, String>()
        val wanted = contactIds.distinct()
        val selection = if (onlyThese) {
            "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} IN (${wanted.joinToString(",") { "?" }})"
        } else null
        try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Email.CONTACT_ID, ContactsContract.CommonDataKinds.Email.ADDRESS),
                selection,
                if (onlyThese) wanted.map { it.toString() }.toTypedArray() else null,
                // The address marked as default comes first, so it is the one kept.
                "${ContactsContract.CommonDataKinds.Email.IS_SUPER_PRIMARY} DESC, ${ContactsContract.CommonDataKinds.Email.IS_PRIMARY} DESC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.CONTACT_ID)
                val addressIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.ADDRESS)
                while (cursor.moveToNext()) {
                    val address = cursor.getString(addressIndex)?.trim().orEmpty()
                    if (address.isNotEmpty()) emails.putIfAbsent(cursor.getLong(idIndex), address)
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("IntelligentSearch", "Contact emails unreadable", e)
        }
        if (emails.isEmpty()) return contacts
        return contacts.mapIndexed { i, contact -> emails[contactIds[i]]?.let { contact.copy(email = it) } ?: contact }
    }

    suspend fun getAllContacts(context: Context): List<ContactItem> = withContext(Dispatchers.IO) {
        val contacts = mutableListOf<ContactItem>()
        val contactIds = mutableListOf<Long>()
        val chatNumbers = ChatNumbers()
        if (context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return@withContext contacts
        }

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
            *ChatNumbers.COLUMNS
        )

        try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )?.use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val lookupKeyIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)
                val idIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val chatColumns = ChatNumbers.columnsOf(cursor)

                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIndex) ?: continue
                    val number = cursor.getString(numberIndex) ?: ""
                    val lookupKey = cursor.getString(lookupKeyIndex) ?: ""
                    val id = cursor.getLong(idIndex)

                    val lookupUri = ContactsContract.Contacts.getLookupUri(id, lookupKey)?.toString() ?: ""
                    contacts.add(ContactItem(name, number, lookupUri))
                    contactIds.add(id)
                    chatNumbers.offer(cursor, chatColumns, id, number)
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("IntelligentSearch", "Unexpected error", e)
        }
        withEmails(context, chatNumbers.applyTo(contacts, contactIds), contactIds, onlyThese = false)
            .distinctBy { it.lookupUri.ifEmpty { "${it.name}:${it.phoneNumber}" } }
    }

    suspend fun getAllRecentFiles(context: Context, limit: Int = 300, includeHidden: Boolean = false): List<FileItem> = withContext(Dispatchers.IO) {
        val files = mutableListOf<FileItem>()
        try {
            val safeLimit = limit.coerceAtLeast(1)
            val projection = arrayOf(
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DATE_MODIFIED
            )

            val queryArgs = android.os.Bundle().apply {
                putStringArray(
                    android.content.ContentResolver.QUERY_ARG_SORT_COLUMNS,
                    arrayOf(MediaStore.Files.FileColumns.DATE_MODIFIED)
                )
                putInt(
                    android.content.ContentResolver.QUERY_ARG_SORT_DIRECTION,
                    android.content.ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
                )
                putInt(
                    android.content.ContentResolver.QUERY_ARG_LIMIT,
                    if (includeHidden) safeLimit else safeLimit * 2
                )
            }

            context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                projection,
                queryArgs,
                null
            )?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val dataIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                val mimeIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
                val idIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
                val modifiedIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)

                while (cursor.moveToNext() && files.size < safeLimit) {
                    if (nameIndex != -1) {
                        val name = cursor.getString(nameIndex) ?: continue
                        if (!includeHidden && name.startsWith(".")) continue

                        val path = if (dataIndex != -1) cursor.getString(dataIndex) ?: "" else ""
                        val mimeType = if (mimeIndex != -1) cursor.getString(mimeIndex) ?: "*/*" else "*/*"
                        val id = if (idIndex != -1) cursor.getLong(idIndex) else 0L
                        val modifiedSec = if (modifiedIndex != -1) cursor.getLong(modifiedIndex) else 0L
                        val lastModifiedMs = if (modifiedSec > 0L) modifiedSec * 1000L else 0L
                        val uri = android.content.ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), id).toString()

                        files.add(FileItem(name, path, mimeType, uri, lastModifiedMs))
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("IntelligentSearch", "Unexpected error", e)
        }
        files
    }

    fun getAppActions(packageName: String): List<AppAction> {
        return when (packageName) {
            "com.google.android.deskclock",
            "com.sec.android.app.clockpackage",
            "com.android.deskclock",
            "com.coloros.alarm",
            "com.oneplus.deskclock",
            "com.motorola.blur.alarmclock" -> listOf(
                AppAction("Set Alarm", android.provider.AlarmClock.ACTION_SET_ALARM),
                AppAction("Start Timer", android.provider.AlarmClock.ACTION_SET_TIMER)
            )
            "com.android.vending" -> listOf(
                AppAction("My Apps", "com.google.android.finsky.VIEW_MY_DOWNLOADS")
            )
            "com.google.android.youtube" -> listOf(
                AppAction("Search", Intent.ACTION_SEARCH)
            )
            "com.google.android.apps.maps" -> listOf(
                AppAction("Navigate Home", Intent.ACTION_VIEW, "google.navigation:q=Home")
            )
            "com.google.android.calendar",
            "com.samsung.android.calendar" -> listOf(
                AppAction("New Event", Intent.ACTION_INSERT, "content://com.android.calendar/events")
            )
            else -> emptyList()
        }
    }
}

