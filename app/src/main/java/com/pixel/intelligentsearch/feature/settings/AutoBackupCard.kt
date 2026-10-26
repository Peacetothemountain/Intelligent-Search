package com.pixel.intelligentsearch.feature.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.pixel.intelligentsearch.core.backup.AutoBackup
import com.pixel.intelligentsearch.core.theme.GoogleSansFlex
import com.pixel.intelligentsearch.di.appEntryPoint
import kotlinx.coroutines.launch

/**
 * The Automatic Backup card of the Encrypted Backup page. Turning it on asks for a folder and uses the
 * passphrase typed above, so the weekly backups restore on any phone with that passphrase.
 */
@Composable
internal fun AutoBackupCard(obtainPassphrase: (onReady: (String?) -> Unit) -> Unit) {
    val context = LocalContext.current
    val autoBackup = remember(context) { AutoBackup(context.applicationContext) }
    val scope = rememberCoroutineScope()
    // Bumped after every change so the status below is read again.
    var revision by remember { mutableIntStateOf(0) }
    // Work can also finish while the card is away (the weekly job, or a run that outlived the card), so the status
    // is read again whenever the page comes back.
    LifecycleResumeEffect(autoBackup) {
        revision++
        onPauseOrDispose { }
    }
    var isRunning by remember { mutableStateOf(false) }
    val enabled = remember(revision) { autoBackup.isEnabled }
    // A DocumentsProvider query (possibly a slow cloud provider), so it runs off the main thread.
    val folderName by produceState<String?>(initialValue = null, revision) {
        value = withContext(Dispatchers.IO) { autoBackup.folderName() }
    }
    val lastSuccess = remember(revision) { autoBackup.lastSuccessMs }
    val lastFailure = remember(revision) { autoBackup.lastFailure }

    // The passphrase chosen for turning backups on, in memory only while the folder picker is open.
    var pendingPassphrase by remember { mutableStateOf<String?>(null) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { folder ->
        val passphrase = pendingPassphrase.orEmpty()
        pendingPassphrase = null
        if (folder == null) return@rememberLauncherForActivityResult
        // The passphrase isn't kept in saved state, so if Android recreated the page while the picker was open it is
        // empty now; ask again rather than turn on backups nobody can restore.
        if (passphrase.length < AutoBackup.MIN_PASSPHRASE_LENGTH) {
            Toast.makeText(context, "Type your passphrase again, then choose the folder.", Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }
        // Deriving the key (600k PBKDF2 rounds) and wrapping it in the Keystore takes seconds: never on the main thread.
        // It finishes even if the page is left or relocks meanwhile; the toast then isn't shown, and the card reads the
        // result when it is back.
        val secret = passphrase.toCharArray()
        scope.launch {
            val turnedOn = withContext(NonCancellable + Dispatchers.IO) { autoBackup.enable(folder, secret) }
            Toast.makeText(
                context,
                if (turnedOn) "Automatic backup is on" else "Couldn't protect the backup key on this phone",
                Toast.LENGTH_SHORT
            ).show()
            revision++
        }
    }

    SettingsCard(modifier = Modifier.backupCard("backup_card_auto")) {
        BackupCardTitle("Automatic Backup")
        BackupCardDescription(
            "Weekly to a Folder You Choose, Encrypted with Your Passphrase Above. Keeps the Latest ${AutoBackup.KEEP}."
        )
        Spacer(modifier = Modifier.height(4.dp))
        if (!enabled) {
            Button(
                onClick = {
                    obtainPassphrase { chosen ->
                        if (chosen == null || chosen.length < AutoBackup.MIN_PASSPHRASE_LENGTH) {
                            Toast.makeText(
                                context,
                                "Enter a passphrase of at least ${AutoBackup.MIN_PASSPHRASE_LENGTH} characters above first.",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            pendingPassphrase = chosen
                            folderPicker.launch(null)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(percent = 50)
            ) {
                Icon(Icons.Outlined.FolderOpen, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Choose Folder and Turn On", fontFamily = GoogleSansFlex)
            }
        } else {
            val lastText = if (lastSuccess > 0L) {
                "Last backup " + android.text.format.DateUtils.getRelativeTimeSpanString(lastSuccess, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS)
            } else {
                "No backup yet"
            }
            Text(
                "On · Saving to ${folderName ?: "your folder"} · $lastText",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = GoogleSansFlex,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            if (lastFailure != null) {
                Text(
                    lastFailure,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        isRunning = true
                        // Like turning backups on, a run finishes even if the card goes away meanwhile.
                        scope.launch {
                            val ok = withContext(NonCancellable + Dispatchers.IO) {
                                autoBackup.runOnce(context.appEntryPoint.backupManager()).isSuccess
                            }
                            isRunning = false
                            revision++
                            Toast.makeText(context, if (ok) "Backup saved" else "Backup didn't finish", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !isRunning,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(percent = 50)
                ) {
                    Icon(Icons.Outlined.Backup, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isRunning) "Backing Up…" else "Back Up Now", fontFamily = GoogleSansFlex)
                }
                TextButton(onClick = {
                    autoBackup.disable()
                    revision++
                    Toast.makeText(context, "Automatic backup is off.", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Turn Off", fontFamily = GoogleSansFlex)
                }
            }
        }
    }
}
