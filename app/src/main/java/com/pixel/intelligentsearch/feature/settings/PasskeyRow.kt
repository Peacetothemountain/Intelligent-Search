package com.pixel.intelligentsearch.feature.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.pixel.intelligentsearch.core.backup.BackupPasskey
import com.pixel.intelligentsearch.core.backup.PasskeyException
import com.pixel.intelligentsearch.core.util.findActivity
import kotlinx.coroutines.launch

/**
 * The passkey part of the Encrypted Backup page's Encryption card, above the passphrase: backups encrypted with a
 * passkey restore on any phone signed in to the password manager that holds it, unlocked with the screen lock, with no
 * passphrase to remember. [passkeySetUp] is whether one is set up; [onChanged] tells the page when that changes. Below
 * Android 14 ([supported] false) it shows nothing: the passphrase is the way to restore elsewhere there.
 */
@Composable
internal fun PasskeyRow(
    passkey: BackupPasskey,
    passkeySetUp: Boolean,
    supported: Boolean,
    onChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var creating by remember { mutableStateOf(false) }

    if (!supported) return
    androidx.compose.foundation.layout.Column(modifier = Modifier.testTag("backup_passkey")) {
        if (!passkeySetUp) {
            FilledTonalButton(
                onClick = {
                    val activity = context.findActivity() ?: return@FilledTonalButton
                    creating = true
                    scope.launch {
                        try {
                            passkey.create(activity)
                            onChanged(true)
                            Toast.makeText(context, "Passkey created. Your backups now use it.", Toast.LENGTH_SHORT).show()
                        } catch (e: PasskeyException) {
                            if (!e.cancelled) Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
                        } finally {
                            creating = false
                        }
                    }
                },
                enabled = !creating,
                // The passphrase field below keeps its own space above, so the button needs little of its own.
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp),
                shape = RoundedCornerShape(percent = 50)
            ) {
                Icon(Icons.Outlined.Key, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (creating) "Creating Passkey…" else "Create Passkey")
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    "Passkey On",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                // Only this phone forgets it: the passkey stays in the password manager, so backups made with it
                // still restore. Remove the passkey itself there.
                OutlinedButton(
                    onClick = {
                        passkey.forget()
                        onChanged(false)
                        Toast.makeText(context, "Passkey removed from this phone. Backups made with it still restore.", Toast.LENGTH_LONG).show()
                    },
                    shape = RoundedCornerShape(percent = 50)
                ) {
                    Text("Remove")
                }
            }
        }
    }
}
