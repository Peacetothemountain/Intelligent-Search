package com.pixel.intelligentsearch.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import com.pixel.intelligentsearch.core.ui.wholeWords
import androidx.compose.ui.unit.dp

/**
 * The app's warning card: an amber warning icon, a title and what will happen, then "Don't show this again" on the far
 * left and Discard and the accept button on the right, on lines of their own where they don't fit side by side
 * ([WarningCardButtons]). Widget Customization's unsaved-changes card, Max Quick Panel
 * Apps' remove-apps card and Manage Hidden Apps' unsaved-changes card all use it, so they look and read alike.
 *
 * [onDontShowAgain] is the caller's to define (it should also remember the choice); [onDismissRequest] is a tap
 * outside or back, which leaves things as they were.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WarningCard(
    title: String,
    message: String,
    acceptLabel: String,
    onAccept: () -> Unit,
    onDiscard: () -> Unit,
    onDontShowAgain: () -> Unit,
    onDismissRequest: () -> Unit
) {
    BasicAlertDialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                WarningCardButtons(
                    dontShowAgain = {
                        TextButton(onClick = onDontShowAgain, contentPadding = PaddingValues(horizontal = 6.dp)) {
                            Text(
                                text = "Don't show this again",
                                // At the largest text it may need two lines: they break between words.
                                style = MaterialTheme.typography.labelMedium.wholeWords(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    discard = {
                        TextButton(onClick = onDiscard, contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text(
                                text = "Discard",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    accept = {
                        Button(
                            onClick = onAccept,
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text(text = acceptLabel, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        }
                    }
                )
            }
        }
    }
}

/**
 * [WarningCard]'s three buttons, each label kept whole on one line. They share a row ([dontShowAgain] at the start,
 * [discard] and [accept] at the end) where they fit at their full width; otherwise Discard and the accept button keep
 * their row and "Don't show this again" moves to a line under them; and where even those two don't fit, as at the
 * largest text sizes, each button gets a line of its own at the end, the accept button on top. A button is never
 * squeezed into what the others leave, which turned "Save" into one letter per line.
 */
@Composable
private fun WarningCardButtons(
    dontShowAgain: @Composable () -> Unit,
    discard: @Composable () -> Unit,
    accept: @Composable () -> Unit
) {
    Layout(
        contents = listOf(dontShowAgain, discard, accept),
        modifier = Modifier.fillMaxWidth()
    ) { (dontShowAgainMeasurables, discardMeasurables, acceptMeasurables), constraints ->
        val width = constraints.maxWidth
        val gap = 8.dp.roundToPx()
        val lineGap = 4.dp.roundToPx()
        val dontShow = dontShowAgainMeasurables.single()
        val discardButton = discardMeasurables.single()
        val acceptButton = acceptMeasurables.single()
        // Each button's width with its label on one line.
        val dontShowWidth = dontShow.maxIntrinsicWidth(Constraints.Infinity)
        val pairWidth = discardButton.maxIntrinsicWidth(Constraints.Infinity) + gap + acceptButton.maxIntrinsicWidth(Constraints.Infinity)
        val loose = Constraints(maxWidth = width)
        val d = dontShow.measure(loose)
        val x = discardButton.measure(loose)
        val a = acceptButton.measure(loose)
        when {
            dontShowWidth + gap + pairWidth <= width -> {
                val height = maxOf(d.height, x.height, a.height)
                layout(width, height) {
                    d.placeRelative(0, (height - d.height) / 2)
                    a.placeRelative(width - a.width, (height - a.height) / 2)
                    x.placeRelative(width - a.width - gap - x.width, (height - x.height) / 2)
                }
            }
            pairWidth <= width -> {
                val pairHeight = maxOf(x.height, a.height)
                layout(width, pairHeight + lineGap + d.height) {
                    a.placeRelative(width - a.width, (pairHeight - a.height) / 2)
                    x.placeRelative(width - a.width - gap - x.width, (pairHeight - x.height) / 2)
                    d.placeRelative(0, pairHeight + lineGap)
                }
            }
            else -> layout(width, a.height + x.height + d.height + 2 * lineGap) {
                a.placeRelative(width - a.width, 0)
                x.placeRelative(width - x.width, a.height + lineGap)
                d.placeRelative(width - d.width, a.height + x.height + 2 * lineGap)
            }
        }
    }
}
