package com.pixel.intelligentsearch.feature.settings

import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.data.SettingsManager
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.getValue
import com.pixel.intelligentsearch.core.util.findActivity
import com.pixel.intelligentsearch.di.appEntryPoint
import android.content.SharedPreferences
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.exoplayer.ExoPlayer
import com.pixel.intelligentsearch.core.backup.AutoBackup
import com.pixel.intelligentsearch.core.backup.EncryptedBackupEnvelope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    prefs: SharedPreferences,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val viewModel = LocalSettingsViewModel.current
    val hapticEngine = remember { com.pixel.intelligentsearch.core.haptics.PixelHapticEngine.get(context) }

    var passphrase by remember { mutableStateOf("") }
    // The passphrase an export in progress will use, kept in memory only while the file picker is open.
    var exportPassphrase by remember { mutableStateOf<String?>(null) }
    // Whether the export in progress was started with a passphrase. The passphrase itself is never put in saved state;
    // if Android recreates the page while the file picker is open, it comes back empty, and the export must not then
    // quietly become a this-phone-only backup.
    var exportWantsPassphrase by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var passphraseVisible by remember { mutableStateOf(false) }
    var isPassphraseSavedOnChip by remember { mutableStateOf(false) }
    val hardwareInfo = remember { com.pixel.intelligentsearch.core.security.HardwareSecurityDetector.detect(context) }
    // Where this page's keys are kept: the security chip on phones with StrongBox, otherwise the processor's secure area.
    val keyHolder = hardwareInfo.keyHolderShortName
    var showNoPassphraseWarningDialog by remember { mutableStateOf(false) }
    var showRestorePassphraseDialog by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var pendingEnvelope by remember { mutableStateOf<EncryptedBackupEnvelope?>(null) }
    var dialogPassphrase by remember { mutableStateOf("") }
    var dialogPassphraseVisible by remember { mutableStateOf(false) }
    var dialogErrorMessage by remember { mutableStateOf<String?>(null) }
    // A passphrase restore spends seconds deriving the key; meanwhile it shows progress and can't be started again.
    var restoring by remember { mutableStateOf(false) }
    // A backup carries every setting (export reveals them, restore replaces them), so the page always asks for the
    // same unlock as Manage Hidden Apps, even before any app is hidden or a passphrase saved.
    val isScreenUnlocked = rememberProtectedPageUnlock(
        title = "Unlock Encrypted Backup",
        subtitle = "Verify It’s You to Back Up or Restore Your Settings.",
        onBack = onBack
    )
    val backupExoPlayer = rememberBugdroidPlayer(BugdroidClip.BACKUP, enabled = isScreenUnlocked)
    // Only whether one is saved: the saved passphrase is never decrypted into the field. Read again after a save that
    // didn't go through, which may have kept the old passphrase or lost it.
    suspend fun refreshSavedOnChip() {
        isPassphraseSavedOnChip = withContext(Dispatchers.IO) { viewModel?.hasSavedPassphraseOnSecurityChip() } == true
    }
    LaunchedEffect(isScreenUnlocked) {
        if (!isScreenUnlocked) return@LaunchedEffect
        refreshSavedOnChip()
    }

    val safeShowToast: (String) -> Unit = { message ->
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show()
            } catch (_: Throwable) {}
        }
    }

    val performRestore: (Uri, String?) -> Unit = { targetUri, targetPass ->
        if (activity != null && viewModel != null && !restoring) {
            restoring = true
            viewModel.importBackup(
                uri = targetUri,
                passphrase = targetPass,
                onSuccess = { count ->
                    restoring = false
                    showRestorePassphraseDialog = false
                    dialogErrorMessage = null
                    safeShowToast("Intelligent Search Settings Restored")
                },
                onError = { err ->
                    restoring = false
                    if (shouldAskForPassphrase(pendingEnvelope, err)) {
                        pendingRestoreUri = targetUri
                        dialogErrorMessage = err
                        showRestorePassphraseDialog = true
                    }
                    safeShowToast("Restore error: $err")
                }
            )
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        android.util.Log.d("BackupRestoreScreen", "exportLauncher callback: uri=$uri, activity=$activity, viewModel=$viewModel")
        // The picker's grant covers this one write; persisting it would use up Android's 512-grant cap,
        // which also holds the folders and photos chosen for file search.
        if (uri != null && exportWantsPassphrase && exportPassphrase.isNullOrBlank()) {
            // The passphrase was lost while the picker was open: remove the empty file and ask again.
            runCatching { android.provider.DocumentsContract.deleteDocument(context.contentResolver, uri) }
            safeShowToast("Type your passphrase again, then export")
            return@rememberLauncherForActivityResult
        }
        if (uri != null && activity != null) {
            if (viewModel == null) {
                android.util.Log.e("BackupRestoreScreen", "viewModel is NULL in exportLauncher!")
                safeShowToast("Error: Settings manager is not available")
                return@rememberLauncherForActivityResult
            }
            val usedPassphrase = exportPassphrase
            exportPassphrase = null
            viewModel.exportBackup(
                uri = uri,
                passphrase = usedPassphrase,
                onSuccess = {
                    android.util.Log.d("BackupRestoreScreen", "exportBackup onSuccess callback")
                    safeShowToast(
                        when {
                            !usedPassphrase.isNullOrBlank() -> "Encrypted backup exported successfully!"
                            else -> "Backup exported. Without a passphrase it can only be restored on this phone."
                        }
                    )
                },
                onError = { err ->
                    safeShowToast("Export error: $err")
                }
            )
        } else {
            android.util.Log.w("BackupRestoreScreen", "exportLauncher: uri or activity was null (uri=$uri, activity=$activity)")
        }
    }

    val coroutineScope = rememberCoroutineScope()

    // The passphrase to back up or restore with: what is typed, or else the one saved on the chip. The chip only hands
    // it over shortly after the user has verified themselves, so when that window has passed the system prompt shows
    // once. When the saved one can't be had, [onUnavailable] runs; without it the user is told why.
    // [forBackup] refuses a saved passphrase shorter than a backup allows (one saved before the minimum existed)
    // and says so, since the user can't see what is saved; a typed one goes to [onReady] for the caller to check.
    fun withPassphrase(forBackup: Boolean = false, onUnavailable: (() -> Unit)? = null, onReady: (String?) -> Unit) {
        val typed = passphrase
        if (typed.isNotBlank() || !isPassphraseSavedOnChip) {
            onReady(typed.ifBlank { null })
            return
        }
        fun load(afterPrompt: Boolean) {
            coroutineScope.launch {
                val saved = withContext(Dispatchers.IO) { viewModel?.getSavedPassphraseFromSecurityChip() }
                when {
                    !saved.isNullOrBlank() && forBackup && saved.length < AutoBackup.MIN_PASSPHRASE_LENGTH -> safeShowToast(
                        "The passphrase saved to $keyHolder is shorter than ${AutoBackup.MIN_PASSPHRASE_LENGTH} characters. Type a longer one and save it."
                    )
                    !saved.isNullOrBlank() -> onReady(saved)
                    !afterPrompt && activity != null -> context.appEntryPoint.biometricSearchGate().authenticate(
                        activity = activity,
                        title = "Verify it’s you",
                        subtitle = "To Use the Passphrase Saved to $keyHolder.",
                        onSuccess = { load(afterPrompt = true) },
                        onFailure = {
                            if (onUnavailable != null) onUnavailable() else safeShowToast("Verify it’s you to use the saved passphrase")
                        },
                    )
                    onUnavailable != null -> onUnavailable()
                    else -> safeShowToast("Couldn’t read the saved passphrase. Type it instead.")
                }
            }
        }
        load(afterPrompt = false)
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && activity != null) {
            coroutineScope.launch {
                val inspectResult = withContext(Dispatchers.IO) {
                    viewModel?.inspectBackupEnvelope(uri)
                }
                if (inspectResult == null || inspectResult.isFailure) {
                    val errorMsg = inspectResult?.exceptionOrNull()?.localizedMessage
                        ?: "Selected backup file could not be read or is invalid."
                    safeShowToast(errorMsg)
                    return@launch
                }

                val envelope = inspectResult.getOrNull()
                pendingEnvelope = envelope
                val needsPass = envelope?.kdf != null
                if (needsPass) {
                    val askForPassphrase = {
                        pendingRestoreUri = uri
                        dialogPassphrase = ""
                        dialogErrorMessage = null
                        showRestorePassphraseDialog = true
                    }
                    // The typed or saved passphrase, as for an export; if the saved one can't be had, the user types it.
                    withPassphrase(onUnavailable = askForPassphrase) { chosen ->
                        if (chosen != null) performRestore(uri, chosen) else askForPassphrase()
                    }
                } else {
                    performRestore(uri, null)
                }
            }
        }
    }

    if (showRestorePassphraseDialog && pendingRestoreUri != null) {
        AlertDialog(
            onDismissRequest = {
                showRestorePassphraseDialog = false
                dialogErrorMessage = null
            },
            title = {
                Text(
                    "Enter Backup Passphrase",
                    fontWeight = FontWeight.Bold,
                    fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex
                )
            },
            text = {
                Column {
                    Text(
                        "This backup is protected with encryption. Enter the passphrase to restore your settings and customizations.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    pendingEnvelope?.let { env ->
                        if (!env.hardwareChip.isNullOrBlank() || !env.deviceModel.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    // These come from the file's unencrypted header, so they're shown as claims only.
                                    Text(
                                        "As stated in the file (not verified):",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (!env.hardwareChip.isNullOrBlank()) {
                                        Text(
                                            "Security Chip: ${env.hardwareChip.take(60)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    if (!env.deviceModel.isNullOrBlank()) {
                                        Text(
                                            "Device: ${env.deviceModel.take(60)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (dialogErrorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            dialogErrorMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.foundation.text.LocalAutofillHighlightColor provides MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
                    OutlinedTextField(
                        value = dialogPassphrase,
                        onValueChange = {
                            dialogPassphrase = it
                            dialogErrorMessage = null
                        },
                        placeholder = { Text("Enter passphrase", fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex, fontSize = 14.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { dialogPassphraseVisible = !dialogPassphraseVisible }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    imageVector = if (dialogPassphraseVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                    contentDescription = if (dialogPassphraseVisible) "Hide passphrase" else "Show passphrase",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        visualTransformation = if (dialogPassphraseVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        // Password type keeps the keyboard from learning or suggesting the passphrase, even when shown.
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(percent = 50)),
                        shape = RoundedCornerShape(percent = 50),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uri = pendingRestoreUri ?: return@Button
                        performRestore(uri, dialogPassphrase)
                    },
                    enabled = !restoring,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(if (restoring) "Restoring…" else "Restore", fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRestorePassphraseDialog = false
                        dialogErrorMessage = null
                    },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Cancel", fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex)
                }
            }
        )
    }

    if (showNoPassphraseWarningDialog) {
        AlertDialog(
            onDismissRequest = { showNoPassphraseWarningDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    "No Passphrase Set",
                    fontWeight = FontWeight.Bold,
                    fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex
                )
            },
            text = {
                Text(
                    "Without a passphrase, this backup is locked to this phone's $keyHolder. It can't be restored on another phone, or on this one after Intelligent Search is reinstalled or its data is cleared. Do you want to set a passphrase first?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNoPassphraseWarningDialog = false
                        exportWantsPassphrase = false
                        exportPassphrase = null
                        exportLauncher.launch("intelligent_search_backup_${System.currentTimeMillis()}.json")
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Export Anyway", fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showNoPassphraseWarningDialog = false
                    },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Set Passphrase", fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex)
                }
            }
        )
    }

    if (!isScreenUnlocked) return

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Encrypted Backup", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, autoSize = oneLineAutoSize(MaterialTheme.typography.headlineSmall)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        CardsAboveBugdroid(
            modifier = Modifier.fillMaxSize().padding(padding),
            // Below Android 13 the bugdroid video can't be shown (see isBugdroidVideoSupported), so neither are the
            // room kept for it and the shadows under its feet.
            bugdroid = if (isBugdroidVideoSupported) { { BackupBugdroid(backupExoPlayer) } } else null
        ) {
            SettingsCard(modifier = Modifier.backupCard("backup_card_passphrase")) {
                BackupCardTitle("Encryption Passphrase")
                BackupCardDescription(
                    "Set a Passphrase to Restore Your Backup on Any Device. If Left Blank, the Backup Is Encrypted with a Key in This Phone's $keyHolder and Can Only Be Restored on This Phone."
                )
                // Autofill's highlight is drawn over the whole field, so clipping the field to its own pill shape makes
                // the highlight a pill; the colour is a light, see-through tint of the theme instead of yellow.
                androidx.compose.runtime.CompositionLocalProvider(androidx.compose.foundation.text.LocalAutofillHighlightColor provides MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    placeholder = {
                        Text(
                            if (isPassphraseSavedOnChip) "Saved to $keyHolder · type to replace" else "Enter passphrase (optional)",
                            fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex,
                            fontSize = 14.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (passphrase.isNotEmpty()) {
                                IconButton(onClick = { passphrase = "" }, modifier = Modifier.size(32.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            IconButton(onClick = { passphraseVisible = !passphraseVisible }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    imageVector = if (passphraseVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                    contentDescription = if (passphraseVisible) "Hide passphrase" else "Show passphrase",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                    },
                    visualTransformation = if (passphraseVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    // Password type keeps the keyboard from learning or suggesting the passphrase, even when shown.
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(percent = 50)),
                    shape = RoundedCornerShape(percent = 50),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                )
                }
                // Export and automatic backup refuse a shorter passphrase, so it can't be saved either; this says why
                // the button below stays off.
                val passphraseLongEnough = passphrase.isNotBlank() && passphrase.length >= AutoBackup.MIN_PASSPHRASE_LENGTH
                if (passphrase.isNotEmpty() && !passphraseLongEnough) {
                    BackupCardDescription("Use a Passphrase of at Least ${AutoBackup.MIN_PASSPHRASE_LENGTH} Characters.")
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = {
                            if (passphraseLongEnough) {
                                val toSave = passphrase
                                // The chip's key only works shortly after the user has verified themselves; when that
                                // window has passed (or the page opened without a prompt) the system prompt shows once
                                // and the save is tried again.
                                fun save(afterPrompt: Boolean) {
                                    coroutineScope.launch {
                                        // StrongBox key generation and the commit() are slow; keep them off the main thread.
                                        val outcome = withContext(Dispatchers.IO) { viewModel?.savePassphraseToSecurityChip(toSave) }
                                        when {
                                            outcome == com.pixel.intelligentsearch.core.security.StrongBoxSecurityManager.PassphraseSave.SAVED -> {
                                                isPassphraseSavedOnChip = true
                                                // Entered once, then gone: the passphrase lives only on the chip now.
                                                passphrase = ""
                                                passphraseVisible = false
                                                hapticEngine.performPredictiveBackHaptic()
                                                safeShowToast("Passphrase saved to $keyHolder")
                                            }
                                            outcome == com.pixel.intelligentsearch.core.security.StrongBoxSecurityManager.PassphraseSave.NEEDS_AUTHENTICATION && !afterPrompt -> {
                                                val activity = context.findActivity()
                                                if (activity == null) {
                                                    safeShowToast("Failed to save to $keyHolder")
                                                    refreshSavedOnChip()
                                                } else {
                                                    context.appEntryPoint.biometricSearchGate().authenticate(
                                                        activity = activity,
                                                        title = "Verify it’s you",
                                                        subtitle = "To Save the Passphrase to $keyHolder.",
                                                        onSuccess = { save(afterPrompt = true) },
                                                        onFailure = {
                                                            safeShowToast("Verify it’s you to save the passphrase")
                                                            coroutineScope.launch { refreshSavedOnChip() }
                                                        },
                                                    )
                                                }
                                            }
                                            else -> {
                                                safeShowToast("Failed to save to $keyHolder")
                                                refreshSavedOnChip()
                                            }
                                        }
                                    }
                                }
                                save(afterPrompt = false)
                            } else {
                                safeShowToast("Use a passphrase of at least ${AutoBackup.MIN_PASSPHRASE_LENGTH} characters")
                            }
                        },
                        shape = RoundedCornerShape(percent = 50),
                        modifier = Modifier.weight(1f),
                        enabled = passphraseLongEnough
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Security,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (isPassphraseSavedOnChip && passphrase.isBlank()) "Saved to $keyHolder" else "Save to $keyHolder",
                            fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (isPassphraseSavedOnChip) {
                        OutlinedButton(
                            onClick = {
                                viewModel?.clearSavedPassphraseFromSecurityChip()
                                isPassphraseSavedOnChip = false
                                safeShowToast("Passphrase removed from $keyHolder")
                            },
                            shape = RoundedCornerShape(percent = 50)
                        ) {
                            Text(
                                "Clear",
                                fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                if (isPassphraseSavedOnChip) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Secured with ${hardwareInfo.keyHolderName} (${hardwareInfo.deviceDisplayName})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex
                        )
                    }
                }
            }

            SettingsCard(modifier = Modifier.backupCard("backup_card_export")) {
                BackupCardTitle("Export Configuration")
                BackupCardDescription("Settings, History, Shortcuts, and Priority Weights.")
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = {
                        withPassphrase(forBackup = true) { chosen ->
                            if (chosen == null) {
                                showNoPassphraseWarningDialog = true
                            } else if (chosen.length < AutoBackup.MIN_PASSPHRASE_LENGTH) {
                                // A portable backup can be attacked offline, so its passphrase needs some length.
                                safeShowToast("Use a passphrase of at least ${AutoBackup.MIN_PASSPHRASE_LENGTH} characters")
                            } else {
                                exportPassphrase = chosen
                                exportWantsPassphrase = true
                                exportLauncher.launch("intelligent_search_backup_${System.currentTimeMillis()}.json")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(percent = 50)
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export Encrypted Backup", fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex)
                }
            }

            SettingsCard(modifier = Modifier.backupCard("backup_card_restore")) {
                BackupCardTitle("Restore from Backup")
                BackupCardDescription("Decrypted and SHA-256 Verified Before Restoring.")
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = {
                        importLauncher.launch(arrayOf("application/json", "*/*"))
                    },
                    enabled = !restoring,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(percent = 50)
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (restoring) "Restoring…" else "Select Backup to Restore", fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex)
                }
            }

            AutoBackupCard(obtainPassphrase = { onReady -> withPassphrase(forBackup = true, onReady = onReady) })
        }
    }
}

/** Space around the page's content. The bugdroid, when shown, goes down to the bottom edge instead. */
private val BACKUP_PAGE_PADDING = 16.dp

/** Space between the last card and the bugdroid's view; the video keeps its own margin around the character. */
private val BUGDROID_GAP = 4.dp

/**
 * The bugdroid's height limits. Below [BUGDROID_MIN_HEIGHT] it would be too small to make out, so the page scrolls
 * instead; at [BUGDROID_MAX_HEIGHT] (its full size) any room left stays empty below it.
 */
internal val BUGDROID_MIN_HEIGHT = 120.dp
internal val BUGDROID_MAX_HEIGHT = 340.dp

/**
 * The page's [cards] with [bugdroid] below them. The bugdroid gets the room the cards leave on the visible page,
 * within [BUGDROID_MIN_HEIGHT]..[BUGDROID_MAX_HEIGHT], so where everything fits the page doesn't scroll and shows the
 * whole bugdroid, legs included. Where the cards leave less than the minimum (large text, display zoom, short
 * screens) the page is taller than the screen and scrolls. The video's fitting keeps the whole character (key, head,
 * arms and legs) inside whatever height it gets.
 */
@Composable
private fun CardsAboveBugdroid(
    modifier: Modifier,
    bugdroid: (@Composable () -> Unit)?,
    cards: @Composable ColumnScope.() -> Unit
) {
    Layout(
        contents = listOf({ Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = cards) }, bugdroid ?: {}),
        modifier = modifier
            .verticalScroll(rememberScrollState())
            // The video keeps its own margin below the character, so the bugdroid needs no padding under it.
            .padding(
                start = BACKUP_PAGE_PADDING,
                top = BACKUP_PAGE_PADDING,
                end = BACKUP_PAGE_PADDING,
                bottom = if (bugdroid == null) BACKUP_PAGE_PADDING else 0.dp
            )
    ) { (cardMeasurables, bugdroidMeasurables), constraints ->
        val width = constraints.maxWidth
        val cardPlaceables = cardMeasurables.map { it.measure(Constraints(maxWidth = width)) }
        val cardsHeight = cardPlaceables.sumOf { it.height }
        val gap = if (bugdroidMeasurables.isEmpty()) 0 else BUGDROID_GAP.roundToPx()
        // verticalScroll lifts only the maximum height, so the minimum is still the visible page, less the padding.
        val bugdroidHeight = (constraints.minHeight - cardsHeight - gap)
            .coerceIn(BUGDROID_MIN_HEIGHT.roundToPx(), BUGDROID_MAX_HEIGHT.roundToPx())
        val bugdroidPlaceables = bugdroidMeasurables.map { it.measure(Constraints.fixed(width, bugdroidHeight)) }
        val height = cardsHeight + gap + bugdroidPlaceables.sumOf { it.height }
        layout(width, height.coerceAtLeast(constraints.minHeight)) {
            cardPlaceables.fold(0) { y, placeable -> placeable.place(0, y); y + placeable.height }
            bugdroidPlaceables.forEach { it.place(0, cardsHeight + gap) }
        }
    }
}

/**
 * The backup bugdroid holding its key, standing on soft shadows that pulse and sway with it. The shadows are placed
 * in the clip's 1920x1080 frame, so they follow wherever the fitting puts the character, at any view size.
 */
@Composable
private fun BackupBugdroid(player: ExoPlayer?) {
    Box(modifier = Modifier.fillMaxSize().testTag("backup_bugdroid"), contentAlignment = Alignment.Center) {
        val isDark = isSystemInDarkTheme()
        val infiniteTransition = rememberInfiniteTransition(label = "BugdroidFeetShadowTransition")
        // The animated values stay State and are read only inside the Canvas, so the pulse redraws
        // the shadow without recomposing this part of the screen every frame.
        val pulseScale = infiniteTransition.animateFloat(
            initialValue = 0.94f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(
                animation = tween(2400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "shadowPulseScale"
        )
        val pulseAlpha = infiniteTransition.animateFloat(
            initialValue = 0.85f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(2400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "shadowPulseAlpha"
        )
        // In frame pixels, like the sizes below.
        val swayOffset = infiniteTransition.animateFloat(
            initialValue = -4.8f,
            targetValue = 4.8f,
            animationSpec = infiniteRepeatable(
                animation = tween(2800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "shadowSwayOffset"
        )

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("backup_bugdroid_shadow")
        ) {
            val alpha = pulseAlpha.value
            val scale = pulseScale.value
            // View pixels per frame pixel: the shadows grow and shrink with the bugdroid, about the same centre.
            val px = BugdroidClip.BACKUP.viewPixelsPerFramePixel(size.width, size.height, 1f)
            val contactShadowColor = Color.Black.copy(alpha = (if (isDark) 0.70f else 0.38f) * alpha)
            val ambientShadowColor = Color.Black.copy(alpha = (if (isDark) 0.32f else 0.16f) * alpha)
            val centerX = size.width / 2f + swayOffset.value * px
            val groundY = size.height / 2f + (FEET_GROUND_Y - BugdroidClip.SOURCE_HEIGHT / 2f) * px

            // Ambient ground diffusion shadow
            val ambientWidth = 350f * scale * px
            val ambientHeight = 70f * scale * px
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(ambientShadowColor, Color.Transparent),
                    center = Offset(centerX, groundY),
                    radius = ambientWidth / 2f
                ),
                topLeft = Offset(centerX - ambientWidth / 2f, groundY - ambientHeight / 2f),
                size = Size(ambientWidth, ambientHeight)
            )

            // Left foot contact shadow (grounded directly beneath left foot)
            val leftFootCenterX = centerX - 89f * px
            val footWidth = 114f * scale * px
            val footHeight = 38f * scale * px
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(contactShadowColor, Color.Transparent),
                    center = Offset(leftFootCenterX, groundY),
                    radius = footWidth / 2f
                ),
                topLeft = Offset(leftFootCenterX - footWidth / 2f, groundY - footHeight / 2f),
                size = Size(footWidth, footHeight)
            )

            // Right foot contact shadow (grounded directly beneath right foot)
            val rightFootCenterX = centerX + 76f * px
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(contactShadowColor, Color.Transparent),
                    center = Offset(rightFootCenterX, groundY),
                    radius = footWidth / 2f
                ),
                topLeft = Offset(rightFootCenterX - footWidth / 2f, groundY - footHeight / 2f),
                size = Size(footWidth, footHeight)
            )
        }

        BugdroidPlayer(
            player = player,
            clip = BugdroidClip.BACKUP,
            modifier = Modifier.fillMaxSize(),
            scaleFactor = 1.0f
        )
    }
}

/** Where the backup bugdroid's feet meet the ground, as a y in its 1920x1080 frame. */
private const val FEET_GROUND_Y = 928f

/**
 * Whether a failed restore should ask for the passphrase (again). Only a backup made with a passphrase can be
 * opened by typing one; an older file's error also mentions a passphrase, and asking for it there would loop.
 */
internal fun shouldAskForPassphrase(envelope: EncryptedBackupEnvelope?, error: String): Boolean =
    envelope?.kdf != null &&
        (error.contains("passphrase", ignoreCase = true) || error.contains("tag mismatch", ignoreCase = true))

/** The title of a card on this page, in the same style as the passphrase card's. */
@Composable
internal fun BackupCardTitle(text: String) {
    Text(
        text,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

/** The explanation under a [BackupCardTitle]. */
@Composable
internal fun BackupCardDescription(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
    )
}

/** [SettingsCard]'s own width and inset, tagged so tests can check that the page's cards line up. */
internal fun Modifier.backupCard(tag: String): Modifier = fillMaxWidth().padding(horizontal = 4.dp).testTag(tag)
