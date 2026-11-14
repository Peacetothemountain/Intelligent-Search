package com.pixel.intelligentsearch.feature.search

import com.pixel.intelligentsearch.feature.settings.IconButton
import com.pixel.intelligentsearch.core.ui.uniqueItems
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.content.edit
import com.pixel.intelligentsearch.core.ui.ClickDebouncer
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.data.settings.rememberSetting
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.util.findActivity
import com.pixel.intelligentsearch.feature.settings.tutorialTarget
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import com.pixel.intelligentsearch.core.ui.icons.filled.*
import com.pixel.intelligentsearch.core.ui.icons.automirrored.filled.*
import com.pixel.intelligentsearch.core.ui.icons.rounded.*
import com.pixel.intelligentsearch.core.ui.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixel.intelligentsearch.R
import com.pixel.intelligentsearch.core.data.*
import com.pixel.intelligentsearch.core.theme.GoogleSansFlex
import com.pixel.intelligentsearch.core.ui.expressive.ExpressiveMotionTokens
import com.pixel.intelligentsearch.core.ui.geminiPlateIdleFlow
import com.pixel.intelligentsearch.core.ui.materialYouGeminiPlate
import com.pixel.intelligentsearch.core.ui.plateFlowsWhileIdle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The Best Match completion drawn behind the search field: the [typed] text, invisible, then the rest of
 * [bestMatchName]. The hidden part is the typed text itself rather than the name's start, which can differ in case
 * ("gm" for "Gmail") and so in width, pushing the visible rest out of line with the letters typed. Null when the name
 * doesn't go on from the typed text. [markerLength] is 1 in a search limited to one kind of result ("@jo"), whose
 * first character is not part of the name looked for.
 */
internal fun ghostText(typed: String, bestMatchName: String?, markerLength: Int = 0): androidx.compose.ui.text.AnnotatedString? {
    // What the name has to go on from: the typed text after the marker of a search limited to one kind of result
    // ("@jo" is "jo"), which is all of it in an ordinary search.
    val words = typed.substring(markerLength.coerceIn(0, typed.length))
    // With only the marker typed there is nothing for a name to go on from.
    if (markerLength > 0 && words.isEmpty()) return null
    if (bestMatchName == null || bestMatchName.length <= words.length || !bestMatchName.startsWith(words, ignoreCase = true)) {
        return null
    }
    return androidx.compose.ui.text.buildAnnotatedString {
        withStyle(androidx.compose.ui.text.SpanStyle(color = Color.Transparent)) { append(typed) }
        withStyle(androidx.compose.ui.text.SpanStyle(color = Color.Unspecified)) { append(bestMatchName.substring(words.length)) }
    }
}

/** The search pill's alpha for the Search Pill Opacity setting: [opacity] percent, so a higher setting is never more see-through. */
internal fun pillAlpha(opacity: Int): Float = (opacity / 100f).coerceIn(0f, 1f)

/** Below this width the search bar gives its Discover button and mic's room to the words being typed. */
private const val NARROW_BAR_DP = 360

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
    val calculatorKeyboard by viewModel.calculatorKeyboard.collectAsStateWithLifecycle()
    val bestMatchText = s.bestMatchText
    var hasStartedTyping by s.ui::hasStartedTyping
    var textFieldValue by s.ui::textFieldValue
    var showDebugPill by s.ui::showDebugPill
    var isKeyboardDismissedByUser by s.ui::isKeyboardDismissedByUser
    val context = LocalContext.current
    // In the first week after installing, once the tutorial is done, the empty bar shows things to type.
    val hintExamples = remember(settingsState.searchCalculator) {
        val firstWeek = SearchHints.inFirstWeek(SearchHints.firstInstallMs(context), System.currentTimeMillis())
        if (firstWeek && prefs[SettingsManager.TUTORIAL_COMPLETED]) SearchHints.examples(settingsState.searchCalculator) else emptyList()
    }
    val coroutineScope = rememberCoroutineScope()
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    val usedKeyPresses = remember { mutableSetOf<androidx.compose.ui.input.key.Key>() }

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
        val showMic by rememberSetting(SettingsManager.OVERLAY_SHOW_MIC)
        // Under 360 dp (a small phone, or the largest display size) the bar's buttons leave the field room for a
        // few letters only, so while words are typed the Discover button and the mic stand aside for them.
        val windowWidthPx = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width
        val narrowBar = with(androidx.compose.ui.platform.LocalDensity.current) { windowWidthPx < NARROW_BAR_DP.dp.roundToPx() }
        val typingOnNarrowBar = narrowBar && uiState.query.isNotEmpty()
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
                    alpha = pillAlpha(settingsState.pillOpacity)
                )
                // While the bar sits idle, that bottom light flows gently.
                .geminiPlateIdleFlow(
                    idle = plateFlowsWhileIdle(textFieldValue.text, voice.active),
                    alpha = pillAlpha(settingsState.pillOpacity),
                    motion = com.pixel.intelligentsearch.core.ui.IdleFlowMotion.SearchBar
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
                        // Voice is over, so the keyboard may come back.
                        isKeyboardDismissedByUser = false
                    },
                    colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.size(36.dp)
                ) { Icon(Icons.Default.Close, contentDescription = "Cancel voice search", modifier = Modifier.size(20.dp)) }
            } else {
                val todayButton by rememberSetting(SettingsManager.TODAY_BUTTON)
                if (todayButton && !typingOnNarrowBar) {
                    // Today, to the left of the lens: the bar's own icon size and weight.
                    com.pixel.intelligentsearch.feature.search.today.TodayBarButton(
                        onClick = {
                            isKeyboardDismissedByUser = true
                            focusManager.clearFocus(force = true)
                            keyboardController?.hide()
                            s.ui.todayClosesAtOnce = false
                            s.ui.todayOpen = true
                        },
                        modifier = Modifier.onGloballyPositioned { s.ui.todayOrigin = it.boundsInRoot().center }
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                SearchOverlayLensIcon(
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                // From the field's own text, so the hidden part lines up with exactly what the field draws.
                // In a search limited to one kind of result the name goes on from the words after its marker.
                val scopeMarker = uiState.scoped?.scope?.marker
                val markerLength = if (scopeMarker != null && textFieldValue.text.firstOrNull() == scopeMarker) 1 else 0
                val ghostTextAnnotated = remember(textFieldValue.text, bestMatchText, markerLength) {
                    ghostText(textFieldValue.text, bestMatchText, markerLength)
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
                            }
                        } else {
                            viewModel.onQueryChanged(newQuery)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        // With Show Keyboard When Search Opens off, a tap on the field itself brings the keyboard
                        // up; from then on it may come back on its own, as after any open.
                        .onFocusChanged { if (it.isFocused && !voice.active) isKeyboardDismissedByUser = false }
                        // A physical keyboard's arrows, Tab, Enter and Escape move through the results first.
                        .onPreviewKeyEvent { s.onResultsKey(it, context, rtl, usedKeyPresses) },
                    textStyle = androidx.compose.ui.text.TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp, fontFamily = GoogleSansFlex),
                    singleLine = true,
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        // The widget's Calculator shortcut asks for the number pad (digits, + - * / and brackets).
                        keyboardType = if (calculatorKeyboard) androidx.compose.ui.text.input.KeyboardType.Phone else androidx.compose.ui.text.input.KeyboardType.Text,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Search
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = {
                        hasStartedTyping = false
                        keyboardController?.hide()
                        // The highlight, then the phrasing's action or the top result (OverlayEnterKey).
                        s.onEnter(context, highlighted = null)
                    }),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (uiState.query.isEmpty()) {
                                if (!voice.active && !calculatorKeyboard && hintExamples.isNotEmpty()) {
                                    // The first week: things to type, in place of the plain hint.
                                    SearchHint(hintExamples)
                                } else {
                                    // One line: in a field too narrow for it (a small screen at the largest text) it wrapped out of the bar.
                                    Text(if (voice.active) "Listening…" else if (calculatorKeyboard) "Calculate…" else SearchHints.PLAIN, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 20.sp, fontFamily = GoogleSansFlex, maxLines = 1, softWrap = false)
                                }
                            } else if (ghostTextAnnotated != null) {
                                // The suggested ending in full onSurfaceVariant, readable at 4.5:1, and set apart
                                // from the typed text's onSurface by its color, as the "Search…" hint is.
                                Text(
                                    text = ghostTextAnnotated,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            // With Show Microphone in Search Bar off there is no mic, but listening started another way (the
            // assistant gesture) still gets its check button.
            if (voice.active) {
                androidx.compose.material3.FilledIconButton(
                    onClick = { voice.finish() },
                    colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    ),
                    modifier = Modifier.size(40.dp)
                ) { Icon(Icons.Default.Check, contentDescription = "Done speaking", modifier = Modifier.size(22.dp)) }
            } else if (showMic && !typingOnNarrowBar) {
                // Gboard Rambler's sparkle mic, drawn in the same colours as the lens icon; no container.
                IconButton(
                    onClick = {
                        focusManager.clearFocus(force = true)
                        keyboardController?.hide()
                        isKeyboardDismissedByUser = true
                        voice.start()
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { contentDescription = "Search by voice" }
                ) {
                    SearchOverlayMicIcon(modifier = Modifier.size(28.dp))
                }
            }

            // A clear gap between the voice button and settings.
            if (voice.active || (showMic && !typingOnNarrowBar)) Spacer(modifier = Modifier.width(6.dp))

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
                            // Only the bang word the chips were found for becomes the chip's (whatever its trigger
                            // symbol); the rest of the query stays.
                            val newQ = replaceBangWord(uiState.query, uiState.bangWordStart, bang.prefix)
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
