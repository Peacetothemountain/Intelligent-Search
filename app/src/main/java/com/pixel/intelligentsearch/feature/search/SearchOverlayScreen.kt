package com.pixel.intelligentsearch.feature.search
import com.pixel.intelligentsearch.feature.settings.tutorialTarget
import android.app.SearchManager
import com.pixel.intelligentsearch.core.data.IntelligentSearchSettings
import com.pixel.intelligentsearch.feature.settings.bouncyClickable
import com.pixel.intelligentsearch.feature.settings.TutorialSpotlightOverlay
import com.pixel.intelligentsearch.feature.settings.TutorialManager
import com.pixel.intelligentsearch.feature.settings.SettingsViewModel
import com.pixel.intelligentsearch.feature.settings.performClickHaptic
import com.pixel.intelligentsearch.core.data.FileItem
import com.pixel.intelligentsearch.core.data.ContactItem
import com.pixel.intelligentsearch.core.data.AppItem
import com.pixel.intelligentsearch.App
import com.pixel.intelligentsearch.feature.settings.TutorialStepInfo
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.speech.RecognizerIntent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pixel.intelligentsearch.R
import com.pixel.intelligentsearch.feature.widget.SearchWidgetProvider
import com.pixel.intelligentsearch.feature.settings.SettingsActivity
import com.pixel.intelligentsearch.core.data.*
import com.pixel.intelligentsearch.core.theme.GoogleSansFlex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AppGridItem(app: AppItem, onClick: () -> Unit) {
    val context = LocalContext.current
    val appIconState = remember(app.packageName) { mutableStateOf<AppIconResult?>(null) }
    LaunchedEffect(app.packageName) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val icon = getThemedAppIcon(context, app.packageName)
            appIconState.value = icon
        }
    }
    val appIcon = appIconState.value

    Column(
        modifier = Modifier
            .width(80.dp)
            .bouncyClickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (appIcon != null) {
            Image(
                bitmap = appIcon.bitmap,
                contentDescription = app.name,
                modifier = Modifier.size(48.dp),
                colorFilter = if (appIcon.isMonochrome) androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant) else null
            )
        } else {
            Image(
                bitmap = app.icon.toBitmap().asImageBitmap(),
                contentDescription = app.name,
                modifier = Modifier.size(48.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = app.name,
            color = Color.White,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontFamily = GoogleSansFlex
        )
    }
}

@Composable
fun SearchSettingsItem(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(80.dp)
            .bouncyClickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search Settings",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Search",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontFamily = GoogleSansFlex
        )
    }
}

@Composable
fun ShortcutRow(iconRes: Int, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(painter = painterResource(id = iconRes), contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = GoogleSansFlex
        )
    }
}

data class AppIconResult(val bitmap: androidx.compose.ui.graphics.ImageBitmap, val isMonochrome: Boolean)

fun getThemedAppIcon(context: Context, packageName: String): AppIconResult? {
    try {
        val pm = context.packageManager
        val icon = pm.getApplicationIcon(packageName)
        
        fun drawableToBitmap(d: android.graphics.drawable.Drawable): android.graphics.Bitmap {
            if (d is android.graphics.drawable.BitmapDrawable && d.bitmap != null) {
                return d.bitmap
            }
            val bmp = android.graphics.Bitmap.createBitmap(
                if (d.intrinsicWidth > 0) d.intrinsicWidth else 100,
                if (d.intrinsicHeight > 0) d.intrinsicHeight else 100,
                android.graphics.Bitmap.Config.ARGB_8888
            )
            val canvas = android.graphics.Canvas(bmp)
            d.setBounds(0, 0, canvas.width, canvas.height)
            d.draw(canvas)
            return bmp
        }

        if (icon is android.graphics.drawable.AdaptiveIconDrawable) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val monochrome = icon.monochrome
                if (monochrome != null) {
                    return AppIconResult(drawableToBitmap(monochrome).asImageBitmap(), true)
                }
            }
            val foreground = icon.foreground
            if (foreground != null) {
                return AppIconResult(drawableToBitmap(foreground).asImageBitmap(), true)
            }
        }
        return AppIconResult(drawableToBitmap(icon).asImageBitmap(), false)
    } catch (e: Exception) {
        return null
    }
}

fun getAppName(context: Context, packageName: String): String {
    return try {
        val pm = context.packageManager
        val info = pm.getApplicationInfo(packageName, 0)
        pm.getApplicationLabel(info).toString()
    } catch (e: Exception) {
        "App"
    }
}

@Composable
fun SearchPill(iconRes: Int? = null, iconBitmap: AppIconResult? = null, title: String, scale: Float = 1f, onClick: () -> Unit) {
    val hPadding = (12 * scale).dp
    val vPadding = (8 * scale).dp
    val iconSize = (18 * scale).dp
    val textSize = (14 * scale).sp

    Row(
        modifier = Modifier
            .padding(end = (8 * scale).dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(percent = 50))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(percent = 50))
            .bouncyClickable(onClick = onClick)
            .padding(horizontal = hPadding, vertical = vPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconBitmap != null) {
            Image(
                bitmap = iconBitmap.bitmap,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                colorFilter = if (iconBitmap.isMonochrome) androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant) else null
            )
        } else if (iconRes != null) {
            Image(painter = painterResource(id = iconRes), contentDescription = null, modifier = Modifier.size(iconSize))
        }
        Spacer(modifier = Modifier.width((8 * scale).dp))
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = textSize,
            fontWeight = FontWeight.Medium,
            fontFamily = GoogleSansFlex
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Suppress("DEPRECATION")
private fun finishWithoutTransition(activity: android.app.Activity?) {
    if (activity != null && !activity.isFinishing) {
        activity.finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            activity.overrideActivityTransition(android.app.Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            activity.overridePendingTransition(0, 0)
        }
    }
}

@Composable
fun SearchOverlayScreen(
    onOpenSettings: (String) -> Unit,
    onLaunchApp: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    isKeyboardDisabled: Boolean = false
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settingsState by settingsViewModel.settingsState.collectAsStateWithLifecycle()
    
    val prefs = remember(context) { context.getSharedPreferences("PREFERENCES_CUSTOMISATIONS", Context.MODE_PRIVATE) }
    val transitionState = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) {
        // Trigger the entrance animation immediately to prevent startup hesitation.
        // With the matrix background optimized, we no longer need to wait for frame flushes.
        transitionState.targetState = true
    }
    val isOpening = transitionState.targetState
    
    val activity = context as? android.app.Activity
    val isFromBackSwipe = remember(activity) {
        activity?.intent?.getBooleanExtra("FROM_BACK_SWIPE", false) == true
    }

    val coroutineScope = rememberCoroutineScope()
    // Animatable for the overlay expansion progress: 0f = collapsed pill, 1f = fully expanded
    val overlayProgressAnim = remember { Animatable(if (isFromBackSwipe) 1f else 0f) }
    
    val morphProgress = overlayProgressAnim.value.coerceIn(0f, 1f)

    LaunchedEffect(isOpening) {
        if (isOpening) {
            if (!isFromBackSwipe) {
                overlayProgressAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = 0.86f,
                        stiffness = 180f
                    )
                )
            } else {
                overlayProgressAnim.snapTo(1f)
            }
        } else {
            val currentVel = overlayProgressAnim.velocity
            overlayProgressAnim.animateTo(
                targetValue = 0f,
                initialVelocity = currentVel,
                animationSpec = spring(
                    dampingRatio = 0.92f,
                    stiffness = 250f
                )
            )
            val act = context as? android.app.Activity
            finishWithoutTransition(act)
        }
    }
    val overlayProgress = overlayProgressAnim.value

    val focusRequester = remember { FocusRequester() }
    
    val isForceTutorial = prefs.getBoolean("debug_unlocked", false) && prefs.getBoolean("force_tutorial", false)
    var showTutorial by remember {
        if (isForceTutorial) {
            TutorialManager.resetForForceTutorial(prefs)
        }
        mutableStateOf(TutorialManager.isTutorialActive(prefs))
    }
    
    var showDebugPill by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val hapticContext = LocalContext.current

    val closeOverlay = {
        keyboardController?.hide()
        if (transitionState.targetState) {
            transitionState.targetState = false
        } else {
            val act = context as? android.app.Activity
            act?.finish()
        }
    }

    val launchWebSearch: (String) -> Unit = { searchQuery ->
        val intent = if (settingsState.searchEngine == "Custom" && settingsState.customSearchEngineUrl.isNotEmpty()) {
            val urlStr = settingsState.customSearchEngineUrl.replace("%s", Uri.encode(searchQuery))
            if (urlStr.startsWith("http://", ignoreCase = true) || urlStr.startsWith("https://", ignoreCase = true)) {
                Intent(Intent.ACTION_VIEW, Uri.parse(urlStr))
            } else {
                Intent(Intent.ACTION_WEB_SEARCH).apply { putExtra(SearchManager.QUERY, searchQuery) }
            }
        } else if (settingsState.searchEngine == "DuckDuckGo") {
            Intent(Intent.ACTION_VIEW, Uri.parse("https://duckduckgo.com/?q=${Uri.encode(searchQuery)}"))
        } else if (settingsState.searchEngine == "Bing") {
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.bing.com/search?q=${Uri.encode(searchQuery)}"))
        } else {
            Intent(Intent.ACTION_WEB_SEARCH).apply {
                if (settingsState.searchEngine == "Google") {
                    setPackage("com.google.android.googlequicksearchbox")
                }
                putExtra(SearchManager.QUERY, searchQuery)
            }
        }
        try {
            viewModel.addSearchHistory(searchQuery)
            context.startActivity(intent)
        } catch (e: Exception) {
            val fallbackIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra(SearchManager.QUERY, searchQuery)
            }
            try { context.startActivity(fallbackIntent) } catch (ex: Exception) {}
        }
        closeOverlay()
    }

    LaunchedEffect(transitionState.targetState) {
        if (transitionState.targetState) {
            performClickHaptic(hapticContext)
            if (!showTutorial) {
                try {
                    delay(220)
                    focusRequester.requestFocus()
                    keyboardController?.show()
                } catch (e: Exception) {}
            }
        }
    }
    
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                keyboardController?.hide()
                viewModel.onQueryChanged("")
                if (transitionState.targetState) {
                    transitionState.targetState = false
                }
            } else if (event == Lifecycle.Event.ON_RESUME) {
                val forceTut = prefs.getBoolean("debug_unlocked", false) && prefs.getBoolean("force_tutorial", false)
                if (forceTut) {
                    TutorialManager.resetForForceTutorial(prefs)
                    showTutorial = true
                } else {
                    showTutorial = TutorialManager.isTutorialActive(prefs)
                }
                
                transitionState.targetState = true
                viewModel.loadInitialData()
                try {
                    coroutineScope.launch {
                        delay(50)
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    }
                } catch (e: Exception) {}
                val currentStep = TutorialManager.getStep(prefs)
                
                if (showTutorial && currentStep == 2) {
                    TutorialManager.setStep(prefs, 3)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    
    BackHandler(enabled = showTutorial) {
        if (showTutorial) {
            // Suppress exit during tutorial
        }
    }

    val visibleApps = remember(uiState.filteredApps, settingsState.hiddenApps) {
        uiState.filteredApps.filter { !settingsState.hiddenApps.contains(it.packageName) }
    }

    val bestMatch = remember(uiState.query, uiState.contacts, visibleApps, uiState.files) {
        if (uiState.query.isEmpty()) return@remember null
        val contactMatch = uiState.contacts.firstOrNull { it.name.startsWith(uiState.query, ignoreCase = true) }
        if (contactMatch != null) return@remember contactMatch
        val appMatch = visibleApps.firstOrNull { it.name.startsWith(uiState.query, ignoreCase = true) }
        if (appMatch != null) return@remember appMatch
        val fileMatch = uiState.files.firstOrNull { it.name.startsWith(uiState.query, ignoreCase = true) }
        if (fileMatch != null) return@remember fileMatch
        null
    }
    
    val bestMatchText = when (bestMatch) {
        is ContactItem -> bestMatch.name
        is AppItem -> bestMatch.name
        is FileItem -> bestMatch.name
        else -> null
    }

    val filteredContacts = remember(uiState.contacts, bestMatch) {
        if (bestMatch is ContactItem) uiState.contacts.filter { it != bestMatch }
        else uiState.contacts
    }
    
    val filteredApps = remember(visibleApps, bestMatch) {
        if (bestMatch is AppItem) visibleApps.filter { it != bestMatch }
        else visibleApps
    }
    
    val filteredFiles = remember(uiState.files, bestMatch) {
        if (bestMatch is FileItem) uiState.files.filter { it != bestMatch }
        else uiState.files
    }

    val searchBarContent = @Composable {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedVisibility(
                visible = showDebugPill,
                enter = fadeIn(animationSpec = spring(0.72f, 400f)) + expandVertically(animationSpec = spring(0.72f, 400f)),
                exit = fadeOut(animationSpec = spring(0.72f, 400f)) + shrinkVertically(animationSpec = spring(0.72f, 400f))
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
            val showPerfStats = prefs.getBoolean("debug.show_perf_stats", false)
            AnimatedVisibility(
                visible = showPerfStats,
                enter = fadeIn(animationSpec = spring(0.72f, 400f)) + expandVertically(animationSpec = spring(0.72f, 400f)),
                exit = fadeOut(animationSpec = spring(0.72f, 400f)) + shrinkVertically(animationSpec = spring(0.72f, 400f))
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
            
            Row(
                modifier = Modifier
                    .tutorialTarget(1, prefs)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(
                            alpha = (settingsState.pillOpacity / 100f).coerceIn(0f, 1f)
                        ),
                        RoundedCornerShape(percent = 50)
                    )
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_search_ai_colored),
                    contentDescription = "Google",
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                androidx.compose.foundation.text.BasicTextField(
                    value = uiState.query,
                    onValueChange = { newQuery ->
                        if (newQuery == "*xy88x*") {
                            prefs.edit().putBoolean("debug_unlocked", true).apply()
                            viewModel.onQueryChanged("")
                            showDebugPill = true
                            coroutineScope.launch {
                                delay(3000)
                                showDebugPill = false
                                onOpenSettings("debug")
                                closeOverlay()
                            }
                        } else {
                            viewModel.onQueryChanged(newQuery)
                        }
                    },
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    textStyle = androidx.compose.ui.text.TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 22.sp, fontFamily = GoogleSansFlex),
                    singleLine = true,
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = {
                        keyboardController?.hide()
                        if (uiState.query.isNotEmpty()) {
                            viewModel.addSearchHistory(uiState.query)
                            if (bestMatch != null) {
                                when (bestMatch) {
                                    is ContactItem -> {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(bestMatch.lookupUri))
                                        try { context.startActivity(intent) } catch (e: Exception) {}
                                    }
                                    is AppItem -> {
                                        onLaunchApp(bestMatch.packageName)
                                    }
                                    is FileItem -> {
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(Uri.parse(bestMatch.uri), bestMatch.mimeType)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            setPackage("com.google.android.apps.nbu.files")
                                        }
                                        try { context.startActivity(intent) } catch (e: Exception) {
                                            intent.setPackage(null)
                                            try { context.startActivity(intent) } catch(e2: Exception) {}
                                        }
                                    }
                                }
                                closeOverlay()
                            } else if (settingsState.appQuickLaunch && visibleApps.isNotEmpty()) {
                                onLaunchApp(visibleApps.first().packageName)
                            } else {
                                launchWebSearch(uiState.query)
                             }
                        }
                    }),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                             if (uiState.query.isEmpty()) {
                                 Text("Search...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 22.sp, fontFamily = GoogleSansFlex)
                            } else if (bestMatchText != null && bestMatchText.startsWith(uiState.query, ignoreCase = true)) {
                                val builder = androidx.compose.ui.text.AnnotatedString.Builder()
                                builder.pushStyle(androidx.compose.ui.text.SpanStyle(color = Color.Transparent))
                                builder.append(bestMatchText.substring(0, uiState.query.length))
                                builder.pop()
                                builder.pushStyle(androidx.compose.ui.text.SpanStyle(color = Color.Gray))
                                builder.append(bestMatchText.substring(uiState.query.length))
                                builder.pop()
                                Text(
                                    text = builder.toAnnotatedString(),
                                    fontSize = 18.sp,
                                    fontFamily = GoogleSansFlex,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                IconButton(
                    onClick = { onOpenSettings("main") },
                    modifier = Modifier
                        .size(48.dp)
                        .zIndex(if (showTutorial) 10000f else 0f)
                        .tutorialTarget(2, prefs)
                        .padding(12.dp)
                ) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    val verticalPad = 14.dp

    val quickAppPanelContent = @Composable {
        if (settingsState.quickSearchHorizontal) {
            LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                                            val pillPackages = if (settingsState.contextAwareQuickApps) {
                        uiState.recentApps.take(settingsState.shortcutResultsCount).map { it.packageName }
                    } else {
                        settingsState.searchPills.split(",").filter { it.isNotBlank() }.take(settingsState.shortcutResultsCount)
                    }
                        val dynamicScale = if (pillPackages.size > 6) (6f / pillPackages.size.toFloat()).coerceIn(0.6f, 1f) else 1f
                        items(pillPackages, key = { it }) { packageName ->
                            val appIconState = remember(packageName) { mutableStateOf<AppIconResult?>(null) }
                            val appNameState = remember(packageName) { mutableStateOf("App") }
                            LaunchedEffect(packageName) {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    val icon = getThemedAppIcon(context, packageName)
                                    val name = getAppName(context, packageName)
                                    appIconState.value = icon
                                    appNameState.value = name
                                }
                            }
                            val appIcon = appIconState.value
                            val appName = appNameState.value
                            if (appIcon != null) {
                                SearchPill(iconBitmap = appIcon, title = appName, scale = dynamicScale) {
                                    if (uiState.query.isNotEmpty()) viewModel.addSearchHistory(uiState.query)
                                    
                                    val searchStr = uiState.query
                                    val intent = when (packageName) {
                                        "com.android.chrome" -> {
                                            val url = if (searchStr.isEmpty()) "https://google.com" else "https://google.com/search?q=${Uri.encode(searchStr)}"
                                            Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { setPackage(packageName) }
                                        }
                                        "com.google.android.apps.maps" -> {
                                            Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(searchStr.ifEmpty { "Restaurants" })}")).apply { setPackage(packageName) }
                                        }
                                        "com.google.android.youtube" -> {
                                            Intent(Intent.ACTION_SEARCH).apply { setPackage(packageName); putExtra("query", searchStr.ifEmpty { "Music" }) }
                                        }
                                        "com.android.vending" -> {
                                            Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=${Uri.encode(searchStr)}"))
                                        }
                                        "com.google.android.contacts" -> {
                                            context.packageManager.getLaunchIntentForPackage(packageName) 
                                                ?: Intent(Intent.ACTION_PICK).apply { type = android.provider.ContactsContract.Contacts.CONTENT_TYPE }
                                        }
                                        "com.google.android.apps.nbu.files" -> {
                                            context.packageManager.getLaunchIntentForPackage(packageName) ?: Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
                                        }
                                        else -> {
                                             val searchIntent = Intent(Intent.ACTION_SEARCH).apply {
                                                 setPackage(packageName)
                                                 putExtra("query", searchStr)
                                             }
                                             val resolved = context.packageManager.queryIntentActivities(searchIntent, 0)
                                             val hasExportedSearch = resolved.any { it.activityInfo.exported }
                                             if (hasExportedSearch) {
                                                 searchIntent
                                             } else {
                                                 context.packageManager.getLaunchIntentForPackage(packageName)
                                             }
                                         }
                                    }
                                    if (intent != null) {
                                        try {
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            // Fallback if needed
                                        }
                                    }
                                    closeOverlay()
                                }
                            }
                        }
            }
        }
    }

    val searchResultsContent = @Composable {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(
                top = if (settingsState.bottomSearch && settingsState.bottomSearchResult) 72.dp else 8.dp,
                bottom = 8.dp
            ),
            reverseLayout = if (!settingsState.bottomSearch) false else settingsState.bottomSearchResult,
            verticalArrangement = if (settingsState.bottomSearch) Arrangement.Bottom else Arrangement.Top
        ) {

            if (settingsState.smartClipboardSuggestions && uiState.directActions.isNotEmpty()) {
                itemsIndexed(uiState.directActions, key = { index, action -> "direct_action_${action.title}_$index" }) { _, action ->
                    val dismissState = rememberSwipeToDismissBoxState()
                    LaunchedEffect(dismissState.currentValue) {
                        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
                            viewModel.dismissDirectAction(action)
                        }
                    }
                    SwipeToDismissBox(
                        state = dismissState,
                        backgroundContent = { Box(modifier = Modifier.fillMaxSize().background(Color.Transparent)) },
                        content = {
                            val actionIcon = when (action.iconType) {
                                "link" -> Icons.Default.Link
                                "phone" -> Icons.Default.Call
                                "search" -> Icons.Default.Search
                                "calendar" -> Icons.Default.Event
                                "message" -> Icons.AutoMirrored.Filled.Message
                                else -> Icons.Default.ContentPaste
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f), RoundedCornerShape(32.dp))
                                    .clip(RoundedCornerShape(32.dp))
                                    .bouncyClickable {
                                        action.intent?.let { intent ->
                                            try {
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }
                                        closeOverlay()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = actionIcon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = action.title,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = GoogleSansFlex
                                    )
                                    Text(
                                        text = action.subtitle,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                        fontSize = 13.sp,
                                        fontFamily = GoogleSansFlex,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    )
                }
                item(key = "direct_actions_divider") { HorizontalDivider(color = Color(0xFF2C2C35), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp)) }
            }

            if (bestMatch != null) {
                item(key = "top_hit_label") {
                    Text("Top Hit", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontFamily = GoogleSansFlex, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                }
                item(key = "top_hit_content") {
                    when (val match = bestMatch) {
                        is ContactItem -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .bouncyClickable {
                                        val intent = if (settingsState.contactDirectCall) {
                                            Intent(Intent.ACTION_DIAL, Uri.parse("tel:${match.phoneNumber}"))
                                        } else {
                                            Intent(Intent.ACTION_VIEW, Uri.parse(match.lookupUri))
                                        }
                                        try { context.startActivity(intent) } catch(e: Exception) {}
                                        closeOverlay()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
                                    Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(match.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontFamily = GoogleSansFlex)
                                    Text(match.phoneNumber, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontFamily = GoogleSansFlex)
                                }
                            }
                        }
                        is AppItem -> {
                            val appIconState = remember(match.packageName) { mutableStateOf<AppIconResult?>(null) }
                            LaunchedEffect(match.packageName) {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    val icon = getThemedAppIcon(context, match.packageName)
                                    appIconState.value = icon
                                }
                            }
                            val appIcon = appIconState.value

                            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().bouncyClickable { onLaunchApp(match.packageName) },
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (appIcon != null) {
                                            Image(
                                                bitmap = appIcon.bitmap,
                                                contentDescription = null,
                                                modifier = Modifier.size(48.dp),
                                                colorFilter = if (appIcon.isMonochrome) androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant) else null
                                            )
                                        } else {
                                            Image(bitmap = match.icon.toBitmap().asImageBitmap(), contentDescription = null, modifier = Modifier.size(48.dp))
                                        }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column {
                                            Text(match.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp, fontFamily = GoogleSansFlex, fontWeight = FontWeight.Medium)
                                            Text("App", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, fontFamily = GoogleSansFlex)
                                        }
                                    }
                                    if (match.actions.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(
                                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            match.actions.forEach { action ->
                                                AssistChip(
                                                    onClick = {
                                                        val intent = Intent(action.action)
                                                        if (action.dataUri != null) intent.data = android.net.Uri.parse(action.dataUri)
                                                        intent.setPackage(match.packageName)
                                                        try {
                                                            context.startActivity(intent)
                                                            closeOverlay()
                                                        } catch (e: Exception) {
                                                            intent.setPackage(null)
                                                            try { context.startActivity(intent); closeOverlay() } catch (e2: Exception) {}
                                                        }
                                                    },
                                                    label = { Text(action.title, color = MaterialTheme.colorScheme.onPrimaryContainer, fontFamily = GoogleSansFlex) },
                                                    colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer, labelColor = MaterialTheme.colorScheme.onPrimaryContainer),
                                                    border = null,
                                                    shape = RoundedCornerShape(32.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        is FileItem -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .bouncyClickable {
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(Uri.parse(match.uri), match.mimeType)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            setPackage("com.google.android.apps.nbu.files")
                                        }
                                        try { context.startActivity(intent) } catch(e: Exception) {
                                            intent.setPackage(null)
                                            try { context.startActivity(intent) } catch(e2: Exception) {}
                                        }
                                        closeOverlay()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (settingsState.filesThumbnails) {
                                    FileIconThumbnail(match.uri, match.mimeType)
                                } else {
                                    Box(modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                                        Icon(imageVector = Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(match.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontFamily = GoogleSansFlex, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
                item(key = "top_hit_divider") { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp)) }
            }

            if (uiState.query.isEmpty() && uiState.recentSearches.isNotEmpty()) {
                item(key = "recent_label") {
                    Text("Recent", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontFamily = GoogleSansFlex, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
                itemsIndexed(uiState.recentSearches, key = { index, query -> "recent_${query}_$index" }) { index, recentQuery ->
                    val dismissState = rememberSwipeToDismissBoxState()
                    LaunchedEffect(dismissState.currentValue) {
                        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
                            viewModel.removeSearchHistory(recentQuery)
                        }
                    }
                    SwipeToDismissBox(
                        state = dismissState,
                        backgroundContent = { Box(modifier = Modifier.fillMaxSize().background(Color.Transparent)) },
                        content = {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(32.dp))
                                    .clip(RoundedCornerShape(32.dp))
                                    .bouncyClickable { launchWebSearch(recentQuery) }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(imageVector = Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(text = recentQuery, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp, fontFamily = GoogleSansFlex)
                                Spacer(modifier = Modifier.weight(1f))
                                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                            }
                        }
                    )
                }
                item(key = "recent_divider") { HorizontalDivider(color = Color(0xFF2C2C35), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp)) }
            }

            if (settingsState.searchCalendar && uiState.calendarEvents.isNotEmpty()) {
                items(uiState.calendarEvents, key = { "event_${it.title}_${it.startTime}" }) { event ->
                    val dismissState = rememberSwipeToDismissBoxState()
                    LaunchedEffect(dismissState.currentValue) {
                        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
                            viewModel.dismissCalendarEvent(event)
                        }
                    }
                    SwipeToDismissBox(
                        state = dismissState,
                        backgroundContent = { Box(modifier = Modifier.fillMaxSize().background(Color.Transparent)) },
                        content = {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = verticalPad),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.size(24.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                                    Text(event.startTime.split(":").first(), color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(event.title, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = GoogleSansFlex)
                                    Text(event.startTime, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontFamily = GoogleSansFlex)
                                }
                            }
                        }
                    )
                }
                item(key = "calendar_divider") { HorizontalDivider(color = Color(0xFF2C2C35), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp)) }
            }

            if (settingsState.searchShortcuts && uiState.shortcuts.isNotEmpty()) {
                items(uiState.shortcuts, key = { "shortcut_${it.id}" }) { shortcut ->
                    Row(
                        modifier = Modifier.fillMaxWidth().bouncyClickable {
                            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? android.content.pm.LauncherApps
                            try {
                                launcherApps?.startShortcut(shortcut.packageName, shortcut.id, null, null, android.os.Process.myUserHandle())
                                viewModel.onQueryChanged("")
                            } catch (e: Exception) { e.printStackTrace() }
                        }.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(shortcut.shortLabel, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontFamily = GoogleSansFlex)
                    }
                }
                item(key = "shortcuts_divider") { HorizontalDivider(color = Color(0xFF2C2C35), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp)) }
            }

            if (settingsState.searchWeb) {
                if (uiState.webSuggestions.isNotEmpty()) {
                    items(uiState.webSuggestions, key = { "web_suggest_$it" }) { suggestion ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(32.dp))
                                .clip(RoundedCornerShape(32.dp))
                                .bouncyClickable {
                                    viewModel.onQueryChanged(suggestion)
                                    launchWebSearch(suggestion)
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(text = suggestion, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp, fontFamily = GoogleSansFlex)
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            if (uiState.mathResult != null) {
                item(key = "math_result") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Calculate, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(text = uiState.mathResult ?: "", color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = GoogleSansFlex)
                    }
                }
                item(key = "math_divider") { HorizontalDivider(color = Color(0xFF2C2C35), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp)) }
            }

            val instantAnswer = uiState.instantAnswer
            if (instantAnswer != null) {
                item(key = "instant_answer") {
                    val icon = when (instantAnswer.iconType) {
                        "weather" -> Icons.Default.WbSunny
                        "time" -> Icons.Default.AccessTime
                        "conversion" -> Icons.Default.SyncAlt
                        else -> Icons.Default.Info
                    }
                    val tint = when (instantAnswer.iconType) {
                        "weather" -> Color(0xFFFFD54F)
                        "time" -> Color(0xFF64B5F6)
                        "conversion" -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(32.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(20.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.width(20.dp))
                            Column {
                                Text(
                                    text = instantAnswer.title,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = GoogleSansFlex
                                )
                                Text(
                                    text = instantAnswer.subtitle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 14.sp,
                                    fontFamily = GoogleSansFlex
                                )
                            }
                        }
                    }
                }
            }
            if (settingsState.searchApps && filteredApps.isNotEmpty()) {
                item(key = "apps_divider") { HorizontalDivider(color = Color(0xFF2C2C35), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp)) }
                item(key = "apps_row") {
                    LazyRow(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        if (uiState.query.isEmpty()) {
                            item(key = "search_settings_shortcut") {
                                SearchSettingsItem {
                                    val intent = Intent(context, SettingsActivity::class.java)
                                    val options = android.app.ActivityOptions.makeCustomAnimation(
                                        context,
                                        R.anim.slide_in_right,
                                        R.anim.slide_out_left
                                    )
                                    context.startActivity(intent, options.toBundle())
                                }
                            }
                        }
                        items(filteredApps, key = { it.packageName }) { app ->
                            AppGridItem(app) { onLaunchApp(app.packageName) }
                        }
                    }
                }
            }

            if (settingsState.shortcutInline && uiState.query.isNotEmpty()) {
                item(key = "inline_shortcuts_divider") { HorizontalDivider(color = Color(0xFF2C2C35), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp)) }
                item(key = "lens_shortcut") {
                    ShortcutRow(
                        iconRes = R.drawable.ic_camera,
                        title = "Search with Google Lens",
                        onClick = {
                            val intent = SearchWidgetProvider.getLensSearchIntent(context)
                            try { context.startActivity(intent) } catch (e: Exception) {}
                            closeOverlay()
                        }
                    )
                }
                item(key = "voice_shortcut") {
                    ShortcutRow(
                        iconRes = R.drawable.ic_mic,
                        title = "Search with Voice",
                        onClick = {
                            val intent = SearchWidgetProvider.getVoiceSearchIntent(context)
                            try { context.startActivity(intent) } catch (e: Exception) {}
                            closeOverlay()
                        }
                    )
                }
                item(key = "gemini_shortcut") {
                    ShortcutRow(
                        iconRes = R.drawable.ic_gemini,
                        title = "Ask Gemini",
                        onClick = {
                            val intent = SearchWidgetProvider.getGeminiSearchIntent(context)
                            try { context.startActivity(intent) } catch (e: Exception) {}
                            closeOverlay()
                        }
                    )
                }
            }
            
            if (settingsState.searchContacts && filteredContacts.isNotEmpty()) {
                item(key = "contacts_divider") { HorizontalDivider(color = Color(0xFF2C2C35), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp)) }
                itemsIndexed(filteredContacts, key = { index, contact -> "contact_${contact.lookupUri}_${contact.phoneNumber}_$index" }) { index, contact ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .bouncyClickable {
                                val intent = if (settingsState.contactDirectCall) {
                                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.phoneNumber}"))
                                } else {
                                    Intent(Intent.ACTION_VIEW, Uri.parse(contact.lookupUri))
                                }
                                try { context.startActivity(intent) } catch(e: Exception) {}
                                closeOverlay()
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(contact.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontFamily = GoogleSansFlex)
                            Text(contact.phoneNumber, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontFamily = GoogleSansFlex)
                        }
                    }
                }
            }

            if (settingsState.searchFiles && filteredFiles.isNotEmpty()) {
                item(key = "files_divider") { HorizontalDivider(color = Color(0xFF2C2C35), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp)) }
                itemsIndexed(filteredFiles, key = { index, file -> "file_${file.uri}_$index" }) { index, file ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .bouncyClickable {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(Uri.parse(file.uri), file.mimeType)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                try { context.startActivity(intent) } catch(e: Exception) {}
                                closeOverlay()
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(file.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontFamily = GoogleSansFlex, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(file.mimeType, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontFamily = GoogleSansFlex, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }

    val imeState = WindowInsets.ime
    val density = LocalDensity.current
    val imeBottom = if (isKeyboardDisabled) 0 else imeState.getBottom(density)
    val animatedImeBottom by animateFloatAsState(
        targetValue = imeBottom.toFloat(),
        animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessLow),
        label = "imeBounce"
    )
    val maxDragDistance = with(density) { 400.dp.toPx() } // Approx swipe distance

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = (animatedImeBottom / density.density).coerceAtLeast(0f).dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        val commitThreshold = 0.75f
                        coroutineScope.launch {
                            val target = if (overlayProgressAnim.value > commitThreshold) 1f else 0f
                            val currentVel = overlayProgressAnim.velocity
                            overlayProgressAnim.animateTo(
                                targetValue = target,
                                initialVelocity = currentVel,
                                animationSpec = spring(dampingRatio = 0.92f, stiffness = 250f)
                            )
                            if (target == 0f) {
                                val act = (context as? android.app.Activity)
                                finishWithoutTransition(act)
                            }
                        }
                    },
                    onDragCancel = {
                        coroutineScope.launch {
                            val currentVel = overlayProgressAnim.velocity
                            overlayProgressAnim.animateTo(1f, initialVelocity = currentVel, animationSpec = spring(0.92f, 250f))
                        }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        if (dragAmount > 0) {
                            change.consume()
                            coroutineScope.launch {
                                val deltaProgress = dragAmount / maxDragDistance
                                val newProgress = (overlayProgressAnim.value - deltaProgress).coerceIn(0f, 1f)
                                overlayProgressAnim.snapTo(newProgress)
                                if (newProgress < 0.95f) {
                                    keyboardController?.hide()
                                }
                            }
                        }
                    }
                )
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (!showTutorial) closeOverlay()
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        if (settingsState.showWallpaper) {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = (settingsState.backgroundTransparency / 100f) * 0.7f * morphProgress)))
        } else {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            if (prefs.getBoolean("matrix_animation_enabled", false)) {
                AnimatedMatrixBackground()
            }
            
            val surfaceAlpha = if (settingsState.showWallpaper) ((100 - settingsState.backgroundTransparency) / 100f).coerceIn(0f, 1f) else 1f
            
            @android.annotation.SuppressLint("UnusedBoxWithConstraintsScope", "ObsoleteSdkInt")
            val screenHeight = LocalConfiguration.current.screenHeightDp.dp
            val screenWidth = LocalConfiguration.current.screenWidthDp.dp
            
            val targetHeight = screenHeight - 32.dp
            val initialHeight = 56.dp
            
            val targetWidth = screenWidth - 32.dp
            val initialWidth = screenWidth - 64.dp

            val searchBarAlpha = morphProgress
            val quickAppPanelAlpha = (morphProgress - 0.1f).coerceIn(0f, 0.9f) / 0.9f
            val searchResultsAlpha = (morphProgress - 0.2f).coerceIn(0f, 0.8f) / 0.8f

            val searchBarOffset = (1f - searchBarAlpha) * 40f
            val quickAppPanelOffset = (1f - quickAppPanelAlpha) * 40f
            val searchResultsOffset = (1f - searchResultsAlpha) * 40f

            Box(
                modifier = Modifier
                    .width(targetWidth)
                    .height(targetHeight)
                    .padding(bottom = 16.dp)
                    .graphicsLayer {
                        val progress = morphProgress.coerceIn(0.001f, 1f)
                        val currentW = initialWidth.toPx() + (targetWidth.toPx() - initialWidth.toPx()) * progress
                        val currentH = initialHeight.toPx() + (targetHeight.toPx() - initialHeight.toPx()) * progress
                        
                        scaleX = currentW / targetWidth.toPx()
                        scaleY = currentH / targetHeight.toPx()
                        transformOrigin = TransformOrigin(0.5f, 1.0f)
                        alpha = progress
                    }
                    .clip(RoundedCornerShape(24.dp))
                    .then(
                        if (settingsState.bottomSearch) {
                            Modifier
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = surfaceAlpha))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = morphProgress), RoundedCornerShape(24.dp))
                        } else {
                            Modifier.background(Color.Transparent)
                        }
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {}
            ) {
                
                Column(
                    modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (!settingsState.bottomSearch) {
                        Spacer(modifier = Modifier.fillMaxHeight(0.2f))
                        Box(modifier = Modifier.graphicsLayer { alpha = searchBarAlpha; translationY = searchBarOffset }) { searchBarContent() }
                        Box(modifier = Modifier.graphicsLayer { alpha = quickAppPanelAlpha; translationY = quickAppPanelOffset }) { quickAppPanelContent() }
                        Box(modifier = Modifier.weight(1f).graphicsLayer { alpha = searchResultsAlpha; translationY = searchResultsOffset }) { searchResultsContent() }
                    } else {
                        Box(modifier = Modifier.weight(1f).graphicsLayer { alpha = searchResultsAlpha; translationY = -searchResultsOffset }) { searchResultsContent() }
                        Box(modifier = Modifier.graphicsLayer { alpha = quickAppPanelAlpha; translationY = -quickAppPanelOffset }) { quickAppPanelContent() }
                        Box(modifier = Modifier.graphicsLayer { alpha = searchBarAlpha; translationY = -searchBarOffset }) { searchBarContent() }
                    }
                }
            }
            TutorialSpotlightOverlay(
                prefs = prefs,
                stepsInfo = mapOf(
                    0 to TutorialStepInfo("Welcome!", "Hello! :) Thank you for installing Intelligent Search. Please follow the tutorial to show you around.", Alignment.Center, showArrow = false, requireButtonPress = true),
                    1 to TutorialStepInfo("Search Bar", "This is your search bar. Start typing to find apps, contacts, and files instantly. You can also swipe away recent search cards to remove them.", Alignment.Center, showArrow = true, requireButtonPress = true),
                    2 to TutorialStepInfo("Settings", "Tap the settings icon (the ⋮ button) to customize your search experience. Press OK below to open Settings now.", Alignment.Center, showArrow = true, requireButtonPress = true, showCircle = true)
                ),
                onComplete = { showTutorial = false },
                onStepAdvance = { step ->
                    if (step == 3) onOpenSettings("main")
                }
            )
            } // Close the Box
        }
    }

@Composable
fun FileIconThumbnail(uri: String, mimeType: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var bitmap by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    androidx.compose.runtime.LaunchedEffect(uri) {
        if (mimeType.startsWith("image/") || mimeType.startsWith("video/")) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val bmp = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        val parsedUri = android.net.Uri.parse(uri)
                        val id = android.content.ContentUris.parseId(parsedUri)
                        val specificUri = if (mimeType.startsWith("image/")) {
                            android.content.ContentUris.withAppendedId(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                        } else {
                            android.content.ContentUris.withAppendedId(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                        }
                        context.contentResolver.loadThumbnail(specificUri, android.util.Size(96, 96), null)
                    } else null
                    
                    if (bmp != null) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            bitmap = bmp.asImageBitmap()
                        }
                    }
                } catch (e: Exception) {
                    // Ignore, fallback to standard icon
                }
            }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = null,
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )
    } else {
        Box(modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
            Icon(imageVector = Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}





