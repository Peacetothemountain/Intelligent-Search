package com.pixel.intelligentsearch.feature.settings

import android.widget.Toast
import com.pixel.intelligentsearch.core.theme.isDark
import com.pixel.intelligentsearch.core.theme.pageContainer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pixel.intelligentsearch.R
import com.pixel.intelligentsearch.core.occasion.DoodleCatalog
import com.pixel.intelligentsearch.core.occasion.Occasion
import com.pixel.intelligentsearch.core.occasion.OccasionCalendar
import com.pixel.intelligentsearch.core.ui.LOADING_INDICATOR_TAG
import com.pixel.intelligentsearch.feature.widget.SearchWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Developer Settings' doodle gallery, full screen over the settings. */
@Composable
internal fun DoodleGalleryDialog(previewing: Occasion?, onPreview: (Occasion?) -> Unit, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        DoodleGallery(previewing = previewing, onPreview = onPreview, onClose = onDismiss)
    }
}

/**
 * Every doodle, each playing around the G as it does on the widget. The doodle picked plays large at the top, with
 * when it next shows and where; from there it can be shown on the widgets (whatever the date) or tapped as the
 * widget's would be. While the widgets are previewing, they follow whichever doodle is picked.
 *
 * Doodles can be searched (by name, group or country), filtered by group or to the ones coming up here, stepped
 * through, shuffled, or found by date with the calendar, which picks the doodle that day gets on this phone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DoodleGallery(
    previewing: Occasion?,
    onPreview: (Occasion?) -> Unit,
    onClose: () -> Unit,
    today: LocalDate = remember { OccasionCalendar.now().toLocalDate() },
    zone: ZoneId = remember { OccasionCalendar.now().zone },
    region: String = remember { OccasionCalendar.region() }
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val catalog by produceState<Map<Occasion, DoodleCatalog.Entry>?>(null, today, zone, region) {
        value = withContext(Dispatchers.Default) { DoodleCatalog.entries(today, zone, region).associateBy { it.occasion } }
    }
    val todays = remember(today, zone, region) { OccasionCalendar.on(today, zone, region) }

    var selectedName by rememberSaveable { mutableStateOf((previewing ?: todays ?: Occasion.entries.first()).name) }
    val selected = Occasion.valueOf(selectedName)
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(FILTER_ALL) }
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    var lightPill by rememberSaveable { mutableStateOf(!darkTheme) }
    var pickingDate by remember { mutableStateOf(false) }

    val titles = remember(resources) { Occasion.entries.associateWith { resources.getString(it.title) } }
    val shown = remember(catalog, query, filter, titles) {
        val needle = query.trim()
        Occasion.entries
            .filter { occasion ->
                when (filter) {
                    FILTER_ALL -> true
                    FILTER_SOON -> catalog?.get(occasion)?.nextHere != null
                    else -> DoodleCatalog.kind(occasion).name == filter
                }
            }
            .filter { needle.isEmpty() || matches(it, needle, titles.getValue(it), catalog?.get(it)) }
            .let { list -> if (filter == FILTER_SOON) list.sortedBy { catalog?.get(it)?.nextHere } else list }
    }

    val grid = rememberLazyGridState()

    fun pick(occasion: Occasion) {
        selectedName = occasion.name
        // Bring its card into view when it's off screen (picked by stepping, shuffling or date).
        val index = shown.indexOf(occasion)
        if (index >= 0 && grid.layoutInfo.visibleItemsInfo.none { it.key == occasion.name }) {
            scope.launch { grid.animateScrollToItem(index) }
        }
        // While the widgets are previewing, they follow the doodle picked here.
        if (previewing != null && previewing != occasion) onPreview(occasion)
    }

    fun step(by: Int) {
        val list = shown.ifEmpty { Occasion.entries }
        val at = list.indexOf(selected)
        pick(list[if (at < 0) 0 else Math.floorMod(at + by, list.size)])
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { SettingsPageTitle("Doodle Gallery", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Normal) },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = "Close") }
                },
                actions = {
                    IconButton(onClick = { lightPill = !lightPill }) {
                        Icon(Icons.Outlined.Contrast, contentDescription = if (lightPill) "Show on a Dark Pill" else "Show on a Light Pill")
                    }
                    IconButton(onClick = { pickingDate = true }) {
                        Icon(Icons.Outlined.CalendarMonth, contentDescription = "Find the Doodle for a Date")
                    }
                    IconButton(onClick = {
                        (shown.filter { it != selected }.ifEmpty { Occasion.entries - selected }).randomOrNull()?.let(::pick)
                    }) {
                        Icon(Icons.Outlined.Shuffle, contentDescription = "Pick a Random Doodle")
                    }
                },
                colors = settingsTopBarColors()
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            DoodleStage(
                occasion = selected,
                title = titles.getValue(selected),
                entry = catalog?.get(selected),
                today = today,
                region = region,
                lightPill = lightPill,
                onWidgets = previewing == selected,
                onShowOnWidgets = { onPreview(if (previewing == selected) null else selected) },
                onTestTap = {
                    val prefs = com.pixel.intelligentsearch.core.data.SettingsManager.storedPrefs(context)
                    runCatching {
                        context.startActivity(
                            SearchWidgetProvider.doodleSearchIntent(context, prefs, 0, selected)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }.onFailure { Toast.makeText(context, "Couldn't open the doodle's search.", Toast.LENGTH_SHORT).show() }
                },
                onPrevious = { step(-1) },
                onNext = { step(1) },
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
                singleLine = true,
                placeholder = { Text("Search Doodles, Groups or Countries") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = if (query.isNotEmpty()) {
                    { IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, contentDescription = "Clear Search") } }
                } else null,
                shape = CircleShape
            )

            val filters = remember(catalog) {
                listOf(FILTER_ALL to "All ${Occasion.entries.size}") +
                    listOf(FILTER_SOON to "Coming Up${catalog?.let { c -> " ${c.values.count { it.nextHere != null }}" }.orEmpty()}") +
                    DoodleCatalog.Kind.entries.map { kind ->
                        kind.name to "${kind.label} ${Occasion.entries.count { DoodleCatalog.kind(it) == kind }}"
                    }
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filters, key = { it.first }) { (key, label) ->
                    FilterChip(selected = filter == key, onClick = { filter = key }, label = { Text(label) })
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 108.dp),
                    state = grid,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (shown.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                "No Doodles Match.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp)
                            )
                        }
                    }
                    items(shown, key = { it.name }) { occasion ->
                        DoodleCard(
                            occasion = occasion,
                            title = titles.getValue(occasion),
                            whenText = catalog?.get(occasion)?.let { shortWhen(it, today) },
                            selected = occasion == selected,
                            onWidgets = occasion == previewing,
                            lightPill = lightPill,
                            onClick = { pick(occasion) }
                        )
                    }
                }
                // Screenshot tests wait while this is on screen, until the dates have been worked out.
                if (catalog == null) Box(Modifier.size(1.dp).testTag(LOADING_INDICATOR_TAG))
            }
        }
    }

    if (pickingDate) {
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickingDate = false
                    val millis = picker.selectedDateMillis ?: return@TextButton
                    val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    val doodle = OccasionCalendar.on(date, zone, region)
                    if (doodle != null) {
                        filter = FILTER_ALL
                        query = ""
                        pick(doodle)
                    } else {
                        scope.launch { snackbar.showSnackbar("No Doodle on ${date.format(LONG_DATE)} Here.") }
                    }
                }) { Text("Show") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } }
        ) { DatePicker(state = picker) }
    }
}

/** The picked doodle playing large, what it is, when and where it shows, and what can be done with it. */
@Composable
private fun DoodleStage(
    occasion: Occasion,
    title: String,
    entry: DoodleCatalog.Entry?,
    today: LocalDate,
    region: String,
    lightPill: Boolean,
    onWidgets: Boolean,
    onShowOnWidgets: () -> Unit,
    onTestTap: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.pageContainer
    ) {
        Column(Modifier.padding(16.dp)) {
            DoodleWidgetMock(occasion, scale = 2f, light = lightPill, full = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(8.dp))
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(
                        DoodleCatalog.kind(occasion).label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            Text(
                entry?.let { longWhen(it, today, region) } ?: "Working Out Dates…",
                style = MaterialTheme.typography.bodyMedium,
                color = if (entry?.nextHere == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 2.dp)
            )
            Text(
                entry?.let { whereText(it, region) } ?: " ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onPrevious) { Icon(Icons.Outlined.ChevronLeft, contentDescription = "Previous Doodle") }
                if (onWidgets) {
                    FilledTonalButton(onClick = onShowOnWidgets, modifier = Modifier.weight(1f)) {
                        Text("Stop Widget Preview", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    Button(onClick = onShowOnWidgets, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Widgets, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Show on Widgets", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                IconButton(onClick = onTestTap) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = "Tap the Doodle")
                }
                IconButton(onClick = onNext) { Icon(Icons.Outlined.ChevronRight, contentDescription = "Next Doodle") }
            }
        }
    }
}

/** One doodle in the grid: playing around the G on the end of a widget, with its name and when it next shows. */
@Composable
private fun DoodleCard(
    occasion: Occasion,
    title: String,
    whenText: String?,
    selected: Boolean,
    onWidgets: Boolean,
    lightPill: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        // A step below the page's cards in the dark theme; on the light theme's tinted page, its near-white cards.
        color = if (selected) colors.secondaryContainer else if (colors.isDark) colors.surfaceContainerLow else colors.pageContainer,
        border = if (selected) BorderStroke(2.dp, colors.primary) else null,
        modifier = Modifier.semantics {
            this.selected = selected
            contentDescription = listOfNotNull(title, whenText, "On Your Widgets".takeIf { onWidgets }).joinToString(", ")
        }
    ) {
        Box {
            Column(Modifier.fillMaxWidth().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                DoodleWidgetMock(occasion, scale = 1f, light = lightPill, full = false, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp))
                Spacer(Modifier.height(4.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) colors.onSecondaryContainer else colors.onSurface,
                    textAlign = TextAlign.Center,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    whenText ?: " ",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (whenText == "Today") colors.primary else colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (onWidgets) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).background(colors.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Widgets, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

/**
 * A mock-up of the Material design widget as it comes (accent rim, Material You G, and the theme's own solid pill:
 * black in the dark, its light twin in the light,
 * [com.pixel.intelligentsearch.core.theme.DynamicThemeDetector.ResolvedWidgetColors.lockedPill]), [scale]
 * times the widget's size, with [occasion] playing around the G exactly where the widget plays it. With [full] it has
 * the voice search button and the round search button too; without, just the G's end of the bar.
 */
@Composable
private fun DoodleWidgetMock(occasion: Occasion, scale: Float, light: Boolean, full: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scheme = remember(context, light) {
        if (light) androidx.compose.material3.dynamicLightColorScheme(context)
        else androidx.compose.material3.dynamicDarkColorScheme(context)
    }
    // The design's defaults: the rim 28% see-through around the theme's own pill, which stays solid.
    val alpha = 0.72f
    val rim = scheme.primaryContainer.copy(alpha = alpha)
    val pill = remember(context, light) {
        Color(com.pixel.intelligentsearch.core.theme.DynamicThemeDetector.resolveWidgetColors(context, isDark = !light).lockedPill)
    }
    // The widget's Material icons are the search overlay's, worked out from the overlay's accents for the pill's shade.
    val (gPrimary, gSecondary, gTertiary) = remember(context, light) {
        val accents = com.pixel.intelligentsearch.core.theme.SearchIconColors.themeAccents(context, light)
        listOf(accents.primary, accents.secondary, accents.tertiary).map { Color(it) }
    }
    Row(
        modifier = modifier
            .background(rim, RoundedCornerShape(32.dp * scale))
            .padding(8.dp * scale),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(48.dp * scale)
                .background(pill, CircleShape)
                .padding(horizontal = 16.dp * scale),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(24.dp * scale), contentAlignment = Alignment.Center) {
                ComposeGIcon(
                    modifier = Modifier.size(24.dp * scale),
                    primaryColor = gPrimary,
                    secondaryColor = gSecondary,
                    tertiaryColor = gTertiary,
                    isPillLight = light
                )
                WidgetDoodleOverlay(occasion, size = 48.dp * scale)
            }
            if (full) {
                Spacer(Modifier.weight(1f))
                ComposeThemedShortcutIcon(
                    resId = R.drawable.ic_mic,
                    contentDescription = "Voice Search",
                    primaryColor = gPrimary,
                    secondaryColor = gSecondary,
                    tertiaryColor = gTertiary,
                    isMaterialTheme = true,
                    modifier = Modifier.size(24.dp * scale),
                    isPillLight = light
                )
            }
        }
        if (full) {
            Spacer(Modifier.width(8.dp * scale))
            Box(
                Modifier.size(48.dp * scale).background(pill, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                ComposeActionIcon(
                    iconType = "Search",
                    modifier = Modifier.size(24.dp * scale),
                    primaryColor = gPrimary,
                    secondaryColor = gSecondary,
                    tertiaryColor = gTertiary,
                    isMaterialTheme = true,
                    isPillLight = light
                )
            }
        }
    }
}

private const val FILTER_ALL = "all"
private const val FILTER_SOON = "soon"

private val LONG_DATE = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.US)
private val SHORT_DATE = DateTimeFormatter.ofPattern("MMM d", Locale.US)
private val SHORT_DATE_YEAR = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

private fun countryName(code: String): String =
    Locale.Builder().setRegion(code).build().getDisplayCountry(Locale.US).ifEmpty { code }

private fun matches(occasion: Occasion, needle: String, title: String, entry: DoodleCatalog.Entry?): Boolean =
    title.contains(needle, ignoreCase = true) ||
        DoodleCatalog.kind(occasion).label.contains(needle, ignoreCase = true) ||
        entry?.regions.orEmpty().any { it.equals(needle, ignoreCase = true) || countryName(it).contains(needle, ignoreCase = true) }

private fun inDays(date: LocalDate, today: LocalDate): String = when (val days = ChronoUnit.DAYS.between(today, date)) {
    0L -> "Today"
    1L -> "Tomorrow"
    else -> "in $days Days"
}

/** A card's one line: how soon it shows here, or where it shows instead. */
internal fun shortWhen(entry: DoodleCatalog.Entry, today: LocalDate): String {
    val next = entry.nextHere
    return when {
        next == null && entry.regions.size == 1 -> "${countryName(entry.regions.first())} Only"
        next == null -> if (entry.regions.isEmpty()) "Not in Sight" else "Not Shown Here"
        ChronoUnit.DAYS.between(today, next) <= 99 -> inDays(next, today).replaceFirstChar { it.uppercase() }
        next.year == today.year -> next.format(SHORT_DATE)
        else -> next.format(SHORT_DATE_YEAR)
    }
}

/** The stage's date line: when it next shows here, or that it doesn't and when it next shows somewhere. */
internal fun longWhen(entry: DoodleCatalog.Entry, today: LocalDate, region: String): String {
    entry.nextHere?.let { next ->
        return if (next == today) "Showing Today" else "Next ${next.format(LONG_DATE)}, ${inDays(next, today)}"
    }
    val here = if (region.isEmpty()) "Your Region" else countryName(region)
    return entry.nextElsewhere?.let { (date, where) ->
        "Not Shown in $here. Next in ${countryName(where)} ${date.format(LONG_DATE)}."
    } ?: "Not Shown in the Next ${DoodleCatalog.HORIZON_YEARS} Years."
}

/** The stage's region line. */
internal fun whereText(entry: DoodleCatalog.Entry, region: String): String {
    if (entry.everywhere) return "Shown Everywhere"
    if (entry.regions.isEmpty()) return "Not Shown in This Time Zone"
    // This phone's region first, then the rest alphabetically by name.
    val names = entry.regions.sortedWith(compareBy<String> { it != region }.thenBy { countryName(it) }).map(::countryName)
    return when (names.size) {
        1 -> "Shown in ${names[0]}"
        2 -> "Shown in ${names[0]} and ${names[1]}"
        3 -> "Shown in ${names[0]}, ${names[1]} and ${names[2]}"
        else -> "Shown in ${names[0]}, ${names[1]} and ${names.size - 2} More Countries"
    }
}
