package com.pixel.intelligentsearch.feature.search

import com.pixel.intelligentsearch.core.ui.uniqueItems
import androidx.core.net.toUri
import androidx.core.content.edit
import com.pixel.intelligentsearch.core.ui.ClickDebouncer
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.data.settings.rememberSetting
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.util.findActivity
import com.pixel.intelligentsearch.feature.settings.tutorialTarget
import com.pixel.intelligentsearch.core.data.FileItem
import com.pixel.intelligentsearch.core.data.ContactItem
import com.pixel.intelligentsearch.core.data.AppItem
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pixel.intelligentsearch.R
import com.pixel.intelligentsearch.core.data.*
import com.pixel.intelligentsearch.core.theme.GoogleSansFlex
import com.pixel.intelligentsearch.core.ui.expressive.ExpressiveMotionTokens
import com.pixel.intelligentsearch.core.ui.materialYouGeminiPlate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The overlay's search bar: the text field, its ghost text, the pills around it and the engine chip. */
@Composable
internal fun OverlaySearchBar(s: OverlayScope) {
    val onOpenSettings = s.onOpenSettings
    val viewModel = s.viewModel
    val uiState = s.uiState
    val settingsState = s.settingsState
    val prefs = s.prefs
    val showTutorial = s.showTutorial
    val searchProviderName = s.searchProviderName
    val focusRequester = s.focusRequester
    val voice = s.voice
    val performAppLaunch = s.performAppLaunch
    val closeOverlay = s.closeOverlay
    val launchWebSearch = s.launchWebSearch
    val openWebAddress = s.openWebAddress
    val submitQuery = s.submitQuery
    val visibleApps = s.visibleApps
    val bestMatch = s.bestMatch
    val bestMatchText = s.bestMatchText
    var hasStartedTyping by s.ui::hasStartedTyping
    var textFieldValue by s.ui::textFieldValue
    var showDebugPill by s.ui::showDebugPill
    var isKeyboardDismissedByUser by s.ui::isKeyboardDismissedByUser
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedVisibility(
            visible = showDebugPill,
            enter = fadeIn(animationSpec = ExpressiveMotionTokens.morphSpring()) + expandVertically(animationSpec = ExpressiveMotionTokens.morphSpring()),
            exit = fadeOut(animationSpec = ExpressiveMotionTokens.morphSpring()) + shrinkVertically(animationSpec = ExpressiveMotionTokens.morphSpring())
        ) {
            Surface(
                shape = RoundedCornerShape(percent = 50),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.padding(bottom = 0.dp)
            ) {
                Text(
                    text = "Debug Enabled",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
        val debugUnlocked by rememberSetting(SettingsManager.DEBUG_UNLOCKED)
        val perfStatsOn by rememberSetting(SettingsManager.DEBUG_SHOW_PERF_STATS)
        val showPerfStats = debugUnlocked && perfStatsOn
        AnimatedVisibility(
            visible = showPerfStats,
            enter = fadeIn(animationSpec = ExpressiveMotionTokens.morphSpring()) + expandVertically(animationSpec = ExpressiveMotionTokens.morphSpring()),
            exit = fadeOut(animationSpec = ExpressiveMotionTokens.morphSpring()) + shrinkVertically(animationSpec = ExpressiveMotionTokens.morphSpring())
        ) {
            val totalResults = uiState.filteredApps.size + uiState.contacts.size + uiState.files.size + uiState.webSuggestions.size
            Surface(
                shape = RoundedCornerShape(percent = 50),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.padding(bottom = 0.dp)
            ) {
                Text(
                    text = "Latency: ${uiState.lastQueryLatency}ms | Results: $totalResults",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }

        val searchBarCornerRadius by androidx.compose.animation.core.animateDpAsState(
            targetValue = if (textFieldValue.text.isNotEmpty()) 20.dp else 100.dp,
            animationSpec = com.pixel.intelligentsearch.core.ui.expressive.ExpressiveMotionTokens.morphSpring(),
            label = "search_pill_shape_morph"
        )
        val searchBarShape = RoundedCornerShape(searchBarCornerRadius)
        Row(
            modifier = Modifier
                .tutorialTarget(1, prefs)
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .graphicsLayer {
                    shape = searchBarShape
                    clip = true
                }
                // Gemini input plate design (Android 17 QPR3 Google app): deep Material You
                // interior, light tone pooling along the bottom edge, ambient rim glow, bottom bevel
                .materialYouGeminiPlate(
                    cornerRadius = searchBarCornerRadius,
                    alpha = if (settingsState.pillOpacity == 50) 1f else (settingsState.pillOpacity / 100f).coerceIn(0f, 1f)
                )
                .listeningGlow(active = voice.active, level = { voice.level }, cornerRadius = searchBarCornerRadius)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    try {
                        if (voice.active) voice.stop()
                        isKeyboardDismissedByUser = false
                        focusRequester.requestFocus()
                        keyboardController?.show()
                        val act = context.findActivity()
                        if (act != null) {
                            androidx.core.view.WindowCompat.getInsetsController(act.window, act.window.decorView)
                                .show(androidx.core.view.WindowInsetsCompat.Type.ime())
                        }
                    } catch (_: Exception) {}
                }
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (voice.active) {
                // Rambler's cancel: stop listening and drop what was heard.
                androidx.compose.material3.FilledTonalIconButton(
                    onClick = {
                        voice.stop()
                        viewModel.onQueryChanged("")
                        textFieldValue = androidx.compose.ui.text.input.TextFieldValue("")
                    },
                    colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.size(36.dp)
                ) { Icon(Icons.Default.Close, contentDescription = "Cancel voice search", modifier = Modifier.size(20.dp)) }
            } else {
                SearchOverlayLensIcon(
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                val ghostTextAnnotated = remember(uiState.query, bestMatchText) {
                    val bmt = bestMatchText
                    if (bmt != null && bmt.startsWith(uiState.query, ignoreCase = true) && bmt.length > uiState.query.length) {
                        androidx.compose.ui.text.buildAnnotatedString {
                            withStyle(androidx.compose.ui.text.SpanStyle(color = Color.Transparent)) {
                                append(bmt.substring(0, minOf(uiState.query.length, bmt.length)))
                            }
                            withStyle(androidx.compose.ui.text.SpanStyle(color = Color.Unspecified)) {
                                append(bmt.substring(uiState.query.length))
                            }
                        }
                    } else null
                }

                androidx.compose.foundation.text.BasicTextField(
                    value = textFieldValue,
                    onValueChange = { newTfv ->
                        if (voice.active) voice.stop()
                        textFieldValue = newTfv
                        val newQuery = newTfv.text
                        if (newQuery.isNotEmpty()) {
                            hasStartedTyping = true
                        }
                        if (newQuery == "*xy88x*") {
                            prefs.edit { put(SettingsManager.DEBUG_UNLOCKED, true) }
                            viewModel.onQueryChanged("")
                            textFieldValue = androidx.compose.ui.text.input.TextFieldValue("")
                            showDebugPill = true
                            coroutineScope.launch {
                                delay(3000)
                                showDebugPill = false
                                onOpenSettings("debug")
                                /* closeOverlay() */
                            }
                        } else {
                            viewModel.onQueryChanged(newQuery)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    textStyle = androidx.compose.ui.text.TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp, fontFamily = GoogleSansFlex),
                    singleLine = true,
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Search
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = {
                        hasStartedTyping = false
                        keyboardController?.hide()
                        // The collected uiState can lag a keystroke behind; read the live value so Enter
                        // never acts on a query missing its last character.
                        submitQuery(viewModel.uiState.value.query, false)
                    }),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (uiState.query.isEmpty()) {
                                Text(if (voice.active) "Listening..." else "Search...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 20.sp, fontFamily = GoogleSansFlex)
                            } else if (ghostTextAnnotated != null) {
                                Text(
                                    text = ghostTextAnnotated,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.50f),
                                    fontSize = 20.sp,
                                    fontFamily = GoogleSansFlex,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                AnimatedVisibility(
                    visible = hasStartedTyping,
                    enter = fadeIn(ExpressiveMotionTokens.gentleSpring()) + expandVertically(ExpressiveMotionTokens.gentleSpring()),
                    exit = fadeOut(ExpressiveMotionTokens.gentleSpring()) + shrinkVertically(ExpressiveMotionTokens.gentleSpring())
                ) {
                    Text(
                        text = searchProviderName,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = GoogleSansFlex,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = uiState.query.isNotEmpty(),
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                IconButton(
                    onClick = {
                        viewModel.onQueryChanged("")
                        textFieldValue = androidx.compose.ui.text.input.TextFieldValue("")
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .padding(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Voice search: the keyboard drops while the search bar listens; while listening the mic becomes
            // Rambler's check button, which finishes and keeps the words. Gboard draws it in the secondary colour.
            if (voice.active) {
                androidx.compose.material3.FilledIconButton(
                    onClick = { voice.finish() },
                    colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    ),
                    modifier = Modifier.size(40.dp)
                ) { Icon(Icons.Default.Check, contentDescription = "Done speaking", modifier = Modifier.size(22.dp)) }
            } else {
                // Gboard Rambler's sparkle mic, drawn in the same colours as the lens icon; no container.
                IconButton(
                    onClick = {
                        focusManager.clearFocus(force = true)
                        keyboardController?.hide()
                        isKeyboardDismissedByUser = true
                        voice.start()
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    SearchOverlayMicIcon(modifier = Modifier.size(28.dp))
                }
            }

            // A clear gap between the voice button and settings.
            Spacer(modifier = Modifier.width(6.dp))

            IconButton(
                onClick = { 
                    if (ClickDebouncer.canClick()) {
                        hasStartedTyping = false
                        focusManager.clearFocus(force = true)
                        keyboardController?.hide()
                        onOpenSettings("main") 
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .zIndex(if (showTutorial) 10000f else 0f)
                    .tutorialTarget(2, prefs)
                    .padding(12.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_three_bars),
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        AnimatedVisibility(
            visible = uiState.bangSuggestions.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                uniqueItems(uiState.bangSuggestions, key = { bang -> "bang_${bang.prefix}" }, contentType = { "bang" }) { bang ->
                    AssistChip(
                        onClick = {
                            val currentQ = uiState.query
                            val bangTrigger = if (bang.prefix.isNotEmpty() && !bang.prefix[0].isLetterOrDigit()) {
                                bang.prefix[0].toString()
                            } else {
                                "!"
                            }
                            val newQ = if (currentQ.startsWith(bangTrigger)) {
                                "${bang.prefix} "
                            } else if (currentQ.contains(bangTrigger)) {
                                "${currentQ.substringBeforeLast(bangTrigger)}${bang.prefix} "
                            } else {
                                "${bang.prefix} "
                            }
                            textFieldValue = androidx.compose.ui.text.input.TextFieldValue(
                                text = newQ,
                                selection = androidx.compose.ui.text.TextRange(newQ.length)
                            )
                            viewModel.onQueryChanged(newQ)
                        },
                        label = { Text("${bang.prefix} ${bang.name}", fontSize = 12.sp, fontFamily = GoogleSansFlex) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        border = null,
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }
        }
    }
}
