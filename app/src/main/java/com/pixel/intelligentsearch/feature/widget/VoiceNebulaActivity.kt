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
import com.pixel.intelligentsearch.core.haptics.PixelHapticEngine
import com.pixel.intelligentsearch.core.system.GoogleApps
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.core.ui.rememberAnimationsEnabled
import com.pixel.intelligentsearch.core.util.resolveActivityCompat
import com.pixel.intelligentsearch.feature.search.today.NebulaPainter
import com.pixel.intelligentsearch.feature.search.today.hueOf
import com.pixel.intelligentsearch.feature.search.today.rememberLoop
import com.pixel.intelligentsearch.feature.voice.MicFeed
import com.pixel.intelligentsearch.feature.voice.QuietEarcons
import kotlin.math.max
import kotlin.math.min

/**
 * The search bar's Voice Nebula shortcut. Google's own speech recognizer listens while the voice nebula floats, small,
 * centered right above the bar (placed from where the tap came from), with no words shown. The moment it has the words,
 * the nebula settles into the bar and Google's search starts with them. Google's voice page is never shown, unless the
 * phone has no recognizer or the microphone isn't allowed: then it opens instead, as the mic does.
 *
 * Its haptics are the app's one haptic: the tap as it starts listening and as the words settle into the bar, and the
 * cancel haptic when it closes having heard nothing, as a gesture let go before it committed.
 */
class VoiceNebulaActivity : ComponentActivity() {
    private enum class Phase { Listening, Searching, Closing }

    private val main = Handler(Looper.getMainLooper())
    private val haptics by lazy { PixelHapticEngine.get(this) }
    private var recognizer: SpeechRecognizer? = null
    /** The app's own recording, handed to the engine so it plays no tones; null while the engine records itself. */
    private var feed: MicFeed? = null
    /**
     * Only where the engine can't be handed audio (Android 12): its start and stop tones, kept quiet. Its own handler,
     * so the channel is still given back after [onDestroy] clears [main].
     */
    private val tones by lazy { QuietEarcons(this, Handler(Looper.getMainLooper())) }
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

    @android.annotation.SuppressLint("MissingPermission") // Only called with the microphone allowed.
    private fun listen(feedAudio: Boolean = true) {
        val r = runCatching { SpeechRecognizer.createSpeechRecognizer(this) }.getOrNull() ?: run {
            openGoogleVoice()
            return
        }
        recognizer = r
        r.setRecognitionListener(listener)
        if (feedAudio) haptics.performPredictiveBackHaptic(window.decorView)
        // The app records and hands the engine the audio, so the engine never opens the microphone and never plays
        // its start and stop tones. Android 12 can't hand it audio: there the tones are only muted.
        feed = if (feedAudio) MicFeed.start(main) { setLevel(it) } else null
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        feed?.let {
            // One session for as long as the audio runs; the first phrase heard is the search (see [listener]).
            it.into(intent, wholeSession = true)
            main.postDelayed(noWords, NO_WORDS_MS)
            main.postDelayed(enough, LONGEST_MS)
        } ?: tones.hold()
        try {
            r.startListening(intent)
        } catch (e: Exception) {
            stopListening()
            openGoogleVoice()
        }
    }

    private fun setLevel(rmsdB: Float) {
        level.floatValue = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
    }

    private fun stopListening() {
        main.removeCallbacks(noWords)
        main.removeCallbacks(enough)
        main.removeCallbacks(settled)
        recognizer?.let {
            if (feed == null) tones.hold()
            runCatching { it.cancel() }
            runCatching { it.destroy() }
        }
        recognizer = null
        feed?.stop()
        feed = null
    }

    /** Fed audio, the engine never stops by itself: nothing said in time closes, as Google's page does. */
    private val noWords = Runnable { if (phase == Phase.Listening && heard.isBlank()) close() }
    /** The words stopped changing a while ago and the engine hasn't called them a phrase yet: search with them. */
    private val settled = Runnable { done(heard) }
    /** A safety stop for a microphone left open. */
    private val enough = Runnable { done(heard) }

    private fun done(words: String) {
        if (phase != Phase.Listening) return
        if (words.isBlank()) close() else search(words)
    }

    private val listener = object : RecognitionListener {
        // Without fed audio (Android 12), the start tone plays as the engine becomes ready, the stop tone as it stops
        // hearing.
        override fun onReadyForSpeech(params: Bundle?) { if (feed == null) tones.hold() }
        override fun onBeginningOfSpeech() {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() { if (feed == null) tones.hold() }
        override fun onEvent(eventType: Int, params: Bundle?) {}

        // About -2 dB in silence to about 10 dB speaking up, as Google's recognizer reports it. Fed audio is measured
        // by the app itself.
        override fun onRmsChanged(rmsdB: Float) {
            if (feed == null) setLevel(rmsdB)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val words = first(partialResults) ?: return
            if (words == heard) return
            heard = words
            if (feed != null) {
                main.removeCallbacks(settled)
                main.postDelayed(settled, SETTLED_MS)
            }
        }

        // Fed audio runs as one segmented session: the first phrase is the search.
        override fun onSegmentResults(segmentResults: Bundle) {
            val words = first(segmentResults) ?: heard
            if (words.isNotBlank()) done(words)
        }

        override fun onEndOfSegmentedSession() = done(heard)

        override fun onResults(results: Bundle?) = done(first(results) ?: heard)

        override fun onError(error: Int) {
            if (phase != Phase.Listening) return
            when {
                // Nothing said, or nothing it could make out: just go, as Google's page does.
                error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> done(heard)
                // An engine that won't take the audio: once more, letting it open the microphone itself.
                feed != null && heard.isBlank() &&
                    (error == SpeechRecognizer.ERROR_AUDIO || error == SpeechRecognizer.ERROR_CLIENT) -> {
                    stopListening()
                    listen(feedAudio = false)
                }
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
        haptics.performPredictiveBackHaptic(window.decorView)
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
        haptics.performPredictiveBackCancelHaptic(window.decorView)
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
        /** Fed audio: how long to wait for the first words, as Google's own voice search waits. */
        const val NO_WORDS_MS = 6_000L
        /** Fed audio: how long the words may sit unchanged before they're taken as said, if the engine hasn't said. */
        const val SETTLED_MS = 1_800L
        const val LONGEST_MS = 20_000L
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
