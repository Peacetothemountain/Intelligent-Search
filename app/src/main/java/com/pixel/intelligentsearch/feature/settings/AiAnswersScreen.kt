package com.pixel.intelligentsearch.feature.settings

import com.pixel.intelligentsearch.core.ui.symbols.Symbols
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
import com.pixel.intelligentsearch.core.ui.icons.outlined.AutoAwesome
import com.pixel.intelligentsearch.core.ui.icons.outlined.Delete
import com.pixel.intelligentsearch.core.ui.icons.outlined.PrivacyTip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import com.pixel.intelligentsearch.core.ai.AiProvider
import com.pixel.intelligentsearch.core.ai.chosenModel
import com.pixel.intelligentsearch.core.ai.fastFrom
import com.pixel.intelligentsearch.core.ai.favoritesFirst
import com.pixel.intelligentsearch.core.ai.reasoningFrom
import com.pixel.intelligentsearch.core.ai.toggled
import com.pixel.intelligentsearch.core.ai.withValue
import com.pixel.intelligentsearch.core.ui.AiProviderLogo
import com.pixel.intelligentsearch.core.ui.aiProviderPillColor
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
@Composable
fun AiAnswersScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val vault = remember(context) { context.appEntryPoint.aiKeyVault() }
    val morphAnimationEnabled by rememberSetting(SettingsManager.MORPH_ANIMATION_ENABLED)
    var enabled by rememberSetting(SettingsManager.AI_ANSWERS_ENABLED)
    var providerId by rememberSetting(SettingsManager.AI_PROVIDER)
    // Kept per provider and per model, so moving between providers never resets one.
    var picks by rememberSetting(SettingsManager.AI_MODELS)
    val legacyModel by rememberSetting(SettingsManager.AI_MODEL)
    var favorites by rememberSetting(SettingsManager.AI_FAVORITE_MODELS)
    var reasoningChoices by rememberSetting(SettingsManager.AI_REASONING)
    var fastModels by rememberSetting(SettingsManager.AI_FAST_MODELS)
    val engine by rememberSetting(SettingsManager.SEARCH_ENGINE)
    val web by rememberSetting(SettingsManager.SEARCH_WEB)
    val provider = AiProvider.fromId(providerId)
    val spec = provider.spec(provider.chosenModel(picks, legacyModel))
    val reasoning = spec.reasoningFrom(reasoningChoices)
    val block = NetworkPolicy.aiBlock(web, engine)
    // Bumped by Save and Remove Key, so whether this provider has a key is read again.
    var keyVersion by remember { mutableIntStateOf(0) }
    // A preferences lookup, no decrypting: cheap enough to decide the rows in the same frame, so none jump.
    val hasKey = remember(provider, keyVersion) { vault.has(provider) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                SettingsTopBar("AI Answers", onBack)
            }
        ) { padding ->
            CardsAboveMorphAnimation(morphAnimationEnabled = morphAnimationEnabled, modifier = Modifier.fillMaxSize().padding(padding)) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SettingsSectionHeader("Answers in Search")
                    SettingsCard {
                        SettingsRowToggle(
                            title = "AI Answers",
                            subtitle = block?.let(NetworkPolicy::blockSubtitle)
                                // On but with nothing to ask with: say so, rather than leave the Ask row missing unexplained.
                                ?: if (enabled && !hasKey) "Add Your ${provider.displayName} API Key Below to Ask."
                                else "Questions Ending in ? or Starting with Ask Show an Ask Row. Only the Question You Tap Ask on Is Sent.",
                            icon = Symbols.Outlined.AutoAwesome,
                            isChecked = enabled && block == null,
                            onCheckedChange = { if (block == null) enabled = it },
                            showDivider = false
                        )
                    }

                    SettingsSectionHeader("Provider and Key")
                    val providerPillBase = MaterialTheme.colorScheme.primaryContainer
                    // Taps on each provider, so every tap, the chosen one's included, replays that logo's reveal.
                    val providerTaps = remember { mutableStateMapOf<AiProvider, Int>() }
                    SettingsCard {
                        SettingsSelectorRow(
                            title = "Provider",
                            subtitle = "Answers Come from ${provider.label}.",
                            icon = Symbols.Outlined.Hub,
                            options = AiProvider.entries.toList(),
                            selected = provider,
                            onSelect = { chosen ->
                                if (chosen != provider) providerId = chosen.id
                                providerTaps[chosen] = (providerTaps[chosen] ?: 0) + 1
                            },
                            label = { it.displayName },
                            showDivider = true,
                            leading = { option, isSelected ->
                                AiProviderLogo(option, size = 20.dp, selected = isSelected, taps = providerTaps[option] ?: 0)
                            },
                            // The chosen provider's pill takes a wash of its brand colour.
                            indicatorColor = { option -> aiProviderPillColor(option, providerPillBase) }
                        )
                        SettingsDropdownRow(
                            title = "Model",
                            subtitle = spec.id,
                            icon = Symbols.Outlined.Memory,
                            options = provider.favoritesFirst(favorites),
                            selectedOption = spec.id,
                            onOptionSelected = { picks = picks.withValue(provider.id, it) },
                            showDivider = true,
                            optionTrailingIcon = { option ->
                                FavoriteStar(option, option in favorites) { favorites = favorites.toggled(option) }
                            }
                        )
                        SettingsDropdownRow(
                            title = "Reasoning",
                            subtitle = "${reasoning.label}: ${reasoning.hint}",
                            icon = Symbols.Outlined.Psychology,
                            options = spec.reasoning.map { it.label },
                            selectedOption = reasoning.label,
                            onOptionSelected = { label ->
                                spec.reasoning.firstOrNull { it.label == label }?.let {
                                    reasoningChoices = reasoningChoices.withValue(spec.id, it.id)
                                }
                            },
                            showDivider = true
                        )
                        if (spec.fast) {
                            val fast = spec.fastFrom(fastModels)
                            SettingsSelectorRow(
                                title = "Speed",
                                subtitle = speedSubtitle(provider, fast),
                                icon = Symbols.Outlined.Speed,
                                options = listOf(false, true),
                                selected = fast,
                                onSelect = { fastModels = if (it) fastModels + spec.id else fastModels - spec.id },
                                label = { if (it) "Fast" else "Standard" },
                                showDivider = true
                            )
                        } else {
                            SettingsInfoRow(
                                title = "Speed",
                                subtitle = "Standard. Fast Mode Is Only on " +
                                    provider.specs.filter { it.fast }.joinToString(" and ") { it.id } + ".",
                                icon = Symbols.Outlined.Speed,
                                showDivider = true
                            )
                        }
                        ApiKeyRows(vault, provider, hasKey, keyVersion) { keyVersion++ }
                        // What is sent, and where: information only, so a row without a chevron.
                        SettingsInfoRow(
                            title = "Privacy",
                            subtitle = "Only the Question You Tap Ask on Is Sent, to ${provider.host} over HTTPS. Your Key Goes Only to ${provider.host}.",
                            icon = Symbols.Outlined.PrivacyTip,
                            showDivider = false
                        )
                    }
                }
            }
        }
    }
}

/**
 * [model]'s star in the Model menu: tap to add it to the favorites, which list first, or take it out. Named with its
 * model, so a screen reader tells the stars apart.
 */
@Composable
private fun FavoriteStar(model: String, isFavorite: Boolean, onToggle: () -> Unit) {
    IconButton(
        onClick = onToggle,
        modifier = Modifier.semantics {
            role = Role.Switch
            toggleableState = ToggleableState(isFavorite)
        }
    ) {
        Icon(
            imageVector = if (isFavorite) Symbols.Filled.Star else Symbols.Outlined.Star,
            contentDescription = "Favorite $model",
            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

/** What Standard or Fast gets the user with [provider]: speed against price, and what Fast needs. */
private fun speedSubtitle(provider: AiProvider, fast: Boolean): String = when {
    !fast -> "Answers at the Usual Speed and Price."
    provider == AiProvider.ANTHROPIC -> "Answers Up to 2.5× Faster, at Twice the Price."
    provider == AiProvider.OPENAI -> "Answers Faster, at Twice the Price."
    else -> "Answers Faster, at Up to Twice the Price, with a Key on Gemini's Tier 2 or Above."
} + if (fast) " When Fast Isn't Available, It Answers at Standard Speed." else ""

/**
 * The key: a masked entry field and Save while none is saved ([hasKey]); the masked key and Remove Key once one is.
 * Only the masked text, which needs the key decrypted, is read off the main thread; [onChanged] after Save or Remove Key.
 */
@Composable
private fun ApiKeyRows(vault: AiKeyVault, provider: AiProvider, hasKey: Boolean, version: Int, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saved by remember(provider) { mutableStateOf<String?>(null) }
    LaunchedEffect(provider, version) {
        saved = withContext(Dispatchers.IO) { vault.masked(provider) }
    }

    if (hasKey) {
        SettingsInfoRow(
            title = "API Key",
            subtitle = "${saved ?: "••••••••"}. Encrypted on This Phone and Never Backed Up.",
            icon = Symbols.Outlined.Key,
            showDivider = true
        )
        SettingsRow(
            title = "Remove Key",
            subtitle = "Delete Your ${provider.displayName} Key from This Phone.",
            icon = Symbols.Outlined.Delete,
            onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) { vault.remove(provider) }
                    onChanged()
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
            Icon(Symbols.Outlined.Key, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.size(16.dp))
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it.trim().take(MAX_KEY_LENGTH) },
                label = { Text("${provider.displayName} API Key") },
                supportingText = { Text("Create One at ${provider.keyPage}.") },
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
                            onChanged()
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
