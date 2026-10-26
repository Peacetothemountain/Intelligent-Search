package com.pixel.intelligentsearch.feature.settings

import com.pixel.intelligentsearch.core.data.settings.SettingKey
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixel.intelligentsearch.core.backup.BackupManager
import com.pixel.intelligentsearch.core.bangs.SearchBang
import com.pixel.intelligentsearch.core.bangs.SearchBangManager
import com.pixel.intelligentsearch.core.data.HistoryDao
import com.pixel.intelligentsearch.core.data.IntelligentSearchSettings
import com.pixel.intelligentsearch.core.data.SettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsManager: SettingsManager,
    private val historyDao: HistoryDao,
    private val bangManager: SearchBangManager,
    private val backupManager: BackupManager,
    private val strongBoxSecurityManager: com.pixel.intelligentsearch.core.security.StrongBoxSecurityManager,
    private val corpusIndexManager: com.pixel.intelligentsearch.core.search.CorpusIndexManager
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
            initialValue = emptyList()
        )


    fun <T : Any> updateSetting(key: SettingKey<T>, value: T) {
        settingsManager.set(key, value)
        if (key == SettingsManager.SEARCH_CONTACTS && value == true) {
            corpusIndexManager.reindexContacts()
        }
    }

    fun reindexContacts() {
        viewModelScope.launch(Dispatchers.IO) {
            corpusIndexManager.reindexContacts()
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            historyDao.clearHistory()
        }
    }

    // --- Search Bangs Operations ---
    // Each user action is one manager call, so its writes can't interleave with another action's.
    fun saveBang(bang: SearchBang, replacing: SearchBang? = null) {
        viewModelScope.launch(Dispatchers.IO) { bangManager.saveBang(bang, replacing) }
    }

    fun removeBangs(bangs: List<SearchBang>) {
        viewModelScope.launch(Dispatchers.IO) { bangManager.removeBangs(bangs) }
    }

    fun restoreBuiltInBang(prefix: String) {
        viewModelScope.launch(Dispatchers.IO) { bangManager.restoreBuiltInBang(prefix) }
    }

    // --- Backup & Restore Operations ---
    // BackupManager does its work on IO; the callbacks run on the main thread (viewModelScope).
    fun exportBackup(
        uri: Uri,
        passphrase: String?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            backupManager.exportToFile(uri, passphrase)
                .onSuccess { onSuccess() }
                .onFailure { onError(it.localizedMessage ?: "Failed to export backup") }
        }
    }

    fun importBackup(
        uri: Uri,
        passphrase: String?,
        onSuccess: (Int) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            backupManager.importFromFile(uri, passphrase)
                .onSuccess(onSuccess)
                .onFailure { onError(it.localizedMessage ?: "Failed to restore backup") }
        }
    }

    fun inspectBackupEnvelope(uri: Uri): Result<com.pixel.intelligentsearch.core.backup.EncryptedBackupEnvelope> {
        return backupManager.inspectBackupEnvelope(uri)
    }

    fun savePassphraseToSecurityChip(passphrase: String): com.pixel.intelligentsearch.core.security.StrongBoxSecurityManager.PassphraseSave {
        return strongBoxSecurityManager.savePassphrase(passphrase)
    }

    fun hasSavedPassphraseOnSecurityChip(): Boolean = strongBoxSecurityManager.hasSavedPassphrase()

    fun getSavedPassphraseFromSecurityChip(): String? {
        return strongBoxSecurityManager.getSavedPassphrase()
    }

    /** Deletes the saved passphrase and its Keystore key off the main thread (both are disk writes). */
    fun clearSavedPassphraseFromSecurityChip() {
        viewModelScope.launch(Dispatchers.IO) { strongBoxSecurityManager.clearSavedPassphrase() }
    }
}
