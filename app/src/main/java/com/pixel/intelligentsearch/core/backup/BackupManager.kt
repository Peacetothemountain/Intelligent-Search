package com.pixel.intelligentsearch.core.backup

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.pixel.intelligentsearch.core.bangs.SearchBangManager
import com.pixel.intelligentsearch.core.data.HistoryDao
import com.pixel.intelligentsearch.core.data.HistoryEntity
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.security.BiometricSearchGate
import com.pixel.intelligentsearch.core.security.StrongBoxSecurityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsManager: SettingsManager,
    private val historyDao: HistoryDao,
    private val searchBangManager: SearchBangManager,
    private val biometricGate: BiometricSearchGate,
    private val strongBoxSecurityManager: StrongBoxSecurityManager
) {

    companion object {
        private const val HARWARE_BACKUP_KEY_ALIAS = "com.pixel.intelligentsearch.hardware_backup_kek"
    }

    suspend fun createBackupPayload(): BackupContentPayload = withContext(Dispatchers.IO) {
        val currentSettings = settingsManager.settingsFlow.first()
        val historyList = historyDao.getSearchHistory().map { it.query }
        val customBangs = searchBangManager.parseCustomBangs(currentSettings.customBangsJson)

        // 1. DataStore settings map for backward compatibility
        val prefsMap = mutableMapOf<String, String>()
        prefsMap["theme"] = currentSettings.theme
        prefsMap["searchApps"] = currentSettings.searchApps.toString()
        prefsMap["searchContacts"] = currentSettings.searchContacts.toString()
        prefsMap["searchFiles"] = currentSettings.searchFiles.toString()
        prefsMap["searchWeb"] = currentSettings.searchWeb.toString()
        prefsMap["searchCalculator"] = currentSettings.searchCalculator.toString()
        prefsMap["searchCalendar"] = currentSettings.searchCalendar.toString()
        prefsMap["searchShortcuts"] = currentSettings.searchShortcuts.toString()
        prefsMap["backgroundBlur"] = currentSettings.backgroundBlur.toString()
        prefsMap["showWallpaper"] = currentSettings.showWallpaper.toString()
        prefsMap["backgroundTransparency"] = currentSettings.backgroundTransparency.toString()
        prefsMap["pillOpacity"] = currentSettings.pillOpacity.toString()
        prefsMap["searchEngine"] = currentSettings.searchEngine
        prefsMap["customSearchEngineUrl"] = currentSettings.customSearchEngineUrl
        prefsMap["filesHiddenFiles"] = currentSettings.filesHiddenFiles.toString()
        prefsMap["filesThumbnails"] = currentSettings.filesThumbnails.toString()
        prefsMap["appAnimations"] = currentSettings.appAnimations.toString()
        prefsMap["bottomSearch"] = currentSettings.bottomSearch.toString()
        prefsMap["bottomSearchResult"] = currentSettings.bottomSearchResult.toString()
        prefsMap["searchPills"] = currentSettings.searchPills
        prefsMap["appQuickLaunch"] = currentSettings.appQuickLaunch.toString()
        prefsMap["contactDirectCall"] = currentSettings.contactDirectCall.toString()
        prefsMap["shortcutInline"] = currentSettings.shortcutInline.toString()
        prefsMap["appFuzzySearch"] = currentSettings.appFuzzySearch.toString()
        prefsMap["quickSearchHorizontal"] = currentSettings.quickSearchHorizontal.toString()
        prefsMap["webResultsCount"] = currentSettings.webResultsCount.toString()
        prefsMap["contactResultsCount"] = currentSettings.contactResultsCount.toString()
        prefsMap["fileResultsCount"] = currentSettings.fileResultsCount.toString()
        prefsMap["shortcutResultsCount"] = currentSettings.shortcutResultsCount.toString()
        prefsMap["activeIconPack"] = currentSettings.activeIconPack
        prefsMap["customIconPills"] = currentSettings.customIconPills
        prefsMap["searchPreviousSearches"] = currentSettings.searchPreviousSearches.toString()
        prefsMap["adaptiveIconShape"] = currentSettings.adaptiveIconShape
        prefsMap["dynamicIconMasking"] = currentSettings.dynamicIconMasking.toString()

        // 2. Comprehensive capture of ALL customizations from PREFERENCES_CUSTOMISATIONS with type fidelity
        val sharedPrefs = context.getSharedPreferences("PREFERENCES_CUSTOMISATIONS", Context.MODE_PRIVATE)
        val allCustomPrefs = sharedPrefs.all
        val spJsonObj = JSONObject()
        for ((k, v) in allCustomPrefs) {
            if (v == null) continue
            val itemObj = JSONObject()
            when (v) {
                is Boolean -> {
                    itemObj.put("type", "BOOLEAN")
                    itemObj.put("value", v)
                }
                is Int -> {
                    itemObj.put("type", "INT")
                    itemObj.put("value", v)
                }
                is Long -> {
                    itemObj.put("type", "LONG")
                    itemObj.put("value", v)
                }
                is Float -> {
                    itemObj.put("type", "FLOAT")
                    itemObj.put("value", v.toDouble())
                }
                is String -> {
                    itemObj.put("type", "STRING")
                    itemObj.put("value", v)
                }
                is Set<*> -> {
                    itemObj.put("type", "STRING_SET")
                    val arr = JSONArray()
                    v.forEach { item -> if (item is String) arr.put(item) }
                    itemObj.put("value", arr)
                }
            }
            spJsonObj.put(k, itemObj)
        }

        val currentVersionCode = try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionCode
            }
        } catch (_: Throwable) { 96 }

        BackupContentPayload(
            schemaVersion = 2,
            exportTimestampMs = System.currentTimeMillis(),
            appVersionCode = currentVersionCode,
            preferencesMap = prefsMap,
            customBangsJson = currentSettings.customBangsJson,
            sectionConfigsJson = currentSettings.searchSectionsConfigJson,
            searchHistory = historyList,
            hiddenApps = currentSettings.hiddenApps.toList(),
            sharedPreferencesJson = spJsonObj.toString()
        )
    }

    suspend fun exportToFile(
        activity: Activity,
        uri: Uri,
        passphrase: String?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val runExport: suspend () -> Unit = {
            try {
                val payload = createBackupPayload()
                val plainJson = serializePayload(payload)

                val envelope = if (!passphrase.isNullOrBlank()) {
                    val salt = BackupCryptoEngine.generateRandomSalt()
                    val iv = BackupCryptoEngine.generateRandomIv()
                    val key = BackupCryptoEngine.deriveKeyFromPassphrase(passphrase.toCharArray(), salt)
                    val cipherBytes = BackupCryptoEngine.encryptPayload(plainJson, key, iv)
                    val sha = BackupCryptoEngine.calculateSha256(cipherBytes)

                    EncryptedBackupEnvelope(
                        isHardwareBacked = false,
                        kdf = KdfMetadata(saltBase64 = BackupCryptoEngine.encodeBase64(salt)),
                        cipher = CipherMetadata(ivBase64 = BackupCryptoEngine.encodeBase64(iv)),
                        encryptedPayloadBase64 = BackupCryptoEngine.encodeBase64(cipherBytes),
                        payloadSha256 = sha
                    )
                } else {
                    val (key, _) = strongBoxSecurityManager.getOrCreateSymmetricKey(HARWARE_BACKUP_KEY_ALIAS)
                    val iv = BackupCryptoEngine.generateRandomIv()
                    val cipherBytes = BackupCryptoEngine.encryptPayload(plainJson, key, iv)
                    val sha = BackupCryptoEngine.calculateSha256(cipherBytes)

                    EncryptedBackupEnvelope(
                        isHardwareBacked = true,
                        kdf = null,
                        cipher = CipherMetadata(ivBase64 = BackupCryptoEngine.encodeBase64(iv)),
                        encryptedPayloadBase64 = BackupCryptoEngine.encodeBase64(cipherBytes),
                        payloadSha256 = sha
                    )
                }

                val envelopeJson = serializeEnvelope(envelope)
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(envelopeJson.toByteArray(Charsets.UTF_8))
                    os.flush()
                }
                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    onError(e.localizedMessage ?: "Failed to export backup")
                }
            }
        }

        if (biometricGate.isDeviceSecure()) {
            biometricGate.authenticate(
                activity = activity,
                title = "Verify Identity to Export Backup",
                subtitle = "Biometric authentication required to protect sensitive data",
                onSuccess = {
                    CoroutineScope(Dispatchers.IO).launch { runExport() }
                },
                onError = { _, err ->
                    CoroutineScope(Dispatchers.Main).launch { onError("Authentication failed: $err") }
                },
                onCancel = {
                    CoroutineScope(Dispatchers.Main).launch { onError("Export cancelled by user") }
                }
            )
        } else {
            withContext(Dispatchers.IO) {
                runExport()
            }
        }
    }

    fun inspectBackupEnvelope(uri: Uri): Result<EncryptedBackupEnvelope> {
        return try {
            val envelopeJson = context.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader().readText()
            } ?: return Result.failure(IllegalArgumentException("Could not read backup file"))
            Result.success(parseEnvelope(envelopeJson))
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    suspend fun importFromFile(
        activity: Activity,
        uri: Uri,
        passphrase: String?,
        onSuccess: (itemsRestored: Int) -> Unit,
        onError: (String) -> Unit
    ) {
        val runImport: suspend () -> Unit = {
            try {
                val envelopeJson = context.contentResolver.openInputStream(uri)?.use { input ->
                    input.bufferedReader().readText()
                } ?: throw IllegalArgumentException("Could not read backup file")

                val envelope = parseEnvelope(envelopeJson)

                // Verify integrity
                val cipherBytes = BackupCryptoEngine.decodeBase64(envelope.encryptedPayloadBase64)
                val calculatedSha = BackupCryptoEngine.calculateSha256(cipherBytes)
                if (calculatedSha != envelope.payloadSha256) {
                    throw SecurityException("Backup integrity verification failed (SHA-256 checksum mismatch). File may be corrupted.")
                }

                val iv = BackupCryptoEngine.decodeBase64(envelope.cipher.ivBase64)
                val decryptedJson: String = if (envelope.isHardwareBacked) {
                    try {
                        val (key, _) = strongBoxSecurityManager.getOrCreateSymmetricKey(HARWARE_BACKUP_KEY_ALIAS)
                        BackupCryptoEngine.decryptPayload(cipherBytes, key, iv)
                    } catch (e: Throwable) {
                        if (!passphrase.isNullOrBlank() && envelope.kdf != null) {
                            val salt = BackupCryptoEngine.decodeBase64(envelope.kdf.saltBase64)
                            val key = BackupCryptoEngine.deriveKeyFromPassphrase(passphrase.toCharArray(), salt)
                            BackupCryptoEngine.decryptPayload(cipherBytes, key, iv)
                        } else {
                            throw SecurityException("Device KeyStore key unavailable. This backup was bound to hardware keys that were reset. Please use a password-protected backup for cross-device/reinstall restoration.")
                        }
                    }
                } else {
                    if (passphrase.isNullOrBlank()) {
                        throw IllegalArgumentException("Passphrase required to decrypt this backup")
                    }
                    val saltBase64 = envelope.kdf?.saltBase64 ?: throw IllegalArgumentException("Missing salt in backup")
                    val salt = BackupCryptoEngine.decodeBase64(saltBase64)
                    val key = BackupCryptoEngine.deriveKeyFromPassphrase(passphrase.toCharArray(), salt)
                    try {
                        BackupCryptoEngine.decryptPayload(cipherBytes, key, iv)
                    } catch (e: javax.crypto.AEADBadTagException) {
                        throw SecurityException("Incorrect passphrase. Authentication tag mismatch.")
                    } catch (e: Throwable) {
                        throw SecurityException("Failed to decrypt: ${e.message ?: "Invalid passphrase"}")
                    }
                }

                val payload = parsePayload(decryptedJson)
                val restoredCount = restorePayload(payload)
                withContext(Dispatchers.Main) {
                    onSuccess(restoredCount)
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    onError(e.localizedMessage ?: "Failed to restore backup")
                }
            }
        }

        if (biometricGate.isDeviceSecure()) {
            biometricGate.authenticate(
                activity = activity,
                title = "Verify Identity to Restore Backup",
                subtitle = "Biometric authentication required to restore data",
                onSuccess = {
                    CoroutineScope(Dispatchers.IO).launch { runImport() }
                },
                onError = { _, err ->
                    CoroutineScope(Dispatchers.Main).launch { onError("Authentication failed: $err") }
                },
                onCancel = {
                    CoroutineScope(Dispatchers.Main).launch { onError("Import cancelled by user") }
                }
            )
        } else {
            withContext(Dispatchers.IO) {
                runImport()
            }
        }
    }

    private suspend fun restorePayload(payload: BackupContentPayload): Int = withContext(Dispatchers.IO) {
        var restoredCount = 0
        val sharedPrefs = context.getSharedPreferences("PREFERENCES_CUSTOMISATIONS", Context.MODE_PRIVATE)

        // 1. Restore all rich typed preferences from sharedPreferencesJson (Schema 2+)
        if (payload.sharedPreferencesJson.isNotBlank() && payload.sharedPreferencesJson != "{}") {
            try {
                val spObj = JSONObject(payload.sharedPreferencesJson)
                val editor = sharedPrefs.edit()
                val keys = spObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val itemObj = spObj.getJSONObject(key)
                    val type = itemObj.optString("type")
                    when (type) {
                        "BOOLEAN" -> editor.putBoolean(key, itemObj.getBoolean("value"))
                        "INT" -> editor.putInt(key, itemObj.getInt("value"))
                        "LONG" -> editor.putLong(key, itemObj.getLong("value"))
                        "FLOAT" -> editor.putFloat(key, itemObj.getDouble("value").toFloat())
                        "STRING" -> editor.putString(key, itemObj.getString("value"))
                        "STRING_SET" -> {
                            val arr = itemObj.getJSONArray("value")
                            val set = mutableSetOf<String>()
                            for (i in 0 until arr.length()) {
                                set.add(arr.getString(i))
                            }
                            editor.putStringSet(key, set)
                        }
                    }
                    restoredCount++
                }
                editor.commit()
            } catch (e: Throwable) {
                android.util.Log.w("BackupManager", "Failed to restore typed SharedPreferences", e)
            }
        }

        // 2. Restore DataStore preferences & fallback to SharedPreferences for Schema 1 backups
        val editor = sharedPrefs.edit()
        payload.preferencesMap.forEach { (k, v) ->
            when (k) {
                "theme" -> {
                    settingsManager.updateSetting(SettingsManager.THEME, v)
                    editor.putString("theme", v)
                    restoredCount++
                }
                "searchApps" -> {
                    val b = v.toBoolean()
                    settingsManager.updateSetting(SettingsManager.SEARCH_APPS, b)
                    editor.putBoolean("search.apps", b)
                    restoredCount++
                }
                "searchContacts" -> {
                    val b = v.toBoolean()
                    settingsManager.updateSetting(SettingsManager.SEARCH_CONTACTS, b)
                    editor.putBoolean("search.contacts", b)
                    restoredCount++
                }
                "searchFiles" -> {
                    val b = v.toBoolean()
                    settingsManager.updateSetting(SettingsManager.SEARCH_FILES, b)
                    editor.putBoolean("search.files", b)
                    restoredCount++
                }
                "searchWeb" -> {
                    val b = v.toBoolean()
                    settingsManager.updateSetting(SettingsManager.SEARCH_WEB, b)
                    editor.putBoolean("search.web", b)
                    restoredCount++
                }
                "searchCalculator" -> {
                    val b = v.toBoolean()
                    settingsManager.updateSetting(SettingsManager.SEARCH_CALCULATOR, b)
                    editor.putBoolean("search.calculator", b)
                    restoredCount++
                }
                "searchCalendar" -> {
                    val b = v.toBoolean()
                    settingsManager.updateSetting(SettingsManager.SEARCH_CALENDAR, b)
                    editor.putBoolean("search.calendar", b)
                    restoredCount++
                }
                "searchShortcuts" -> {
                    val b = v.toBoolean()
                    settingsManager.updateSetting(SettingsManager.SEARCH_SHORTCUTS, b)
                    editor.putBoolean("search.shortcuts", b)
                    restoredCount++
                }
                "backgroundBlur" -> {
                    val intVal = v.toIntOrNull() ?: 50
                    settingsManager.updateSetting(SettingsManager.BACKGROUND_BLUR, intVal)
                    editor.putInt("background.blur", intVal)
                    restoredCount++
                }
                "showWallpaper" -> {
                    val b = v.toBoolean()
                    settingsManager.updateSetting(SettingsManager.SHOW_WALLPAPER, b)
                    editor.putBoolean("show.wallpaper", b)
                    restoredCount++
                }
                "backgroundTransparency" -> {
                    val intVal = v.toIntOrNull() ?: 50
                    settingsManager.updateSetting(SettingsManager.BACKGROUND_TRANSPARENCY, intVal)
                    editor.putInt("background.transparency", intVal)
                    restoredCount++
                }
                "pillOpacity" -> {
                    val intVal = v.toIntOrNull() ?: 50
                    settingsManager.updateSetting(SettingsManager.PILL_OPACITY, intVal)
                    editor.putInt("pill.opacity", intVal)
                    restoredCount++
                }
                "searchEngine" -> {
                    settingsManager.updateSetting(SettingsManager.SEARCH_ENGINE, v)
                    editor.putString("search.engine", v)
                    restoredCount++
                }
                "customSearchEngineUrl" -> {
                    settingsManager.updateSetting(SettingsManager.CUSTOM_SEARCH_ENGINE_URL, v)
                    editor.putString("custom_search_engine_url", v)
                    restoredCount++
                }
                "filesHiddenFiles" -> {
                    val b = v.toBoolean()
                    settingsManager.updateSetting(SettingsManager.FILES_HIDDEN_FILES, b)
                    editor.putBoolean("files.hidden.files", b)
                    restoredCount++
                }
                "filesThumbnails" -> {
                    val b = v.toBoolean()
                    settingsManager.updateSetting(SettingsManager.FILES_THUMBNAILS, b)
                    editor.putBoolean("files.thumbnails", b)
                    restoredCount++
                }
                "appAnimations" -> {
                    val b = v.toBoolean()
                    settingsManager.updateSetting(SettingsManager.APP_ANIMATIONS, b)
                    editor.putBoolean("app.animations", b)
                    restoredCount++
                }
                "bottomSearch" -> {
                    val b = v.toBoolean()
                    settingsManager.updateSetting(SettingsManager.BOTTOM_SEARCH, b)
                    editor.putBoolean("settings.bottom.search", b)
                    restoredCount++
                }
                "bottomSearchResult" -> {
                    val b = v.toBoolean()
                    settingsManager.updateSetting(SettingsManager.BOTTOM_SEARCH_RESULT, b)
                    editor.putBoolean("settings.bottom.search.result", b)
                    restoredCount++
                }
                "searchPills" -> {
                    settingsManager.updateSetting(SettingsManager.SEARCH_PILLS, v)
                    editor.putString("search.pills", v)
                    restoredCount++
                }
                "appQuickLaunch" -> {
                    val b = v.toBoolean()
                    settingsManager.updateSetting(SettingsManager.APP_QUICK_LAUNCH, b)
                    editor.putBoolean("app.quick.launch", b)
                    restoredCount++
                }
                "contactDirectCall" -> {
                    val b = v.toBoolean()
                    settingsManager.updateSetting(SettingsManager.CONTACT_DIRECT_CALL, b)
                    editor.putBoolean("contact.direct.call", b)
                    restoredCount++
                }
                "shortcutInline" -> {
                    val b = v.toBoolean()
                    settingsManager.updateSetting(SettingsManager.SHORTCUT_INLINE, b)
                    editor.putBoolean("shortcut.inline", b)
                    restoredCount++
                }
                "appFuzzySearch" -> {
                    val b = v.toBoolean()
                    settingsManager.updateSetting(SettingsManager.APP_FUZZY_SEARCH, b)
                    editor.putBoolean("app.fuzzy.search", b)
                    restoredCount++
                }
                "quickSearchHorizontal" -> {
                    val b = v.toBoolean()
                    settingsManager.updateSetting(SettingsManager.QUICK_SEARCH_HORIZONTAL, b)
                    editor.putBoolean("quick.search.horizontal", b)
                    restoredCount++
                }
                "webResultsCount" -> {
                    val intVal = v.toIntOrNull() ?: 5
                    settingsManager.updateSetting(SettingsManager.WEB_RESULTS_COUNT, intVal)
                    editor.putInt("web.results.count", intVal)
                    restoredCount++
                }
                "contactResultsCount" -> {
                    val intVal = v.toIntOrNull() ?: 5
                    settingsManager.updateSetting(SettingsManager.CONTACT_RESULTS_COUNT, intVal)
                    editor.putInt("contact.results.count", intVal)
                    restoredCount++
                }
                "fileResultsCount" -> {
                    val intVal = v.toIntOrNull() ?: 5
                    settingsManager.updateSetting(SettingsManager.FILE_RESULTS_COUNT, intVal)
                    editor.putInt("file.results.count", intVal)
                    restoredCount++
                }
                "shortcutResultsCount" -> {
                    val intVal = v.toIntOrNull() ?: 6
                    settingsManager.updateSetting(SettingsManager.SHORTCUT_RESULTS_COUNT, intVal)
                    editor.putInt("shortcut.results.count", intVal)
                    restoredCount++
                }
                "activeIconPack" -> {
                    settingsManager.updateSetting(SettingsManager.ACTIVE_ICON_PACK, v)
                    editor.putString("active_icon_pack", v)
                    restoredCount++
                }
                "customIconPills" -> {
                    settingsManager.updateSetting(SettingsManager.CUSTOM_ICON_PILLS, v)
                    editor.putString("custom_icon_pills", v)
                    restoredCount++
                }
                "searchPreviousSearches" -> {
                    val b = v.toBoolean()
                    settingsManager.updateSetting(SettingsManager.SEARCH_PREVIOUS_SEARCHES, b)
                    editor.putBoolean("search_previous_searches", b)
                    restoredCount++
                }
                "adaptiveIconShape" -> {
                    settingsManager.updateSetting(SettingsManager.ADAPTIVE_ICON_SHAPE, v)
                    editor.putString("adaptive_icon_shape", v)
                    restoredCount++
                }
                "dynamicIconMasking" -> {
                    val b = v.toBoolean()
                    settingsManager.updateSetting(SettingsManager.DYNAMIC_ICON_MASKING, b)
                    editor.putBoolean("dynamic_icon_masking", b)
                    restoredCount++
                }
            }
        }
        editor.commit()

        // 3. Restore Custom Bangs & Section Configs
        if (payload.customBangsJson.isNotBlank() && payload.customBangsJson != "[]") {
            settingsManager.updateSetting(SettingsManager.CUSTOM_BANGS_JSON, payload.customBangsJson)
            restoredCount++
        }
        if (payload.sectionConfigsJson.isNotBlank() && payload.sectionConfigsJson != "[]") {
            settingsManager.updateSetting(SettingsManager.SEARCH_SECTIONS_CONFIG_JSON, payload.sectionConfigsJson)
            restoredCount++
        }
        if (payload.hiddenApps.isNotEmpty()) {
            settingsManager.updateSetting(SettingsManager.HIDDEN_APPS, payload.hiddenApps.toSet())
            restoredCount += payload.hiddenApps.size
        }

        // 4. Restore Search History
        if (payload.searchHistory.isNotEmpty()) {
            payload.searchHistory.forEach { q ->
                historyDao.insertSearch(HistoryEntity(query = q, timestamp = System.currentTimeMillis()))
                restoredCount++
            }
        }

        // 5. Update Home Screen Widgets immediately
        try {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, com.pixel.intelligentsearch.feature.widget.SearchWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(component)
            if (appWidgetIds != null && appWidgetIds.isNotEmpty()) {
                val updateIntent = Intent(context, com.pixel.intelligentsearch.feature.widget.SearchWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                }
                context.sendBroadcast(updateIntent)
            }
        } catch (_: Throwable) {}

        try {
            com.pixel.intelligentsearch.core.util.IconPackManager.clearCache()
        } catch (_: Throwable) {}

        restoredCount
    }

    private fun serializePayload(payload: BackupContentPayload): String {
        val root = JSONObject()
        root.put("schemaVersion", payload.schemaVersion)
        root.put("exportTimestampMs", payload.exportTimestampMs)
        root.put("appVersionCode", payload.appVersionCode)

        val prefsObj = JSONObject()
        payload.preferencesMap.forEach { (k, v) -> prefsObj.put(k, v) }
        root.put("preferencesMap", prefsObj)

        root.put("customBangsJson", payload.customBangsJson)
        root.put("sectionConfigsJson", payload.sectionConfigsJson)

        val historyArray = JSONArray()
        payload.searchHistory.forEach { historyArray.put(it) }
        root.put("searchHistory", historyArray)

        val hiddenArray = JSONArray()
        payload.hiddenApps.forEach { hiddenArray.put(it) }
        root.put("hiddenApps", hiddenArray)

        root.put("sharedPreferencesJson", payload.sharedPreferencesJson)

        return root.toString()
    }

    private fun parsePayload(jsonString: String): BackupContentPayload {
        val root = JSONObject(jsonString)
        val prefsObj = root.optJSONObject("preferencesMap")
        val prefsMap = mutableMapOf<String, String>()
        prefsObj?.keys()?.forEach { k ->
            prefsMap[k] = prefsObj.getString(k)
        }

        val historyArray = root.optJSONArray("searchHistory")
        val history = mutableListOf<String>()
        if (historyArray != null) {
            for (i in 0 until historyArray.length()) {
                history.add(historyArray.getString(i))
            }
        }

        val hiddenArray = root.optJSONArray("hiddenApps")
        val hidden = mutableListOf<String>()
        if (hiddenArray != null) {
            for (i in 0 until hiddenArray.length()) {
                hidden.add(hiddenArray.getString(i))
            }
        }

        return BackupContentPayload(
            schemaVersion = root.optInt("schemaVersion", 2),
            exportTimestampMs = root.optLong("exportTimestampMs", System.currentTimeMillis()),
            appVersionCode = root.optInt("appVersionCode", 96),
            preferencesMap = prefsMap,
            customBangsJson = root.optString("customBangsJson", "[]"),
            sectionConfigsJson = root.optString("sectionConfigsJson", "[]"),
            searchHistory = history,
            hiddenApps = hidden,
            sharedPreferencesJson = root.optString("sharedPreferencesJson", "{}")
        )
    }

    private fun serializeEnvelope(envelope: EncryptedBackupEnvelope): String {
        val root = JSONObject()
        root.put("format", envelope.format)
        root.put("schemaVersion", envelope.schemaVersion)
        root.put("timestampMs", envelope.timestampMs)
        root.put("isHardwareBacked", envelope.isHardwareBacked)

        if (envelope.kdf != null) {
            val kdfObj = JSONObject().apply {
                put("algorithm", envelope.kdf.algorithm)
                put("iterations", envelope.kdf.iterations)
                put("saltBase64", envelope.kdf.saltBase64)
                put("keyLengthBits", envelope.kdf.keyLengthBits)
            }
            root.put("kdf", kdfObj)
        }

        val cipherObj = JSONObject().apply {
            put("algorithm", envelope.cipher.algorithm)
            put("ivBase64", envelope.cipher.ivBase64)
            put("tagLengthBits", envelope.cipher.tagLengthBits)
        }
        root.put("cipher", cipherObj)
        root.put("encryptedPayloadBase64", envelope.encryptedPayloadBase64)
        root.put("payloadSha256", envelope.payloadSha256)

        return root.toString(2)
    }

    private fun parseEnvelope(jsonString: String): EncryptedBackupEnvelope {
        val root = JSONObject(jsonString)
        val kdfObj = root.optJSONObject("kdf")
        val kdf = if (kdfObj != null) {
            KdfMetadata(
                algorithm = kdfObj.optString("algorithm", "PBKDF2WithHmacSHA256"),
                iterations = kdfObj.optInt("iterations", 65536),
                saltBase64 = kdfObj.getString("saltBase64"),
                keyLengthBits = kdfObj.optInt("keyLengthBits", 256)
            )
        } else null

        val cipherObj = root.getJSONObject("cipher")
        val cipher = CipherMetadata(
            algorithm = cipherObj.optString("algorithm", "AES/GCM/NoPadding"),
            ivBase64 = cipherObj.getString("ivBase64"),
            tagLengthBits = cipherObj.optInt("tagLengthBits", 128)
        )

        return EncryptedBackupEnvelope(
            format = root.optString("format", "INTELLIGENT_SEARCH_ENCRYPTED_BACKUP"),
            schemaVersion = root.optInt("schemaVersion", 1),
            timestampMs = root.optLong("timestampMs", System.currentTimeMillis()),
            isHardwareBacked = root.optBoolean("isHardwareBacked", false),
            kdf = kdf,
            cipher = cipher,
            encryptedPayloadBase64 = root.getString("encryptedPayloadBase64"),
            payloadSha256 = root.getString("payloadSha256")
        )
    }
}
