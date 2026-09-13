package com.pixel.intelligentsearch.feature.settings

import android.app.Activity
import android.net.Uri
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixel.intelligentsearch.core.backup.BackupManager
import com.pixel.intelligentsearch.core.bangs.SearchBang
import com.pixel.intelligentsearch.core.bangs.SearchBangManager
import com.pixel.intelligentsearch.core.data.HistoryDao
import com.pixel.intelligentsearch.core.data.IntelligentSearchSettings
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.icons.UniversalIconEngine
import com.pixel.intelligentsearch.core.weighting.SearchSectionConfig
import com.pixel.intelligentsearch.core.weighting.SearchWeightingDefaults
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsManager: SettingsManager,
    private val historyDao: HistoryDao,
    private val bangManager: SearchBangManager,
    private val backupManager: BackupManager,
    private val iconEngine: UniversalIconEngine,
    private val strongBoxSecurityManager: com.pixel.intelligentsearch.core.security.StrongBoxSecurityManager
) : ViewModel() {

    val settingsState: StateFlow<IntelligentSearchSettings> = settingsManager.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = settingsManager.getInitialSettings()
        )

    val bangsFlow: StateFlow<List<SearchBang>> = bangManager.bangsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = bangManager.getAllBangsSync()
        )

    val sectionConfigsFlow: StateFlow<List<SearchSectionConfig>> = settingsManager.settingsFlow
        .map { settings -> SearchWeightingDefaults.parseConfigs(settings.searchSectionsConfigJson) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SearchWeightingDefaults.parseConfigs(settingsManager.getInitialSettings().searchSectionsConfigJson)
        )

    fun <T> updateSetting(key: Preferences.Key<T>, value: T) {
        viewModelScope.launch {
            settingsManager.updateSetting(key, value)
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            historyDao.clearHistory()
        }
    }

    // --- Search Bangs Operations ---
    fun saveCustomBang(bang: SearchBang) {
        viewModelScope.launch(Dispatchers.IO) {
            bangManager.saveCustomBang(bang)
        }
    }

    fun deleteCustomBang(prefix: String) {
        viewModelScope.launch(Dispatchers.IO) {
            bangManager.deleteCustomBang(prefix)
        }
    }

    fun disableBuiltInBang(prefix: String) {
        viewModelScope.launch(Dispatchers.IO) {
            bangManager.disableBuiltInBang(prefix)
        }
    }

    fun enableBuiltInBang(prefix: String) {
        viewModelScope.launch(Dispatchers.IO) {
            bangManager.enableBuiltInBang(prefix)
        }
    }

    // --- Search Source Weighting Operations ---
    fun reorderSections(fromIndex: Int, toIndex: Int) {
        val current = sectionConfigsFlow.value
        val reordered = SearchWeightingDefaults.reorderConfigs(current, fromIndex, toIndex)
        val json = SearchWeightingDefaults.serializeConfigs(reordered)
        viewModelScope.launch(Dispatchers.IO) {
            settingsManager.updateSetting(SettingsManager.SEARCH_SECTIONS_CONFIG_JSON, json)
        }
    }

    fun updateSectionConfig(config: SearchSectionConfig) {
        val current = sectionConfigsFlow.value.toMutableList()
        val index = current.indexOfFirst { it.sectionType == config.sectionType }
        if (index != -1) {
            current[index] = config
            val json = SearchWeightingDefaults.serializeConfigs(current)
            viewModelScope.launch(Dispatchers.IO) {
                settingsManager.updateSetting(SettingsManager.SEARCH_SECTIONS_CONFIG_JSON, json)
            }
        }
    }

    fun resetSectionConfigsToDefault() {
        val defaultConfigs = SearchWeightingDefaults.createDefaultConfigs()
        val json = SearchWeightingDefaults.serializeConfigs(defaultConfigs)
        viewModelScope.launch(Dispatchers.IO) {
            settingsManager.updateSetting(SettingsManager.SEARCH_SECTIONS_CONFIG_JSON, json)
        }
    }

    // --- Backup & Restore Operations ---
    fun exportBackup(
        activity: Activity,
        uri: Uri,
        passphrase: String?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            backupManager.exportToFile(
                activity = activity,
                uri = uri,
                passphrase = passphrase,
                onSuccess = onSuccess,
                onError = onError
            )
        }
    }

    fun importBackup(
        activity: Activity,
        uri: Uri,
        passphrase: String?,
        onSuccess: (Int) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            backupManager.importFromFile(
                activity = activity,
                uri = uri,
                passphrase = passphrase,
                onSuccess = onSuccess,
                onError = onError
            )
        }
    }

    fun inspectBackupEnvelope(uri: Uri): Result<com.pixel.intelligentsearch.core.backup.EncryptedBackupEnvelope> {
        return backupManager.inspectBackupEnvelope(uri)
    }

    fun clearIconCaches() {
        iconEngine.clearCache()
        com.pixel.intelligentsearch.core.util.IconPackManager.clearCache()
    }

    fun savePassphraseToSecurityChip(passphrase: String): Boolean {
        return strongBoxSecurityManager.savePassphrase(passphrase)
    }

    fun getSavedPassphraseFromSecurityChip(): String? {
        return strongBoxSecurityManager.getSavedPassphrase()
    }

    fun clearSavedPassphraseFromSecurityChip(): Boolean {
        return strongBoxSecurityManager.clearSavedPassphrase()
    }
}
