package com.pixel.intelligentsearch.feature.search

import android.content.Context
import com.pixel.intelligentsearch.core.data.ContactItem
import com.pixel.intelligentsearch.core.data.FileItem
import com.pixel.intelligentsearch.core.search.SearchScope

/*
 * What Enter does in the search overlay: the keyboard's search key, a physical keyboard's Enter, and (from the
 * settled stage on) the voice check button. One set of rules, so every way of "going" agrees.
 */

/** What Enter does with the query. */
internal sealed interface EnterOutcome {
    /** Opens the result the arrow keys highlight. */
    data class OpenHighlighted(val target: NavTarget) : EnterOutcome

    /**
     * Runs the action the query asks for with nothing guessed ("15 min" starts the timer, "7:30am" sets the alarm,
     * "remind me to call mom tomorrow at 5pm" the reminder).
     */
    data class RunAction(val action: DirectAction) : EnterOutcome

    /** Opens the Best Match, a typed web address or the Quick Launch app, and otherwise searches the web. */
    data object OpenTopResult : EnterOutcome

    /** Searches the web for the query; a bang goes to its site. */
    data object SearchWeb : EnterOutcome

    /**
     * For a query limited to one kind of result ("@jo"): opens that kind's Best Match, else its first result, else
     * nothing ([OverlayEnterKey.scopedTarget]). Never a web search, a bang, an action or an app.
     */
    data object OpenScopedResult : EnterOutcome

    /** Nothing typed: nothing to do. */
    data object NothingTyped : EnterOutcome
}

/** The rules for Enter; pure, so the order is pinned by tests. */
internal object OverlayEnterKey {

    /**
     * What Enter does, in order: the result the arrow keys [highlighted] opens; with nothing typed, nothing. A query
     * limited to one kind of result ([scoped]: "@jo", "#tax", ">wifi") opens a result of that kind or nothing. A bang
     * is an explicit request for its site, so it is searched. Otherwise the timer, alarm or reminder the query asks for
     * runs ([phrasingAction], [SearchUiState.enterAction]), else the top result opens. That action is only ever one
     * with nothing guessed (THE CERTAINTY RULE FOR ENTER): "remind me to call mom tomorrow at 5pm" sets it, while
     * "remind me to call mom at 5", whose day and half of the day are a guess, is shown for a tap and Enter goes on to
     * the top result or a search, as for any other query.
     */
    fun decide(
        query: String,
        highlighted: NavTarget?,
        phrasingAction: DirectAction?,
        isBang: Boolean,
        scoped: Boolean = false
    ): EnterOutcome = when {
        highlighted != null -> EnterOutcome.OpenHighlighted(highlighted)
        query.isEmpty() -> EnterOutcome.NothingTyped
        scoped -> EnterOutcome.OpenScopedResult
        isBang -> EnterOutcome.SearchWeb
        phrasingAction != null -> EnterOutcome.RunAction(phrasingAction)
        else -> EnterOutcome.OpenTopResult
    }

    /**
     * What Enter opens for [state], a search limited to one kind of result: that kind's [bestMatch], else the first
     * of its results, else nothing (null). Only ever a contact, a file or a settings page of the kind asked for.
     */
    fun scopedTarget(state: SearchUiState, bestMatch: Any?): Any? = when (state.scoped?.scope) {
        SearchScope.CONTACTS -> bestMatch as? ContactItem ?: state.contacts.firstOrNull()
        SearchScope.FILES -> bestMatch as? FileItem ?: state.files.firstOrNull()
        SearchScope.SETTINGS -> state.androidSettings.firstOrNull()
        null -> null
    }
}

/**
 * Enter in the search field, with [highlighted] the result the arrow keys are on (null for none). The highlight acts
 * at once; the rest waits for this query's results ([OverlayScope.submitQuery]), so the phrasing's action and the top
 * result are the ones for what was typed.
 */
internal fun OverlayScope.onEnter(context: Context, highlighted: NavTarget?) {
    // The collected uiState can lag a keystroke behind; read the live value so Enter never acts on a query missing
    // its last character.
    val query = viewModel.uiState.value.query
    // Enter accepts text another app handed in as the user's own search.
    viewModel.confirmQuery()
    when (val outcome = OverlayEnterKey.decide(query, highlighted, phrasingAction = null, isBang = false)) {
        is EnterOutcome.OpenHighlighted -> {
            // The highlight is gone once it has opened, so a second Enter would go on to the top result. What stays
            // in search with the same words (asking the AI, dropping a file filter) leaves Enter free.
            val staysInSearch = outcome.target is NavTarget.AskAi || outcome.target is NavTarget.RemoveFileTypeFilter ||
                outcome.target is NavTarget.RemoveScope || outcome.target is NavTarget.Correction ||
                (outcome.target as? NavTarget.Answer)?.action is AnswerAction.Copy
            if (!staysInSearch) ui.enterTakenFor = query
            openNavTarget(context, this, outcome.target)
        }
        EnterOutcome.NothingTyped -> Unit
        // Decided again once the results are in, with the phrasing's action and whether the query is a bang.
        is EnterOutcome.RunAction, EnterOutcome.OpenTopResult, EnterOutcome.SearchWeb, EnterOutcome.OpenScopedResult ->
            submitQuery(query, false)
    }
}
