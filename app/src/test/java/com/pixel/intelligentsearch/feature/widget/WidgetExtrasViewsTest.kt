package com.pixel.intelligentsearch.feature.widget

import com.pixel.intelligentsearch.core.system.GoogleApps
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.R
import com.pixel.intelligentsearch.core.system.MediaSessionAccess
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The search bar widget's new options as the launcher shows them: the favourite apps and media rows only once the
 * widget is tall enough, the outline and corners, and the Calculator and Discover shortcuts.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class WidgetExtrasViewsTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs by lazy { context.getSharedPreferences("widget_extras_views_test", Context.MODE_PRIVATE) }
    private val activity by lazy { Robolectric.buildActivity(Activity::class.java).setup().get() }
    private val defaultNowPlaying = WidgetExtras.nowPlaying
    private val song = MediaSessionAccess.NowPlaying("Golden Hour", "JVKE", isPlaying = true, packageName = "com.example.music")

    @Before fun clean() {
        prefs.edit().clear().commit()
        clearBitmapCache()
        install(MAIL)
    }

    /** An installed app the launcher can open, with an icon. */
    private fun install(packageName: String) {
        val activity = ComponentName(packageName, "$packageName.Main")
        shadowOf(context.packageManager).apply {
            addActivityIfNotPresent(activity)
            addIntentFilterForActivity(activity, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
            setApplicationIcon(packageName, android.graphics.drawable.ColorDrawable(0xFF3366CC.toInt()))
        }
    }

    @After fun restore() {
        WidgetExtras.nowPlaying = defaultNowPlaying
    }

    private fun p(material: Boolean) = if (material) "widget_material" else "widget_system"

    private fun views(material: Boolean) =
        SearchWidgetProvider.buildResponsiveViews(context, appWidgetId = 1, isMaterialYou = material, prefs = prefs)

    private fun render(material: Boolean, size: SizeF): View = views(material).getValue(size).apply(activity, FrameLayout(activity))

    private fun tall(rows: Int, width: SizeF = SearchWidgetProvider.STANDARD_WIDTH) = SizeF(
        width.width,
        if (rows == 2) SearchWidgetProvider.TWO_EXTRA_ROWS_HEIGHT else SearchWidgetProvider.ONE_EXTRA_ROW_HEIGHT
    )

    private fun View.shown(id: Int) = findViewById<View>(id).visibility == View.VISIBLE

    @Test
    fun aDesignWithoutExtrasKeepsItsFourWidths() {
        assertEquals(4, views(material = true).size)
        assertEquals(4, views(material = false).size)
    }

    @Test
    fun favouriteAppsShowAboveTheBarOnlyWhenTheWidgetIsTallEnough() {
        for (material in listOf(true, false)) {
            prefs.edit().putString("${p(material)}_favourite_apps", "$MAIL,com.example.not.installed").commit()
            assertEquals(12, views(material).size)

            val oneRow = render(material, SearchWidgetProvider.STANDARD_WIDTH)
            assertFalse(oneRow.shown(R.id.widget_favourites))

            val twoRows = render(material, tall(1))
            assertTrue(twoRows.shown(R.id.widget_favourites))
            // An app that isn't installed is left out.
            assertTrue(twoRows.shown(R.id.widget_favourite_0))
            assertFalse(twoRows.shown(R.id.widget_favourite_1))
            val icon = twoRows.findViewById<ImageView>(R.id.widget_favourite_0)
            assertNotNull(icon.drawable)
            assertTrue(icon.hasOnClickListeners())
            assertTrue(icon.contentDescription.startsWith("Open "))
        }
    }

    @Test
    fun theMediaRowShowsWhatsPlayingWithItsControls() {
        WidgetExtras.nowPlaying = { song }
        prefs.edit().putBoolean("widget_material_show_media", true).commit()

        assertFalse(render(true, SearchWidgetProvider.STANDARD_WIDTH).shown(R.id.widget_media))
        val widget = render(true, tall(1))
        assertTrue(widget.shown(R.id.widget_media))
        assertEquals("Golden Hour · JVKE", widget.findViewById<TextView>(R.id.widget_media_title).text.toString())
        val playPause = widget.findViewById<ImageView>(R.id.widget_media_play_pause)
        assertEquals("Pause", playPause.contentDescription)
        assertEquals(R.drawable.ic_media_pause, shadowOf(playPause.drawable).createdFromResId)
        assertTrue(playPause.hasOnClickListeners())
        assertTrue(widget.findViewById<View>(R.id.widget_media_next).hasOnClickListeners())
        // The row is the pill's height.
        assertEquals(dp(48), widget.findViewById<View>(R.id.widget_media).layoutParams.height)
    }

    @Test
    fun withNothingPlayingTheMediaRowStaysHidden() {
        WidgetExtras.nowPlaying = { null }
        prefs.edit().putBoolean("widget_system_show_media", true).commit()
        assertFalse(render(false, tall(2)).shown(R.id.widget_media))
    }

    @Test
    fun withBothRowsTheFavouritesComeFirstAndTheMediaRowNeedsAThirdRow() {
        WidgetExtras.nowPlaying = { song }
        prefs.edit()
            .putBoolean("widget_system_show_media", true)
            .putString("widget_system_favourite_apps", MAIL)
            .commit()
        val twoRows = render(false, tall(1))
        assertTrue(twoRows.shown(R.id.widget_favourites))
        assertFalse(twoRows.shown(R.id.widget_media))
        val threeRows = render(false, tall(2))
        assertTrue(threeRows.shown(R.id.widget_favourites))
        assertTrue(threeRows.shown(R.id.widget_media))
    }

    /** As SearchWidgetViewsTest checks for the bar: nothing tappable is hidden at every size the launcher gets. */
    @Test
    fun everyTappableViewShowsAtSomeSize() {
        WidgetExtras.nowPlaying = { song }
        for (material in listOf(false, true)) {
            prefs.edit()
                .putBoolean("${p(material)}_show_media", true)
                .putString("${p(material)}_favourite_apps", MAIL)
                .commit()
            val shownSomewhere = mutableMapOf<Int, Boolean>()
            for ((size, remote) in views(material)) {
                forEachView(remote.apply(activity, FrameLayout(activity)), parentShown = true) { view, shown ->
                    if (view.hasOnClickListeners()) shownSomewhere.merge(view.id, shown, Boolean::or)
                }
                assertNotNull(size)
            }
            val neverShown = shownSomewhere.filterValues { !it }.keys.map { context.resources.getResourceEntryName(it) }
            assertTrue("Tappable but never shown (material=$material): $neverShown", neverShown.isEmpty())
        }
    }

    @Test
    fun theOutlineAndCornersAreDrawnAsSet() {
        prefs.edit()
            .putInt("widget_material_outline_thickness", 4)
            .putInt("widget_material_corner_radius", 10)
            .putInt("widget_system_outline_thickness", 6)
            .putInt("widget_system_corner_radius", 0)
            .commit()

        val material = render(true, SearchWidgetProvider.STANDARD_WIDTH)
        assertEquals(dp(56), material.findViewById<View>(android.R.id.background).layoutParams.height)
        assertEquals(dp(4), material.findViewById<View>(R.id.widget_bar_row).paddingTop)
        assertTrue(material.shown(R.id.widget_outer_background))
        assertEquals(R.drawable.bg_widget_r10, drawableOf(material, R.id.widget_outer_background))
        assertEquals(R.drawable.bg_widget_r6, drawableOf(material, R.id.widget_pill_background))
        assertEquals(R.drawable.bg_widget_r6, drawableOf(material, R.id.widget_sound_background))

        val system = render(false, SearchWidgetProvider.STANDARD_WIDTH)
        assertEquals(dp(68), system.findViewById<View>(android.R.id.background).layoutParams.height)
        assertTrue(system.shown(R.id.widget_outer_background))
        assertEquals(R.drawable.bg_widget_r0, drawableOf(system, R.id.widget_outer_background))
        assertEquals(R.drawable.bg_widget_r0, drawableOf(system, R.id.widget_pill_background))
        val pill = system.findViewById<View>(R.id.widget_pill_container).layoutParams as ViewGroup.MarginLayoutParams
        assertEquals(listOf(dp(6), dp(6), dp(6), dp(6)), listOf(pill.leftMargin, pill.topMargin, pill.rightMargin, pill.bottomMargin))
    }

    @Test
    fun withoutAnOutlineTheMaterialRimIsHidden() {
        prefs.edit().putInt("widget_material_outline_thickness", 0).commit()
        val material = render(true, SearchWidgetProvider.STANDARD_WIDTH)
        assertFalse(material.shown(R.id.widget_outer_background))
        assertEquals(dp(48), material.findViewById<View>(android.R.id.background).layoutParams.height)
        assertEquals(0, material.findViewById<View>(R.id.widget_bar_row).paddingTop)
    }

    @Test
    fun theWidgetDrawsItsOwnCornersSoTheLauncherDoesntCutThem() {
        // A launcher rounds a widget's background unless the widget clips that view itself (Launcher3's
        // RoundedCornerEnforcement), which would cut off square or tighter corners.
        for (material in listOf(true, false)) {
            assertTrue(render(material, SearchWidgetProvider.STANDARD_WIDTH).findViewById<View>(android.R.id.background).clipToOutline)
        }
    }

    @Test
    fun calculatorAndDiscoverAreShortcuts() {
        prefs.edit()
            .putString("widget_system_shortcut_1", "Calculator")
            .putString("widget_system_shortcut_2", "Discover")
            .putString("widget_system_shortcut_order", "shortcut1,shortcut2,mic,shortcut3")
            .commit()
        val widget = render(false, SearchWidgetProvider.EXPANDED_WIDTH)
        val calculator = widget.findViewById<ImageView>(R.id.widget_voice_search)
        assertEquals(R.drawable.ic_calculator, shadowOf(calculator.drawable).createdFromResId)
        assertEquals(context.getString(R.string.widget_talkback_calculator), calculator.contentDescription)
        val discover = widget.findViewById<ImageView>(R.id.widget_lens_search)
        assertEquals(R.drawable.ic_discover, shadowOf(discover.drawable).createdFromResId)
        assertEquals(context.getString(R.string.widget_talkback_discover), discover.contentDescription)
    }

    @Test
    fun theCalculatorOpensSearchWithTheNumberPad() {
        val intent = getCalculatorIntent(context)
        assertEquals(WidgetActivity::class.java.name, intent.component?.className)
        assertTrue(intent.getBooleanExtra(EXTRA_CALCULATOR, false))
    }

    @Test
    fun discoverOpensTheGoogleAppOrItsStorePage() {
        assertFalse(isDiscoverAvailable(context))
        assertEquals("play.google.com", getDiscoverIntent(context).data?.host)

        val google = ComponentName(GoogleApps.GOOGLE_APP, "$GoogleApps.GOOGLE_APP.SearchActivity")
        shadowOf(context.packageManager).apply {
            addActivityIfNotPresent(google)
            addIntentFilterForActivity(google, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
        }
        assertTrue(isDiscoverAvailable(context))
        assertEquals(GoogleApps.GOOGLE_APP, getDiscoverIntent(context).`package` ?: getDiscoverIntent(context).component?.packageName)
    }

    private companion object {
        const val MAIL = "com.example.mail"
    }

    private fun drawableOf(root: View, id: Int): Int = shadowOf(root.findViewById<ImageView>(id).drawable).createdFromResId

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics).toInt()

    private fun forEachView(view: View, parentShown: Boolean, action: (View, Boolean) -> Unit) {
        val shown = parentShown && view.visibility == View.VISIBLE
        action(view, shown)
        if (view is ViewGroup) for (i in 0 until view.childCount) forEachView(view.getChildAt(i), shown, action)
    }
}
