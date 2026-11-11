package com.pixel.intelligentsearch.feature.search.today.cards.grid

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.graphics.drawable.toBitmap
import com.pixel.intelligentsearch.core.ui.icons.filled.Event
import com.pixel.intelligentsearch.core.ui.icons.filled.ExpandMore
import com.pixel.intelligentsearch.core.ui.icons.filled.Forum
import com.pixel.intelligentsearch.core.ui.icons.filled.History
import com.pixel.intelligentsearch.core.ui.icons.filled.HourglassBottom
import com.pixel.intelligentsearch.core.ui.icons.filled.WbSunny
import com.pixel.intelligentsearch.core.ui.icons.outlined.CalendarMonth
import com.pixel.intelligentsearch.core.ui.icons.outlined.Close
import com.pixel.intelligentsearch.core.ui.icons.outlined.Language
import com.pixel.intelligentsearch.core.ui.icons.outlined.MusicNote
import com.pixel.intelligentsearch.core.ui.icons.outlined.PhoneAndroid
import com.pixel.intelligentsearch.core.ui.icons.outlined.Schedule
import com.pixel.intelligentsearch.core.ui.icons.outlined.Search
import com.pixel.intelligentsearch.core.ui.icons.outlined.Spellcheck
import com.pixel.intelligentsearch.core.ui.icons.outlined.Timer
import com.pixel.intelligentsearch.core.ui.icons.outlined.Tune
import com.pixel.intelligentsearch.feature.search.today.AndroidTodaySources
import com.pixel.intelligentsearch.feature.search.today.ChooseControlsSheet
import com.pixel.intelligentsearch.feature.search.today.LocalTodaySources
import com.pixel.intelligentsearch.core.ui.icons.outlined.Widgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import com.pixel.intelligentsearch.feature.search.today.cards.CardKind
import com.pixel.intelligentsearch.feature.search.today.cards.DiscoverLayout
import com.pixel.intelligentsearch.feature.search.today.cards.WidgetChoice
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/*
 * Edit mode's sheets: Add card (an app's widget, a website, or a hidden part
 * back), and every edit of the selected card as a plain button, for TalkBack, Switch Access and keyboards.
 *
 * NG Designs
 */

/**
 * The open sheet ([DiscoverEditState.sheet]), if any, as a modal bottom sheet. [layout] is the page's layout, which
 * the sheets show; [widgetChoices], [onAddWidget] and [onAddSite] are the page's, as [DiscoverEditChrome] takes them.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun EditSheets(
    state: DiscoverEditState,
    layout: DiscoverLayout,
    widgetChoices: suspend () -> List<WidgetChoice>,
    onAddWidget: (WidgetChoice) -> Unit,
    onAddSite: suspend (String) -> Result<Unit>
) {
    val which = state.sheet ?: return
    if (which == EditSheet.CONTROLS) {
        // The card's own list, which draws its own sheet.
        val context = LocalContext.current
        val sources = LocalTodaySources.current ?: remember(context) { AndroidTodaySources(context) }
        ChooseControlsSheet(sources.controls) { if (state.sheet == EditSheet.CONTROLS) state.open(null) }
        return
    }
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
                    EditSheet.ADD -> AddSheet(state, layout, widgetChoices, onAddWidget, onAddSite, close)
                    EditSheet.ACTIONS -> ActionsSheet(state, layout, close)
                    EditSheet.CONTROLS -> Unit
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

/**
 * Add to Discover: one search field for apps and websites (an address shows a row to add it as a website card), the
 * page's hidden parts to put back, and every app with a widget, each opening to its widgets' previews; a tap on one adds
 * it as a live card. [close] closes the sheet.
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
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val choices by produceState<List<WidgetChoice>?>(null) {
        value = try {
            widgetChoices()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }
    // Each widget's own preview, as the launcher's widget list shows it.
    val providers by produceState(emptyMap<ComponentName, AppWidgetProviderInfo>()) {
        value = withContext(Dispatchers.IO) {
            runCatching { AppWidgetManager.getInstance(context).installedProviders.associateBy { it.provider } }.getOrDefault(emptyMap())
        }
    }
    val site = query.trim().takeIf(::looksLikeSite)
    fun addSite() {
        val input = site ?: return
        if (busy) return
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
                val why = it.message ?: "That website couldn't be added. Check the address and try again."
                error = why
                state.announce(why)
            }
        }
    }

    Text(
        "Add to Discover",
        style = MaterialTheme.typography.headlineSmall,
        color = scheme.onSurface,
        modifier = Modifier.padding(top = 2.dp, bottom = 16.dp).semantics { heading() }
    )
    SearchField(query, onChange = { query = it; error = null }, onGo = { if (site != null) addSite() })

    if (site != null) {
        AddRow(
            leading = { RoundIcon(Icons.Outlined.Language, scheme.tertiaryContainer, scheme.onTertiaryContainer) },
            title = site,
            detail = error ?: "Add as a website card",
            detailColor = if (error != null) scheme.error else scheme.onSurfaceVariant,
            busy = busy,
            modifier = Modifier.padding(top = 12.dp),
            onClick = ::addSite
        )
    }

    val hidden = CardKind.PAGE_ORDER.filter { it in layout.hidden && (query.isBlank() || it.label.contains(query.trim(), ignoreCase = true)) }
    if (hidden.isNotEmpty()) {
        SectionLabel("Put back")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (kind in hidden) {
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(scheme.secondaryContainer)
                        .clickable(role = Role.Button, onClickLabel = "Put back") {
                            state.addBack(kind)
                            close()
                        }
                        .heightIn(min = 48.dp)
                        .padding(start = 12.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(partIcon(kind), contentDescription = null, tint = scheme.onSecondaryContainer, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(kind.label, style = MaterialTheme.typography.labelLarge, color = scheme.onSecondaryContainer)
                }
            }
        }
    }

    SectionLabel("Widgets")
    when (val list = choices) {
        null -> Row(Modifier.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp).testTag(LOADING_INDICATOR_TAG), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text("Finding widgets", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
        else -> {
            val words = query.trim()
            val groups = list
                .filter { words.isEmpty() || it.appLabel.contains(words, ignoreCase = true) || it.widgetLabel.contains(words, ignoreCase = true) }
                .groupBy { it.packageName }
                .values
                .toList()
            if (groups.isEmpty()) {
                Text(
                    if (list.isEmpty()) "No apps on this phone have a widget." else "No widgets match.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 14.dp)
                )
            }
            Column(Modifier.clip(RoundedCornerShape(24.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                groups.forEachIndexed { i, widgets ->
                    val app = widgets.first()
                    val expanded = open == app.packageName || (words.isNotEmpty() && groups.size == 1)
                    AppGroup(
                        app = app,
                        widgets = widgets,
                        providers = providers,
                        expanded = expanded,
                        first = i == 0,
                        last = i == groups.lastIndex,
                        onToggle = { open = if (open == app.packageName) null else app.packageName },
                        // Said once the card is really added (the page does): Android can still ask, and be refused.
                        onAdd = { choice ->
                            onAddWidget(choice)
                            close()
                        }
                    )
                }
            }
        }
    }
}

/** The search field at the top of Add to Discover, for apps or a website. */
@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, onGo: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    TextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text("Search apps, or type a website") },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = if (value.isNotEmpty()) {
            { IconButton(onClick = { onChange("") }) { Icon(Icons.Outlined.Close, contentDescription = "Clear") } }
        } else null,
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = scheme.surfaceContainerHighest,
            unfocusedContainerColor = scheme.surfaceContainerHighest,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onGo() }),
        modifier = Modifier.fillMaxWidth()
    )
}

/** A small heading over a part of the sheet. */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 22.dp, bottom = 10.dp).semantics { heading() }
    )
}

/** An icon on a round tile of [back]. */
@Composable
private fun RoundIcon(icon: ImageVector, back: Color, ink: Color) {
    Box(Modifier.size(40.dp).background(back, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(20.dp))
    }
}

/** One thing to add: its [leading] picture, [title] and [detail], and a plus (or a spinner while [busy]); a tap adds it. */
@Composable
private fun AddRow(
    leading: @Composable () -> Unit,
    title: String,
    detail: String?,
    detailColor: Color,
    busy: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(scheme.surfaceContainerHigh)
            .clickable(enabled = !busy, role = Role.Button, onClickLabel = "Add", onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        leading()
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = detailColor, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
        if (busy) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        } else {
            Icon(EditIcons.Add, contentDescription = null, tint = scheme.primary)
        }
    }
}

/**
 * An app with widgets: its icon, name and how many widgets it has; a tap opens it to its widgets' previews side by side,
 * each with its name. Grouped rows share one rounded block, as Settings lists are.
 */
@Composable
private fun AppGroup(
    app: WidgetChoice,
    widgets: List<WidgetChoice>,
    providers: Map<ComponentName, AppWidgetProviderInfo>,
    expanded: Boolean,
    first: Boolean,
    last: Boolean,
    onToggle: () -> Unit,
    onAdd: (WidgetChoice) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val big = 24.dp
    val small = 6.dp
    val shape = RoundedCornerShape(
        topStart = if (first) big else small, topEnd = if (first) big else small,
        bottomStart = if (last) big else small, bottomEnd = if (last) big else small
    )
    val turn by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.surfaceContainerHigh)
            .animateContentSize()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = if (expanded) "Close" else "Show widgets", onClick = onToggle)
                .heightIn(min = 64.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AppIcon(app)
            Column(Modifier.weight(1f)) {
                Text(app.appLabel, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (widgets.size == 1) widgets.first().widgetLabel.ifBlank { "1 widget" } else "${widgets.size} widgets",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.Filled.ExpandMore, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.graphicsLayer { rotationZ = turn })
        }
        if (expanded) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 12.dp, end = 12.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (choice in widgets) WidgetTile(choice, providers[choice.provider]) { onAdd(choice) }
            }
        }
    }
}

/** One widget to add: its preview as the app draws it (its icon when it has none), and its name. */
@Composable
private fun WidgetTile(choice: WidgetChoice, info: AppWidgetProviderInfo?, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val preview by produceState<ImageBitmap?>(null, info) {
        value = if (info == null) null else withContext(Dispatchers.IO) { previewBitmap(context, info) }
    }
    Column(
        Modifier
            .width(156.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClickLabel = "Add") { onClick() }
            .semantics(mergeDescendants = true) { contentDescription = "${choice.appLabel}, ${choice.widgetLabel}" }
            .padding(6.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(112.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(scheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            val image = preview ?: choice.icon
            if (image != null) {
                Image(
                    image,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = if (preview != null) Modifier.fillMaxSize().padding(8.dp) else Modifier.size(44.dp)
                )
            }
        }
        Text(
            choice.widgetLabel.ifBlank { choice.appLabel },
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp)
        )
    }
}

/** [info]'s preview image, scaled to at most 480 pixels across; its icon when it has none. */
private fun previewBitmap(context: android.content.Context, info: AppWidgetProviderInfo): ImageBitmap? = runCatching {
    val drawable = info.loadPreviewImage(context, 0) ?: info.loadIcon(context, 0) ?: return@runCatching null
    val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: 240
    val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: 240
    val k = minOf(1f, 480f / maxOf(w, h))
    drawable.toBitmap((w * k).toInt().coerceAtLeast(1), (h * k).toInt().coerceAtLeast(1)).asImageBitmap()
}.getOrNull()

/** Whether [text] is a web address rather than an app's name: no spaces, and a dot with something after it. */
private fun looksLikeSite(text: String): Boolean {
    if (text.isEmpty() || text.any { it.isWhitespace() }) return false
    if (text.startsWith("http://", ignoreCase = true) || text.startsWith("https://", ignoreCase = true)) return true
    val dot = text.lastIndexOf('.')
    return dot > 0 && text.length - dot - 1 >= 2 && text.substring(dot + 1).all { it.isLetter() || it == '/' }
}

/** The icon for one of the page's own parts, as its card's label has it. */
private fun partIcon(kind: CardKind): ImageVector = when (kind) {
    CardKind.UP_NEXT -> Icons.Filled.Event
    CardKind.REST_OF_DAY -> Icons.Outlined.Schedule
    CardKind.WEATHER -> Icons.Filled.WbSunny
    CardKind.TILES -> Icons.Outlined.Timer
    CardKind.NOW_PLAYING -> Icons.Outlined.MusicNote
    CardKind.CATCH_UP -> Icons.Filled.Forum
    CardKind.CONTROLS -> Icons.Outlined.Tune
    CardKind.PHONE -> Icons.Outlined.PhoneAndroid
    CardKind.LEFT_OFF -> Icons.Filled.History
    CardKind.WORD -> Icons.Outlined.Spellcheck
    CardKind.YEAR -> Icons.Filled.HourglassBottom
    CardKind.TOMORROW -> Icons.Outlined.CalendarMonth
    CardKind.APP -> Icons.Outlined.Widgets
    CardKind.SITE -> Icons.Outlined.Language
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
