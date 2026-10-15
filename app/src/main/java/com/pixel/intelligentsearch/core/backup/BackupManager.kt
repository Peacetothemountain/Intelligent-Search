package com.pixel.intelligentsearch.core.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import com.pixel.intelligentsearch.core.bangs.SearchBangManager
import com.pixel.intelligentsearch.core.data.HistoryDao
import com.pixel.intelligentsearch.core.data.HistoryEntity
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.security.StrongBoxSecurityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsManager: SettingsManager,
    private val historyDao: HistoryDao,
    private val searchBangManager: SearchBangManager,
    private val strongBoxSecurityManager: StrongBoxSecurityManager
) {

    companion object {
        private const val HARWARE_BACKUP_KEY_ALIAS = "com.pixel.intelligentsearch.hardware_backup_kek"
        private const val HISTORY_LIMIT = 25

        // Real backups are a few KB of settings text; anything near this is not one of ours.
        private const val MAX_BACKUP_BYTES = 4 * 1024 * 1024

        /** Backup preferencesMap names mapped to the DataStore keys they restore. */
        private val BOOLEAN_SETTING_KEYS = mapOf(
            "searchApps" to SettingsManager.SEARCH_APPS,
            "searchContacts" to SettingsManager.SEARCH_CONTACTS,
            "searchFiles" to SettingsManager.SEARCH_FILES,
            "searchWeb" to SettingsManager.SEARCH_WEB,
            "searchCalculator" to SettingsManager.SEARCH_CALCULATOR,
            "searchCalendar" to SettingsManager.SEARCH_CALENDAR,
            "searchShortcuts" to SettingsManager.SEARCH_SHORTCUTS,
            "showWallpaper" to SettingsManager.SHOW_WALLPAPER,
            "filesHiddenFiles" to SettingsManager.FILES_HIDDEN_FILES,
            "filesThumbnails" to SettingsManager.FILES_THUMBNAILS,
            "appAnimations" to SettingsManager.APP_ANIMATIONS,
            "bottomSearch" to SettingsManager.BOTTOM_SEARCH,
            "bottomSearchResult" to SettingsManager.BOTTOM_SEARCH_RESULT,
            "appQuickLaunch" to SettingsManager.APP_QUICK_LAUNCH,
            "contactDirectCall" to SettingsManager.CONTACT_DIRECT_CALL,
            "shortcutInline" to SettingsManager.SHORTCUT_INLINE,
            "appFuzzySearch" to SettingsManager.APP_FUZZY_SEARCH,
            "quickSearchHorizontal" to SettingsManager.QUICK_SEARCH_HORIZONTAL,
            "searchPreviousSearches" to SettingsManager.SEARCH_PREVIOUS_SEARCHES,
            "dynamicIconMasking" to SettingsManager.DYNAMIC_ICON_MASKING
        )
        private val INT_SETTING_KEYS = mapOf(
            "backgroundBlur" to SettingsManager.BACKGROUND_BLUR,
            "backgroundTransparency" to SettingsManager.BACKGROUND_TRANSPARENCY,
            "pillOpacity" to SettingsManager.PILL_OPACITY,
            "webResultsCount" to SettingsManager.WEB_RESULTS_COUNT,
            "contactResultsCount" to SettingsManager.CONTACT_RESULTS_COUNT,
            "fileResultsCount" to SettingsManager.FILE_RESULTS_COUNT,
            "shortcutResultsCount" to SettingsManager.SHORTCUT_RESULTS_COUNT
        )
        private val STRING_SETTING_KEYS = mapOf(
            "theme" to SettingsManager.THEME,
            "searchEngine" to SettingsManager.SEARCH_ENGINE,
            "customSearchEngineUrl" to SettingsManager.CUSTOM_SEARCH_ENGINE_URL,
            "searchPills" to SettingsManager.SEARCH_PILLS,
            "activeIconPack" to SettingsManager.ACTIVE_ICON_PACK,
            "customIconPills" to SettingsManager.CUSTOM_ICON_PILLS,
            "adaptiveIconShape" to SettingsManager.ADAPTIVE_ICON_SHAPE
        )

        private val EXPECTED_SHARED_PREF_TYPES = mapOf(
            "search.apps" to "BOOLEAN",
            "search_apps" to "BOOLEAN",
            "search.contacts" to "BOOLEAN",
            "search_contacts" to "BOOLEAN",
            "search.files" to "BOOLEAN",
            "search_files" to "BOOLEAN",
            "search.web" to "BOOLEAN",
            "search_web" to "BOOLEAN",
            "search_previous_searches" to "BOOLEAN",
            "search.calculator" to "BOOLEAN",
            "search_calculator" to "BOOLEAN",
            "search.calendar" to "BOOLEAN",
            "search_calendar" to "BOOLEAN",
            "search.shortcuts" to "BOOLEAN",
            "search_shortcuts" to "BOOLEAN",
            "search.background.show.wall" to "BOOLEAN",
            "show_wallpaper" to "BOOLEAN",
            "show.wallpaper" to "BOOLEAN",
            "search.files.hidden.files" to "BOOLEAN",
            "search_files_hidden_files" to "BOOLEAN",
            "search.files.thumbnails" to "BOOLEAN",
            "search_files_thumbnails" to "BOOLEAN",
            "app_animations" to "BOOLEAN",
            "settings.bottom.search" to "BOOLEAN",
            "bottom_search" to "BOOLEAN",
            "settings.bottom.search.result" to "BOOLEAN",
            "bottom_search_result" to "BOOLEAN",
            "tutorial_completed" to "BOOLEAN",
            "force_tutorial" to "BOOLEAN",
            "g_icon_enabled" to "BOOLEAN",
            "widget_show_voice" to "BOOLEAN",
            "widget_show_gemini" to "BOOLEAN",
            "quick_search_youtube" to "BOOLEAN",
            "quick_search_wikipedia" to "BOOLEAN",
            "quick_search_play_store" to "BOOLEAN",
            "quick_search_maps" to "BOOLEAN",
            "app_quick_launch" to "BOOLEAN",
            "contact_direct_call" to "BOOLEAN",
            "shortcut.inline" to "BOOLEAN",
            "shortcut_inline" to "BOOLEAN",
            "app.fuzzy.search" to "BOOLEAN",
            "app_fuzzy_search" to "BOOLEAN",
            "quick_search_horizontal" to "BOOLEAN",
            "quick.search.horizontal" to "BOOLEAN",
            "context_aware_quick_apps" to "BOOLEAN",
            "smart_clipboard_suggestions" to "BOOLEAN",
            "search_overlay_enabled" to "BOOLEAN",
            "never_show_icon_pack_warning" to "BOOLEAN",
            "dynamic_icon_masking" to "BOOLEAN",
            "diagnostics_overlay_enabled" to "BOOLEAN",
            "matrix_animation_enabled" to "BOOLEAN",
            "settings_back_to_search_overlay" to "BOOLEAN",
            "vibration_enabled" to "BOOLEAN",
            "search.background.blur" to "INT",
            "background.blur" to "INT",
            "search.background.transparency" to "INT",
            "background.transparency" to "INT",
            "search.pill.opacity" to "INT",
            "pill.opacity" to "INT",
            "tutorial_step" to "INT",
            "web_results_count" to "INT",
            "contact_results_count" to "INT",
            "file_results_count" to "INT",
            "shortcut_results_count" to "INT",
            "night.mode" to "STRING",
            "theme" to "STRING",
            "search.engine" to "STRING",
            "custom_search_engine_url" to "STRING",
            "search_pills" to "STRING",
            "search.pills" to "STRING",
            "widget.theme.style" to "STRING",
            "widget_theme_style" to "STRING",
            "active_icon_pack" to "STRING",
            "custom_icon_pills" to "STRING",
            "custom_bangs_json" to "STRING",
            "search_sections_config_json" to "STRING",
            "adaptive_icon_shape" to "STRING",
            "web_shortcut_trigger_symbol" to "STRING",
            "hidden_apps" to "STRING_SET",
            "disabled_web_shortcuts" to "STRING_SET"
        )

        private fun isBlockedInternalKey(key: String): Boolean {
            if (key.isBlank() || key.length > 128) return true
            return key.endsWith("_vault") ||
                key.endsWith("_iv") ||
                key.endsWith("_level") ||
                key.startsWith("saved_passphrase") ||
                key.startsWith("biometric_") ||
                key.startsWith("strongbox_") ||
                key == "blind_seed" ||
                key == "datastore_migration_v1_completed" ||
                key == "profile_1_restored_to_default_v918" ||
                key == "app_night_mode_override_cleared_v1"
        }

        private fun isValidCustomSearchUrl(url: String): Boolean {
            if (url.isBlank()) return true
            val trimmed = url.trim().lowercase(java.util.Locale.ROOT)
            return trimmed.startsWith("http://") || trimmed.startsWith("https://")
        }
    }

    suspend fun createBackupPayload(): BackupContentPayload = withContext(Dispatchers.IO) {
        val currentSettings = settingsManager.settingsFlow.first()
        val historyList = runCatching { historyDao.getSearchHistory().map { it.query } }.getOrElse { if (it is kotlinx.coroutines.CancellationException) throw it; emptyList<String>() }

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
            context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
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
        
        uri: Uri,
        passphrase: String?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                val payload = createBackupPayload()
                val plainJson = serializePayload(payload)

                val hwInfo = com.pixel.intelligentsearch.core.security.HardwareSecurityDetector.detect(context)

                val envelope = if (!passphrase.isNullOrBlank()) {
                    // Passphrase backups are encrypted in software from the passphrase so they restore on
                    // any phone; the device Keystore is not involved, so they are not hardware backed.
                    val salt = BackupCryptoEngine.generateRandomSalt()
                    val iv = BackupCryptoEngine.generateRandomIv()
                    val passChars = passphrase.toCharArray()
                    val key = try {
                        BackupCryptoEngine.deriveKeyFromPassphrase(passChars, salt)
                    } finally {
                        com.pixel.intelligentsearch.core.security.MemorySanitizer.wipe(passChars)
                    }
                    val cipherBytes = BackupCryptoEngine.encryptPayload(plainJson, key, iv)
                    val sha = BackupCryptoEngine.calculateSha256(cipherBytes)

                    EncryptedBackupEnvelope(
                        isHardwareBacked = false,
                        hardwareChip = hwInfo.chipName,
                        deviceModel = hwInfo.deviceDisplayName,
                        kdf = KdfMetadata(iterations = BackupCryptoEngine.PBKDF2_ITERATIONS, saltBase64 = BackupCryptoEngine.encodeBase64(salt)),
                        cipher = CipherMetadata(ivBase64 = BackupCryptoEngine.encodeBase64(iv)),
                        encryptedPayloadBase64 = BackupCryptoEngine.encodeBase64(cipherBytes),
                        payloadSha256 = sha
                    )
                } else {
                    // No passphrase: encrypt with a non-exportable AES-256 key generated inside this phone's
                    // Keystore (StrongBox when present, otherwise the TEE). The key never leaves the
                    // hardware, so the file only restores on this phone and this install. The old
                    // APK-embedded "portable" key is kept for restoring legacy files only.
                    val (key, keyLevel) = strongBoxSecurityManager.getOrCreateSymmetricKey(HARWARE_BACKUP_KEY_ALIAS)
                    // Keystore keys require randomized encryption and reject a caller-supplied IV, so the
                    // cipher generates the IV itself.
                    val (iv, cipherBytes) = BackupCryptoEngine.encryptPayloadWithGeneratedIv(plainJson, key)
                    val sha = BackupCryptoEngine.calculateSha256(cipherBytes)

                    // Record where the key actually landed: StrongBox can fall back to the TEE, and some
                    // keystores report software backing.
                    EncryptedBackupEnvelope(
                        isHardwareBacked = keyLevel != com.pixel.intelligentsearch.core.security.HardwareSecurityLevel.SOFTWARE,
                        hardwareChip = when (keyLevel) {
                            com.pixel.intelligentsearch.core.security.HardwareSecurityLevel.STRONGBOX -> hwInfo.chipName
                            com.pixel.intelligentsearch.core.security.HardwareSecurityLevel.TEE -> hwInfo.teeName
                            com.pixel.intelligentsearch.core.security.HardwareSecurityLevel.SOFTWARE -> "Android Keystore (software)"
                        },
                        deviceModel = hwInfo.deviceDisplayName,
                        kdf = null,
                        cipher = CipherMetadata(ivBase64 = BackupCryptoEngine.encodeBase64(iv)),
                        encryptedPayloadBase64 = BackupCryptoEngine.encodeBase64(cipherBytes),
                        payloadSha256 = sha
                    )
                }

                val envelopeJson = serializeEnvelope(envelope)
                val bytes = envelopeJson.toByteArray(Charsets.UTF_8)

                var writeSuccess = false
                // Method 1: Try openFileDescriptor with rwt / sync
                try {
                    val pfd = context.contentResolver.openFileDescriptor(uri, "rwt")
                        ?: context.contentResolver.openFileDescriptor(uri, "wt")
                        ?: context.contentResolver.openFileDescriptor(uri, "w")
                    if (pfd != null) {
                        pfd.use { fd ->
                            java.io.FileOutputStream(fd.fileDescriptor).let { fos ->
                                fos.write(bytes)
                                fos.flush()
                                try { fd.fileDescriptor.sync() } catch (_: Throwable) {}
                            }
                        }
                        writeSuccess = true
                    }
                } catch (e: Throwable) {
                    Log.w("BackupManager", "openFileDescriptor write failed, falling back to openOutputStream", e)
                }

                // Method 2: Fallback to openOutputStream if PFD failed
                if (!writeSuccess) {
                    val os = context.contentResolver.openOutputStream(uri, "wt")
                        ?: context.contentResolver.openOutputStream(uri, "w")
                        ?: throw IllegalArgumentException("Could not open destination file for writing")
                    os.use { stream ->
                        stream.write(bytes)
                        stream.flush()
                    }
                }

                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e; Log.e("BackupManager", "exportToFile failed", e)
                withContext(Dispatchers.Main) {
                    onError(e.localizedMessage ?: "Failed to export backup")
                }
            }
        }
    }

    /** Reads a user-picked file, refusing to buffer anything larger than [MAX_BACKUP_BYTES]. */
    private fun readBackupText(uri: Uri): String? =
        context.contentResolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
                if (out.size() > MAX_BACKUP_BYTES) {
                    throw IllegalArgumentException("Selected file is too large to be an Intelligent Search backup.")
                }
            }
            out.toString(Charsets.UTF_8.name())
        }

    fun inspectBackupEnvelope(uri: Uri): Result<EncryptedBackupEnvelope> {
        return try {
            val envelopeJson = readBackupText(uri)
                ?: return Result.failure(IllegalArgumentException("Could not read backup file"))
            if (envelopeJson.isBlank()) {
                return Result.failure(IllegalArgumentException("Selected backup file is empty (0 bytes). Please create a fresh backup."))
            }
            Result.success(parseEnvelope(envelopeJson))
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    suspend fun importFromFile(
        
        uri: Uri,
        passphrase: String?,
        onSuccess: (itemsRestored: Int) -> Unit,
        onError: (String) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                val envelopeJson = readBackupText(uri)
                    ?: throw IllegalArgumentException("Could not read backup file")

                if (envelopeJson.isBlank()) {
                    throw IllegalArgumentException("Selected backup file is empty (0 bytes). Please create a fresh backup.")
                }

                val envelope = parseEnvelope(envelopeJson)
                require(envelope.format == "INTELLIGENT_SEARCH_ENCRYPTED_BACKUP") {
                    "Unsupported backup format: ${envelope.format}"
                }
                require(envelope.cipher.tagLengthBits == 128) {
                    "Unsupported GCM authentication tag length: ${envelope.cipher.tagLengthBits}"
                }

                // Verify integrity
                val cipherBytes = BackupCryptoEngine.decodeBase64(envelope.encryptedPayloadBase64)
                val calculatedSha = BackupCryptoEngine.calculateSha256(cipherBytes)
                if (calculatedSha != envelope.payloadSha256) {
                    throw SecurityException("Backup integrity verification failed (SHA-256 checksum mismatch). File may be corrupted.")
                }

                val iv = BackupCryptoEngine.decodeBase64(envelope.cipher.ivBase64)
                require(iv.size == 12) { "Invalid backup IV length: ${iv.size}" }

                val decryptedJson: String = if (envelope.kdf != null) {
                    if (passphrase.isNullOrBlank()) {
                        throw IllegalArgumentException("Passphrase required to decrypt this backup")
                    }
                    val salt = BackupCryptoEngine.decodeBase64(envelope.kdf.saltBase64)
                    require(salt.size >= 16) { "Backup KDF salt is too short (${salt.size} bytes)" }
                    require(envelope.kdf.iterations in 10_000..5_000_000) {
                        "Backup KDF iteration count out of bounds: ${envelope.kdf.iterations}"
                    }
                    val passChars = passphrase.toCharArray()
                    val key = try {
                        BackupCryptoEngine.deriveKeyFromPassphrase(passChars, salt, envelope.kdf.iterations)
                    } finally {
                        com.pixel.intelligentsearch.core.security.MemorySanitizer.wipe(passChars)
                    }
                    try {
                        BackupCryptoEngine.decryptPayload(cipherBytes, key, iv)
                    } catch (e: javax.crypto.AEADBadTagException) {
                        throw SecurityException("Incorrect passphrase. Authentication tag mismatch.")
                    } catch (e: Throwable) {
                        throw SecurityException("Failed to decrypt: ${e.message ?: "Invalid passphrase"}")
                    }
                } else if (envelope.isHardwareBacked) {
                    try {
                        val (key, _) = strongBoxSecurityManager.getOrCreateSymmetricKey(HARWARE_BACKUP_KEY_ALIAS)
                        BackupCryptoEngine.decryptPayload(cipherBytes, key, iv)
                    } catch (e: Throwable) {
                        throw SecurityException("Device KeyStore key unavailable or backup integrity check failed.", e)
                    }
                } else {
                    // Legacy portable file (APK-embedded key, not secret); new exports never use this.
                    try {
                        val key = BackupCryptoEngine.getPortableDefaultKey()
                        BackupCryptoEngine.decryptPayload(cipherBytes, key, iv)
                    } catch (e: Throwable) {
                        throw SecurityException("Failed to decrypt backup. The file may be corrupt or encrypted with an incompatible key.")
                    }
                }

                val payload = parsePayload(decryptedJson)
                val restoredCount = restorePayload(payload)
                withContext(Dispatchers.Main) {
                    onSuccess(restoredCount)
                }
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e("BackupManager", "importFromFile failed", e)
                withContext(Dispatchers.Main) {
                    onError(e.localizedMessage ?: "Failed to restore backup")
                }
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
                    if (isBlockedInternalKey(key)) continue
                    runCatching {
                        val itemObj = spObj.getJSONObject(key)
                        val type = itemObj.optString("type")
                        val expectedType = EXPECTED_SHARED_PREF_TYPES[key]
                        if (expectedType != null && expectedType != type) {
                            return@runCatching
                        }
                        when (type) {
                            "BOOLEAN" -> editor.putBoolean(key, itemObj.getBoolean("value"))
                            "INT" -> editor.putInt(key, itemObj.getInt("value"))
                            "LONG" -> editor.putLong(key, itemObj.getLong("value"))
                            "FLOAT" -> {
                                val fVal = itemObj.getDouble("value").toFloat()
                                if (fVal.isFinite()) editor.putFloat(key, fVal) else return@runCatching
                            }
                            "STRING" -> {
                                val strVal = itemObj.getString("value")
                                if (key == "custom_search_engine_url" && !isValidCustomSearchUrl(strVal)) {
                                    return@runCatching
                                }
                                editor.putString(key, strVal)
                            }
                            "STRING_SET" -> {
                                val arr = itemObj.getJSONArray("value")
                                val set = mutableSetOf<String>()
                                for (i in 0 until arr.length()) {
                                    set.add(arr.getString(i))
                                }
                                editor.putStringSet(key, set)
                            }
                            else -> return@runCatching
                        }
                        restoredCount++
                    }
                }
                editor.commit()
            } catch (e: Throwable) {
                android.util.Log.w("BackupManager", "Failed to restore typed SharedPreferences", e)
            }
        }

        // Typed prefs only feed settingsFlow where DataStore has no value, so push every restored key
        // into DataStore as well; otherwise settings already customised on this device would win.
        settingsManager.syncSharedPreferencesToDataStore(overwrite = true)

        // 2. Restore the DataStore snapshot (the only settings source in schema 1 backups). updateSetting
        // writes DataStore plus every SharedPreferences spelling of the key.
        payload.preferencesMap.forEach { (k, v) ->
            val restored = BOOLEAN_SETTING_KEYS[k]?.let { key ->
                v.toBooleanStrictOrNull()?.let { settingsManager.updateSetting(key, it) }
            } ?: INT_SETTING_KEYS[k]?.let { key ->
                v.toIntOrNull()?.let { settingsManager.updateSetting(key, it) }
            } ?: STRING_SETTING_KEYS[k]?.let { key ->
                if (key == SettingsManager.CUSTOM_SEARCH_ENGINE_URL && !isValidCustomSearchUrl(v)) {
                    null
                } else {
                    settingsManager.updateSetting(key, v)
                }
            }
            if (restored != null) restoredCount++
        }

        // 3. Restore Custom Bangs & Section Configs (including empty "[]" or emptyList() so clean backups clear prior items)
        if (payload.customBangsJson.isNotBlank()) {
            settingsManager.updateSetting(SettingsManager.CUSTOM_BANGS_JSON, payload.customBangsJson)
            restoredCount++
        }
        settingsManager.updateSetting(SettingsManager.SEARCH_SECTIONS_CONFIG_JSON, payload.sectionConfigsJson)
        restoredCount++

        settingsManager.updateSetting(SettingsManager.HIDDEN_APPS, payload.hiddenApps.toSet())
        restoredCount += payload.hiddenApps.size

        // 4. Restore Search History
        if (payload.searchHistory.isNotEmpty()) {
            val now = System.currentTimeMillis()
            val entities = payload.searchHistory.mapIndexed { idx, q ->
                HistoryEntity(query = q, timestamp = now - idx * 1000L)
            }
            historyDao.insertSearches(entities)
            historyDao.pruneHistory(HISTORY_LIMIT)
            restoredCount += entities.size
        }

        // 5. Update Home Screen Widgets immediately
        try {
            com.pixel.intelligentsearch.feature.widget.SearchWidgetProvider.updateAllWidgets(context)
        } catch (e: Exception) {
            Log.w("BackupManager", "Widget refresh after restore failed", e)
        }

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
        if (envelope.hardwareChip != null) root.put("hardwareChip", envelope.hardwareChip)
        if (envelope.deviceModel != null) root.put("deviceModel", envelope.deviceModel)

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

        val hwChip = root.optString("hardwareChip").takeIf { it.isNotBlank() }
        val devModel = root.optString("deviceModel").takeIf { it.isNotBlank() }

        return EncryptedBackupEnvelope(
            format = root.optString("format", "INTELLIGENT_SEARCH_ENCRYPTED_BACKUP"),
            schemaVersion = root.optInt("schemaVersion", 1),
            timestampMs = root.optLong("timestampMs", System.currentTimeMillis()),
            isHardwareBacked = root.optBoolean("isHardwareBacked", false),
            hardwareChip = hwChip,
            deviceModel = devModel,
            kdf = kdf,
            cipher = cipher,
            encryptedPayloadBase64 = root.getString("encryptedPayloadBase64"),
            payloadSha256 = root.getString("payloadSha256")
        )
    }
}
