package com.pixel.intelligentsearch.feature.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
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

    val backupExoPlayer = remember(context) {
        runCatching {
            val uri = android.net.Uri.parse("android.resource://" + context.packageName + "/" + com.pixel.intelligentsearch.R.raw.gemini_generated_video_2209818f)
            val mediaItem = androidx.media3.common.MediaItem.fromUri(uri)
            val mediaSource = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context).createMediaSource(mediaItem)
            androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
                setMediaSource(mediaSource)
                repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
                volume = 0f
                prepare()
                playWhenReady = true
            }
        }.getOrNull()
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, backupExoPlayer) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                backupExoPlayer?.play()
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                backupExoPlayer?.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            backupExoPlayer?.release()
        }
    }

    var passphrase by remember { mutableStateOf("") }
    var passphraseVisible by remember { mutableStateOf(false) }
    var isPassphraseSavedOnChip by remember { mutableStateOf(false) }
    val hardwareInfo = remember { com.pixel.intelligentsearch.core.security.HardwareSecurityDetector.detect(context) }
    var showNoPassphraseWarningDialog by remember { mutableStateOf(false) }
    var showRestorePassphraseDialog by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var pendingEnvelope by remember { mutableStateOf<com.pixel.intelligentsearch.core.backup.EncryptedBackupEnvelope?>(null) }
    var dialogPassphrase by remember { mutableStateOf("") }
    var dialogPassphraseVisible by remember { mutableStateOf(false) }
    var dialogErrorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        // Keystore (StrongBox) decryption can take hundreds of milliseconds; keep it off the main thread.
        val saved = withContext(Dispatchers.IO) { viewModel?.getSavedPassphraseFromSecurityChip() }
        if (!saved.isNullOrBlank()) {
            passphrase = saved
            isPassphraseSavedOnChip = true
        }
    }

    val safeShowToast: (String) -> Unit = { message ->
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show()
            } catch (_: Throwable) {}
        }
    }

    val performRestore: (Uri, String?) -> Unit = { targetUri, targetPass ->
        if (activity != null) {
            viewModel?.importBackup(
                uri = targetUri,
                passphrase = targetPass,
                onSuccess = { count ->
                    showRestorePassphraseDialog = false
                    dialogErrorMessage = null
                    if (!targetPass.isNullOrBlank() && passphrase.isBlank()) {
                        passphrase = targetPass
                    }
                    safeShowToast("Intelligent Search Settings Restored")
                },
                onError = { err ->
                    if (err.contains("passphrase", ignoreCase = true) || err.contains("tag mismatch", ignoreCase = true)) {
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
        if (uri != null && activity != null) {
            try {
                activity.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Throwable) {
                android.util.Log.d("BackupRestoreScreen", "takePersistableUriPermission: ${e.message}")
            }
            if (viewModel == null) {
                android.util.Log.e("BackupRestoreScreen", "viewModel is NULL in exportLauncher!")
                safeShowToast("Error: Settings manager is not available")
                return@rememberLauncherForActivityResult
            }
            viewModel.exportBackup(
                uri = uri,
                passphrase = passphrase.ifBlank { null },
                onSuccess = {
                    android.util.Log.d("BackupRestoreScreen", "exportBackup onSuccess callback")
                    safeShowToast(
                        when {
                            passphrase.isNotBlank() -> "Encrypted backup exported successfully!"
                            hardwareInfo.isStrongBox -> "Backup exported. Without a passphrase it can only be restored on this phone."
                            else -> "Backup exported (no passphrase: not securely encrypted)"
                        }
                    )
                },
                onError = { err ->
                    android.util.Log.e("BackupRestoreScreen", "exportBackup onError: $err")
                    safeShowToast("Export error: $err")
                }
            )
        } else {
            android.util.Log.w("BackupRestoreScreen", "exportLauncher: uri or activity was null (uri=$uri, activity=$activity)")
        }
    }

    val coroutineScope = rememberCoroutineScope()

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && activity != null) {
            try {
                activity.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Throwable) {}
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
                    val effectivePass = passphrase.ifBlank {
                        withContext(Dispatchers.IO) { viewModel?.getSavedPassphraseFromSecurityChip() } ?: ""
                    }
                    if (effectivePass.isNotBlank()) {
                        performRestore(uri, effectivePass)
                    } else {
                        pendingRestoreUri = uri
                        dialogPassphrase = ""
                        dialogErrorMessage = null
                        showRestorePassphraseDialog = true
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
                                    if (!env.hardwareChip.isNullOrBlank()) {
                                        Text(
                                            "Security Chip: ${env.hardwareChip}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    if (!env.deviceModel.isNullOrBlank()) {
                                        Text(
                                            "Device: ${env.deviceModel}",
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
                        modifier = Modifier.fillMaxWidth(),
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
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uri = pendingRestoreUri ?: return@Button
                        performRestore(uri, dialogPassphrase)
                    },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Restore", fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex)
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
                    if (hardwareInfo.isStrongBox) {
                        "Without a passphrase, this backup is locked to this phone's ${hardwareInfo.shortChipName}. It can't be restored on another phone, or on this one after Intelligent Search is reinstalled or its data is cleared. Do you want to set a passphrase first?"
                    } else {
                        "Without a passphrase, your backup will not be securely encrypted. Anyone with access to the exported file will be able to read your settings, history, and configuration. Do you want to set a passphrase first, or export without encryption?"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNoPassphraseWarningDialog = false
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

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Encrypted Backup", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsCard {
                Text(
                    "Encryption Passphrase",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                Text(
                    if (hardwareInfo.isStrongBox) {
                        "Set a passphrase to restore your backup on any device. If left blank, the backup is encrypted with a key in this phone's ${hardwareInfo.shortChipName} and can only be restored on this phone."
                    } else {
                        "Set a passphrase to securely encrypt your backup. If left blank, the backup restores automatically on any device, but it is not securely encrypted: anyone with the file can read it."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = {
                        passphrase = it
                        if (isPassphraseSavedOnChip && it.isBlank()) {
                            isPassphraseSavedOnChip = false
                        }
                    },
                    placeholder = { Text("Enter passphrase (optional)", fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex, fontSize = 14.sp) },
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
                                IconButton(onClick = {
                                    passphrase = ""
                                    if (isPassphraseSavedOnChip) {
                                        isPassphraseSavedOnChip = false
                                        viewModel?.clearSavedPassphraseFromSecurityChip()
                                    }
                                }, modifier = Modifier.size(32.dp)) {
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
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(percent = 50),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = {
                            if (passphrase.isNotBlank()) {
                                val saved = viewModel?.savePassphraseToSecurityChip(passphrase) == true
                                if (saved) {
                                    isPassphraseSavedOnChip = true
                                    hapticEngine.performHaptic(null, com.pixel.intelligentsearch.core.haptics.PixelHapticType.CLICK)
                                    safeShowToast("Passphrase saved to ${hardwareInfo.shortChipName}")
                                } else {
                                    safeShowToast("Failed to save to ${hardwareInfo.shortChipName}")
                                }
                            } else {
                                safeShowToast("Enter a passphrase first")
                            }
                        },
                        shape = RoundedCornerShape(percent = 50),
                        modifier = Modifier.weight(1f),
                        enabled = passphrase.isNotBlank()
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Security,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (isPassphraseSavedOnChip) "Saved to ${hardwareInfo.shortChipName}" else "Save to ${hardwareInfo.shortChipName}",
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
                                hapticEngine.performHaptic(null, com.pixel.intelligentsearch.core.haptics.PixelHapticType.CLICK)
                                safeShowToast("Passphrase removed from ${hardwareInfo.shortChipName}")
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
                            "Secured with ${hardwareInfo.chipName} (${hardwareInfo.deviceDisplayName})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Export Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Export Configuration", fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex)
                    Text(
                        "Includes Settings, Search History, Shortcuts, and Priority Weights.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            hapticEngine.performHaptic(null, com.pixel.intelligentsearch.core.haptics.PixelHapticType.CLICK)
                            if (passphrase.isBlank()) {
                                showNoPassphraseWarningDialog = true
                            } else {
                                exportLauncher.launch("intelligent_search_backup_${System.currentTimeMillis()}.json")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export Encrypted Backup", fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex)
                    }
                }
            }

            // Restore Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Restore from Backup", fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex)
                    Text(
                        "Select an Existing .json Backup File to Decrypt, Verify SHA-256 Integrity, and Restore.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedButton(
                        onClick = {
                            hapticEngine.performHaptic(null, com.pixel.intelligentsearch.core.haptics.PixelHapticType.CLICK)
                            importLauncher.launch(arrayOf("application/json", "*/*"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Select Backup to Restore", fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                val isDark = isSystemInDarkTheme()
                val infiniteTransition = rememberInfiniteTransition(label = "BugdroidFeetShadowTransition")
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 0.94f,
                    targetValue = 1.06f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2400, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "shadowPulseScale"
                )
                val pulseAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.85f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2400, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "shadowPulseAlpha"
                )
                val swayOffset by infiniteTransition.animateFloat(
                    initialValue = -1.5f,
                    targetValue = 1.5f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2800, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "shadowSwayOffset"
                )

                val contactShadowColor = if (isDark) {
                    Color.Black.copy(alpha = 0.70f * pulseAlpha)
                } else {
                    Color.Black.copy(alpha = 0.38f * pulseAlpha)
                }
                val ambientShadowColor = if (isDark) {
                    Color.Black.copy(alpha = 0.32f * pulseAlpha)
                } else {
                    Color.Black.copy(alpha = 0.16f * pulseAlpha)
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                ) {
                    val centerX = size.width / 2f + swayOffset.dp.toPx()
                    val groundY = 292.dp.toPx()

                    // Ambient ground diffusion shadow
                    val ambientWidth = 110.dp.toPx() * pulseScale
                    val ambientHeight = 22.dp.toPx() * pulseScale
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
                    val leftFootCenterX = centerX - 28.dp.toPx()
                    val footWidth = 36.dp.toPx() * pulseScale
                    val footHeight = 12.dp.toPx() * pulseScale
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
                    val rightFootCenterX = centerX + 24.dp.toPx()
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
                    player = backupExoPlayer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp),
                    scaleFactor = 1.0f
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
