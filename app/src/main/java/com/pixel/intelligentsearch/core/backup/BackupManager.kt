package com.pixel.intelligentsearch.core.backup

import com.pixel.intelligentsearch.core.system.WidgetUpdater
import com.pixel.intelligentsearch.core.data.settings.StringSetSetting
import com.pixel.intelligentsearch.core.data.settings.StringSetting
import com.pixel.intelligentsearch.core.data.settings.IntSetting
import com.pixel.intelligentsearch.core.data.settings.BooleanSetting
import com.pixel.intelligentsearch.core.data.settings.SettingKey
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import com.pixel.intelligentsearch.core.currency.CurrencyRateRepository
import com.pixel.intelligentsearch.core.data.HistoryDao
import com.pixel.intelligentsearch.core.data.HistoryEntity
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.UserGrantedFiles
import com.pixel.intelligentsearch.core.data.widget.WidgetDesign
import com.pixel.intelligentsearch.core.data.widget.WidgetSettingsStore
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
    private val strongBoxSecurityManager: StrongBoxSecurityManager,
    private val widgetUpdater: WidgetUpdater,
    private val currencyRates: CurrencyRateRepository
) {
    /** Outside Hilt (tests): the currency schedule is kept by a repository of its own. */
    constructor(
        context: Context,
        settingsManager: SettingsManager,
        historyDao: HistoryDao,
        strongBoxSecurityManager: StrongBoxSecurityManager,
        widgetUpdater: WidgetUpdater
    ) : this(context, settingsManager, historyDao, strongBoxSecurityManager, widgetUpdater, CurrencyRateRepository(context, settingsManager))

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

        private fun isBlockedInternalKey(key: String): Boolean {
            if (key.isBlank() || key.length > 128) return true
            return key in UserGrantedFiles.DEVICE_LOCAL_KEYS ||
                key.endsWith("_vault") ||
                key.endsWith("_iv") ||
                key.endsWith("_level") ||
                key.startsWith("saved_passphrase") ||
                key.startsWith("biometric_") ||
                key.startsWith("strongbox_") ||
                key == "blind_seed" ||
                key == "datastore_migration_v1_completed" ||
                key == "profile_1_restored_to_default_v918" ||
                key == "app_night_mode_override_cleared_v1" ||
                key == SettingsManager.PILL_OPACITY_LINEAR_KEY ||
                key == SettingsManager.QUICK_PANEL_CAPS_CHOSEN_APPS_KEY ||
                key in SettingsManager.REMOVED_NAMES
        }

        /** The backup file's type label for a declared setting. */
        private fun sharedPrefTypeName(setting: SettingKey<*>): String = when (setting) {
            is BooleanSetting -> "BOOLEAN"
            is IntSetting -> "INT"
            is StringSetting -> "STRING"
            is StringSetSetting -> "STRING_SET"
        }

        private fun sharedPrefTypeName(value: Any): String? = when (value) {
            is Boolean -> "BOOLEAN"
            is Int -> "INT"
            is Long -> "LONG"
            is Float -> "FLOAT"
            is String -> "STRING"
            is Set<*> -> "STRING_SET"
            else -> null
        }

        /**
         * Opt-ins that share data or reach the network. A backup may switch them off but never on: a file someone
         * else made must not be able to start sharing contacts with assistants, sending clipboard text and keystrokes
         * out, reading notifications, or making requests the user turned off.
         */
        private val PRIVACY_OPT_IN_KEYS = setOf(
            SettingsManager.APP_FUNCTIONS_SHARE_CONTACTS.name,
            SettingsManager.SMART_CLIPBOARD_SUGGESTIONS.name,
            SettingsManager.SEARCH_WEB_SUGGESTIONS.name,
            SettingsManager.LIVE_CURRENCY_RATES.name,
            SettingsManager.SEARCH_NOTIFICATIONS.name
        )

        private fun isPrivacyOptInKey(key: String): Boolean =
            key in PRIVACY_OPT_IN_KEYS || SettingsManager.keyFor(key)?.name in PRIVACY_OPT_IN_KEYS

        /** Blank (no custom engine) or an address that passes the same checks as one typed in settings. */
        private fun isValidCustomSearchUrl(url: String): Boolean =
            url.isBlank() || com.pixel.intelligentsearch.core.security.CustomSearchUrl.validate(url) is
                com.pixel.intelligentsearch.core.security.CustomSearchUrl.Validation.Valid
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
        val sharedPrefs = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
        val allCustomPrefs = sharedPrefs.all
        val spJsonObj = JSONObject()
        for ((k, v) in allCustomPrefs) {
            if (v == null || k in UserGrantedFiles.DEVICE_LOCAL_KEYS) continue
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
            sectionConfigsJson = "[]", // retired feature; field kept for older readers
            searchHistory = historyList,
            hiddenApps = currentSettings.hiddenApps.toList(),
            sharedPreferencesJson = spJsonObj.toString()
        )
    }

    /** Encrypts the current settings, hidden apps and history and writes them to [uri]. */
    suspend fun exportToFile(uri: Uri, passphrase: String?): Result<Unit> = withContext(Dispatchers.IO) {
        backupResult("exportToFile") {
            val payload = createBackupPayload()
            val plainJson = serializePayload(payload)

            val hwInfo = com.pixel.intelligentsearch.core.security.HardwareSecurityDetector.detect(context)

            val envelope = if (!passphrase.isNullOrBlank()) {
                // Passphrase backups are encrypted in software from the passphrase so they restore on
                // any phone; the device Keystore is not involved, so they are not hardware backed.
                val salt = BackupCryptoEngine.generateRandomSalt()
                val passChars = passphrase.toCharArray()
                val key = try {
                    BackupCryptoEngine.deriveKeyFromPassphrase(passChars, salt)
                } finally {
                    com.pixel.intelligentsearch.core.security.MemorySanitizer.wipe(passChars)
                }
                passphraseEnvelope(plainJson, key, salt, hwInfo)
            } else {
                // No passphrase: encrypt with a non-exportable AES-256 key generated inside this phone's
                // Keystore (StrongBox when present, otherwise the TEE). The key never leaves the
                // hardware, so the file only restores on this phone and this install.
                val (key, keyLevel) = strongBoxSecurityManager.getOrCreateSymmetricKey(
                    HARWARE_BACKUP_KEY_ALIAS,
                    purgeIfUnrecoverable = false
                )
                // Keystore keys require randomized encryption and reject a caller-supplied IV, so the
                // cipher generates the IV itself.
                val (iv, cipherBytes) = BackupCryptoEngine.encryptPayloadWithGeneratedIv(plainJson, key)
                val sha = BackupCryptoEngine.calculateSha256(cipherBytes)

                // isHardwareBacked marks the file as encrypted with this device's Keystore key (import
                // relies on it); hardwareChip records where the key actually landed, since StrongBox can
                // fall back to the TEE and some keystores report software backing.
                EncryptedBackupEnvelope(
                    isHardwareBacked = true,
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

            writeEnvelope(uri, envelope)
        }
    }

    /**
     * Writes an automatic backup to [uri], encrypted with a key already derived from the user's passphrase with
     * [salt]. The file is the same as a passphrase export, so it restores on any phone with that passphrase.
     */
    suspend fun exportWithDerivedKey(uri: Uri, key: javax.crypto.SecretKey, salt: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        backupResult("exportWithDerivedKey") {
            val plainJson = serializePayload(createBackupPayload())
            val hwInfo = com.pixel.intelligentsearch.core.security.HardwareSecurityDetector.detect(context)
            writeEnvelope(uri, passphraseEnvelope(plainJson, key, salt, hwInfo))
        }
    }

    private fun passphraseEnvelope(
        plainJson: String,
        key: javax.crypto.SecretKey,
        salt: ByteArray,
        hwInfo: com.pixel.intelligentsearch.core.security.SecurityHardwareInfo
    ): EncryptedBackupEnvelope {
        val iv = BackupCryptoEngine.generateRandomIv()
        val cipherBytes = BackupCryptoEngine.encryptPayload(plainJson, key, iv)
        return EncryptedBackupEnvelope(
            isHardwareBacked = false,
            // Where this phone keeps its keys: a phone without StrongBox must not name a security chip it doesn't use.
            hardwareChip = hwInfo.keyHolderName,
            deviceModel = hwInfo.deviceDisplayName,
            kdf = KdfMetadata(iterations = BackupCryptoEngine.PBKDF2_ITERATIONS, saltBase64 = BackupCryptoEngine.encodeBase64(salt)),
            cipher = CipherMetadata(ivBase64 = BackupCryptoEngine.encodeBase64(iv)),
            encryptedPayloadBase64 = BackupCryptoEngine.encodeBase64(cipherBytes),
            payloadSha256 = BackupCryptoEngine.calculateSha256(cipherBytes)
        )
    }

    /** Serializes [envelope] and writes it to [uri], replacing anything already there. */
    private fun writeEnvelope(uri: Uri, envelope: EncryptedBackupEnvelope) {
        val bytes = serializeEnvelope(envelope).toByteArray(Charsets.UTF_8)

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

    }

    /** Runs [block], turning any failure other than cancellation into a logged [Result.failure]. */
    private inline fun <T> backupResult(operation: String, block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Throwable) {
        Log.e("BackupManager", "$operation failed: ${e.javaClass.simpleName}") // messages can contain the file URI
        Result.failure(e)
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

    /** Decrypts the backup at [uri], restores it, and returns how many items were restored. */
    /** Restores [uri]: a passphrase backup, or one locked to this phone's Keystore. */
    suspend fun importFromFile(uri: Uri, passphrase: String?): Result<Int> = withContext(Dispatchers.IO) {
        backupResult("importFromFile") {
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
                // Never create or purge the key here: a fresh key can't decrypt the file, and purging a
                // briefly unreadable one would orphan every device-bound backup.
                val key = try {
                    strongBoxSecurityManager.getExistingSymmetricKey(HARWARE_BACKUP_KEY_ALIAS)
                } catch (e: Throwable) {
                    throw SecurityException("This phone's backup key could not be read. Try again in a moment.", e)
                } ?: throw SecurityException(
                    "This backup is locked to the phone and install it was made on, and can't be restored here."
                )
                try {
                    BackupCryptoEngine.decryptPayload(cipherBytes, key, iv)
                } catch (e: Throwable) {
                    throw SecurityException("This backup could not be decrypted with this phone's key. It may be corrupt, or from another install.", e)
                }
            } else {
                // One short-lived build wrote device-key files with this flag off, so the device key is tried. Older
                // files made with a key built into the app are refused: anyone with the app could read or forge them.
                val deviceKey = runCatching {
                    strongBoxSecurityManager.getExistingSymmetricKey(HARWARE_BACKUP_KEY_ALIAS)
                }.getOrNull()
                deviceKey?.let { runCatching { BackupCryptoEngine.decryptPayload(cipherBytes, it, iv) }.getOrNull() }
                    ?: throw SecurityException(
                        "This backup was made by an older version of Intelligent Search and can't be restored. Make a new backup with a passphrase."
                    )
            }

            restorePayload(parsePayload(decryptedJson))
        }
    }

    private suspend fun restorePayload(payload: BackupContentPayload): Int = withContext(Dispatchers.IO) {
        var restoredCount = 0
        val sharedPrefs = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
        // Files saved before the pill opacity became linear hold 50 for an opaque pill (schema 1 files never say).
        val pillOpacityIsLinear = runCatching {
            JSONObject(payload.sharedPreferencesJson).optJSONObject(SettingsManager.PILL_OPACITY_LINEAR_KEY)
                ?.optBoolean("value") == true
        }.getOrDefault(false)
        // Files saved before Max Quick Panel Apps capped the chosen apps showed every one of them.
        val quickPanelCapsChosenApps = runCatching {
            JSONObject(payload.sharedPreferencesJson).optJSONObject(SettingsManager.QUICK_PANEL_CAPS_CHOSEN_APPS_KEY)
                ?.optBoolean("value") == true
        }.getOrDefault(false)
        fun restoredInt(setting: SettingKey<*>?, value: Int): Int =
            if (setting == SettingsManager.PILL_OPACITY && !pillOpacityIsLinear) SettingsManager.pillOpacityFromOldScale(value) else value

        // 1. Restore all rich typed preferences from sharedPreferencesJson (Schema 2+)
        if (payload.sharedPreferencesJson.isNotBlank() && payload.sharedPreferencesJson != "{}") {
            try {
                val spObj = JSONObject(payload.sharedPreferencesJson)
                require(spObj.length() <= BackupPreferenceSchema.MAX_KEYS) { "Backup has too many settings" }
                val editor = sharedPrefs.edit()
                val localValues = sharedPrefs.all
                var typedRestored = 0
                val keys = spObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (isBlockedInternalKey(key) || BackupPreferenceSchema.isDebugKey(key)) continue
                    // Older files carry several spellings of one setting: restore it under its canonical name,
                    // taking the canonical entry when the file has one.
                    val setting = SettingsManager.keyFor(key)
                    if (setting != null && key != setting.name && spObj.has(setting.name)) continue
                    val target = setting?.name ?: key
                    runCatching {
                        val itemObj = spObj.getJSONObject(key)
                        val type = itemObj.optString("type")
                        // Known keys (including per-shortcut custom actions and pill profiles) must carry the type
                        // the app reads them with. Keys the schema doesn't name must at least match what is stored here now.
                        val expectedType = setting?.let(::sharedPrefTypeName)
                            ?: BackupPreferenceSchema.expectedType(key)
                            ?: localValues[key]?.let { sharedPrefTypeName(it) }
                        if (expectedType != null && expectedType != type) {
                            return@runCatching
                        }
                        when (type) {
                            "BOOLEAN" -> {
                                val boolVal = itemObj.getBoolean("value")
                                if (boolVal && isPrivacyOptInKey(target)) return@runCatching
                                editor.putBoolean(target, boolVal)
                            }
                            "INT" -> {
                                val intVal = itemObj.getInt("value")
                                val range = (setting as? IntSetting)?.range ?: BackupPreferenceSchema.intRange(key)
                                if (range != null && intVal !in range) return@runCatching
                                editor.putInt(target, restoredInt(setting, intVal))
                            }
                            "LONG" -> editor.putLong(target, itemObj.getLong("value"))
                            "FLOAT" -> {
                                val fVal = itemObj.getDouble("value").toFloat()
                                if (fVal.isFinite()) editor.putFloat(target, fVal) else return@runCatching
                            }
                            "STRING" -> {
                                val strVal = itemObj.getString("value")
                                if (strVal.length > BackupPreferenceSchema.MAX_STRING_LENGTH) return@runCatching
                                if (target == SettingsManager.CUSTOM_SEARCH_ENGINE_URL.name && !isValidCustomSearchUrl(strVal)) {
                                    return@runCatching
                                }
                                editor.putString(target, strVal)
                            }
                            "STRING_SET" -> {
                                val arr = itemObj.getJSONArray("value")
                                if (arr.length() > BackupPreferenceSchema.MAX_STRING_SET_SIZE) return@runCatching
                                val set = mutableSetOf<String>()
                                for (i in 0 until arr.length()) {
                                    set.add(arr.getString(i))
                                }
                                editor.putStringSet(target, set)
                            }
                            else -> return@runCatching
                        }
                        // The restored canonical value replaces any old spelling still stored locally.
                        setting?.legacyNames?.forEach(editor::remove)
                        typedRestored++
                    }
                }
                // A widget design's colour sliders from a file older than the HCT seed are HSV, so they mustn't be
                // read as a seed because of a flag this phone set.
                for (design in WidgetDesign.entries) {
                    val flag = WidgetSettingsStore.hctFlagKey(design)
                    val restoresSliders = listOf("hue", "saturation", "lightness").any { spObj.has("${design.prefix}_custom_$it") }
                    if (restoresSliders && !spObj.has(flag)) editor.remove(flag)
                }
                if (editor.commit()) {
                    restoredCount += typedRestored
                } else {
                    // None of them reached storage, so none count as restored.
                    Log.w("BackupManager", "Restored typed SharedPreferences could not be saved")
                }
            } catch (e: Throwable) {
                android.util.Log.w("BackupManager", "Failed to restore typed SharedPreferences", e)
            }
        }

        // 2. Restore the DataStore snapshot (the only settings source in schema 1 backups). updateSetting
        // writes DataStore plus every SharedPreferences spelling of the key.
        payload.preferencesMap.forEach { (k, v) ->
            val restored = BOOLEAN_SETTING_KEYS[k]?.let { key ->
                v.toBooleanStrictOrNull()
                    ?.takeUnless { it && isPrivacyOptInKey(key.name) }
                    ?.let { settingsManager.updateSetting(key, it) }
            } ?: INT_SETTING_KEYS[k]?.let { key ->
                v.toIntOrNull()
                    ?.takeIf { value -> BackupPreferenceSchema.intRange(key.name)?.contains(value) ?: true }
                    ?.let { settingsManager.updateSetting(key, restoredInt(key, it)) }
            } ?: STRING_SETTING_KEYS[k]?.let { key ->
                if (key == SettingsManager.CUSTOM_SEARCH_ENGINE_URL && !isValidCustomSearchUrl(v)) {
                    null
                } else {
                    settingsManager.updateSetting(key, v)
                }
            }
            if (restored != null) restoredCount++
        }
        if (!quickPanelCapsChosenApps) {
            sharedPrefs.edit { SettingsManager.fitQuickPanelToChosenApps(sharedPrefs, this) }
        }

        // 3. Restore Custom Bangs & Section Configs (including empty "[]" or emptyList() so clean backups clear prior items)
        if (payload.customBangsJson.isNotBlank()) {
            settingsManager.updateSetting(SettingsManager.CUSTOM_BANGS_JSON, payload.customBangsJson)
            restoredCount++
        }

        settingsManager.updateSetting(SettingsManager.HIDDEN_APPS, payload.hiddenApps.toSet())
        restoredCount += payload.hiddenApps.size

        // Scheduled work lives outside the settings file: a restored Calculator, Live Currency Rates or search engine
        // setting (Tor) can mean the daily rates download must stop. Applied now the settings are in, so a failure
        // restoring the history below can't leave it out of step.
        currencyRates.syncSchedule()

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
            widgetUpdater.updateAll()
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
