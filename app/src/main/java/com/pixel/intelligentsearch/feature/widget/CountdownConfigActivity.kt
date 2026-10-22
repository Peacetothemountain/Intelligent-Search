package com.pixel.intelligentsearch.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.data.widget.CountdownEvent
import com.pixel.intelligentsearch.core.data.widget.CountdownEvents
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.core.theme.ThemeMode
import com.pixel.intelligentsearch.core.ui.expressive.MatteShell
import com.pixel.intelligentsearch.feature.settings.SettingsCard
import com.pixel.intelligentsearch.feature.settings.SettingsRow
import com.pixel.intelligentsearch.feature.settings.SettingsRowToggle
import com.pixel.intelligentsearch.feature.settings.SettingsSectionHeader
import com.pixel.intelligentsearch.feature.settings.ShellSelector
import com.pixel.intelligentsearch.feature.settings.oneLineAutoSize
import java.time.Instant
import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Sets up a countdown widget when it's placed (and again from the launcher's widget settings): the event's name, its
 * date and whether it comes round every year. Saving draws the widget; backing out leaves it unplaced.
 */
class CountdownConfigActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        // Backing out of a new widget's setup cancels placing it.
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        val prefs = SearchWidgetProvider.getSafeSharedPreferences(this)
        val events = CountdownEvents(prefs)
        val initial = events.get(appWidgetId)
        val today = CountdownWidgetProvider.now().toLocalDate()
        setContent {
            IntelligentSearchTheme(darkTheme = ThemeMode.from(prefs[SettingsManager.THEME]).isDark(isSystemInDarkTheme())) {
                CountdownSetupScreen(
                    initial = initial,
                    today = today,
                    onSave = { event ->
                        events.put(appWidgetId, event)
                        CountdownWidgetProvider.updateAll(this)
                        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
                        finish()
                    },
                    onBack = { finish() }
                )
            }
        }
    }

    companion object {
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

/** A day that comes round every year, offered as a quick pick. */
private data class CountdownPreset(val title: String, val day: MonthDay)

private val PRESETS = listOf(
    CountdownPreset("Halloween", MonthDay.of(10, 31)),
    CountdownPreset("Christmas", MonthDay.of(12, 25)),
    CountdownPreset("New Year's Day", MonthDay.of(1, 1))
)

private const val CUSTOM = "Custom"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CountdownSetupScreen(
    initial: CountdownEvent?,
    today: LocalDate,
    onSave: (CountdownEvent) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var title by rememberSaveable { mutableStateOf(initial?.title.orEmpty()) }
    var dateText by rememberSaveable { mutableStateOf((initial?.date ?: today.plusWeeks(1)).toString()) }
    var yearly by rememberSaveable { mutableStateOf(initial?.yearly ?: false) }
    var pickingDate by remember { mutableStateOf(false) }
    val date = LocalDate.parse(dateText)
    val draft = CountdownEvent(title.trim().ifEmpty { "Event" }, date, yearly)
    val canSave = title.isNotBlank()

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
                    TextButton(enabled = canSave, onClick = { onSave(draft.copy(title = title.trim())) }) {
                        Text("Save", color = if (canSave) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // What the widget will show, in its colours.
            val (number, caption) = CountdownWidgetProvider.texts(context, draft, today)
            MatteShell(
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                shape = RoundedCornerShape(24.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Box(Modifier.fillMaxWidth().heightIn(min = 120.dp).padding(16.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(number, style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1, autoSize = oneLineAutoSize(MaterialTheme.typography.displayMedium))
                        Text(caption, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
                    }
                }
            }

            SettingsSectionHeader("Event")
            SettingsCard(lit = false) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(CountdownEvent.MAX_TITLE_LENGTH) },
                    label = { Text("Event Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                )
                SettingsRow(
                    title = "Date",
                    subtitle = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)) + ".",
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
                val options = listOf(CUSTOM) + PRESETS.map { it.title }
                val selected = PRESETS.firstOrNull { it.title == title.trim() && MonthDay.from(date) == it.day && yearly }?.title ?: CUSTOM
                ShellSelector(
                    options = options,
                    selected = selected,
                    onSelect = { option ->
                        val preset = PRESETS.firstOrNull { it.title == option } ?: return@ShellSelector
                        title = preset.title
                        dateText = CountdownEvent(preset.title, preset.day.atYear(today.year), yearly = true).target(today).toString()
                        yearly = true
                    },
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) { option, _ ->
                    Text(option, style = MaterialTheme.typography.labelMedium, maxLines = 1, autoSize = oneLineAutoSize(MaterialTheme.typography.labelMedium))
                }
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
