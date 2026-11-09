package com.pixel.intelligentsearch.feature.widget

import android.Manifest
import android.app.SearchManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.pixel.intelligentsearch.core.system.GoogleApps
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.core.ui.rememberAnimationsEnabled
import com.pixel.intelligentsearch.core.util.resolveActivityCompat
import com.pixel.intelligentsearch.feature.search.today.NebulaPainter
import com.pixel.intelligentsearch.feature.search.today.hueOf
import com.pixel.intelligentsearch.feature.search.today.rememberLoop
import kotlin.math.max
import kotlin.math.min

/**
 * The search bar's Voice Nebula shortcut. Google's own speech recognizer listens while the voice nebula floats, small,
 * centered right above the bar (placed from where the tap came from), with no words shown. The moment it has the words,
 * the nebula settles into the bar and Google's search starts with them. Google's voice page is never shown, unless the
 * phone has no recognizer or the microphone isn't allowed: then it opens instead, as the mic does.
 */
class VoiceNebulaActivity : ComponentActivity() {
    private enum class Phase { Listening, Searching, Closing }

    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private val level = mutableFloatStateOf(0f)
    private var heard by mutableStateOf("")
    private var phase by mutableStateOf(Phase.Listening)
    private var barTop = -1
    private val askMic = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) listen() else openGoogleVoice()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        barTop = intent.sourceBounds?.top ?: -1
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            openGoogleVoice()
            return
        }
        setContent { IntelligentSearchTheme(darkTheme = true) { Listening() } }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) listen()
        else askMic.launch(Manifest.permission.RECORD_AUDIO)
    }

    override fun onStop() {
        super.onStop()
        // Leaving (Home, a call) ends it: nothing keeps listening out of sight.
        if (!isChangingConfigurations && phase == Phase.Listening) {
            stopListening()
            finish()
        }
    }

    override fun onDestroy() {
        stopListening()
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun listen() {
        val r = runCatching { SpeechRecognizer.createSpeechRecognizer(this) }.getOrNull() ?: run {
            openGoogleVoice()
            return
        }
        recognizer = r
        r.setRecognitionListener(listener)
        r.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        )
    }

    private fun stopListening() {
        recognizer?.let {
            runCatching { it.cancel() }
            runCatching { it.destroy() }
        }
        recognizer = null
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        // About -2 dB in silence to about 10 dB speaking up, as Google's recognizer reports it.
        override fun onRmsChanged(rmsdB: Float) {
            level.floatValue = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            first(partialResults)?.let { heard = it }
        }

        override fun onResults(results: Bundle?) {
            val words = first(results) ?: heard
            if (words.isBlank()) close() else search(words)
        }

        override fun onError(error: Int) {
            if (phase != Phase.Listening) return
            when (error) {
                // Nothing said, or nothing it could make out: just go, as Google's page does.
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> close()
                else -> {
                    stopListening()
                    openGoogleVoice()
                }
            }
        }
    }

    private fun first(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }

    /** The words are in: the nebula settles into the bar, then Google's search starts with them. */
    private fun search(words: String) {
        heard = words
        phase = Phase.Searching
        stopListening()
        main.postDelayed({
            val google = Intent(Intent.ACTION_WEB_SEARCH)
                .putExtra(SearchManager.QUERY, words)
                .setPackage(GoogleApps.GOOGLE_APP)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (packageManager.resolveActivityCompat(google) == null) google.setPackage(null)
            runCatching { startActivity(google) }
            finishQuietly()
        }, SETTLE_MS)
    }

    private fun close() {
        phase = Phase.Closing
        stopListening()
        main.postDelayed({ finishQuietly() }, SETTLE_MS)
    }

    /** Google's own voice page, as the mic opened before: for phones without a recognizer, or without the microphone. */
    private fun openGoogleVoice() {
        phase = Phase.Closing
        val voice = Intent(Intent.ACTION_MAIN)
            .setClassName(GoogleApps.GOOGLE_APP, GoogleApps.VOICE_SEARCH_ACTIVITY)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val target = if (packageManager.resolveActivityCompat(voice) != null) voice
        else Intent(RecognizerIntent.ACTION_WEB_SEARCH).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(target) }
        finishQuietly()
    }

    private fun finishQuietly() {
        if (isFinishing) return
        finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    @Composable
    private fun Listening() {
        val scheme = MaterialTheme.colorScheme
        val animations by rememberAnimationsEnabled()
        val clock = rememberLoop(LOOP_MS, LinearEasing)
        val painter = remember { NebulaPainter() }
        val sound = remember { FloatArray(SOUND_VALUES) }
        val last = remember { FloatArray(1) }
        val hue = remember(scheme.primary) { hueOf(scheme.primary) }
        val shown = remember { Animatable(0f) }
        LaunchedEffect(phase, animations) {
            if (phase == Phase.Listening) shown.animateTo(1f, if (animations) spring(dampingRatio = 0.7f, stiffness = 220f) else tween(0))
            else shown.animateTo(0f, tween(if (animations) SETTLE_MS.toInt() - 40 else 0))
        }
        val density = LocalDensity.current
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { if (phase == Phase.Listening) close() } }
                .semantics {
                    contentDescription = if (heard.isEmpty()) "Voice search, listening. Double-tap to cancel." else heard
                    liveRegion = LiveRegionMode.Polite
                }
        ) {
            val heightPx = with(density) { maxHeight.toPx() }
            // The tap's top edge is the bar's; without it, where a home screen's bottom search bar sits.
            val top = if (barTop > 0) barTop.toFloat() else heightPx - with(density) { DEFAULT_BAR_FROM_BOTTOM.toPx() }
            val centerY = top - with(density) { ABOVE_BAR.toPx() }
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val a = shown.value
                        // Shade gathered around the nebula, wherever the bar is, so it stands out on any wallpaper.
                        drawRect(
                            Brush.radialGradient(
                                0f to Color.Black.copy(alpha = 0.55f), 0.6f to Color.Black.copy(alpha = 0.25f), 1f to Color.Transparent,
                                center = androidx.compose.ui.geometry.Offset(size.width / 2f, centerY),
                                radius = size.width * 0.95f
                            ),
                            alpha = a
                        )
                        if (a <= 0.01f) return@drawBehind
                        val seconds = clock() * LOOP_MS / 1000f
                        val l = level.floatValue
                        sound[0] = l; sound[1] = l; sound[2] = 0.7f * l; sound[3] = 0.5f * l
                        sound[4] = min(1f, max(0f, l - last[0]) * 4f)
                        last[0] = l
                        val drop = if (phase == Phase.Listening) 0f else (1f - a) * SETTLE_DROP.toPx()
                        drawIntoCanvas {
                            painter.draw(it.nativeCanvas, size.width / 2f, centerY + drop, NEBULA.toPx(), seconds, sound, phase == Phase.Listening, true, hue, animations, a)
                        }
                    }
            )
        }
    }

    private companion object {
        const val SETTLE_MS = 380L
        const val LOOP_MS = 600_000
        /** What [NebulaPainter] reads from the voice: loudness, low, middle and high sound, and a syllable's start. */
        const val SOUND_VALUES = 5
        val NEBULA = 150.dp
        /** The nebula's center above the bar's top edge: its glow rests on the bar. */
        val ABOVE_BAR = 58.dp
        val DEFAULT_BAR_FROM_BOTTOM = 96.dp
        val SETTLE_DROP = 24.dp
    }
}
