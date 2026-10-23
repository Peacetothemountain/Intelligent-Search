package com.pixel.intelligentsearch.feature.settings

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pixel.intelligentsearch.core.data.widget.WidgetDesignSettings
import kotlin.math.roundToInt
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** A read-write view of one field of a design draft held in [state], so the UI keeps binding to plain vars. */
internal fun <T> draftField(
    state: MutableState<WidgetDesignSettings>,
    get: (WidgetDesignSettings) -> T,
    set: (WidgetDesignSettings, T) -> WidgetDesignSettings
) = object : ReadWriteProperty<Any?, T> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): T = get(state.value)
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        state.value = set(state.value, value)
    }
}

/** [value] rounded to the nearest whole number, as a slider value is saved. */
private fun whole(value: Float): Float = value.roundToInt().toFloat()

/**
 * The widget design drafts as plain properties: reading one reads the draft being edited, assigning one updates
 * it. Rebuilt when the user switches design, so [active] always points at that design's draft.
 */
internal class WidgetDraftFields(active: MutableState<WidgetDesignSettings>, material: MutableState<WidgetDesignSettings>) {
    var subtheme by draftField(active, { it.subtheme }) { d, v -> d.copy(subtheme = v) }
    var materialGIconTheme by draftField(active, { it.gIconTheme }) { d, v -> d.copy(gIconTheme = v) }
    var customGIconColor by draftField(active, { it.elementColors.gIcon }) { d, v -> d.copy(elementColors = d.elementColors.copy(gIcon = v)) }
    var customMicColor by draftField(active, { it.elementColors.mic }) { d, v -> d.copy(elementColors = d.elementColors.copy(mic = v)) }
    var customShortcut1Color by draftField(active, { it.elementColors.shortcut1 }) { d, v -> d.copy(elementColors = d.elementColors.copy(shortcut1 = v)) }
    var customShortcut2Color by draftField(active, { it.elementColors.shortcut2 }) { d, v -> d.copy(elementColors = d.elementColors.copy(shortcut2 = v)) }
    var customShortcut3Color by draftField(active, { it.elementColors.shortcut3 }) { d, v -> d.copy(elementColors = d.elementColors.copy(shortcut3 = v)) }
    var customActionColor by draftField(active, { it.elementColors.action }) { d, v -> d.copy(elementColors = d.elementColors.copy(action = v)) }
    var customSyncColors by draftField(active, { it.syncColors }) { d, v -> d.copy(syncColors = v) }
    // The Color Studio sliders pass fractions, but a design is saved in whole numbers; the draft keeps whole numbers
    // too, so the preview shows the colour the widget will draw once saved.
    var hue by draftField(active, { it.hue }) { d, v -> d.copy(hue = whole(v)) }
    var saturation by draftField(active, { it.saturation }) { d, v -> d.copy(saturation = whole(v)) }
    var lightness by draftField(active, { it.lightness }) { d, v -> d.copy(lightness = whole(v)) }
    var colorOpacity by draftField(active, { it.colorOpacity }) { d, v -> d.copy(colorOpacity = whole(v)) }
    var transparency by draftField(active, { it.transparency }) { d, v -> d.copy(transparency = whole(v)) }
    var showGIcon by draftField(active, { it.showGIcon }) { d, v -> d.copy(showGIcon = v) }
    var showDoodles by draftField(active, { it.showDoodles }) { d, v -> d.copy(showDoodles = v) }
    var showVoice by draftField(active, { it.showVoice }) { d, v -> d.copy(showVoice = v) }
    var shortcut1 by draftField(active, { it.shortcut1 }) { d, v -> d.copy(shortcut1 = v) }
    var shortcut2 by draftField(active, { it.shortcut2 }) { d, v -> d.copy(shortcut2 = v) }
    var shortcut3 by draftField(active, { it.shortcut3 }) { d, v -> d.copy(shortcut3 = v) }
    var slotOrderStr by draftField(active, { it.slotOrder }) { d, v -> d.copy(slotOrder = v) }
    var keepSystemG by draftField(active, { it.keepSystemG }) { d, v -> d.copy(keepSystemG = v) }
    var outlineThickness by draftField(active, { it.outlineThickness }) { d, v -> d.copy(outlineThickness = v) }
    var cornerRadius by draftField(active, { it.cornerRadius }) { d, v -> d.copy(cornerRadius = v) }
    // Material-only settings.
    var lockBlack by draftField(material, { it.lockBlack }) { d, v -> d.copy(lockBlack = v) }
    var actionIcon by draftField(material, { it.actionIcon }) { d, v -> d.copy(actionIcon = v) }
}

/** UI state the widget editor's sections share for as long as the screen is open: dialogs, sheets, drag state. */
internal class WidgetEditorState(initialSlotItemHeightPx: Float) {
    val pendingCustomShortcutTypes = mutableStateMapOf<String, String>()
    val pendingCustomShortcutValues = mutableStateMapOf<String, String>()
    var showShortcutSheet by mutableStateOf(false)
    var showCustomUrlDialogFor by mutableStateOf<String?>(null)
    var showCustomAppDialogFor by mutableStateOf<String?>(null)
    var customInputValue by mutableStateOf("")
    var expandedDropdownFor by mutableStateOf<String?>(null)
    var showHexInput by mutableStateOf(false)
    var tempHexInput by mutableStateOf("")
    var showCustomHexDialog by mutableStateOf(false)
    var tempCustomHexInput by mutableStateOf("")
    var activeShortcutSlot by mutableIntStateOf(1)
    var draggingSlotKey by mutableStateOf<String?>(null)
    var slotItemHeightPx by mutableFloatStateOf(initialSlotItemHeightPx)
    var itemDragOffset by mutableFloatStateOf(0f)
    var selectedTabName by mutableStateOf("Appearance")
}

/**
 * The element being coloured by the Custom G style's sliders, and their Hue, Saturation and Brightness (HSV, 0..360 and
 * 0..100); starts over when the user switches design.
 */
internal class WidgetColorSelection {
    var activeCustomColorTarget by mutableStateOf("g_icon")
    var customIconHue by mutableFloatStateOf(0f)
    var customIconSat by mutableFloatStateOf(100f)
    var customIconVal by mutableFloatStateOf(100f)

    /** The colour the sliders are on: the one they last picked or were put on, null before either. */
    private var shown: Int? = null

    /**
     * Puts the sliders on [color], the colour of the element being edited, unless they are already on it. A colour set
     * anywhere but the sliders (another element, another profile, Reset, the hex field) moves them, so they never show
     * one colour while the next touch picks from another. A colour they picked themselves leaves them exactly where
     * they are: it holds only whole steps of 1/255, and a grey or black has no hue (or black no saturation) to give
     * back. A grey keeps the hue the sliders had.
     */
    fun show(color: Int) {
        if (color == shown) return
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color, hsv)
        if (hsv[1] > 0f && hsv[2] > 0f) customIconHue = hsv[0]
        customIconSat = hsv[1] * 100f
        customIconVal = hsv[2] * 100f
        shown = color
    }

    /** Moves the sliders to [hue], [saturation] and [brightness] and gives the opaque colour they pick there. */
    fun pick(hue: Float = customIconHue, saturation: Float = customIconSat, brightness: Float = customIconVal): Int {
        customIconHue = hue
        customIconSat = saturation
        customIconVal = brightness
        val color = android.graphics.Color.HSVToColor(
            floatArrayOf(hue, (saturation / 100f).coerceIn(0f, 1f), (brightness / 100f).coerceIn(0f, 1f))
        )
        shown = color
        return color
    }
}
