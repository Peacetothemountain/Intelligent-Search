package com.pixel.intelligentsearch.core.ui

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dehaze
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixel.intelligentsearch.core.answers.DictionaryEntry
import com.pixel.intelligentsearch.core.answers.ForecastDay
import com.pixel.intelligentsearch.core.answers.LiveAnswer
import com.pixel.intelligentsearch.core.answers.ScreenTimeCalculator
import com.pixel.intelligentsearch.core.answers.ScreenTimeReport
import com.pixel.intelligentsearch.core.answers.TranslationLanguages
import com.pixel.intelligentsearch.core.answers.WeatherCodes
import com.pixel.intelligentsearch.core.answers.WeatherReport
import com.pixel.intelligentsearch.core.ui.expressive.ExpressiveCornerTokens
import com.pixel.intelligentsearch.core.ui.expressive.ExpressiveMotionTokens
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/*
 * The overlay's live instant answers (definitions, forecasts, translations, screen time), in the same OneBox card as
 * the calculator and conversion answers: the same container, corner, header and chips. Their actions are chips, as on
 * those cards; the cards themselves don't press.
 */

/** Senses shown before the card is expanded. */
private const val COLLAPSED_SENSES = 3

/** Which of the theme's accent pairs a card's header uses, as the other OneBox cards do. */
internal enum class AnswerTone { Primary, Secondary, Tertiary }

/** The header every answer card starts with: an icon in its accent container, the kind of answer, and its title. */
@Composable
internal fun AnswerHeader(icon: ImageVector, label: String, title: String, subtitle: String? = null, tone: AnswerTone) {
    val scheme = MaterialTheme.colorScheme
    val (container, onContainer, accent) = when (tone) {
        AnswerTone.Primary -> Triple(scheme.primaryContainer, scheme.onPrimaryContainer, scheme.primary)
        AnswerTone.Secondary -> Triple(scheme.secondaryContainer, scheme.onSecondaryContainer, scheme.secondary)
        AnswerTone.Tertiary -> Triple(scheme.tertiaryContainer, scheme.onTertiaryContainer, scheme.tertiary)
    }
    val iconBox = @Composable {
        Box(
            modifier = Modifier.size(48.dp).background(container, ExpressiveCornerTokens.ShapeMedium),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = onContainer, modifier = Modifier.size(24.dp))
        }
    }
    // Titles wrap rather than being cut off; nothing in them is worth losing.
    val texts = @Composable { textModifier: Modifier ->
        Column(modifier = textModifier) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface,
                modifier = Modifier.semantics { heading() }
            )
            if (subtitle != null) {
                Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
        }
    }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // On a narrow screen or at large text sizes the icon goes above the text, which then gets the full width.
        val roomy = maxWidth / LocalDensity.current.fontScale >= HEADER_SIDE_BY_SIDE_MIN_WIDTH
        if (roomy) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                iconBox()
                Spacer(modifier = Modifier.width(16.dp))
                texts(Modifier.weight(1f))
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                iconBox()
                Spacer(modifier = Modifier.height(12.dp))
                texts(Modifier.fillMaxWidth())
            }
        }
    }
}

/** The width (in text-scaled dp) under which an answer card's header stacks its icon above its text. */
private val HEADER_SIDE_BY_SIDE_MIN_WIDTH = 160.dp

/** An action on an answer card, styled like the calculator card's Open Calculator chip; [emphasized] for the main one. */
@Composable
internal fun AnswerChip(label: String, icon: ImageVector, emphasized: Boolean = false, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    AssistChip(
        onClick = onClick,
        // The padding keeps a label that wraps (large text, narrow screen) off the chip's edges; one line stays 32dp.
        label = { Text(label, fontWeight = FontWeight.Medium, modifier = Modifier.padding(vertical = 6.dp)) },
        leadingIcon = { Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
        // The same pill as the other cards' chips at one line; a chip grown taller keeps rounded corners rather
        // than becoming a circle that cuts its label off.
        shape = ExpressiveCornerTokens.ShapeLarge,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (emphasized) scheme.primaryContainer else scheme.surfaceContainerHighest,
            labelColor = if (emphasized) scheme.onPrimaryContainer else scheme.onSurface,
            leadingIconContentColor = if (emphasized) scheme.onPrimaryContainer else scheme.primary
        ),
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.35f))
    )
}

/** A row of chips that flows onto a second line on narrow screens and at large text sizes. */
@Composable
internal fun AnswerChips(content: @Composable () -> Unit) {
    Spacer(modifier = Modifier.height(14.dp))
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically
    ) { content() }
}

@Composable
internal fun AnswerCard(modifier: Modifier, content: @Composable () -> Unit) {
    ExpressiveOneBoxCard(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        cornerRadius = ExpressiveCornerTokens.ExtraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        idleElevation = 2.dp,
        contentPadding = PaddingValues(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(ExpressiveMotionTokens.Spatial.ExpressiveDefault())
        ) { content() }
    }
}

// -------------------------------------------------------------------------------------------------
// DICTIONARY
// -------------------------------------------------------------------------------------------------

/**
 * A word's definition: the word, how it's said, and its top senses with an example each. More Definitions shows the
 * rest; Full Definition searches the web for it.
 */
@Composable
fun DefinitionOneBox(
    entry: DictionaryEntry,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    onOpenFullDefinition: () -> Unit
) {
    var expanded by rememberSaveable(entry.word) { mutableStateOf(initiallyExpanded) }
    val locale = LocalConfiguration.current.locales[0]
    val shown = if (expanded) entry.senses else entry.senses.take(COLLAPSED_SENSES)
    AnswerCard(modifier) {
        AnswerHeader(
            icon = Icons.AutoMirrored.Filled.MenuBook,
            label = "DICTIONARY",
            title = entry.word.replaceFirstChar { it.titlecase(locale) },
            subtitle = entry.phonetic,
            tone = AnswerTone.Tertiary
        )
        var lastPart: String? = null
        shown.forEachIndexed { index, sense ->
            Spacer(modifier = Modifier.height(if (index == 0) 14.dp else 10.dp))
            if (sense.partOfSpeech.isNotEmpty() && sense.partOfSpeech != lastPart) {
                Text(
                    text = sense.partOfSpeech,
                    style = MaterialTheme.typography.labelLarge,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
            lastPart = sense.partOfSpeech
            Text(
                text = "${index + 1}. ${sense.definition}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            sense.example?.let { example ->
                Text(
                    text = "“$example”",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        // The definitions are Wiktionary's, shared under CC BY-SA, which asks for this credit.
        Text(
            text = "From Wiktionary via Free Dictionary API",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        AnswerChips {
            if (entry.senses.size > COLLAPSED_SENSES) {
                AnswerChip(
                    label = if (expanded) "Fewer Definitions" else "More Definitions",
                    icon = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    onClick = { expanded = !expanded }
                )
            }
            AnswerChip(label = "Full Definition", icon = Icons.AutoMirrored.Filled.OpenInNew, onClick = onOpenFullDefinition)
        }
    }
}

// -------------------------------------------------------------------------------------------------
// WEATHER
// -------------------------------------------------------------------------------------------------

/** The icon for a WMO weather [code], with a moon for clear nights. */
internal fun weatherIcon(code: Int, isDay: Boolean): ImageVector = when (WeatherCodes.kind(code)) {
    WeatherCodes.Kind.CLEAR -> if (isDay) Icons.Default.WbSunny else Icons.Default.NightsStay
    WeatherCodes.Kind.PARTLY_CLOUDY -> if (isDay) Icons.Default.WbCloudy else Icons.Default.NightsStay
    WeatherCodes.Kind.CLOUDY -> Icons.Default.Cloud
    WeatherCodes.Kind.FOG -> Icons.Default.Dehaze
    WeatherCodes.Kind.DRIZZLE -> Icons.Default.Grain
    WeatherCodes.Kind.RAIN, WeatherCodes.Kind.FREEZING_RAIN -> Icons.Default.WaterDrop
    WeatherCodes.Kind.SHOWERS -> Icons.Default.Umbrella
    WeatherCodes.Kind.SNOW -> Icons.Default.AcUnit
    WeatherCodes.Kind.THUNDERSTORM -> Icons.Default.Thunderstorm
}

private fun degrees(value: Double): String = "${value.roundToInt()}°"

/**
 * The weather for a place: conditions now (or tomorrow's), the high and low, and the next hours. [is24Hour] follows
 * the phone's clock setting for the hour labels. Open Weather opens the weather app, or the web without one.
 */
@Composable
fun WeatherOneBox(
    report: WeatherReport,
    modifier: Modifier = Modifier,
    is24Hour: Boolean = android.text.format.DateFormat.is24HourFormat(LocalContext.current),
    onOpenWeather: () -> Unit
) {
    val locale = LocalConfiguration.current.locales[0]
    val hourFormat = remember(is24Hour, locale) { DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h a", locale) }
    val description = WeatherCodes.describe(report.code)
    AnswerCard(modifier) {
        AnswerHeader(
            icon = weatherIcon(report.code, report.isDay),
            label = if (report.day == ForecastDay.TODAY) "WEATHER · NOW" else "WEATHER · TOMORROW",
            title = report.placeName,
            tone = AnswerTone.Tertiary
        )
        Spacer(modifier = Modifier.height(12.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            itemVerticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = degrees(report.temperature) + report.unit.symbol.removePrefix("°"),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Column {
                Text(
                    text = description,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val details = buildList {
                    report.feelsLike?.let { add("Feels like ${degrees(it)}") }
                    add("H ${degrees(report.high)}  L ${degrees(report.low)}")
                }.joinToString(" · ")
                Text(text = details, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (report.hours.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            // Scrolls sideways when six hours don't fit (narrow screens, large text) rather than squeezing them.
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                report.hours.forEach { hour ->
                    val time = hour.time.format(hourFormat)
                    Column(
                        modifier = Modifier
                            .widthIn(min = 52.dp)
                            .padding(horizontal = 2.dp)
                            .semantics(mergeDescendants = true) {
                                contentDescription = "$time, ${degrees(hour.temperature)}, ${WeatherCodes.describe(hour.code)}"
                            },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = time, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        Spacer(modifier = Modifier.height(4.dp))
                        Icon(
                            imageVector = weatherIcon(hour.code, hour.isDay),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = degrees(hour.temperature), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(text = "Weather data by Open-Meteo.com", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AnswerChips {
            AnswerChip(label = "Open Weather", icon = Icons.AutoMirrored.Filled.OpenInNew, onClick = onOpenWeather)
        }
    }
}

/** Weather for "here" with no home location: offers the phone's approximate location or choosing a home location. */
@Composable
fun WeatherLocationOneBox(
    modifier: Modifier = Modifier,
    onUseApproximateLocation: () -> Unit,
    onSetHomeLocation: () -> Unit
) {
    AnswerCard(modifier) {
        AnswerHeader(icon = Icons.Default.WbSunny, label = "WEATHER", title = "Where Are You?", tone = AnswerTone.Tertiary)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Choose a home location for weather answers, or let them use your phone's approximate location.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        AnswerChips {
            AnswerChip(label = "Set Home Location", icon = Icons.Default.EditLocationAlt, emphasized = true, onClick = onSetHomeLocation)
            AnswerChip(label = "Use Approximate Location", icon = Icons.Default.MyLocation, onClick = onUseApproximateLocation)
        }
    }
}

// -------------------------------------------------------------------------------------------------
// TRANSLATION
// -------------------------------------------------------------------------------------------------

/**
 * Text to translate, handed to Google Translate: the languages, the text, and the chip that opens it there (the app
 * when the phone has it, else the web page).
 */
@Composable
fun TranslationOneBox(
    answer: LiveAnswer.Translation,
    modifier: Modifier = Modifier,
    onOpenGoogleTranslate: () -> Unit
) {
    val locale = LocalConfiguration.current.locales[0]
    val sourceName = answer.source?.let { TranslationLanguages.displayName(it, locale) }
    val targetName = TranslationLanguages.displayName(answer.target, locale)
    AnswerCard(modifier) {
        AnswerHeader(
            icon = Icons.Default.Translate,
            label = "TRANSLATION",
            title = if (sourceName != null) "$sourceName → $targetName" else "To $targetName",
            tone = AnswerTone.Primary
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = answer.text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
        AnswerChips {
            AnswerChip(label = "Translate in Google Translate", icon = Icons.AutoMirrored.Filled.OpenInNew, emphasized = true, onClick = onOpenGoogleTranslate)
        }
    }
}

// -------------------------------------------------------------------------------------------------
// SCREEN TIME
// -------------------------------------------------------------------------------------------------

/**
 * Today's screen time and the five apps that took most of it, each with a bar against the busiest; without Usage
 * Access, what it needs and a way to allow it.
 */
@Composable
fun ScreenTimeOneBox(
    report: ScreenTimeReport?,
    modifier: Modifier = Modifier,
    onAllowUsageAccess: () -> Unit,
    onOpenDetails: (() -> Unit)?
) {
    AnswerCard(modifier) {
        if (report == null) {
            AnswerHeader(icon = Icons.Default.HourglassBottom, label = "SCREEN TIME", title = "See Your Screen Time", tone = AnswerTone.Secondary)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Allow Usage Access to see today's screen time here. It's read on your phone and never leaves it.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AnswerChips {
                AnswerChip(label = "Allow Usage Access", icon = Icons.Default.HourglassBottom, emphasized = true, onClick = onAllowUsageAccess)
            }
            return@AnswerCard
        }
        AnswerHeader(
            icon = Icons.Default.HourglassBottom,
            label = "SCREEN TIME · TODAY",
            title = ScreenTimeCalculator.formatDuration(report.totalMs),
            tone = AnswerTone.Secondary
        )
        val busiest = report.topApps.maxOfOrNull { it.durationMs }?.coerceAtLeast(1L) ?: 1L
        report.topApps.forEach { app ->
            Spacer(modifier = Modifier.height(12.dp))
            val duration = ScreenTimeCalculator.formatDuration(app.durationMs)
            Row(
                modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "${app.label}, $duration" },
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScreenTimeAppIcon(app.packageName, app.label)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    // The time goes under the name when both don't fit on one line, so neither is cut off.
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        itemVerticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = app.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(text = duration, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest, ExpressiveCornerTokens.ShapeFull)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth((app.durationMs.toFloat() / busiest).coerceIn(0.02f, 1f))
                                .height(6.dp)
                                .background(MaterialTheme.colorScheme.secondary, ExpressiveCornerTokens.ShapeFull)
                        )
                    }
                }
            }
        }
        if (onOpenDetails != null) {
            AnswerChips {
                AnswerChip(label = "Open Digital Wellbeing", icon = Icons.AutoMirrored.Filled.OpenInNew, onClick = onOpenDetails)
            }
        }
    }
}

/** An app's themed icon in a circle, or its initial while there is none (or the app is gone). */
@Composable
private fun ScreenTimeAppIcon(packageName: String, label: String) {
    val locale = LocalConfiguration.current.locales[0]
    val icon = rememberAppIcon(packageName, iconPack = null).themed
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(ExpressiveCornerTokens.ShapeFull)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        val loaded = icon
        if (loaded != null) {
            Image(
                bitmap = loaded.bitmap,
                contentDescription = null,
                modifier = Modifier.size(if (loaded.isMonochrome) 24.dp else 36.dp),
                colorFilter = if (loaded.isMonochrome) ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant) else null
            )
        } else {
            Text(
                text = label.take(1).uppercase(locale),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
