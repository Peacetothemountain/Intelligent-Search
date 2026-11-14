package com.pixel.intelligentsearch.feature.settings

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pixel.intelligentsearch.core.data.widget.WidgetProfileDesign
import com.pixel.intelligentsearch.core.data.widget.WidgetSetupCode
import com.pixel.intelligentsearch.core.system.launchSafeIntent
import com.pixel.intelligentsearch.core.ui.symbols.Symbols

/**
 * Shares the editor's look as a setup code, or loads one someone sent into [profile]. [code] makes the code for what
 * the editor shows now; [read] gives the look a pasted text holds, or null when it holds none. A loaded look goes
 * into the editor only ([onUse]): the widget changes when the user saves, as with any other change here.
 */
@Composable
internal fun WidgetSetupCodeDialog(
    profile: Int,
    code: () -> String,
    read: (String) -> WidgetProfileDesign?,
    onUse: (WidgetProfileDesign) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var pasted by rememberSaveable { mutableStateOf("") }
    val incoming = remember(pasted) { if (pasted.isBlank()) null else read(pasted) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Widget Setup Code", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "A setup is this profile's look: its colors, outline, corners and buttons. Nothing else is shared.",
                    style = MaterialTheme.typography.bodyMedium
                )
                FilledTonalButton(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, "My Intelligent Search widget setup:\n${code()}")
                        launchSafeIntent(context, Intent.createChooser(send, "Share Setup").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Share This Setup")
                }
                OutlinedTextField(
                    value = pasted,
                    // A paste far longer than any code is not kept.
                    onValueChange = { pasted = it.take(WidgetSetupCode.MAX_TEXT_LENGTH) },
                    label = { Text("Paste a Setup Code") },
                    textStyle = MaterialTheme.typography.bodySmall,
                    maxLines = 4,
                    isError = pasted.isNotBlank() && incoming == null,
                    trailingIcon = {
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val text = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                            if (!text.isNullOrBlank()) pasted = text.take(WidgetSetupCode.MAX_TEXT_LENGTH)
                        }) {
                            Icon(Symbols.Outlined.ContentPaste, contentDescription = "Paste")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = when {
                        pasted.isBlank() -> "A code loads into Profile $profile. Your widget changes only when you save."
                        incoming == null -> "That isn't a setup code, or part of it is missing."
                        else -> "Ready to load into Profile $profile."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (pasted.isNotBlank() && incoming == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = incoming != null,
                onClick = {
                    incoming?.let(onUse)
                    Toast.makeText(context, "Setup loaded into Profile $profile. Save to put it on your widget.", Toast.LENGTH_LONG).show()
                    onDismiss()
                }
            ) {
                Text("Load into Profile $profile")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
