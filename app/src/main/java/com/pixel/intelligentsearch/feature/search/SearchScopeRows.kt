package com.pixel.intelligentsearch.feature.search

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixel.intelligentsearch.core.search.SearchScope
import com.pixel.intelligentsearch.core.theme.GoogleSansFlex
import com.pixel.intelligentsearch.core.ui.icons.automirrored.outlined.InsertDriveFile
import com.pixel.intelligentsearch.core.ui.icons.outlined.Person
import com.pixel.intelligentsearch.core.ui.icons.outlined.Settings
import com.pixel.intelligentsearch.feature.settings.expressiveRowClickable

/*
 * A search limited to one kind of result by its first character ("@jo" for contacts, "#tax" for files, ">wifi" for
 * settings pages): the chip that says so and takes the limit off, and the one line shown when there are no results.
 */

/** The settings page the "search is off" line opens: where contact and file search are switched on. */
internal const val SEARCH_SOURCES_PAGE = "search_sources"

/** The icon on [scope]'s chip. */
internal fun searchScopeIcon(scope: SearchScope): ImageVector = when (scope) {
    SearchScope.CONTACTS -> Icons.Outlined.Person
    SearchScope.FILES -> Icons.AutoMirrored.Outlined.InsertDriveFile
    SearchScope.SETTINGS -> Icons.Outlined.Settings
}

/**
 * The line shown under the chip in place of results: [text], and whether tapping it [opensSearchSources] (it says
 * that kind of search is switched off).
 */
internal data class ScopeNotice(val text: String, val opensSearchSources: Boolean = false)

/**
 * What a search limited to one kind of result says when it has nothing to list, or null when it has results (or the
 * search isn't limited): that kind of search is off; what to type, with only the marker typed; or that nothing matches,
 * once the results for these words are in.
 */
internal fun scopeNotice(state: SearchUiState): ScopeNotice? {
    val scoped = state.scoped ?: return null
    val scope = scoped.scope
    val offMessage = scope.offMessage
    if (state.scopedSourceOff && offMessage != null) return ScopeNotice(offMessage, opensSearchSources = true)
    if (scoped.rest.isEmpty()) return ScopeNotice(scope.emptyHint)
    val found = when (scope) {
        SearchScope.CONTACTS -> state.contacts.isNotEmpty()
        SearchScope.FILES -> state.files.isNotEmpty()
        SearchScope.SETTINGS -> state.androidSettings.isNotEmpty()
    }
    return if (found || !state.scopedResultsIn) null else ScopeNotice(scope.noMatchMessage(scoped.rest))
}

/**
 * Takes the limit to one kind of result off, as tapping its chip does: the marker leaves the search field and the
 * words after it are searched as usual.
 */
internal fun removeSearchScope(s: OverlayScope) {
    val words = s.viewModel.uiState.value.scoped?.rest ?: return
    s.ui.textFieldValue = TextFieldValue(words, TextRange(words.length))
    s.viewModel.onQueryChanged(words)
}

/** The chip over a limited search's results ("Contacts only"); tapping it searches everything again. */
@Composable
internal fun SearchScopeChip(
    scope: SearchScope,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    /** The keyboard's highlight ring, drawn on the chip itself. */
    highlight: Modifier = Modifier
) = ResultFilterChip(
    label = scope.chipLabel,
    icon = searchScopeIcon(scope),
    description = "Showing only ${scope.noun}. Remove filter",
    onRemove = onRemove,
    modifier = modifier,
    highlight = highlight
)

/** The outline of the "search is off" line, which the keyboard's highlight ring follows. */
internal val ScopeNoticeShape = RoundedCornerShape(28.dp)

/**
 * [notice] as one quiet line, in the results' secondary text. The line that says a kind of search is off opens Search
 * Sources with [onOpenSearchSources]; the others only say something.
 */
@Composable
internal fun ScopeNoticeRow(
    notice: ScopeNotice,
    modifier: Modifier = Modifier,
    /** The keyboard's highlight ring, drawn on the line itself. */
    highlight: Modifier = Modifier,
    onOpenSearchSources: () -> Unit
) {
    Text(
        text = notice.text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 14.sp,
        fontFamily = GoogleSansFlex,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .then(
                if (notice.opensSearchSources) {
                    Modifier.clip(ScopeNoticeShape).then(highlight).expressiveRowClickable(onClick = onOpenSearchSources)
                } else Modifier
            )
            .padding(horizontal = 8.dp, vertical = 12.dp)
    )
}
