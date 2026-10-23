package com.pixel.intelligentsearch.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.rememberSetting
import com.pixel.intelligentsearch.core.glance.NotificationAccess
import com.pixel.intelligentsearch.di.appEntryPoint
import com.pixel.intelligentsearch.feature.search.GlanceClock
import com.pixel.intelligentsearch.feature.search.GlanceClockFace
import com.pixel.intelligentsearch.feature.search.GlanceClockStyle
import com.pixel.intelligentsearch.feature.search.LocalGlanceClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Test tag of a Clock Style choice: the prefix and the style's stored value. */
internal const val CLOCK_STYLE_TAG_PREFIX = "clock_style_"

/**
 * Whether each At a Glance item can show, given what it reads from: timers and Media Controls need notification
 * access, Birthdays contact search and the contacts. A switch shows as on only when its item can actually show.
 */
internal data class GlancePrerequisites(
    val notificationAccess: Boolean,
    val contactsAllowed: Boolean,
    val searchContacts: Boolean
) {
    val timers: Boolean get() = notificationAccess
    val media: Boolean get() = notificationAccess
    val birthdays: Boolean get() = contactsAllowed && searchContacts
}

/**
 * Search Behavior's At a Glance card: the section's own switch, then one per item in the order they show, then Show
 * Clock and, while the clock shows, its Clock Style. A switch shows as on only when its item can show
 * ([GlancePrerequisites]); turning one on without what it needs asks for it the app's usual way (notification
 * access, the contacts) and switches on what that needs too.
 */
@Composable
internal fun AtAGlanceSettingsCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var searchContacts by rememberSetting(SettingsManager.SEARCH_CONTACTS)
    // Granted or revoked in Android's settings while away: read again on the way back.
    var notificationAccess by remember { mutableStateOf(NotificationAccess.isGranted(context)) }
    var contactsPermission by remember { mutableStateOf(contactsAllowed(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationAccess = NotificationAccess.isGranted(context)
                contactsPermission = contactsAllowed(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val ready = GlancePrerequisites(notificationAccess, contactsPermission, searchContacts)

    SettingsSectionHeader("At a Glance")
    SettingsCard {
        var enabled by rememberSetting(SettingsManager.GLANCE_ENABLED)
        SettingsRowToggle(
            title = "At a Glance",
            subtitle = "Show Timely Cards Above the Empty Search.",
            icon = Icons.Outlined.Today,
            isChecked = enabled,
            onCheckedChange = { enabled = it },
            showDivider = enabled
        )
        AttachedRows(visible = enabled) {
            Column {
                var timers by rememberSetting(SettingsManager.GLANCE_TIMERS)
                var media by rememberSetting(SettingsManager.GLANCE_MEDIA)
                // Timers and media share the one notification access grant.
                val timersAccess = rememberNotificationAccessRequest { granted ->
                    notificationAccess = NotificationAccess.isGranted(context)
                    if (granted) {
                        timers = true
                        NotificationAccess.start(context)
                    }
                }
                NotificationAccessDialog(timersAccess)
                SettingsRowToggle(
                    title = "Timers & Stopwatch",
                    subtitle = "Show Running Timers and Stopwatches. Uses Notification Access.",
                    icon = Icons.Outlined.Timer,
                    isChecked = timers && ready.timers,
                    onCheckedChange = { on ->
                        when {
                            !on -> timers = false
                            ready.timers -> timers = true
                            else -> timersAccess.request()
                        }
                    }
                )
                var alarm by rememberSetting(SettingsManager.GLANCE_ALARM)
                SettingsRowToggle(
                    title = "Next Alarm",
                    subtitle = "Show Your Next Alarm Within 12 Hours.",
                    icon = Icons.Outlined.Alarm,
                    isChecked = alarm,
                    onCheckedChange = { alarm = it }
                )
                val mediaAccess = rememberNotificationAccessRequest { granted ->
                    notificationAccess = NotificationAccess.isGranted(context)
                    if (granted) {
                        media = true
                        NotificationAccess.start(context)
                    }
                }
                NotificationAccessDialog(mediaAccess)
                SettingsRowToggle(
                    title = "Media Controls",
                    subtitle = "Play, Pause and Skip What's Playing. Uses Notification Access.",
                    icon = Icons.Outlined.LibraryMusic,
                    isChecked = media && ready.media,
                    onCheckedChange = { on ->
                        when {
                            !on -> media = false
                            ready.media -> media = true
                            else -> mediaAccess.request()
                        }
                    }
                )
                var birthdays by rememberSetting(SettingsManager.GLANCE_BIRTHDAYS)
                val contactsRequest = rememberContactsRequest { granted ->
                    contactsPermission = contactsAllowed(context)
                    if (granted) {
                        searchContacts = true
                        birthdays = true
                        val index = context.appEntryPoint.corpusIndexManager()
                        scope.launch(Dispatchers.IO) { index.reindexContacts() }
                    }
                }
                SettingsRowToggle(
                    title = "Birthdays",
                    subtitle = "Show Contacts' Birthdays This Week. Uses Contact Search.",
                    icon = Icons.Outlined.Cake,
                    isChecked = birthdays && ready.birthdays,
                    onCheckedChange = { on ->
                        when {
                            !on -> birthdays = false
                            ready.birthdays -> birthdays = true
                            else -> contactsRequest.request()
                        }
                    }
                )
                var showClock by rememberSetting(SettingsManager.GLANCE_CLOCK)
                SettingsRowToggle(
                    title = "Show Clock",
                    subtitle = "Show a Clock in the Corner of At a Glance.",
                    icon = Icons.Outlined.Schedule,
                    isChecked = showClock,
                    onCheckedChange = { showClock = it },
                    showDivider = false
                )
                AttachedRows(visible = showClock) {
                    var style by rememberSetting(SettingsManager.GLANCE_CLOCK_STYLE)
                    ClockStylePicker(
                        selected = GlanceClockStyle.from(style),
                        onSelect = { style = it.storedValue }
                    )
                }
            }
        }
    }
}

/** The time every Clock Style choice shows, 10:10, the hands framing the face as clock makers set them. */
private val PreviewClock: GlanceClock by lazy {
    val at = LocalDate.of(2026, 1, 1).atTime(LocalTime.of(10, 10, 0)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    GlanceClock(now = { at }, ticks = false)
}

/**
 * Clock Style: the clock faces side by side, each a still 10:10 on its own tile with its name under it, the chosen
 * one on the secondary container with a primary ring. One tap picks; TalkBack hears each as a radio button.
 */
@Composable
internal fun ClockStylePicker(selected: GlanceClockStyle, onSelect: (GlanceClockStyle) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp)) {
        Text(
            text = "Clock Style",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier
                .padding(start = 40.dp, bottom = 10.dp)
                .semantics { heading() }
        )
        Row(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CompositionLocalProvider(LocalGlanceClock provides PreviewClock) {
                for (style in GlanceClockStyle.entries) {
                    val isSelected = style == selected
                    val tileShape = RoundedCornerShape(if (isSelected) 22.dp else 18.dp)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .testTag(CLOCK_STYLE_TAG_PREFIX + style.storedValue)
                            .clearAndSetSemantics {
                                contentDescription = "${style.label} clock"
                                role = Role.RadioButton
                                this.selected = isSelected
                            }
                            .bouncyClickable(shape = tileShape, role = Role.RadioButton) { if (!isSelected) onSelect(style) },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .background(if (isSelected) scheme.secondaryContainer else scheme.surfaceContainerHigh, tileShape)
                                .border(2.dp, if (isSelected) scheme.primary else Color.Transparent, tileShape),
                            contentAlignment = Alignment.Center
                        ) {
                            GlanceClockFace(size = 44.dp, style = style)
                        }
                        Text(
                            text = style.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) scheme.primary else scheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            autoSize = oneLineAutoSize(MaterialTheme.typography.labelMedium),
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        }
    }
}
