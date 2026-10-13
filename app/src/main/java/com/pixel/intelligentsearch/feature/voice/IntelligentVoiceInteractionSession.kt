package com.pixel.intelligentsearch.feature.voice

import android.Manifest
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import android.service.voice.VoiceInteractionSession
import com.pixel.intelligentsearch.core.haptics.PixelHapticEngine
import com.pixel.intelligentsearch.core.haptics.PixelHapticType
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.feature.widget.WidgetActivity

/**
 * IntelligentVoiceInteractionSession
 *
 * Modern Android 17 (Canary ZP11) Voice & Assistant Search Session Overlay.
 * Renders edge-to-edge Material 3 Expressive UI in a floating assistant window,
 * featuring a 120 FPS multi-harmonic ripple waveform canvas, live speech-to-text
 * transcription, quick action chips, and seamless handoff to Intelligent Search.
 */
class IntelligentVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    private val sessionLifecycleOwner = SessionLifecycleOwner(
        fallbackOnBackPressed = {
            PixelHapticEngine.get(context).performHaptic(PixelHapticType.OVERLAY_DISMISS)
            hide()
        }
    )
    private var recognitionManager: VoiceRecognitionManager? = null

    private fun bindViewTreeOwners(targetView: View) {
        targetView.setViewTreeLifecycleOwner(sessionLifecycleOwner)
        targetView.setViewTreeViewModelStoreOwner(sessionLifecycleOwner)
        targetView.setViewTreeSavedStateRegistryOwner(sessionLifecycleOwner)
        targetView.setViewTreeOnBackPressedDispatcherOwner(sessionLifecycleOwner)
    }

    override fun onCreate() {
        super.onCreate()
        sessionLifecycleOwner.onCreate()

        // Configure floating assistant overlay window
        val dialog = window
        val win = dialog?.window
        win?.let { w ->
            w.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
            w.setBackgroundDrawableResource(android.R.color.transparent)
            w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                w.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
                w.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else {
                w.addFlags(
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
                )
                @Suppress("DEPRECATION")
                WindowCompat.setDecorFitsSystemWindows(w, false)
            }

            bindViewTreeOwners(w.decorView)
        }

        recognitionManager = VoiceRecognitionManager(context)
        Log.d(TAG, "IntelligentVoiceInteractionSession initialized.")
    }

    override fun onCreateContentView(): View {
        return ComposeView(context).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool
            )
            bindViewTreeOwners(this)

            setContent {
                IntelligentSearchTheme {
                    val rm = recognitionManager
                    if (rm != null) {
                        VoiceAssistantBottomSheet(
                            state = rm.state.value,
                            audioAmplitude = rm.amplitude.floatValue,
                            spokenText = rm.recognizedText.value,
                            errorMessage = rm.errorMessage.value,
                            onMicClick = { rm.toggleListening() },
                            onDismiss = {
                                PixelHapticEngine.get(context).performHaptic(PixelHapticType.OVERLAY_DISMISS)
                                hide()
                            },
                            onOpenFullSearch = { query ->
                                launchFullSearch(query)
                            },
                            onQuickPromptClick = { prompt ->
                                launchFullSearch(prompt)
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onComputeInsets(insets: Insets) {
        super.onComputeInsets(insets)
        insets.contentInsets.top = 0
        insets.contentInsets.bottom = 0
        // Entire frame intercepts touches so backdrop scrim dismisses cleanly
        insets.touchableInsets = Insets.TOUCHABLE_INSETS_FRAME
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        window?.window?.let { win -> bindViewTreeOwners(win.decorView) }
        sessionLifecycleOwner.onStart()
        sessionLifecycleOwner.onResume()

        // Haptic feedback on assistant invocation
        PixelHapticEngine.get(context).performHaptic(PixelHapticType.OVERLAY_OPEN)

        // Start active voice recognition
        recognitionManager?.startListening()
    }

    override fun onHide() {
        recognitionManager?.stopListening()
        sessionLifecycleOwner.onPause()
        sessionLifecycleOwner.onStop()
        PixelHapticEngine.get(context).performHaptic(PixelHapticType.OVERLAY_DISMISS)
        super.onHide()
    }

    override fun onDestroy() {
        recognitionManager?.destroy()
        recognitionManager = null
        sessionLifecycleOwner.onDestroy()
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (sessionLifecycleOwner.onBackPressedDispatcher.hasEnabledCallbacks()) {
            sessionLifecycleOwner.onBackPressedDispatcher.onBackPressed()
        } else {
            PixelHapticEngine.get(context).performHaptic(PixelHapticType.OVERLAY_DISMISS)
            hide()
        }
    }

    override fun onHandleAssist(state: AssistState) {
        super.onHandleAssist(state)
        val assistData = state.assistData
        if (assistData != null) {
            Log.d(TAG, "Contextual assist structure captured for foreground task.")
        }
    }

    override fun onHandleScreenshot(screenshot: Bitmap?) {
        super.onHandleScreenshot(screenshot)
        if (screenshot != null) {
            Log.d(TAG, "Contextual assistant screenshot captured: ${screenshot.width}x${screenshot.height}")
        }
    }

    private fun launchFullSearch(query: String) {
        val keyguardManager = context.getSystemService(android.app.KeyguardManager::class.java)
        if (keyguardManager?.isKeyguardLocked == true) {
            Log.w(TAG, "Blocked launching full search over locked keyguard without authentication")
            hide()
            return
        }
        val intent = Intent(context, WidgetActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("query", query)
            putExtra(SearchManager.QUERY, query)
            putExtra(Intent.EXTRA_TEXT, query)
            putExtra("voice_source", "assistant_overlay")
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // A blocked or failed launch must not crash the assistant process.
            Log.w(TAG, "Could not open full search from the assistant session", e)
        }
        hide()
    }

    companion object {
        private const val TAG = "VoiceInteractionSession"
    }
}

/**
 * Dedicated Lifecycle, ViewModelStore, SavedState & OnBackPressed host for hosting Compose
 * inside VoiceInteractionSession floating assistant window.
 */
private class SessionLifecycleOwner(
    private val fallbackOnBackPressed: () -> Unit
) : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner, OnBackPressedDispatcherOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val appViewModelStore = ViewModelStore()

    override val onBackPressedDispatcher = OnBackPressedDispatcher(
        Runnable { fallbackOnBackPressed() }
    )

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = appViewModelStore

    fun onCreate() {
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun onStart() {
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.CREATED) &&
            !lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)
        ) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        }
    }

    fun onResume() {
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED) &&
            !lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.RESUMED)
        ) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
    }

    fun onPause() {
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        }
    }

    fun onStop() {
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }
    }

    fun onDestroy() {
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            onPause()
        }
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            onStop()
        }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        appViewModelStore.clear()
    }
}

/**
 * Voice Recognition state engine.
 */
enum class VoiceRecognitionState {
    INITIALIZING,
    LISTENING,
    PROCESSING,
    RESULT,
    ERROR
}

/**
 * Encapsulates Android SpeechRecognizer with reactive state and audio amplitude normalization.
 * Supports Android 14+ / 17 Canary on-device speech recognition model when available.
 */
class VoiceRecognitionManager(private val context: Context) {
    val state = mutableStateOf(VoiceRecognitionState.INITIALIZING)
    val amplitude = mutableFloatStateOf(0f)
    val recognizedText = mutableStateOf("")
    val errorMessage = mutableStateOf<String?>(null)

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun checkAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun startListening() {
        mainHandler.post {
            executeStartListening()
        }
    }

    private fun executeStartListening() {
        if (!checkAudioPermission()) {
            state.value = VoiceRecognitionState.ERROR
            errorMessage.value = "Microphone permission required for voice search."
            PixelHapticEngine.get(context).performHaptic(PixelHapticType.REJECT)
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            state.value = VoiceRecognitionState.ERROR
            errorMessage.value = "Speech recognition service unavailable on device."
            PixelHapticEngine.get(context).performHaptic(PixelHapticType.REJECT)
            return
        }

        cleanupRecognizer()

        try {
            state.value = VoiceRecognitionState.INITIALIZING
            errorMessage.value = null
            recognizedText.value = ""
            amplitude.floatValue = 0f

            // Leverage on-device recognition if supported (API 33+, e.g. Pixel Tensor) with resilient fallback
            val recognizer = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
                ) {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                } else {
                    SpeechRecognizer.createSpeechRecognizer(context)
                }
            } catch (e: Throwable) {
                Log.w("VoiceRecManager", "Failed to initialize on-device recognizer, falling back", e)
                SpeechRecognizer.createSpeechRecognizer(context)
            }
            speechRecognizer = recognizer

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    state.value = VoiceRecognitionState.LISTENING
                }

                override fun onBeginningOfSpeech() {
                    state.value = VoiceRecognitionState.LISTENING
                }

                override fun onRmsChanged(rmsdB: Float) {
                    val normalized = normalizeRmsDb(rmsdB)
                    val current = amplitude.floatValue
                    amplitude.floatValue = computeAmplitudeStep(current, normalized)
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    state.value = VoiceRecognitionState.PROCESSING
                    amplitude.floatValue = 0f
                }

                override fun onError(error: Int) {
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected. Tap mic to retry."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Listening timed out. Tap mic to speak."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network connection issue. Tap mic to retry."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error. Check microphone."
                        SpeechRecognizer.ERROR_CLIENT -> "Speech engine busy. Tap mic to retry."
                        SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "Voice service disconnected. Tap mic to retry."
                        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "Voice service busy. Please wait a moment."
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "Language not supported by voice recognizer."
                        else -> "Unable to recognize voice. Tap mic to retry."
                    }
                    errorMessage.value = msg
                    state.value = VoiceRecognitionState.ERROR
                    amplitude.floatValue = 0f
                    PixelHapticEngine.get(context).performHaptic(PixelHapticType.REJECT)
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val best = matches?.firstOrNull().orEmpty()
                    if (best.isNotBlank()) {
                        recognizedText.value = best
                        state.value = VoiceRecognitionState.RESULT
                        PixelHapticEngine.get(context).performHaptic(PixelHapticType.CONFIRM)
                    } else {
                        state.value = VoiceRecognitionState.ERROR
                        errorMessage.value = "No words recognized. Tap mic to speak."
                    }
                    amplitude.floatValue = 0f
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()
                    if (!text.isNullOrBlank()) {
                        recognizedText.value = text
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            recognizer.startListening(intent)
        } catch (e: Exception) {
            Log.e("VoiceRecManager", "Error initializing speech recognizer", e)
            state.value = VoiceRecognitionState.ERROR
            errorMessage.value = "Voice recognizer error. Tap mic to retry."
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.w("VoiceRecManager", "Error stopping speech recognizer", e)
            }
        }
    }

    fun toggleListening() {
        if (state.value == VoiceRecognitionState.LISTENING) {
            stopListening()
            state.value = VoiceRecognitionState.INITIALIZING
        } else {
            startListening()
        }
    }

    private fun cleanupRecognizer() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w("VoiceRecManager", "Error cleaning up speech recognizer", e)
        } finally {
            speechRecognizer = null
        }
    }

    fun destroy() {
        mainHandler.post {
            cleanupRecognizer()
            amplitude.floatValue = 0f
        }
    }

    companion object {
        fun normalizeRmsDb(rmsdB: Float): Float {
            val clamped = rmsdB.coerceIn(-2f, 10f)
            return ((clamped + 2f) / 12f).coerceIn(0f, 1f)
        }

        fun computeAmplitudeStep(
            current: Float,
            target: Float,
            attack: Float = 0.40f,
            decay: Float = 0.14f
        ): Float {
            val speed = if (target > current) attack else decay
            return current + (target - current) * speed
        }
    }
}

// Google / Pixel brand expressive color palette
private val GoogleBlue = Color(0xFF4285F4)
private val GoogleRed = Color(0xFFEA4335)
private val GoogleYellow = Color(0xFFFBBC04)
private val GoogleGreen = Color(0xFF34A853)

private class HarmonicWave(
    val freq: Float,
    val speed: Float,
    val phaseOffset: Float,
    val color: Color,
    val glowColor: Color,
    val path: Path = Path()
)

/**
 * 120 FPS high-refresh rate Waveform Canvas.
 * Zero-heap allocation inside DrawScope loop:
 * - Pre-allocated HarmonicWave descriptors and cached Paths.
 * - State reads isolated inside Canvas DrawScope to avoid 120 FPS composable recomposition storms.
 * - Mathematically exact Hann-window envelope sin^2(pi * x), avoiding expensive Math.pow calculations.
 * - Smooth crossfaded orbital morph transitions between LISTENING, PROCESSING, and RESULT states.
 * - Direct GPU hardware acceleration without offscreen buffer stalls.
 */
@Composable
fun VoiceWaveformCanvas(
    amplitude: Float,
    state: VoiceRecognitionState,
    modifier: Modifier = Modifier
) {
    val isListening = state == VoiceRecognitionState.LISTENING
    val isProcessing = state == VoiceRecognitionState.PROCESSING

    val animatedAmpState = animateFloatAsState(
        targetValue = if (isListening) amplitude.coerceIn(0f, 1f) else if (state == VoiceRecognitionState.INITIALIZING) 0.10f else 0f,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = 650f
        ),
        label = "WaveformAmplitude"
    )

    val processingProgressState = animateFloatAsState(
        targetValue = if (isProcessing) 1f else 0f,
        animationSpec = tween(durationMillis = 280),
        label = "ProcessingProgress"
    )

    val isWaveActive = isListening ||
        isProcessing ||
        state == VoiceRecognitionState.INITIALIZING ||
        animatedAmpState.value > 0.001f ||
        processingProgressState.value > 0.001f

    val idleZeroState = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val phaseState: androidx.compose.runtime.State<Float>
    val processingAngleState: androidx.compose.runtime.State<Float>
    val rippleProgressState: androidx.compose.runtime.State<Float>

    if (isWaveActive) {
        val infiniteTransition = rememberInfiniteTransition(label = "WaveMotion")
        phaseState = infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "WavePhase"
        )
        processingAngleState = if (isProcessing || processingProgressState.value > 0.001f) {
            infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1400, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "ProcessingAngle"
            )
        } else {
            idleZeroState
        }
        rippleProgressState = if (isListening) {
            infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1800, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "RippleProgress"
            )
        } else {
            idleZeroState
        }
    } else {
        phaseState = idleZeroState
        processingAngleState = idleZeroState
        rippleProgressState = idleZeroState
    }

    val density = androidx.compose.ui.platform.LocalDensity.current
    val glowStroke = remember(density) {
        Stroke(
            width = with(density) { 8.dp.toPx() },
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    }
    val primaryStroke = remember(density) {
        Stroke(
            width = with(density) { 3.dp.toPx() },
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    }

    val baseRadiusPx = remember(density) { with(density) { 36.dp.toPx() } }
    val maxExpandPx = remember(density) { with(density) { 64.dp.toPx() } }
    val ampMultiplierPx = remember(density) { with(density) { 24.dp.toPx() } }
    val orbitRadiusMaxPx = remember(density) { with(density) { 26.dp.toPx() } }
    val dotRadiusMaxPx = remember(density) { with(density) { 5.dp.toPx() } }
    val baseAmpPx = remember(density) { with(density) { 5.dp.toPx() } }
    val maxDynamicAmpPx = remember(density) { with(density) { 36.dp.toPx() } }

    // Pre-allocated harmonic wave descriptors and cached paths (zero heap allocations inside 120 FPS draw loop)
    val waves = remember {
        arrayOf(
            HarmonicWave(1.0f, 1.0f, 0.0f, GoogleBlue, GoogleBlue.copy(alpha = 0.28f)),
            HarmonicWave(1.4f, -1.1f, 1.3f, GoogleRed, GoogleRed.copy(alpha = 0.28f)),
            HarmonicWave(0.85f, 1.3f, 2.6f, GoogleYellow, GoogleYellow.copy(alpha = 0.28f)),
            HarmonicWave(1.6f, -0.9f, 3.8f, GoogleGreen, GoogleGreen.copy(alpha = 0.28f))
        )
    }

    val orbitalColors = remember {
        arrayOf(GoogleBlue, GoogleRed, GoogleYellow, GoogleGreen)
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(110.dp)
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val centerX = width / 2f

        if (width <= 0f || height <= 0f || !isWaveActive) return@Canvas

        val phase = phaseState.value
        val processingAngle = processingAngleState.value
        val rippleProgress = rippleProgressState.value
        val animatedAmp = animatedAmpState.value
        val processingProgress = processingProgressState.value
        val waveAlpha = (1f - processingProgress).coerceIn(0f, 1f)
        val centerOffset = Offset(centerX, centerY)

        // Radial glowing aura rings when actively listening (zero heap allocations)
        if (isListening && waveAlpha > 0.01f) {
            val rippleRadius = baseRadiusPx + (rippleProgress * maxExpandPx) + (animatedAmp * ampMultiplierPx)
            val rippleAlpha = ((1f - rippleProgress) * (0.35f + animatedAmp * 0.45f) * waveAlpha).coerceIn(0f, 1f)

            if (rippleAlpha > 0.001f) {
                drawCircle(
                    color = GoogleBlue.copy(alpha = rippleAlpha * 0.20f),
                    radius = rippleRadius,
                    center = centerOffset
                )
                drawCircle(
                    color = GoogleGreen.copy(alpha = rippleAlpha * 0.14f),
                    radius = rippleRadius * 0.65f,
                    center = centerOffset
                )
                drawCircle(
                    color = GoogleBlue.copy(alpha = rippleAlpha * 0.10f),
                    radius = rippleRadius * 0.35f,
                    center = centerOffset
                )
            }
        }

        // Orbital morphing animation during PROCESSING state (crossfaded smoothly)
        if (processingProgress > 0.01f) {
            val orbitRadius = orbitRadiusMaxPx * processingProgress
            val dotRadius = (dotRadiusMaxPx * processingProgress).coerceAtLeast(1f)
            val dotCount = orbitalColors.size
            for (i in 0 until dotCount) {
                val angleRad = Math.toRadians(processingAngle + (i * 90.0))
                val dotX = centerX + (orbitRadius * Math.cos(angleRad)).toFloat()
                val dotY = centerY + (orbitRadius * Math.sin(angleRad)).toFloat()
                drawCircle(
                    color = orbitalColors[i].copy(alpha = orbitalColors[i].alpha * processingProgress),
                    radius = dotRadius,
                    center = Offset(dotX, dotY)
                )
            }
        }

        // Multi-harmonic waveforms (rendered when waveAlpha > 0)
        if (waveAlpha > 0.01f && isWaveActive) {
            val steps = 60
            val stepX = width / steps
            val totalAmp = (baseAmpPx + (animatedAmp * maxDynamicAmpPx)) * waveAlpha
            val waveCount = waves.size

            for (wIndex in 0 until waveCount) {
                val w = waves[wIndex]
                w.path.reset()
                w.path.moveTo(0f, centerY)

                for (step in 0..steps) {
                    val x = step * stepX
                    val normalizedX = (step.toFloat() / steps).coerceIn(0f, 1f)
                    // Hann-window envelope: w(x) = sin^2(pi * x), zero at boundaries, maximum at center
                    val sinVal = Math.sin(normalizedX * Math.PI).toFloat()
                    val envelope = sinVal * sinVal

                    val waveAngle = (2 * Math.PI * w.freq * normalizedX) + (phase * w.speed) + w.phaseOffset
                    val y = centerY + (Math.sin(waveAngle).toFloat() * totalAmp * envelope)

                    if (step == 0) {
                        w.path.moveTo(x, y)
                    } else {
                        w.path.lineTo(x, y)
                    }
                }

                // Glow pass (scaled by waveAlpha, reusing preallocated Stroke)
                drawPath(
                    path = w.path,
                    color = w.glowColor.copy(alpha = w.glowColor.alpha * waveAlpha),
                    style = glowStroke
                )

                // Primary stroke pass (scaled by waveAlpha, reusing preallocated Stroke)
                drawPath(
                    path = w.path,
                    color = w.color.copy(alpha = w.color.alpha * waveAlpha),
                    style = primaryStroke
                )
            }
        }
    }
}

/**
 * Material 3 Expressive Voice Assistant Bottom Sheet.
 */
@Composable
fun VoiceAssistantBottomSheet(
    state: VoiceRecognitionState,
    audioAmplitude: Float,
    spokenText: String,
    errorMessage: String?,
    onMicClick: () -> Unit,
    onDismiss: () -> Unit,
    onOpenFullSearch: (String) -> Unit,
    onQuickPromptClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* Consume clicks to prevent dismissing */ }
                ),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 12.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Drag handle pill
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Assistant",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Intelligent Assistant",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    RecognitionStateBadge(state = state)

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Live transcript or status prompt
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        !errorMessage.isNullOrBlank() -> {
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                        }
                        spokenText.isNotBlank() -> {
                            Text(
                                text = spokenText,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        onOpenFullSearch(spokenText)
                                    }
                            )
                        }
                        state == VoiceRecognitionState.LISTENING -> {
                            Text(
                                text = "Listening... Speak now",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center
                            )
                        }
                        state == VoiceRecognitionState.PROCESSING -> {
                            Text(
                                text = "Transcribing voice...",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                textAlign = TextAlign.Center
                            )
                        }
                        else -> {
                            Text(
                                text = "Tap the mic to search apps, contacts, files, or web",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Waveform canvas
                VoiceWaveformCanvas(
                    amplitude = audioAmplitude,
                    state = state,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Mic and controls row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    FilledTonalIconButton(
                        onClick = { onOpenFullSearch(spokenText) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Type Search",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    val micColor = when (state) {
                        VoiceRecognitionState.LISTENING -> MaterialTheme.colorScheme.primary
                        VoiceRecognitionState.ERROR -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.primaryContainer
                    }
                    val micIconTint = when (state) {
                        VoiceRecognitionState.LISTENING -> MaterialTheme.colorScheme.onPrimary
                        VoiceRecognitionState.ERROR -> MaterialTheme.colorScheme.onErrorContainer
                        else -> MaterialTheme.colorScheme.onPrimaryContainer
                    }

                    FilledIconButton(
                        onClick = onMicClick,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = micColor,
                            contentColor = micIconTint
                        ),
                        modifier = Modifier.size(64.dp)
                    ) {
                        val icon = if (state == VoiceRecognitionState.ERROR) {
                            Icons.Default.Refresh
                        } else {
                            Icons.Default.Mic
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = "Voice Search Mic",
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    FilledTonalIconButton(
                        // Nothing heard yet opens an empty search box rather than a placeholder query.
                        onClick = { onOpenFullSearch(spokenText) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Confirm Search",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Quick Suggestion Chips
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val quickPrompts = listOf(
                        "Search Apps" to "apps",
                        "Find Contacts" to "contacts",
                        "Search Files" to "files",
                        "Device Settings" to "settings",
                        "Search Web" to "web"
                    )

                    items(quickPrompts) { (label, prompt) ->
                        SuggestionChip(
                            onClick = { onQuickPromptClick(prompt) },
                            label = {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            shape = CircleShape,
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars))
            }
        }
    }
}

@Composable
private fun RecognitionStateBadge(state: VoiceRecognitionState) {
    val (label, bg, fg) = when (state) {
        VoiceRecognitionState.INITIALIZING -> Triple(
            "Connecting",
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        VoiceRecognitionState.LISTENING -> Triple(
            "Listening",
            GoogleBlue.copy(alpha = 0.15f),
            GoogleBlue
        )
        VoiceRecognitionState.PROCESSING -> Triple(
            "Thinking",
            GoogleYellow.copy(alpha = 0.15f),
            GoogleYellow
        )
        VoiceRecognitionState.RESULT -> Triple(
            "Ready",
            GoogleGreen.copy(alpha = 0.15f),
            GoogleGreen
        )
        VoiceRecognitionState.ERROR -> Triple(
            "Tap to Retry",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
    }

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = fg
        )
    }
}
