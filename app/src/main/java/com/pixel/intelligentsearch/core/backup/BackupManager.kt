package com.pixel.intelligentsearch.core.backup

import android.app.Activity
import android.content.Context
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
import java.io.InputStream
import java.io.OutputStream
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

        BackupContentPayload(
            schemaVersion = 1,
            exportTimestampMs = System.currentTimeMillis(),
            appVersionCode = 92,
            preferencesMap = prefsMap,
            customBangsJson = currentSettings.customBangsJson,
            sectionConfigsJson = currentSettings.searchSectionsConfigJson,
            searchHistory = historyList,
            hiddenApps = currentSettings.hiddenApps.toList()
        )
    }

    suspend fun exportToFile(
        activity: Activity,
        uri: Uri,
        passphrase: String?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val runExport: suspend () -> Unit = suspend {
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
                onSuccess()
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Failed to export backup")
            }
        }

        if (biometricGate.isDeviceSecure()) {
            biometricGate.authenticate(
                activity = activity,
                title = "Verify Identity to Export Backup",
                subtitle = "Biometric authentication required to protect sensitive data",
                onSuccess = {
                    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch { runExport() }
                },
                onError = { _, err -> onError("Authentication failed: $err") },
                onCancel = { onError("Export cancelled by user") }
            )
        } else {
            runExport()
        }
    }

    suspend fun importFromFile(
        activity: Activity,
        uri: Uri,
        passphrase: String?,
        onSuccess: (itemsRestored: Int) -> Unit,
        onError: (String) -> Unit
    ) {
        val runImport: suspend () -> Unit = suspend {
            try {
                val envelopeJson = context.contentResolver.openInputStream(uri)?.use { input ->
                    input.bufferedReader().readText()
                } ?: throw IllegalArgumentException("Could not read backup file")

                val envelope = parseEnvelope(envelopeJson)

                // Verify integrity
                val cipherBytes = BackupCryptoEngine.decodeBase64(envelope.encryptedPayloadBase64)
                val calculatedSha = BackupCryptoEngine.calculateSha256(cipherBytes)
                if (calculatedSha != envelope.payloadSha256) {
                    throw SecurityException("Backup integrity verification failed (SHA-256 mismatch)")
                }

                val iv = BackupCryptoEngine.decodeBase64(envelope.cipher.ivBase64)
                val decryptedJson: String = if (envelope.isHardwareBacked) {
                    val (key, _) = strongBoxSecurityManager.getOrCreateSymmetricKey(HARWARE_BACKUP_KEY_ALIAS)
                    BackupCryptoEngine.decryptPayload(cipherBytes, key, iv)
                } else {
                    if (passphrase.isNullOrBlank()) {
                        throw IllegalArgumentException("Passphrase required to decrypt this backup")
                    }
                    val saltBase64 = envelope.kdf?.saltBase64 ?: throw IllegalArgumentException("Missing salt in backup")
                    val salt = BackupCryptoEngine.decodeBase64(saltBase64)
                    val key = BackupCryptoEngine.deriveKeyFromPassphrase(passphrase.toCharArray(), salt)
                    BackupCryptoEngine.decryptPayload(cipherBytes, key, iv)
                }

                val payload = parsePayload(decryptedJson)
                restorePayload(payload)
                val count = payload.preferencesMap.size + payload.searchHistory.size
                onSuccess(count)
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Failed to restore backup")
            }
        }

        if (biometricGate.isDeviceSecure()) {
            biometricGate.authenticate(
                activity = activity,
                title = "Verify Identity to Restore Backup",
                subtitle = "Biometric authentication required to restore data",
                onSuccess = {
                    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch { runImport() }
                },
                onError = { _, err -> onError("Authentication failed: $err") },
                onCancel = { onError("Import cancelled by user") }
            )
        } else {
            runImport()
        }
    }

    private suspend fun restorePayload(payload: BackupContentPayload) = withContext(Dispatchers.IO) {
        // 1. Restore Preferences
        payload.preferencesMap.forEach { (k, v) ->
            when (k) {
                "theme" -> settingsManager.updateSetting(SettingsManager.THEME, v)
                "searchApps" -> settingsManager.updateSetting(SettingsManager.SEARCH_APPS, v.toBoolean())
                "searchContacts" -> settingsManager.updateSetting(SettingsManager.SEARCH_CONTACTS, v.toBoolean())
                "searchFiles" -> settingsManager.updateSetting(SettingsManager.SEARCH_FILES, v.toBoolean())
                "searchWeb" -> settingsManager.updateSetting(SettingsManager.SEARCH_WEB, v.toBoolean())
                "searchCalculator" -> settingsManager.updateSetting(SettingsManager.SEARCH_CALCULATOR, v.toBoolean())
                "searchCalendar" -> settingsManager.updateSetting(SettingsManager.SEARCH_CALENDAR, v.toBoolean())
                "searchShortcuts" -> settingsManager.updateSetting(SettingsManager.SEARCH_SHORTCUTS, v.toBoolean())
                "backgroundBlur" -> settingsManager.updateSetting(SettingsManager.BACKGROUND_BLUR, v.toIntOrNull() ?: 50)
                "showWallpaper" -> settingsManager.updateSetting(SettingsManager.SHOW_WALLPAPER, v.toBoolean())
                "backgroundTransparency" -> settingsManager.updateSetting(SettingsManager.BACKGROUND_TRANSPARENCY, v.toIntOrNull() ?: 50)
                "pillOpacity" -> settingsManager.updateSetting(SettingsManager.PILL_OPACITY, v.toIntOrNull() ?: 50)
                "searchEngine" -> settingsManager.updateSetting(SettingsManager.SEARCH_ENGINE, v)
                "customSearchEngineUrl" -> settingsManager.updateSetting(SettingsManager.CUSTOM_SEARCH_ENGINE_URL, v)
                "filesHiddenFiles" -> settingsManager.updateSetting(SettingsManager.FILES_HIDDEN_FILES, v.toBoolean())
                "filesThumbnails" -> settingsManager.updateSetting(SettingsManager.FILES_THUMBNAILS, v.toBoolean())
                "appAnimations" -> settingsManager.updateSetting(SettingsManager.APP_ANIMATIONS, v.toBoolean())
                "bottomSearch" -> settingsManager.updateSetting(SettingsManager.BOTTOM_SEARCH, v.toBoolean())
                "bottomSearchResult" -> settingsManager.updateSetting(SettingsManager.BOTTOM_SEARCH_RESULT, v.toBoolean())
                "searchPills" -> settingsManager.updateSetting(SettingsManager.SEARCH_PILLS, v)
                "appQuickLaunch" -> settingsManager.updateSetting(SettingsManager.APP_QUICK_LAUNCH, v.toBoolean())
                "contactDirectCall" -> settingsManager.updateSetting(SettingsManager.CONTACT_DIRECT_CALL, v.toBoolean())
                "shortcutInline" -> settingsManager.updateSetting(SettingsManager.SHORTCUT_INLINE, v.toBoolean())
                "appFuzzySearch" -> settingsManager.updateSetting(SettingsManager.APP_FUZZY_SEARCH, v.toBoolean())
                "quickSearchHorizontal" -> settingsManager.updateSetting(SettingsManager.QUICK_SEARCH_HORIZONTAL, v.toBoolean())
                "webResultsCount" -> settingsManager.updateSetting(SettingsManager.WEB_RESULTS_COUNT, v.toIntOrNull() ?: 5)
                "contactResultsCount" -> settingsManager.updateSetting(SettingsManager.CONTACT_RESULTS_COUNT, v.toIntOrNull() ?: 5)
                "fileResultsCount" -> settingsManager.updateSetting(SettingsManager.FILE_RESULTS_COUNT, v.toIntOrNull() ?: 5)
                "shortcutResultsCount" -> settingsManager.updateSetting(SettingsManager.SHORTCUT_RESULTS_COUNT, v.toIntOrNull() ?: 6)
                "activeIconPack" -> settingsManager.updateSetting(SettingsManager.ACTIVE_ICON_PACK, v)
                "customIconPills" -> settingsManager.updateSetting(SettingsManager.CUSTOM_ICON_PILLS, v)
                "searchPreviousSearches" -> settingsManager.updateSetting(SettingsManager.SEARCH_PREVIOUS_SEARCHES, v.toBoolean())
                "adaptiveIconShape" -> settingsManager.updateSetting(SettingsManager.ADAPTIVE_ICON_SHAPE, v)
                "dynamicIconMasking" -> settingsManager.updateSetting(SettingsManager.DYNAMIC_ICON_MASKING, v.toBoolean())
            }
        }

        // 2. Restore Custom Bangs & Section Configs
        if (payload.customBangsJson.isNotBlank()) {
            settingsManager.updateSetting(SettingsManager.CUSTOM_BANGS_JSON, payload.customBangsJson)
        }
        if (payload.sectionConfigsJson.isNotBlank()) {
            settingsManager.updateSetting(SettingsManager.SEARCH_SECTIONS_CONFIG_JSON, payload.sectionConfigsJson)
        }
        if (payload.hiddenApps.isNotEmpty()) {
            settingsManager.updateSetting(SettingsManager.HIDDEN_APPS, payload.hiddenApps.toSet())
        }

        // 3. Restore Search History
        if (payload.searchHistory.isNotEmpty()) {
            payload.searchHistory.forEach { q ->
                historyDao.insertSearch(HistoryEntity(query = q, timestamp = System.currentTimeMillis()))
            }
        }
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
            schemaVersion = root.optInt("schemaVersion", 1),
            exportTimestampMs = root.optLong("exportTimestampMs", System.currentTimeMillis()),
            appVersionCode = root.optInt("appVersionCode", 92),
            preferencesMap = prefsMap,
            customBangsJson = root.optString("customBangsJson", "[]"),
            sectionConfigsJson = root.optString("sectionConfigsJson", "[]"),
            searchHistory = history,
            hiddenApps = hidden
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
