package com.pixel.intelligentsearch.core.data.settings

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.pixel.intelligentsearch.di.appEntryPoint

/**
 * A setting as Compose state: reads the stored value, follows changes made anywhere else (another screen,
 * a backup restore), and writes through to the settings store when assigned. [onChanged] runs after each
 * write made through this state.
 */
@Composable
fun <T : Any> rememberSetting(key: SettingKey<T>, onChanged: () -> Unit = {}): MutableState<T> {
    val context = LocalContext.current
    val settings = remember { context.appEntryPoint.settingsManager() }
    val currentOnChanged by rememberUpdatedState(onChanged)
    val state = remember(key) { mutableStateOf(settings[key]) }

    DisposableEffect(key) {
        val prefs = settings.prefs
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, changed ->
            if (changed == null || changed in key.allNames) state.value = sp[key]
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        // Pick up a change made between composition and registration.
        state.value = prefs[key]
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    return remember(key) {
        object : MutableState<T> {
            override var value: T
                get() = state.value
                set(newValue) {
                    if (state.value == newValue) return
                    state.value = newValue
                    settings.set(key, newValue)
                    currentOnChanged()
                }

            override fun component1(): T = value
            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}
