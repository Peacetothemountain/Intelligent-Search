package com.pixel.intelligentsearch.feature.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.occasion.OccasionCalendar
import com.pixel.intelligentsearch.core.theme.DynamicThemeDetector
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Adding the search bar widget from Widget Customization: the launcher's add-widget dialog must preview the widget
 * exactly as the user saved it, which is how the widget it places then draws itself.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class PinWidgetPreviewTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs by lazy { SettingsManager.storedPrefs(context) }
    private val defaultNow = OccasionCalendar.now

    @Before fun pinningIsSupported() {
        shadowOf(AppWidgetManager.getInstance(context)).setRequestPinAppWidgetSupported(true)
        // No holiday, so no doodle unless a test asks for one.
        OccasionCalendar.now = { java.time.ZonedDateTime.parse("2026-03-03T09:00-05:00[America/New_York]") }
    }

    @After fun restoreClock() {
        OccasionCalendar.now = defaultNow
    }

    /** A saved design far from the defaults: Custom subtheme and G style, own colours, other shortcuts, dark. */
    private fun saveCustomDesign(material: Boolean) {
        val p = if (material) "widget_material" else "widget_system"
        prefs.edit()
            .putString(SettingsManager.THEME.name, "Material Dark")
            .putBoolean("${p}_design_saved", true)
            .putString("${p}_subtheme", "Custom")
            .putString("${p}_material_g_icon", "Custom G Icon")
            .putInt("${p}_custom_hue", 200)
            .putInt("${p}_custom_saturation", 60)
            .putInt("${p}_custom_lightness", 90)
            .putInt("${p}_custom_color_opacity", 100)
            .putInt("${p}_background_transparency", 10)
            .putInt("${p}_custom_g_icon_color", 0xFFB3261E.toInt())
            .putInt("${p}_custom_mic_color", 0xFF1E6FB3.toInt())
            .putBoolean("${p}_custom_sync_colors", false)
            .putString("${p}_shortcut_1", "Weather")
            .putString("${p}_shortcut_2", "Translate (text)")
            .putBoolean("${p}_lock_black", false)
            .commit()
    }

    /** Pins the widget of the given design and returns what the launcher was handed. */
    private fun pin(material: Boolean): Pair<ComponentName, Bundle?> {
        var requested: Pair<ComponentName, Bundle?>? = null
        val dispatched = runBlocking {
            PinWidgetReceiver.requestPinWidget(context, isMaterial = material) { _, provider, extras, _ ->
                requested = provider to extras
                true
            }
        }
        assertTrue(dispatched)
        return requested!!
    }

    private fun Bundle?.preview(): RemoteViews? =
        this?.getParcelable(AppWidgetManager.EXTRA_APPWIDGET_PREVIEW, RemoteViews::class.java)

    /** [views] applied the way a launcher applies them, at a phone's default 4-cell width, as pixels. */
    private fun render(views: RemoteViews): Bitmap {
        val host = FrameLayout(context)
        val widget = views.apply(context, host)
        host.addView(widget)
        val width = (SearchWidgetProvider.STANDARD_WIDTH.width * context.resources.displayMetrics.density).toInt()
        host.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        host.layout(0, 0, host.measuredWidth, host.measuredHeight)
        return Bitmap.createBitmap(host.measuredWidth, host.measuredHeight, Bitmap.Config.ARGB_8888).also { host.draw(Canvas(it)) }
    }

    /** What a widget of the design placed at the default width shows, drawn the way the provider draws it. */
    private fun placedWidget(material: Boolean): Bitmap = render(
        SearchWidgetProvider.buildResponsiveViews(context, appWidgetId = 1, isMaterialYou = material, prefs = prefs)
            .getValue(SearchWidgetProvider.STANDARD_WIDTH)
    )

    private fun assertThePreviewIsThePlacedWidget(material: Boolean) {
        val (provider, extras) = pin(material)
        val expectedProvider = if (material) SearchWidgetMaterialProvider::class.java else SearchWidgetProvider::class.java
        assertEquals(expectedProvider.name, provider.className)
        val preview = extras.preview()
        assertNotNull("the pin request carries a preview", preview)
        assertTrue("the preview looks exactly like the placed widget", render(preview!!).sameAs(placedWidget(material)))
    }

    @Test
    fun theSystemDesignPreviewsAsSaved() {
        saveCustomDesign(material = false)
        assertThePreviewIsThePlacedWidget(material = false)
    }

    /** The outline and corners are part of the design the launcher previews; rows above the bar are not (one row tall). */
    @Test
    fun theOutlineCornersAndExtrasPreviewAsPlaced() {
        for (material in listOf(true, false)) {
            saveCustomDesign(material)
            val p = if (material) "widget_material" else "widget_system"
            prefs.edit()
                .putInt("${p}_outline_thickness", 4)
                .putInt("${p}_corner_radius", 6)
                .putString("${p}_favourite_apps", context.packageName)
                .putBoolean("${p}_show_media", true)
                .commit()
            assertThePreviewIsThePlacedWidget(material)
        }
    }

    @Test
    fun theMaterialDesignPreviewsAsSaved() {
        saveCustomDesign(material = true)
        assertThePreviewIsThePlacedWidget(material = true)
    }

    @Test
    fun aDesignWithoutTheCircleIconPreviewsAllItsShortcuts() {
        // Without its circle icon the Material design has room for a third shortcut, as the placed widget shows.
        prefs.edit()
            .putBoolean("widget_material_design_saved", true)
            .putString("widget_material_action_icon", "None")
            .putString("widget_material_shortcut_3", "Weather")
            .commit()
        assertThePreviewIsThePlacedWidget(material = true)
    }

    @Test
    fun theWidgetPickersPreviewIsThePlacedWidgetToo() {
        // Android 15+ takes this one when the design is saved; it used to leave out the third shortcut.
        prefs.edit()
            .putBoolean("widget_material_design_saved", true)
            .putString("widget_material_action_icon", "None")
            .putString("widget_material_shortcut_3", "Weather")
            .commit()
        SearchWidgetProvider.publishedPreviews.clear()
        val previews = mutableMapOf<String, RemoteViews>()
        val colors = DynamicThemeDetector.resolveWidgetColors(context, isDark = false)
        SearchWidgetProvider.updateWidgetPickerPreviews(context, prefs, isDark = false, widgetColors = colors) { provider, views ->
            previews[provider.className] = views
            true
        }
        assertTrue(render(previews.getValue(SearchWidgetMaterialProvider::class.java.name)).sameAs(placedWidget(material = true)))
    }

    @Test
    fun onAHolidayThePreviewWearsTheDoodle() {
        OccasionCalendar.now = { java.time.ZonedDateTime.parse("2026-10-31T09:00-04:00[America/New_York]") }
        assertThePreviewIsThePlacedWidget(material = false)
    }

    @Test
    fun thePreviewIsTheSavedDesignNotTheDefaultLook() {
        val defaultLook = render(pin(material = false).second.preview()!!)
        saveCustomDesign(material = false)
        val savedLook = render(pin(material = false).second.preview()!!)
        assertFalse(savedLook.sameAs(defaultLook))
    }

    @Test
    fun withoutAPreviewTheRequestCarriesNoExtras() {
        assertNull(PinWidgetReceiver.pinRequestExtras(null))
    }
}
