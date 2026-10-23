package com.pixel.intelligentsearch.feature.settings

import androidx.compose.material.icons.outlined.Hub
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pixel.intelligentsearch.core.ai.AiProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.rememberSetting
import com.pixel.intelligentsearch.core.security.AiKeyVault
import com.pixel.intelligentsearch.core.security.NetworkPolicy
import com.pixel.intelligentsearch.di.appEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AI Answers, reached from Web Search: the opt-in switch, the provider and model, and the user's own API key, saved
 * encrypted on the phone (never in backups) and shown masked, with Remove Key. Off while Tor is the search engine or Web is off.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAnswersScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val vault = remember(context) { context.appEntryPoint.aiKeyVault() }
    val morphAnimationEnabled by rememberSetting(SettingsManager.MORPH_ANIMATION_ENABLED)
    var enabled by rememberSetting(SettingsManager.AI_ANSWERS_ENABLED)
    var providerId by rememberSetting(SettingsManager.AI_PROVIDER)
    var model by rememberSetting(SettingsManager.AI_MODEL)
    val engine by rememberSetting(SettingsManager.SEARCH_ENGINE)
    val web by rememberSetting(SettingsManager.SEARCH_WEB)
    val provider = AiProvider.fromId(providerId)
    val block = NetworkPolicy.aiBlock(web, engine)

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("AI Answers", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, autoSize = oneLineAutoSize(MaterialTheme.typography.headlineSmall)) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") } }
                )
            }
        ) { padding ->
            CardsAboveMorphAnimation(morphAnimationEnabled = morphAnimationEnabled, modifier = Modifier.fillMaxSize().padding(padding)) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SettingsCard {
                        SettingsRowToggle(
                            title = "AI Answers",
                            subtitle = block?.let(NetworkPolicy::blockSubtitle)
                                ?: "Questions Ending with ? or Starting with Ask Offer an Ask Row. Only the Question You Tap Ask On Is Sent, with Your Own Key.",
                            icon = Icons.Outlined.AutoAwesome,
                            isChecked = enabled && block == null,
                            onCheckedChange = { if (block == null) enabled = it },
                            showDivider = false
                        )
                    }

                    SettingsSectionHeader("Provider and Key")
                    SettingsCard {
                        SettingsSelectorRow(
                            title = "Provider",
                            subtitle = "Answers Come from ${provider.label}.",
                            icon = Icons.Outlined.Hub,
                            options = AiProvider.entries.toList(),
                            selected = provider,
                            onSelect = { chosen ->
                                if (chosen != provider) {
                                    providerId = chosen.id
                                    model = ""
                                }
                            },
                            label = { it.displayName },
                            showDivider = true
                        )
                        SettingsDropdownRow(
                            title = "Model",
                            subtitle = provider.model(model),
                            icon = Icons.Outlined.Memory,
                            options = provider.models,
                            selectedOption = provider.model(model),
                            onOptionSelected = { model = if (it == provider.defaultModel) "" else it },
                            showDivider = true
                        )
                        ApiKeyRows(vault, provider)
                        // What is sent, and where: information only, so a row without a chevron.
                        SettingsInfoRow(
                            title = "Privacy",
                            subtitle = "Only When You Tap Ask, Only That Question Goes to ${provider.host} over HTTPS. Nothing Else Is Sent, and Your Key Never Leaves This Phone Except to ${provider.host}.",
                            icon = Icons.Outlined.PrivacyTip,
                            showDivider = false
                        )
                    }
                }
            }
        }
    }
}

/** The key: a masked entry field and Save while none is saved; the masked key and Remove Key once one is. */
@Composable
private fun ApiKeyRows(vault: AiKeyVault, provider: AiProvider) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Bumped by Save and Remove Key, so the rows read the vault again.
    var version by remember { mutableIntStateOf(0) }
    var saved by remember(provider) { mutableStateOf<String?>(null) }
    var hasKey by remember(provider) { mutableStateOf(false) }
    LaunchedEffect(provider, version) {
        val (has, masked) = withContext(Dispatchers.IO) { vault.has(provider).let { it to if (it) vault.masked(provider) else null } }
        hasKey = has
        saved = masked
    }

    if (hasKey) {
        SettingsInfoRow(
            title = "API Key",
            subtitle = "${saved ?: "••••••••"}. Encrypted on This Phone and Never Backed Up.",
            icon = Icons.Outlined.Key,
            showDivider = true
        )
        SettingsRow(
            title = "Remove Key",
            subtitle = "Delete Your ${provider.displayName} Key from This Phone.",
            icon = Icons.Outlined.Delete,
            onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) { vault.remove(provider) }
                    version++
                }
            },
            showDivider = true
        )
    } else {
        var typed by remember(provider) { mutableStateOf("") }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Key, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.size(16.dp))
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it.trim().take(MAX_KEY_LENGTH) },
                label = { Text("${provider.displayName} API Key") },
                supportingText = { Text("Make One at ${provider.keyPage}.") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.weight(1f)
            )
            TextButton(
                enabled = typed.isNotBlank(),
                onClick = {
                    val key = typed
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { vault.save(provider, key) }
                        if (ok) {
                            typed = ""
                            version++
                        } else {
                            Toast.makeText(context, "Couldn't save the key on this phone.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            ) { Text("Save", style = MaterialTheme.typography.labelLarge) }
        }
    }
}

/** No provider's key is longer; a longer paste isn't a key. */
private const val MAX_KEY_LENGTH = 512
