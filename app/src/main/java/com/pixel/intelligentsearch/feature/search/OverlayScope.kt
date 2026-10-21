package com.pixel.intelligentsearch.feature.search

import android.content.SharedPreferences
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.pixel.intelligentsearch.core.data.AppItem
import com.pixel.intelligentsearch.core.data.ContactItem
import com.pixel.intelligentsearch.core.data.FileItem
import com.pixel.intelligentsearch.core.data.IntelligentSearchSettings

/**
 * UI state the overlay's sections share for as long as the overlay is open: what the user has typed, which
 * popups are up, whether they dismissed the keyboard. The two values that survive the activity being recreated
 * are handed in as saveable state.
 */
internal class OverlayUiState(
    hasStartedTyping: MutableState<Boolean>,
    isKeyboardDismissedByUser: MutableState<Boolean>,
    initialQuery: String
) {
    var hasStartedTyping by hasStartedTyping
    var isKeyboardDismissedByUser by isKeyboardDismissedByUser
    var textFieldValue by mutableStateOf(TextFieldValue(initialQuery, TextRange(initialQuery.length)))
    var showDebugPill by mutableStateOf(false)
    var showHistoryClearedSnackbar by mutableStateOf(false)
    var showTorInstallPopup by mutableStateOf(false)
}

/**
 * What the overlay screen hands its sections (search bar, quick-app panel, results list): the current state and
 * results, the shared [ui] state, and the screen's actions. Built on every composition of the screen, so the
 * sections always see the values the screen just computed.
 */
internal class OverlayScope(
    val onOpenSettings: (String) -> Unit,
    val viewModel: SearchViewModel,
    val uiState: SearchUiState,
    val settingsState: IntelligentSearchSettings,
    val prefs: SharedPreferences,
    val ui: OverlayUiState,
    val showTutorial: Boolean,
    val searchProviderName: String,
    val appWeight: Int,
    val webWeight: Int,
    val contactWeight: Int,
    val fileWeight: Int,
    val suggestionsEnabled: Boolean,
    val focusRequester: FocusRequester,
    /** Voice search for the search bar's mic button and the assistant gesture. */
    val voice: OverlayVoice,
    val listState: LazyListState,
    /** Launches an app: package, profile (null = personal) and launcher activity if known. */
    val performLaunch: (String, android.os.UserHandle?, String?) -> Unit,
    val performAppLaunch: (AppItem) -> Unit,
    /** ActivityOptions for opening an app's shortcut, per the App Animations setting (null = system default). */
    val launchOptions: () -> android.os.Bundle?,
    val closeOverlay: () -> Unit,
    val launchWebSearch: (String) -> Unit,
    val openWebAddress: (String) -> Unit,
    /** "Go" for a query: Enter and the voice check button. The flag says it was spoken rather than typed. */
    val submitQuery: (query: String, fromVoice: Boolean) -> Unit,
    val visibleApps: List<AppItem>,
    val bestMatch: Any?,
    val bestMatchText: String?,
    val filteredContacts: List<ContactItem>,
    /** The Call, Message, Email and chat buttons this device can answer, for contact results. */
    val contactActionTargets: ContactActionTargets,
    val filteredApps: List<AppItem>,
    val filteredFiles: List<FileItem>,
    val distinctRecents: List<String>
)
