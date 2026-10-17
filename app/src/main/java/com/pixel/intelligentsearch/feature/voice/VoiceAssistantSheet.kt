package com.pixel.intelligentsearch.feature.voice

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive Voice Assistant Bottom Sheet.
 */
@Composable
fun VoiceAssistantBottomSheet(
    state: VoiceRecognitionState,
    /** Read only while drawing the waveform, so speech levels redraw it without recomposing the sheet. */
    audioAmplitude: () -> Float,
    spokenText: String,
    errorMessage: String?,
    onMicClick: () -> Unit,
    onDismiss: () -> Unit,
    onOpenFullSearch: (String) -> Unit,
    onQuickPromptClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* Consume clicks to prevent dismissing */ }
                ),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 12.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Drag handle pill
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Assistant",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Intelligent Assistant",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    RecognitionStateBadge(state = state)

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Live transcript or status prompt; a live region so TalkBack reads status changes and errors.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        !errorMessage.isNullOrBlank() -> {
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                        }
                        spokenText.isNotBlank() -> {
                            Text(
                                text = spokenText,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(onClickLabel = "Search for this") {
                                        onOpenFullSearch(spokenText)
                                    }
                            )
                        }
                        state == VoiceRecognitionState.LISTENING -> {
                            Text(
                                text = "Listening... Speak now",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center
                            )
                        }
                        state == VoiceRecognitionState.PROCESSING -> {
                            Text(
                                text = "Transcribing voice...",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                textAlign = TextAlign.Center
                            )
                        }
                        else -> {
                            Text(
                                text = "Tap the mic to search apps, contacts, files, or web",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Waveform canvas
                VoiceWaveformCanvas(
                    amplitude = audioAmplitude,
                    state = state,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Mic and controls row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    FilledTonalIconButton(
                        onClick = { onOpenFullSearch(spokenText) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Type Search",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    val micColor = when (state) {
                        VoiceRecognitionState.LISTENING -> MaterialTheme.colorScheme.primary
                        VoiceRecognitionState.ERROR -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.primaryContainer
                    }
                    val micIconTint = when (state) {
                        VoiceRecognitionState.LISTENING -> MaterialTheme.colorScheme.onPrimary
                        VoiceRecognitionState.ERROR -> MaterialTheme.colorScheme.onErrorContainer
                        else -> MaterialTheme.colorScheme.onPrimaryContainer
                    }

                    FilledIconButton(
                        onClick = onMicClick,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = micColor,
                            contentColor = micIconTint
                        ),
                        modifier = Modifier.size(64.dp)
                    ) {
                        val icon = if (state == VoiceRecognitionState.ERROR) {
                            Icons.Default.Refresh
                        } else {
                            Icons.Default.Mic
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = "Voice Search Mic",
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    FilledTonalIconButton(
                        // Nothing heard yet opens an empty search box rather than a placeholder query.
                        onClick = { onOpenFullSearch(spokenText) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Confirm Search",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Quick Suggestion Chips
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val quickPrompts = listOf(
                        "Search Apps" to "apps",
                        "Find Contacts" to "contacts",
                        "Search Files" to "files",
                        "Device Settings" to "settings",
                        "Search Web" to "web"
                    )

                    items(quickPrompts) { (label, prompt) ->
                        SuggestionChip(
                            onClick = { onQuickPromptClick(prompt) },
                            label = {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            shape = CircleShape,
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars))
            }
        }
    }
}

@Composable
private fun RecognitionStateBadge(state: VoiceRecognitionState) {
    val (label, bg, fg) = when (state) {
        VoiceRecognitionState.INITIALIZING -> Triple(
            "Connecting",
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        VoiceRecognitionState.LISTENING -> Triple(
            "Listening",
            GoogleBlue.copy(alpha = 0.15f),
            GoogleBlue
        )
        VoiceRecognitionState.PROCESSING -> Triple(
            "Thinking",
            GoogleYellow.copy(alpha = 0.15f),
            GoogleYellow
        )
        VoiceRecognitionState.RESULT -> Triple(
            "Ready",
            GoogleGreen.copy(alpha = 0.15f),
            GoogleGreen
        )
        VoiceRecognitionState.ERROR -> Triple(
            "Tap to Retry",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
    }

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = fg
        )
    }
}
