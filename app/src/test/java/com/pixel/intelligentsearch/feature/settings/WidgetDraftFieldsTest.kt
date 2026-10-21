package com.pixel.intelligentsearch.feature.settings

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.widget.WidgetDefaults
import com.pixel.intelligentsearch.core.data.widget.WidgetDesign
import com.pixel.intelligentsearch.core.data.widget.WidgetSettingsStore
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The Color Studio sliders slide through fractions, but a design is saved in whole numbers. The draft keeps whole
 * numbers too, so the preview paints the colour the widget will draw once saved, not one a fraction away.
 */
@RunWith(RobolectricTestRunner::class)
class WidgetDraftFieldsTest {

    private val prefs = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("widget_draft_fields_test", Context.MODE_PRIVATE)
    private val defaults = WidgetDefaults(primaryColor = 0xFF6750A4.toInt(), isDynamicColorActive = true)

    @Test
    fun sliderValuesAreWhatGetsSaved() {
        for (design in WidgetDesign.entries) {
            prefs.edit().clear().commit()
            val store = WidgetSettingsStore(prefs)
            val draft = mutableStateOf(store.load(design, defaults))
            val material = if (design == WidgetDesign.MATERIAL) draft else mutableStateOf(store.load(WidgetDesign.MATERIAL, defaults))
            val fields = WidgetDraftFields(draft, material)

            fields.hue = 123.6f
            fields.saturation = 40.4f
            fields.lightness = 0.7f
            fields.colorOpacity = 49.5f
            fields.transparency = 99.6f

            assertEquals(124f, fields.hue)
            assertEquals(40f, fields.saturation)
            assertEquals(1f, fields.lightness)
            assertEquals(50f, fields.colorOpacity)
            assertEquals(100f, fields.transparency)

            val editor = prefs.edit()
            store.save(design, draft.value, editor)
            editor.commit()
            val saved = store.load(design, defaults)
            assertEquals("$design custom colour", draft.value.customColorInt, saved.customColorInt)
            assertEquals("$design transparency", draft.value.transparency, saved.transparency)
            assertEquals("$design draft", draft.value, saved)
        }
    }
}
