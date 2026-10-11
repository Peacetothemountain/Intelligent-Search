package com.pixel.intelligentsearch.core.data
import android.app.usage.UsageStatsManager
import android.app.usage.UsageStats
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
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

@Immutable
data class CalendarEvent(
    val title: String,
    val startTime: String
)

@Immutable
data class ContactItem(
    val name: String,
    val phoneNumber: String,
    val lookupUri: String
)

@Immutable
data class FileItem(
    val name: String,
    val path: String,
    val mimeType: String,
    val uri: String
)

object SystemDataProvider {

    @Volatile
    private var cachedApps: List<AppItem>? = null

    @dagger.hilt.EntryPoint
    @dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
    interface MultiProfileEntryPoint {
        fun multiProfileManager(): com.pixel.intelligentsearch.core.profile.MultiProfileManager
    }

    /** Uses the Hilt singleton so only one LauncherApps callback and profile cache exist per process. */
    private fun getMultiProfileManager(context: Context): com.pixel.intelligentsearch.core.profile.MultiProfileManager =
        dagger.hilt.android.EntryPointAccessors
            .fromApplication(context.applicationContext, MultiProfileEntryPoint::class.java)
            .multiProfileManager()

    fun invalidateAppsCache() {
        cachedApps = null
    }

    suspend fun getAllApps(context: Context, forceRefresh: Boolean = false): List<AppItem> = withContext(Dispatchers.IO) {
        if (!forceRefresh && cachedApps != null) {
            cachedApps?.let { return@withContext it }
        }

        // 1. Universal PackageManager query guarantees all installed apps on any device/OEM are found
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
            cachedApps?.let { return@withContext it }
            emptyList()
        }
        val baseApps = resolveInfos.asSequence()
            .distinctBy { it.activityInfo.packageName }
            .mapNotNull {
                try {
                    val label = it.loadLabel(pm).toString()
                    val packageName = it.activityInfo.packageName
                    val icon = it.loadIcon(pm)
                    AppItem(
                        name = label,
                        packageName = packageName,
                        icon = icon,
                        actions = getAppActions(packageName),
                        activityName = it.activityInfo.name
                    )
                } catch (e: Throwable) {
                    null
                }
            }
            .toList()

        // 2. Discover multi-profile apps (Work Profile, Private Space, Dual Apps) and merge
        val mergedMap = LinkedHashMap<String, AppItem>()
        for (app in baseApps) {
            mergedMap[app.packageName] = app
        }

        try {
            val multiProfileManager = getMultiProfileManager(context)
            val profileApps = multiProfileManager.getAllProfileApps(forceRefresh)
            for (app in profileApps) {
                val key = if (app.profileType != ProfileType.PERSONAL) "${app.packageName}_${app.userHandle}" else app.packageName
                mergedMap[key] = app
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) {
            // Non-fatal, base apps remain intact
        }

        val finalApps = mergedMap.values.sortedBy { it.name.lowercase() }
        cachedApps = finalApps
        finalApps
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
        val sortedStats = stats.filter { it.totalTimeInForeground > 0 && !hiddenApps.contains(it.packageName) }
            .sortedByDescending { it.lastTimeUsed }

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
                CalendarContract.Instances.BEGIN
            )

            context.contentResolver.query(
                builder.build(),
                projection,
                null,
                null,
                "${CalendarContract.Instances.BEGIN} ASC"
            )?.use { cursor ->
                val titleIndex = cursor.getColumnIndex(CalendarContract.Instances.TITLE)
                val dtStartIndex = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
                // Honours the user's 12/24-hour preference and locale instead of always printing AM/PM.
                val timeFormat = android.text.format.DateFormat.getTimeFormat(context)

                while (cursor.moveToNext() && events.size < 3) { // Max 3 events
                    val title = if (titleIndex >= 0) cursor.getString(titleIndex) else null
                    val startTimeMillis = if (dtStartIndex >= 0) cursor.getLong(dtStartIndex) else 0L
                    val timeString = timeFormat.format(java.util.Date(startTimeMillis))

                    events.add(CalendarEvent(title ?: "Event", timeString))
                }
            }
        } catch (_: Throwable) {}
        events
    }

    suspend fun getContacts(context: Context, query: String): List<ContactItem> = withContext(Dispatchers.IO) {
        val contacts = mutableListOf<ContactItem>()
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
                    }
                }
            }
        } catch (_: Throwable) {}

        if (contacts.isEmpty()) {
            try {
                val all = getAllContacts(context)
                val q = query.trim()
                val qDigits = q.filter { it.isDigit() }
                return@withContext all.filter { c ->
                    c.name.contains(q, ignoreCase = true) ||
                    (qDigits.length >= 3 && c.phoneNumber.filter { it.isDigit() }.contains(qDigits))
                }.take(15)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Throwable) {}
        }

        contacts.distinctBy { it.phoneNumber }
    }

    suspend fun getAllContacts(context: Context): List<ContactItem> = withContext(Dispatchers.IO) {
        val contacts = mutableListOf<ContactItem>()
        if (context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return@withContext contacts
        }

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY
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

                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIndex) ?: continue
                    val number = cursor.getString(numberIndex) ?: ""
                    val lookupKey = cursor.getString(lookupKeyIndex) ?: ""
                    val id = cursor.getLong(idIndex)

                    val lookupUri = ContactsContract.Contacts.getLookupUri(id, lookupKey)?.toString() ?: ""
                    contacts.add(ContactItem(name, number, lookupUri))
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("IntelligentSearch", "Unexpected error", e)
        }
        contacts.distinctBy { it.phoneNumber }
    }

    suspend fun getAllRecentFiles(context: Context, limit: Int = 300, includeHidden: Boolean = false): List<FileItem> = withContext(Dispatchers.IO) {
        val files = mutableListOf<FileItem>()
        try {
            val projection = arrayOf(
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns._ID
            )

            context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                projection,
                null,
                null,
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val dataIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                val mimeIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
                val idIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)

                while (cursor.moveToNext() && files.size < limit) {
                    if (nameIndex != -1) {
                        val name = cursor.getString(nameIndex) ?: continue
                        if (!includeHidden && name.startsWith(".")) continue

                        val path = if (dataIndex != -1) cursor.getString(dataIndex) ?: "" else ""
                        val mimeType = if (mimeIndex != -1) cursor.getString(mimeIndex) ?: "*/*" else "*/*"
                        val id = if (idIndex != -1) cursor.getLong(idIndex) else 0L
                        val uri = android.content.ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), id).toString()

                        files.add(FileItem(name, path, mimeType, uri))
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("IntelligentSearch", "Unexpected error", e)
        }
        files
    }

    suspend fun getFiles(context: Context, query: String, includeHidden: Boolean): List<FileItem> = withContext(Dispatchers.IO) {
        val files = mutableListOf<FileItem>()
        if (query.isBlank()) return@withContext files

        try {
            val projection = arrayOf(
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns._ID
            )

            // Escape LIKE wildcards so a query such as "50%" or "my_file" matches literally.
            val escaped = query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
            val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ? ESCAPE '\\'"
            val selectionArgs = arrayOf("%$escaped%")

            context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val dataIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                val mimeIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
                val idIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)

                while (cursor.moveToNext() && files.size < 15) {
                    if (nameIndex != -1) {
                        val name = cursor.getString(nameIndex) ?: continue
                        if (!includeHidden && name.startsWith(".")) continue
                        
                        val path = if (dataIndex != -1) cursor.getString(dataIndex) ?: "" else ""
                        val mimeType = if (mimeIndex != -1) cursor.getString(mimeIndex) ?: "*/*" else "*/*"
                        val id = if (idIndex != -1) cursor.getLong(idIndex) else 0L
                        val uri = android.content.ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), id).toString()
                        
                        files.add(FileItem(name, path, mimeType, uri))
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

