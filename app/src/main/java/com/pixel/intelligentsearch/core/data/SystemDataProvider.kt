package com.pixel.intelligentsearch.core.data
import android.app.usage.UsageStatsManager
import android.app.usage.UsageStats
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

data class AppAction(
    val title: String,
    val action: String,
    val dataUri: String? = null
)

data class AppItem(
    val name: String,
    val packageName: String,
    val icon: Drawable,
    val actions: List<AppAction> = emptyList()
)

data class CalendarEvent(
    val title: String,
    val startTime: String
)

data class ContactItem(
    val name: String,
    val phoneNumber: String,
    val lookupUri: String
)

data class FileItem(
    val name: String,
    val path: String,
    val mimeType: String,
    val uri: String
)

object SystemDataProvider {

    val semanticTaxonomy = mapOf(
        "ride" to listOf("uber", "lyft", "grab", "waze", "maps"),
        "food" to listOf("eats", "doordash", "grubhub", "yelp", "pizza", "food"),
        "music" to listOf("spotify", "music", "shazam", "soundcloud", "pandora"),
        "video" to listOf("youtube", "netflix", "hulu", "disney", "prime video", "tiktok"),
        "social" to listOf("instagram", "facebook", "twitter", "x", "reddit", "snapchat", "tiktok"),
        "chat" to listOf("whatsapp", "messenger", "telegram", "discord", "signal", "messages"),
        "browser" to listOf("chrome", "firefox", "brave", "edge", "opera", "browser"),
        "shop" to listOf("amazon", "ebay", "target", "walmart", "vending")
    )

    suspend fun getAllApps(context: Context): List<AppItem> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
        resolveInfos.asSequence()
            .distinctBy { it.activityInfo.packageName }
            .map {
                val label = it.loadLabel(pm).toString()
                val packageName = it.activityInfo.packageName
                val icon = it.loadIcon(pm)
                AppItem(
                    name = label,
                    packageName = packageName,
                    icon = icon,
                    actions = getAppActions(packageName)
                )
            }
            .sortedBy { it.name.lowercase() }
            .toList()
    }

    suspend fun getRecentApps(context: Context): List<AppItem> = withContext(Dispatchers.IO) {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            time - 1000 * 60 * 60 * 24, // Last 24 hours
            time
        )

        val pm = context.packageManager
        val sortedStats = stats.filter { it.totalTimeInForeground > 0 }
            .sortedByDescending { it.lastTimeUsed }

        val recentApps = mutableListOf<AppItem>()
        for (stat in sortedStats) {
            if (recentApps.size >= 8) break // Max 8 recent apps
            try {
                // Check if it's a launchable app
                val intent = pm.getLaunchIntentForPackage(stat.packageName)
                if (intent != null) {
                    val appInfo = pm.getApplicationInfo(stat.packageName, 0)
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
            } catch (e: PackageManager.NameNotFoundException) {
                // Ignore
            }
        }
        
        if (recentApps.isEmpty()) {
            return@withContext getAllApps(context).take(8)
        }
        
        recentApps.distinctBy { it.packageName }
    }

        suspend fun getContextAwareQuickApps(context: Context): List<AppItem> = withContext(Dispatchers.IO) {
        val allApps = getAllApps(context)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        
        val suggestedPackages = when (hour) {
            in 6..11 -> listOf("com.google.android.calendar", "com.google.android.gm", "com.google.android.apps.magazines", "com.google.android.deskclock") // Morning
            in 12..17 -> listOf("com.slack", "com.google.android.apps.docs", "com.google.android.apps.maps", "com.linkedin.android") // Afternoon
            else -> listOf("com.google.android.youtube", "com.spotify.music", "com.netflix.mediaclient", "com.instagram.android") // Evening
        }
        
        val result = mutableListOf<AppItem>()
        for (pkg in suggestedPackages) {
            val app = allApps.find { it.packageName == pkg }
            if (app != null) result.add(app)
        }
        
        // Fill the rest with recent apps if not enough
        if (result.size < 6) {
            val recents = getRecentApps(context)
            for (recent in recents) {
                if (result.size >= 6) break
                if (result.none { it.packageName == recent.packageName }) {
                    result.add(recent)
                }
            }
        }
        
        result.take(6)
    }

    suspend fun getUpcomingEvents(context: Context): List<CalendarEvent> = withContext(Dispatchers.IO) {
        val events = mutableListOf<CalendarEvent>()
        if (context.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return@withContext events
        }

        val projection = arrayOf(
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART
        )

        val now = System.currentTimeMillis()
        val tomorrow = now + 1000 * 60 * 60 * 24
        
        val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
        val selectionArgs = arrayOf(now.toString(), tomorrow.toString())

        context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${CalendarContract.Events.DTSTART} ASC"
        )?.use { cursor ->
            val titleIndex = cursor.getColumnIndex(CalendarContract.Events.TITLE)
            val dtStartIndex = cursor.getColumnIndex(CalendarContract.Events.DTSTART)

            while (cursor.moveToNext() && events.size < 3) { // Max 3 events
                val title = cursor.getString(titleIndex)
                val startTimeMillis = cursor.getLong(dtStartIndex)
                
                val calendar = Calendar.getInstance().apply { timeInMillis = startTimeMillis }
                val hour = calendar.get(Calendar.HOUR_OF_DAY)
                val minute = calendar.get(Calendar.MINUTE)
                val amPm = if (hour < 12) "AM" else "PM"
                val displayHour = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
                val timeString = String.format(java.util.Locale.getDefault(), "%d:%02d %s", displayHour, minute, amPm)

                events.add(CalendarEvent(title ?: "Event", timeString))
            }
        }
        events
    }

    suspend fun getContacts(context: Context, query: String): List<ContactItem> = withContext(Dispatchers.IO) {
        val contacts = mutableListOf<ContactItem>()
        if (query.isEmpty() || context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return@withContext contacts
        }

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY
        )

        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$query%")

        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val lookupKeyIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)
            val idIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)

            while (cursor.moveToNext() && contacts.size < 10) {
                val name = cursor.getString(nameIndex)
                val number = cursor.getString(numberIndex)
                val lookupKey = cursor.getString(lookupKeyIndex)
                val id = cursor.getLong(idIndex)
                
                val lookupUri = ContactsContract.Contacts.getLookupUri(id, lookupKey).toString()
                
                contacts.add(ContactItem(name, number, lookupUri))
            }
        }
        contacts.distinctBy { it.phoneNumber }
    }

    suspend fun getFiles(context: Context, query: String, includeHidden: Boolean): List<FileItem> = withContext(Dispatchers.IO) {
        val files = mutableListOf<FileItem>()
        val hasStoragePermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(android.Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(android.Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else {
            context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

        if (query.isEmpty() || !hasStoragePermission) {
            return@withContext files
        }

        val projection = arrayOf(
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns._ID
        )

        val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$query%")

        // Querying MediaStore.Files includes everything indexed by the system (Docs, Images, Videos, Audio, Downloads)
        context.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            selection,
            selectionArgs,
            MediaStore.Files.FileColumns.DATE_MODIFIED + " DESC"
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val dataIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
            val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)

            while (cursor.moveToNext() && files.size < 15) {
                val name = cursor.getString(nameIndex) ?: continue
                
                // Skip hidden files if not enabled
                if (!includeHidden && name.startsWith(".")) continue
                
                val path = cursor.getString(dataIndex) ?: ""
                val mimeType = cursor.getString(mimeIndex) ?: "*/*"
                val id = cursor.getLong(idIndex)
                val uri = android.content.ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), id).toString()
                
                files.add(FileItem(name, path, mimeType, uri))
            }
        }
        files
    }

    fun evaluateMath(expression: String): String? {
        val sanitized = expression.replace(" ", "")
        if (sanitized.isBlank() || !sanitized.matches(Regex("^[0-9+\\-*/.()^]+$"))) return null
        
        return try {
            val result = eval(sanitized)
            if (result.isInfinite() || result.isNaN()) return null
            if (result % 1.0 == 0.0) {
                result.toLong().toString()
            } else {
                String.format(java.util.Locale.getDefault(), "%.2f", result)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun eval(str: String): Double {
        return object : Any() {
            var pos = -1
            var ch = 0

            fun nextChar() {
                ch = if (++pos < str.length) str[pos].code else -1
            }

            fun eat(charToEat: Int): Boolean {
                while (ch == ' '.code) nextChar()
                if (ch == charToEat) {
                    nextChar()
                    return true
                }
                return false
            }

            fun parse(): Double {
                nextChar()
                val x = parseExpression()
                if (pos < str.length) throw RuntimeException("Unexpected: " + ch.toChar())
                return x
            }

            fun parseExpression(): Double {
                var x = parseTerm()
                while (true) {
                    if (eat('+'.code)) x += parseTerm() // addition
                    else if (eat('-'.code)) x -= parseTerm() // subtraction
                    else return x
                }
            }

            fun parseTerm(): Double {
                var x = parseFactor()
                while (true) {
                    if (eat('*'.code)) x *= parseFactor() // multiplication
                    else if (eat('/'.code)) x /= parseFactor() // division
                    else return x
                }
            }

            fun parseFactor(): Double {
                if (eat('+'.code)) return parseFactor() // unary plus
                if (eat('-'.code)) return -parseFactor() // unary minus
                var x: Double
                val startPos = pos
                if (eat('('.code)) { // parentheses
                    x = parseExpression()
                    eat(')'.code)
                } else if (ch >= '0'.code && ch <= '9'.code || ch == '.'.code) { // numbers
                    while (ch >= '0'.code && ch <= '9'.code || ch == '.'.code) nextChar()
                    x = str.substring(startPos, pos).toDouble()
                } else {
                    throw RuntimeException("Unexpected: " + ch.toChar())
                }
                if (eat('^'.code)) x = Math.pow(x, parseFactor()) // exponentiation
                return x
            }
        }.parse()
    }

    fun evaluateUnitConversion(query: String): String? {
        val regex = Regex("^([0-9.]+)\\s*([a-zA-Z]+)\\s*(to|in)\\s*([a-zA-Z]+)$", RegexOption.IGNORE_CASE)
        val match = regex.find(query.trim()) ?: return null
        
        val value = match.groupValues[1].toDoubleOrNull() ?: return null
        val fromUnit = match.groupValues[2].lowercase()
        val toUnit = match.groupValues[4].lowercase()
        
        val lengths = mapOf("m" to 1.0, "km" to 1000.0, "cm" to 0.01, "mm" to 0.001, "mi" to 1609.34, "miles" to 1609.34, "yd" to 0.9144, "ft" to 0.3048, "in" to 0.0254)
        val weights = mapOf("g" to 1.0, "kg" to 1000.0, "mg" to 0.001, "lb" to 453.592, "lbs" to 453.592, "oz" to 28.3495)
        val currencies = mapOf("usd" to 1.0, "eur" to 1.09, "gbp" to 1.28, "jpy" to 0.0068, "cad" to 0.74, "aud" to 0.65) // Hardcoded approx rates for demo
        
        if (lengths.containsKey(fromUnit) && lengths.containsKey(toUnit)) {
            val baseValue = value * lengths[fromUnit]!!
            val result = baseValue / lengths[toUnit]!!
            return formatResult(result, toUnit)
        } else if (weights.containsKey(fromUnit) && weights.containsKey(toUnit)) {
            val baseValue = value * weights[fromUnit]!!
            val result = baseValue / weights[toUnit]!!
            return formatResult(result, toUnit)
        } else if (currencies.containsKey(fromUnit) && currencies.containsKey(toUnit)) {
            val baseValue = value * currencies[fromUnit]!!
            val result = baseValue / currencies[toUnit]!!
            return formatResult(result, toUnit)
        } else if (isTemperature(fromUnit) && isTemperature(toUnit)) {
            return formatResult(convertTemperature(value, fromUnit, toUnit), toUnit)
        }
        
        return null
    }
    
    private fun formatResult(result: Double, unit: String): String {
        val rounded = if (result % 1.0 == 0.0) result.toLong().toString() else String.format(java.util.Locale.getDefault(), "%.2f", result)
        return "$rounded $unit"
    }

    private fun isTemperature(unit: String): Boolean {
        return unit in listOf("c", "celsius", "f", "fahrenheit", "k", "kelvin")
    }

    private fun convertTemperature(value: Double, from: String, to: String): Double {
        val c = when (from) {
            "f", "fahrenheit" -> (value - 32) * 5/9
            "k", "kelvin" -> value - 273.15
            else -> value
        }
        return when (to) {
            "f", "fahrenheit" -> c * 9/5 + 32
            "k", "kelvin" -> c + 273.15
            else -> c
        }
    }

    fun getAppActions(packageName: String): List<AppAction> {
        return when (packageName) {
            "com.google.android.deskclock" -> listOf(
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
            else -> emptyList()
        }
    }
}

