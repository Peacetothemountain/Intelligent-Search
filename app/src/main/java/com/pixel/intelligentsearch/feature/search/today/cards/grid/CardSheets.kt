package com.pixel.intelligentsearch.feature.search.today.cards.grid

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pixel.intelligentsearch.core.ui.LOADING_INDICATOR_TAG
import com.pixel.intelligentsearch.core.ui.icons.outlined.VisibilityOff
import com.pixel.intelligentsearch.feature.search.today.cards.CARD_SHAPES
import com.pixel.intelligentsearch.feature.search.today.cards.CardKind
import com.pixel.intelligentsearch.feature.search.today.cards.CardMode
import com.pixel.intelligentsearch.feature.search.today.cards.DiscoverLayout
import com.pixel.intelligentsearch.feature.search.today.cards.WidgetChoice
import com.pixel.intelligentsearch.feature.search.today.cards.shape.ShapeSwatch
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/*
 * Edit mode's sheets: the Material shapes for a shaped card, Add card (an app's widget, a website, or a hidden part
 * back), and every edit of the selected card as a plain button, for TalkBack, Switch Access and keyboards.
 *
 * NG Designs
 */

/** Shapes per row in the Shape sheet. */
private const val SHAPES_PER_ROW = 7

/**
 * The open sheet ([DiscoverEditState.sheet]), if any, as a modal bottom sheet. [layout] is the page's layout, which
 * the sheets show; [widgetChoices], [onAddWidget] and [onAddSite] are the page's, as [DiscoverEditChrome] takes them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditSheets(
    state: DiscoverEditState,
    layout: DiscoverLayout,
    widgetChoices: suspend () -> List<WidgetChoice>,
    onAddWidget: (WidgetChoice) -> Unit,
    onAddSite: suspend (String) -> Result<Unit>
) {
    val which = state.sheet ?: return
    key(which) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()
        val close: () -> Unit = {
            scope.launch { sheetState.hide() }.invokeOnCompletion { if (state.sheet == which) state.open(null) }
        }
        ModalBottomSheet(onDismissRequest = { if (state.sheet == which) state.open(null) }, sheetState = sheetState) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 20.dp)
            ) {
                when (which) {
                    EditSheet.SHAPE -> ShapeSheet(state, layout)
                    EditSheet.ADD -> AddSheet(state, layout, widgetChoices, onAddWidget, onAddSite, close)
                    EditSheet.ACTIONS -> ActionsSheet(state, layout, close)
                }
            }
        }
    }
}

/** A sheet's [title] and the [note] under it. */
@Composable
private fun SheetHeading(title: String, note: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = 4.dp).semantics { heading() }
    )
    Text(
        note,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

/** The Material shapes the selected shaped card can take, seven to a row; picking one morphs the card to it. */
@Composable
private fun ShapeSheet(state: DiscoverEditState, layout: DiscoverLayout) {
    val scheme = MaterialTheme.colorScheme
    val spec = layout.cards.firstOrNull { it.id == state.selectedId }?.takeIf { it.mode == CardMode.SHAPED }
    SheetHeading("Shape", "Small cards can take any shape. Their design re-forms around it.")
    if (spec == null) return
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        for (row in CARD_SHAPES.chunked(SHAPES_PER_ROW)) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                for (name in row) {
                    val selected = spec.shape == name
                    val tile = RoundedCornerShape(13.dp)
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(tile)
                            .background(if (selected) scheme.primaryContainer else scheme.onSurface.copy(alpha = 0.05f))
                            .then(if (selected) Modifier.border(2.dp, scheme.primary, tile) else Modifier)
                            .selectable(selected = selected, role = Role.RadioButton) { state.setShape(spec.id, name) }
                            .semantics { contentDescription = shapeName(name) },
                        contentAlignment = Alignment.Center
                    ) {
                        ShapeSwatch(name, selected, Modifier.fillMaxSize(0.7f))
                    }
                }
                repeat(SHAPES_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * Add card: on Apps, the hidden parts of the page to put back and the widgets a card can be made from; on Website, a
 * field for the site to read. [close] closes the sheet.
 */
@Composable
private fun AddSheet(
    state: DiscoverEditState,
    layout: DiscoverLayout,
    widgetChoices: suspend () -> List<WidgetChoice>,
    onAddWidget: (WidgetChoice) -> Unit,
    onAddSite: suspend (String) -> Result<Unit>,
    close: () -> Unit
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Text(
        "Add a card",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = 12.dp).semantics { heading() }
    )
    Tabs(tab, listOf("Apps", "Website")) { tab = it }
    if (tab == 0) AppsTab(state, layout, widgetChoices, onAddWidget, close) else WebsiteTab(state, onAddSite, close)
}

/** Two or more tabs as one pill, the [selected] one filled. */
@Composable
private fun Tabs(selected: Int, names: List<String>, onSelect: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(CircleShape)
            .background(scheme.onSurface.copy(alpha = 0.05f))
            .padding(3.dp)
            .selectableGroup()
    ) {
        names.forEachIndexed { i, name ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(if (on) scheme.secondaryContainer else Color.Transparent)
                    .selectable(selected = on, role = Role.Tab) { onSelect(i) }
                    .heightIn(min = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (on) scheme.onSecondaryContainer else scheme.onSurfaceVariant
                )
            }
        }
    }
}

/** The hidden parts of the page, to put back, then the widgets a card can be made from. */
@Composable
private fun AppsTab(
    state: DiscoverEditState,
    layout: DiscoverLayout,
    widgetChoices: suspend () -> List<WidgetChoice>,
    onAddWidget: (WidgetChoice) -> Unit,
    close: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Text(
        "Apps appear here only if they have a home-screen widget. The card is drawn from what that widget shows.",
        style = MaterialTheme.typography.bodySmall,
        color = scheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 12.dp)
    )
    val choices by produceState<List<WidgetChoice>?>(null) {
        value = try {
            widgetChoices()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (kind in CardKind.PAGE_ORDER.filter { it in layout.hidden }) {
            ChoiceRow(
                leading = {
                    Box(Modifier.size(36.dp).background(scheme.surfaceContainerHighest, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.VisibilityOff, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                },
                title = kind.label,
                detail = "Hidden. Add it back to the page."
            ) {
                state.addBack(kind)
                close()
            }
        }
        when (val list = choices) {
            null -> Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp).testTag(LOADING_INDICATOR_TAG), strokeWidth = 2.dp)
                Spacer(Modifier.size(12.dp))
                Text("Looking for widgets", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
            else -> {
                if (list.isEmpty()) {
                    Text(
                        "No apps with a widget to add.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
                for (choice in list) {
                    ChoiceRow(
                        leading = { AppIcon(choice) },
                        title = "${choice.appLabel} · ${choice.widgetLabel}",
                        detail = null
                    ) {
                        onAddWidget(choice)
                        state.haptic(EditHaptic.CONFIRM)
                        state.announce("${choice.appLabel} added")
                        close()
                    }
                }
            }
        }
    }
}

/** A widget's app icon in a circle, or the app's first letter in its color when it has none. */
@Composable
private fun AppIcon(choice: WidgetChoice) {
    val accent = Color(choice.accent)
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        val icon = choice.icon
        if (icon != null) {
            Image(icon, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(36.dp))
        } else {
            Text(choice.appLabel.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = accent)
        }
    }
}

/** One thing to add: its [leading] picture, [title] and [detail], and a plus; a tap adds it. */
@Composable
private fun ChoiceRow(leading: @Composable () -> Unit, title: String, detail: String?, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.onSurface.copy(alpha = 0.04f))
            .clickable(role = Role.Button, onClickLabel = "Add", onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        leading()
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface, maxLines = 2)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        Icon(EditIcons.AddCircle, contentDescription = null, tint = scheme.primary)
    }
}

/** A field for the website to add, its Add button, why it couldn't be added (if it couldn't), and how it is read. */
@Composable
private fun WebsiteTab(state: DiscoverEditState, onAddSite: suspend (String) -> Result<Unit>, close: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val add = {
        val input = text.trim()
        if (!busy && input.isNotEmpty()) {
            busy = true
            error = null
            scope.launch {
                val result = onAddSite(input)
                busy = false
                result.onSuccess {
                    state.haptic(EditHaptic.CONFIRM)
                    state.announce("Website added")
                    close()
                }.onFailure {
                    state.haptic(EditHaptic.REJECT)
                    error = it.message ?: "That website couldn't be added. Check the address and try again."
                }
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                error = null
            },
            placeholder = { Text("Type a website") },
            singleLine = true,
            isError = error != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { add() }),
            modifier = Modifier.weight(1f)
        )
        Button(onClick = { add() }, enabled = !busy && text.isNotBlank()) { Text("Add") }
    }
    error?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.error, modifier = Modifier.padding(top = 6.dp))
    }
    Text(
        "The page is read once, signed out, then refreshed daily. Only what anyone could see is shown.",
        style = MaterialTheme.typography.bodySmall,
        color = scheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp)
    )
}

/** Every edit of the selected card as a plain button. [close] closes the sheet after one is picked. */
@Composable
private fun ActionsSheet(state: DiscoverEditState, layout: DiscoverLayout, close: () -> Unit) {
    val spec = layout.cards.firstOrNull { it.id == state.selectedId }
    SheetHeading(spec?.let(state.label) ?: "Card actions", "Every edit is also here, for TalkBack, Switch Access and keyboards.")
    if (spec == null) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (action in state.actions(spec)) {
            ActionRow(action.label, action.icon, action.enabled) {
                action.run()
                if (state.sheet == EditSheet.ACTIONS) close()
            }
        }
    }
}

/** One edit in the actions sheet; dimmed and not tappable when it can't be done now. */
@Composable
private fun ActionRow(label: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f)
            .heightIn(min = 52.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
    }
}
