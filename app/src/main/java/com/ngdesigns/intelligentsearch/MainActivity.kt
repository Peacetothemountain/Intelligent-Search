package com.ngdesigns.intelligentsearch

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ngdesigns.intelligentsearch.ui.components.LocalHapticsEnabled
import com.ngdesigns.intelligentsearch.ui.search.SearchRoute
import com.ngdesigns.intelligentsearch.ui.search.SearchViewModel
import com.ngdesigns.intelligentsearch.ui.settings.SettingsIndex
import com.ngdesigns.intelligentsearch.ui.theme.SearchTheme

/**
 * The search screen. The window shows the wallpaper behind it (blurred and dimmed by the user's settings) or a solid
 * surface, and closes itself once something has been opened.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: SearchViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = SearchViewModel(graph, SettingsIndex.entries) as T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val initial = intent?.getStringExtra(EXTRA_QUERY).orEmpty()
        setContent {
            val ui by viewModel.state.collectAsState()
            val look = ui.settings.look
            LaunchedEffect(look.showWallpaper, look.blur) {
                window.setBackgroundBlurRadius(if (look.showWallpaper) look.blur * 2 / 3 else 0)
            }
            SearchTheme(look) {
                CompositionLocalProvider(LocalHapticsEnabled provides look.haptics) {
                    val surface = MaterialTheme.colorScheme.surface
                    val alpha = if (look.showWallpaper) 1f - look.transparency / 100f * 0.85f else 1f
                    Box(Modifier.fillMaxSize().background(surface.copy(alpha = alpha))) {
                        val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { heard ->
                                intent.putExtra(EXTRA_QUERY, heard)
                                recreate()
                            }
                        }
                        SearchRoute(
                            viewModel = viewModel,
                            initialQuery = initial,
                            onOpenSetting = { /* settings screens arrive in D5 */ },
                            onVoice = {
                                runCatching {
                                    voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH))
                                }
                            },
                            onLeave = { finish() },
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.refresh()
    }

    companion object { const val EXTRA_QUERY = "query" }
}
