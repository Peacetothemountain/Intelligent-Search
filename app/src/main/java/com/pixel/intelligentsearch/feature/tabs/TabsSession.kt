package com.pixel.intelligentsearch.feature.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import com.pixel.intelligentsearch.feature.tabs.gallery.GallerySession
import com.pixel.intelligentsearch.feature.tabs.ui.findActivity
import com.pixel.intelligentsearch.feature.tabs.wellbeing.WellbeingSession
import java.time.LocalDate

/**
 * What Settings' Gallery and Wellbeing tabs keep while Settings is open, above the tabs' slide: Gallery's looks, its
 * full preview and its messages; Wellbeing's data and where the person was on it. A tab that slides out of sight
 * leaves composition, and Android may recreate the screen (setting a wallpaper changes the phone's colors), so none of
 * this lives in a page: going back to a tab finds it as it was left. Only while the app is alive.
 */
internal class TabsSession : ViewModel() {
    private var gallery: GallerySession? = null

    /** Gallery's state, made the first time Gallery opens ([today] fixes its look of the week for the visit). */
    fun gallery(today: LocalDate): GallerySession = gallery ?: GallerySession(today, viewModelScope).also { gallery = it }

    val wellbeing = WellbeingSession()
}

/** The tabs' session, kept by the activity across recreation. Outside an activity (tests) a fresh one. */
@Composable
internal fun rememberTabsSession(): TabsSession {
    val owner = LocalContext.current.findActivity() as? ViewModelStoreOwner
    return remember(owner) { if (owner != null) ViewModelProvider(owner)[TabsSession::class.java] else TabsSession() }
}
