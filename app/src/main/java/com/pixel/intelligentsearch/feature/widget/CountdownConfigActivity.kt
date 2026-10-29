package com.pixel.intelligentsearch.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.data.widget.CountdownEvent
import com.pixel.intelligentsearch.core.data.widget.CountdownEventStore
import com.pixel.intelligentsearch.core.data.widget.WidgetDesign
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.core.theme.ThemeMode
import com.pixel.intelligentsearch.core.ui.expressive.matteShell
import com.pixel.intelligentsearch.core.ui.expressive.rememberMatteShellLook
import com.pixel.intelligentsearch.feature.settings.IconButton
import com.pixel.intelligentsearch.feature.settings.SettingsCard
import com.pixel.intelligentsearch.feature.settings.SettingsRow
import com.pixel.intelligentsearch.feature.settings.SettingsRowToggle
import com.pixel.intelligentsearch.feature.settings.SettingsSectionHeader
import com.pixel.intelligentsearch.feature.settings.ShellSelector
import com.pixel.intelligentsearch.feature.settings.TextButton
import com.pixel.intelligentsearch.feature.settings.oneLineAutoSize
import com.pixel.intelligentsearch.feature.widget.CountdownWidgetProvider.CountdownSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Sets up a countdown widget when it's placed, from the launcher's widget settings (Reconfigure), and when a widget
 * that isn't set up yet is tapped: the event's name, its date, whether it comes round every year and the design it
 * is drawn in. The widget itself is previewed at the top, drawn exactly as it will be. Saving draws the widget and
 * answers the launcher with its id; backing out of a new widget's setup leaves it unplaced.
 */
class CountdownConfigActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        // Backing out of a new widget's setup cancels placing it.
        setResult(RESULT_CANCELED, resultFor(appWidgetId))
        val prefs = SettingsManager.storedPrefs(this)
        val events = CountdownEventStore(prefs)
        val initial = events.get(appWidgetId)
        val today = CountdownWidgetProvider.now().toLocalDate()
        setContent {
            IntelligentSearchTheme(darkTheme = ThemeMode.from(prefs[SettingsManager.THEME]).isDark(isSystemInDarkTheme())) {
                CountdownSetupScreen(
                    initial = initial,
                    today = today,
                    previewViews = { draft ->
                        // Worked out off the main thread: the design's colors look up the theme and the shortcuts.
                        withContext(Dispatchers.IO) { CountdownWidgetProvider.buildSizes(this@CountdownConfigActivity, appWidgetId, draft, prefs) }
                    },
                    onSave = { event ->
                        events.put(appWidgetId, event)
                        CountdownWidgetProvider.updateAll(this)
                        setResult(RESULT_OK, resultFor(appWidgetId))
                        finish()
                    },
                    onBack = { finish() }
                )
            }
        }
    }

    companion object {
        /** What the launcher is answered with: the widget's id, as its setup requires. */
        internal fun resultFor(appWidgetId: Int): Intent = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

        /** Whether [appWidgetId] is a countdown widget (the launcher sets every widget up through SettingsActivity). */
        fun isCountdownWidget(context: Context, appWidgetId: Int): Boolean =
            appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID && runCatching {
                AppWidgetManager.getInstance(context)?.getAppWidgetInfo(appWidgetId)?.provider?.className ==
                    CountdownWidgetProvider::class.java.name
            }.getOrDefault(false)

        /**
         * Hands the launcher's setup of countdown widget [appWidgetId] on to this screen: its result (saved, or backed
         * out of) goes straight back to the launcher.
         */
        fun setupIntent(context: Context, appWidgetId: Int): Intent =
            Intent(context, CountdownConfigActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                .addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT)
    }
}

/** A day that comes round every year, offered as a quick pick under its short [label]. */
private data class CountdownPreset(val label: String, val title: String, val day: MonthDay)

private val PRESETS = listOf(
    CountdownPreset("Halloween", "Halloween", MonthDay.of(10, 31)),
    CountdownPreset("Christmas", "Christmas", MonthDay.of(12, 25)),
    CountdownPreset("New Year", "New Year's Day", MonthDay.of(1, 1))
)

private const val CUSTOM = "Custom"

/** The design choices: the search bar's (whichever was last saved in Widget Customization), or one of the two. */
private val DESIGNS: List<WidgetDesign?> = listOf(null, WidgetDesign.SYSTEM, WidgetDesign.MATERIAL)

private fun designLabel(design: WidgetDesign?): String = when (design) {
    null -> "Search Bar"
    WidgetDesign.SYSTEM -> "System"
    WidgetDesign.MATERIAL -> "Material"
}

/** The height a one-row widget is previewed at (a home screen row's), the width of the page. */
private const val PILL_PREVIEW_HEIGHT = 72

/** The size a 2 x 2 widget is previewed at. */
private const val TILE_PREVIEW = 164

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CountdownSetupScreen(
    initial: CountdownEvent?,
    today: LocalDate,
    /** The widget's views for [CountdownEvent] at each size, as [CountdownWidgetProvider.buildSizes] draws them. */
    previewViews: suspend (CountdownEvent) -> Map<SizeF, RemoteViews>,
    onSave: (CountdownEvent) -> Unit,
    onBack: () -> Unit
) {
    val view = LocalView.current
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val hapticEngine = remember(context) { com.pixel.intelligentsearch.core.haptics.PixelHapticEngine.get(context) }
    var title by rememberSaveable { mutableStateOf(initial?.title.orEmpty()) }
    var dateText by rememberSaveable { mutableStateOf((initial?.date ?: today.plusWeeks(1)).toString()) }
    var yearly by rememberSaveable { mutableStateOf(initial?.yearly ?: false) }
    var designName by rememberSaveable { mutableStateOf(initial?.design?.name) }
    var pickingDate by remember { mutableStateOf(false) }
    val date = LocalDate.parse(dateText)
    val design = WidgetDesign.entries.firstOrNull { it.name == designName }
    val canSave = title.isNotBlank()
    val event = CountdownEvent(title.trim(), date, yearly, design)
    // The preview counts down to something while the name is still empty.
    val draft = if (canSave) event else event.copy(title = "Event")

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Countdown", style = MaterialTheme.typography.titleLarge, maxLines = 1, autoSize = oneLineAutoSize(MaterialTheme.typography.titleLarge)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    TextButton(
                        enabled = canSave,
                        onClick = {
                            hapticEngine.performPredictiveBackHaptic(view)
                            onSave(event)
                        }
                    ) {
                        Text("Save", color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (canSave) 1f else 0.38f))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            // The widget as it will be, drawn by the widget itself, pinned at the top as Widget Customization's.
            CountdownPreview(draft, previewViews, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SettingsSectionHeader("Event")
                SettingsCard {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.Label,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(16.dp))
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it.take(CountdownEvent.MAX_TITLE_LENGTH) },
                            label = { Text("Event Name") },
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    SettingsRow(
                        title = "Date",
                        subtitle = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)) + ".",
                        icon = Icons.Outlined.CalendarMonth,
                        onClick = { pickingDate = true },
                        showDivider = true
                    )
                    SettingsRowToggle(
                        title = "Repeats Every Year",
                        subtitle = "Counts Down to the Next One Once the Day Has Passed.",
                        icon = Icons.Outlined.EventRepeat,
                        isChecked = yearly,
                        onCheckedChange = { yearly = it },
                        showDivider = false
                    )
                }

                SettingsSectionHeader("Quick Picks")
                SettingsCard(lit = false) {
                    val options = listOf(CUSTOM) + PRESETS.map { it.label }
                    val selected = PRESETS.firstOrNull { it.title == title.trim() && MonthDay.from(date) == it.day && yearly }?.label ?: CUSTOM
                    ShellSelector(
                        options = options,
                        selected = selected,
                        onSelect = { option ->
                            val preset = PRESETS.firstOrNull { it.label == option } ?: return@ShellSelector
                            title = preset.title
                            dateText = CountdownEvent(preset.title, preset.day.atYear(today.year), yearly = true).target(today).toString()
                            yearly = true
                        },
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) { option, _ ->
                        Text(option, style = MaterialTheme.typography.labelLarge, maxLines = 1, autoSize = oneLineAutoSize(MaterialTheme.typography.labelLarge))
                    }
                }

                SettingsSectionHeader("Design")
                SettingsCard(lit = false) {
                    ShellSelector(
                        options = DESIGNS,
                        selected = design,
                        onSelect = { designName = it?.name },
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) { option, _ ->
                        Text(designLabel(option), style = MaterialTheme.typography.labelLarge, maxLines = 1, autoSize = oneLineAutoSize(MaterialTheme.typography.labelLarge))
                    }
                    Text(
                        text = if (design == null) "Drawn in the Design Saved in Widget Customization, Like the Search Bar."
                        else "Drawn in the ${designLabel(design)} Design Saved in Widget Customization.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (pickingDate) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickingDate = false
                    val millis = picker.selectedDateMillis ?: return@TextButton
                    dateText = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } }
        ) { DatePicker(state = picker) }
    }
}

/**
 * The widget one row high and as a 2 x 2 tile, drawn from the views the widget itself shows ([previewViews]), on the
 * same stage as Widget Customization's preview (widgetPreviewStageColor), so it is exactly what the home screen will
 * show. Taps go nowhere, and TalkBack reads it as one preview.
 */
@Composable
private fun CountdownPreview(
    draft: CountdownEvent,
    previewViews: suspend (CountdownEvent) -> Map<SizeF, RemoteViews>,
    modifier: Modifier = Modifier
) {
    val views by produceState<Map<SizeF, RemoteViews>?>(null, draft) { value = previewViews(draft) }
    val look = rememberMatteShellLook(containerColor = com.pixel.intelligentsearch.feature.settings.widgetPreviewStageColor(), border = null)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .matteShell(look, RoundedCornerShape(24.dp))
            .padding(16.dp)
            .semantics(mergeDescendants = true) { contentDescription = "Widget Preview" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val pill = views?.get(CountdownSize.PILL.size)
        val tile = views?.get(CountdownSize.TILE.size)
        Box(Modifier.fillMaxWidth().height(PILL_PREVIEW_HEIGHT.dp)) { if (pill != null) RemoteViewsPreview(pill, Modifier.fillMaxSize()) }
        Box(Modifier.size(TILE_PREVIEW.dp)) { if (tile != null) RemoteViewsPreview(tile, Modifier.fillMaxSize()) }
    }
}

/** [views] drawn as a launcher would draw them, for looking at only. */
@Composable
private fun RemoteViewsPreview(views: RemoteViews, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            FrameLayout(context).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS }
        },
        update = { host ->
            host.removeAllViews()
            // Inflated as a launcher inflates it: the activity's AppCompat inflater would swap in its own views,
            // which RemoteViews refuses to set up.
            val shown = views.apply(host.context.applicationContext, host)
            // A preview: the widget's tap would open the search, or this screen again.
            shown.findViewById<View>(android.R.id.background)?.apply {
                setOnClickListener(null)
                isClickable = false
                isFocusable = false
            }
            host.addView(shown, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        },
        modifier = modifier
    )
}
