package com.pixel.intelligentsearch.feature.search

import android.content.Context
import androidx.compose.ui.text.input.TextFieldValue
import com.pixel.intelligentsearch.core.data.settings.EnterKeyAction

/*
 * What Enter does in the search overlay: the keyboard's search key, a physical keyboard's Enter, and (from the
 * settled stage on) the voice check button. One set of rules, so every way of "going" agrees.
 */

/** What Enter does with the query. */
internal sealed interface EnterOutcome {
    /** The query is exactly a trigger word: opens the result it was set on. */
    data class OpenTriggered(val result: Any) : EnterOutcome

    /** Opens the result the arrow keys highlight. */
    data class OpenHighlighted(val target: NavTarget) : EnterOutcome

    /** Runs the short phrasing's action ("15 min" starts the timer, "7:30am" sets the alarm). */
    data class RunAction(val action: DirectAction) : EnterOutcome

    /** Opens the Best Match, a typed web address or the Quick Launch app, and otherwise searches the web. */
    data object OpenTopResult : EnterOutcome

    /** Searches the web for the query; a bang goes to its site. */
    data object SearchWeb : EnterOutcome

    /** Nothing typed: nothing to do. */
    data object NothingTyped : EnterOutcome
}

/** The rules for Enter; pure, so the order is pinned by tests. */
internal object OverlayEnterKey {

    /**
     * What Enter does, in order: a trigger word ([triggered]) opens its result; then the result the arrow keys
     * [highlighted]; then, with nothing typed, nothing. A bang is an explicit request for its site, so it is searched.
     * With [setting] Open Top Result the short phrasing's [phrasingAction] runs, else the top result opens; with
     * Search the Web, the query is searched.
     */
    fun decide(
        query: String,
        triggered: Any?,
        highlighted: NavTarget?,
        phrasingAction: DirectAction?,
        isBang: Boolean,
        setting: EnterKeyAction
    ): EnterOutcome = when {
        triggered != null -> EnterOutcome.OpenTriggered(triggered)
        highlighted != null -> EnterOutcome.OpenHighlighted(highlighted)
        query.isEmpty() -> EnterOutcome.NothingTyped
        isBang || setting == EnterKeyAction.SEARCH_WEB -> EnterOutcome.SearchWeb
        phrasingAction != null -> EnterOutcome.RunAction(phrasingAction)
        else -> EnterOutcome.OpenTopResult
    }
}

/**
 * Enter in the search field, with [highlighted] the result the arrow keys are on (null for none). A trigger word,
 * the highlight and Search the Web act at once; the rest waits for this query's results ([OverlayScope.submitQuery]),
 * so the phrasing's action and the top result are the ones for what was typed.
 */
internal fun OverlayScope.onEnter(context: Context, highlighted: NavTarget?) {
    // The collected uiState can lag a keystroke behind; read the live value so Enter never acts on a query missing
    // its last character.
    val query = viewModel.uiState.value.query
    val triggered = viewModel.quickKeyTarget(query, submitted = true)
    when (val outcome = OverlayEnterKey.decide(query, triggered, highlighted, phrasingAction = null, isBang = false, setting = enterKeyAction)) {
        is EnterOutcome.OpenTriggered -> {
            ui.highlightedKey = null
            ui.textFieldValue = TextFieldValue("")
            viewModel.onQueryChanged("")
            openResult(context, this, outcome.result, LaunchSource.keyboard())
        }
        is EnterOutcome.OpenHighlighted -> openNavTarget(context, this, outcome.target)
        EnterOutcome.SearchWeb -> launchWebSearch(query)
        EnterOutcome.NothingTyped -> Unit
        // Decided again once the results are in, with the phrasing's action and whether the query is a bang.
        is EnterOutcome.RunAction, EnterOutcome.OpenTopResult -> submitQuery(query, false)
    }
}
