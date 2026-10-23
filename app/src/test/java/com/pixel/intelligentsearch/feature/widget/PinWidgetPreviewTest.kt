package com.pixel.intelligentsearch.feature.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RemoteViews
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.R
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.widget.WidgetDefaults
import com.pixel.intelligentsearch.core.data.widget.WidgetDesign
import com.pixel.intelligentsearch.core.data.widget.WidgetDesignSettings
import com.pixel.intelligentsearch.core.data.widget.WidgetSettingsStore
import com.pixel.intelligentsearch.core.occasion.OccasionCalendar
import com.pixel.intelligentsearch.core.theme.DynamicThemeDetector
import com.pixel.intelligentsearch.core.theme.SearchIconColors
import com.pixel.intelligentsearch.feature.settings.PreviewTheme
import com.pixel.intelligentsearch.feature.settings.widgetPreviewColors
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
import kotlin.math.abs

/**
 * Adding the search bar widget from Widget Customization: the launcher's add-widget dialog must preview the widget
 * exactly as the editor's live preview shows it (the editor saves that design first), at whatever size it's placed,
 * which is how the widget it places then draws itself.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class PinWidgetPreviewTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs by lazy { SettingsManager.storedPrefs(context) }
    private val defaultNow = OccasionCalendar.now
    private val widths = listOf(
        SearchWidgetProvider.ULTRA_COMPACT_WIDTH, SearchWidgetProvider.COMPACT_WIDTH,
        SearchWidgetProvider.STANDARD_WIDTH, SearchWidgetProvider.EXPANDED_WIDTH
    )

    @Before fun pinningIsSupported() {
        prefs.edit().clear().commit()
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

    /**
     * The views a launcher shows for responsive [this] in a widget [size] dp big (RemoteViews' own choice; Android's
     * public API only applies them at the launcher's size, so the framework's own method is called).
     */
    private fun RemoteViews.at(size: SizeF): RemoteViews =
        RemoteViews::class.java.getMethod("getRemoteViewsToApply", Context::class.java, SizeF::class.java)
            .invoke(this, context, size) as RemoteViews

    /** [views] applied the way a launcher applies them, [width] dp wide, as a view. */
    private fun apply(views: RemoteViews, width: Float): View {
        val host = FrameLayout(context)
        val widget = views.apply(context, host)
        host.addView(widget)
        val px = (width * context.resources.displayMetrics.density).toInt()
        host.measure(View.MeasureSpec.makeMeasureSpec(px, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        host.layout(0, 0, host.measuredWidth, host.measuredHeight)
        return host
    }

    /** [views] applied at [width] dp, as pixels. */
    private fun render(views: RemoteViews, width: Float = SearchWidgetProvider.STANDARD_WIDTH.width): Bitmap {
        val host = apply(views, width)
        return Bitmap.createBitmap(host.measuredWidth, host.measuredHeight, Bitmap.Config.ARGB_8888).also { host.draw(Canvas(it)) }
    }

    /** What a widget of the design placed at [width] shows, drawn the way the provider draws it. */
    private fun placedWidget(material: Boolean, width: SizeF = SearchWidgetProvider.STANDARD_WIDTH): Bitmap = render(
        SearchWidgetProvider.buildResponsiveViews(context, appWidgetId = 1, isMaterialYou = material, prefs = prefs).getValue(width),
        width.width
    )

    /** The launcher is handed a preview that, at every width, looks exactly like the widget placed at that width. */
    private fun assertThePreviewIsThePlacedWidget(material: Boolean) {
        val (provider, extras) = pin(material)
        val expectedProvider = if (material) SearchWidgetMaterialProvider::class.java else SearchWidgetProvider::class.java
        assertEquals(expectedProvider.name, provider.className)
        val preview = extras.preview()
        assertNotNull("the pin request carries a preview", preview)
        for (width in widths) {
            assertTrue("the preview at $width looks exactly like the placed widget", render(preview!!.at(width), width.width).sameAs(placedWidget(material, width)))
        }
    }

    @Test
    fun theSystemDesignPreviewsAsSaved() {
        saveCustomDesign(material = false)
        assertThePreviewIsThePlacedWidget(material = false)
    }

    /** The outline and corners are part of the design the launcher previews. */
    @Test
    fun theOutlineAndCornersPreviewAsPlaced() {
        for (material in listOf(true, false)) {
            saveCustomDesign(material)
            val p = if (material) "widget_material" else "widget_system"
            prefs.edit()
                .putInt("${p}_outline_thickness", 4)
                .putInt("${p}_corner_radius", 6)
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

    /**
     * The bug: the launcher was handed the narrow 4-cell layout only, which drops shortcuts, so a phone's 4-cell widget
     * (wider than that layout) previewed fewer than the editor's live preview and the placed widget show. At a phone's
     * 4 cells (over 340dp on a 412dp wide phone) the preview shows the mic and every shortcut, as the editor does.
     */
    @Test
    fun atAPhonesFourCellsThePreviewShowsEveryShortcutTheEditorShows() {
        prefs.edit()
            .putBoolean("widget_material_design_saved", true)
            .putString("widget_material_action_icon", "Search")
            .putString("widget_material_shortcut_1", "Weather")
            .putString("widget_material_shortcut_2", "News")
            .putString("widget_material_shortcut_3", "Calculator")
            .putBoolean("widget_material_show_voice", true)
            .commit()
        val preview = pin(material = true).second.preview()!!
        val shown = apply(preview.at(SizeF(380f, 64f)), 380f)
        for (id in listOf(R.id.widget_voice_search, R.id.widget_lens_search, R.id.widget_shortcut_2, R.id.widget_shortcut_3)) {
            assertEquals(View.VISIBLE, shown.findViewById<View>(id).visibility)
        }
        assertEquals(View.VISIBLE, shown.findViewById<View>(R.id.widget_sound_search).visibility)
    }

    /**
     * The editor's live preview and the pin preview are the same design: what the editor paints for its draft
     * ([widgetPreviewColors], [WidgetBarShape]) is what the launcher is shown once the editor has saved that draft.
     */
    @Test
    fun thePreviewIsTheEditorsDesign() {
        for (material in listOf(true, false)) {
            val design = if (material) WidgetDesign.MATERIAL else WidgetDesign.SYSTEM
            prefs.edit().clear().putString(SettingsManager.THEME.name, "Material Light").commit()
            val theme = DynamicThemeDetector.resolveWidgetColors(context, isDark = false)
            val dynamic = DynamicThemeDetector.isDynamicColorActive(context)
            val store = WidgetSettingsStore(prefs)
            // The editor's draft, as its controls leave it, saved the way its Save (or Save and Add) saves it.
            val draft: WidgetDesignSettings = store.load(design, WidgetDefaults(theme.primary, dynamic)).copy(
                subtheme = "Custom",
                gIconTheme = "Accented G Icon",
                hue = 140f,
                saturation = 70f,
                lightness = 80f,
                colorOpacity = 90f,
                outlineThickness = 6,
                cornerRadius = 14,
                shortcut1 = "Weather",
                shortcut2 = "News",
                lockBlack = false
            )
            prefs.edit().also { store.save(design, draft, it) }.commit()

            val editor = widgetPreviewColors(
                draft = draft,
                isMaterialDesign = material,
                theme = PreviewTheme(
                    primary = Color(theme.primary),
                    primaryContainer = Color(theme.primaryContainer),
                    surfaceContainer = Color(theme.surfaceContainer),
                    appDark = false,
                    systemDark = false
                ),
                isDynamicColorActive = dynamic,
                iconAccents = { SearchIconColors.themeAccents(context, it) }
            ) { DynamicThemeDetector.resolveWidgetColors(context, it) }
            val editorShape = WidgetBarShape.of(draft, material)

            val shown = apply(pin(material).second.preview()!!.at(SearchWidgetProvider.EXPANDED_WIDTH), SearchWidgetProvider.EXPANDED_WIDTH.width)
            val label = "material=$material"
            fun drawn(id: Int) = shadowOf(shown.findViewById<ImageView>(id).drawable).createdFromResId
            fun tint(id: Int) = shown.findViewById<ImageView>(id).imageTintList!!.defaultColor

            assertEquals("$label pill colour", editor.pill.fill.toArgb() and 0xFFFFFF, tint(R.id.widget_pill_background) and 0xFFFFFF)
            assertTrue("$label pill alpha", abs((editor.pill.fill.toArgb() ushr 24) - shown.findViewById<ImageView>(R.id.widget_pill_background).imageAlpha) <= 1)
            assertEquals("$label outline colour", editor.rim.toArgb() and 0xFFFFFF, tint(R.id.widget_outer_background) and 0xFFFFFF)
            assertEquals("$label G", editor.accentPrimary.toArgb(), tint(R.id.widget_g_logo))
            assertEquals("$label pill corners", WidgetBarShape.background(editorShape.pillRadius), drawn(R.id.widget_pill_background))
            assertEquals("$label outline corners", WidgetBarShape.background(editorShape.outerRadius), drawn(R.id.widget_outer_background))
            assertEquals("$label bar height", (editorShape.height * context.resources.displayMetrics.density).toInt(), shown.findViewById<View>(android.R.id.background).layoutParams.height)
            if (material) assertEquals("$label circle corners", WidgetBarShape.background(editorShape.circleRadius), drawn(R.id.widget_sound_background))
            // The mic and both shortcuts, in the editor's order.
            assertEquals(View.VISIBLE, shown.findViewById<View>(R.id.widget_voice_search).visibility)
            assertEquals(View.VISIBLE, shown.findViewById<View>(R.id.widget_lens_search).visibility)
            assertEquals(View.VISIBLE, shown.findViewById<View>(R.id.widget_shortcut_2).visibility)
            assertEquals(View.GONE, shown.findViewById<View>(R.id.widget_shortcut_3).visibility)
        }
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

    /** The editor's preview never shows the day's doodle (it only lasts the day), so neither does the launcher's. */
    @Test
    fun onAHolidayThePreviewLeavesTheDoodleOutAsTheEditorDoes() {
        OccasionCalendar.now = { java.time.ZonedDateTime.parse("2026-10-31T09:00-04:00[America/New_York]") }
        val preview = pin(material = false).second.preview()!!
        for (width in widths) {
            val shown = apply(preview.at(width), width.width)
            assertEquals("no doodle at $width", View.GONE, shown.findViewById<View>(R.id.widget_doodle).visibility)
        }
        // The placed widget still wears it on the day.
        val placed = apply(SearchWidgetProvider.buildResponsiveViews(context, 1, isMaterialYou = false, prefs = prefs).getValue(SearchWidgetProvider.STANDARD_WIDTH), 260f)
        assertEquals(View.VISIBLE, placed.findViewById<View>(R.id.widget_doodle).visibility)
    }

    @Test
    fun thePreviewIsTheSavedDesignNotTheDefaultLook() {
        val defaultLook = render(pin(material = false).second.preview()!!.at(SearchWidgetProvider.STANDARD_WIDTH))
        saveCustomDesign(material = false)
        val savedLook = render(pin(material = false).second.preview()!!.at(SearchWidgetProvider.STANDARD_WIDTH))
        assertFalse(savedLook.sameAs(defaultLook))
    }

    @Test
    fun withoutAPreviewTheRequestCarriesNoExtras() {
        assertNull(PinWidgetReceiver.pinRequestExtras(null))
    }
}
