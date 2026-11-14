package com.pixel.intelligentsearch.core.data.widget

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.CRC32
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/**
 * A widget's look as a piece of text someone else can paste in: "IS1." then the design, packed, then a short check
 * that catches a code with a piece missing or mistyped.
 *
 * A code holds one design's look and nothing else: its colors, outline, corners, which buttons show and their order.
 * A button that opens the sharer's own app or address is left out, and nothing about searches, contacts, files or any
 * other setting is ever in it. A code that comes in is not trusted: every value has to be one the editor itself could
 * have saved, or the code is refused.
 */
object WidgetSetupCode {
    private const val PREFIX = "IS1."

    /** Longer than any real code; a longer paste is not read at all. */
    const val MAX_TEXT_LENGTH = 8192
    private const val MAX_JSON_BYTES = 16 * 1024
    private const val MAX_STRING_LENGTH = 48
    private const val DESIGN_FIELD = "design"
    private const val VALUES_FIELD = "values"
    private const val OUTLINE_THICKNESS = "outline_thickness"
    private const val CORNER_RADIUS = "corner_radius"

    private val CODE = Regex("IS1\\.([A-Za-z0-9_-]{8,})\\.([0-9a-z]{7})")
    private val PLAIN_NAME = Regex("[A-Za-z0-9 ()&.,'-]*")
    private val JSON = Json { ignoreUnknownKeys = true }

    /** The buttons every phone has. Any other (the sharer's own app or address) is shared as no button. */
    internal val SHAREABLE_SHORTCUTS = setOf(
        "None", "Google Lens", "Live", "Translate (text)", "Translate (camera)", "Song Search", "Weather", "Sports",
        "Dictionary", "Homework", "Finance", "Saved", "News", "Calculator", "Voice Nebula", "Discover"
    )
    private val SHORTCUT_KEYS = setOf("shortcut_1", "shortcut_2", "shortcut_3")
    private val SLOTS = WidgetSettingsStore.DEFAULT_SLOT_ORDER.split(',').toSet()
    private val PERCENT_KEYS = setOf("custom_saturation", "custom_lightness", "custom_color_opacity", "background_transparency")

    /** The code for the design [setup] shows. */
    fun encode(setup: WidgetProfileDesign): String {
        val values = shareable(WidgetProfiles.valuesOf(setup.design, setup.settings(setup.design)))
        val json = buildJsonObject {
            put(DESIGN_FIELD, setup.design.name)
            put(VALUES_FIELD, values)
        }.toString()
        val body = Base64.getUrlEncoder().withoutPadding().encodeToString(deflate(json.toByteArray(Charsets.UTF_8)))
        return "$PREFIX$body.${check(body)}"
    }

    /**
     * The setup in [text], which may have other words around the code: the shared design with its look, and
     * [current]'s own settings for the other design, which a code doesn't carry. Null when [text] holds no code, the
     * code is damaged, or a value in it is not one the editor could have saved.
     */
    fun decode(text: String, current: WidgetProfileDesign, defaults: WidgetDefaults): WidgetProfileDesign? {
        if (text.length > MAX_TEXT_LENGTH) return null
        val match = CODE.find(text) ?: return null
        val (body, check) = match.destructured
        if (check(body) != check) return null
        val json = try {
            val packed = Base64.getUrlDecoder().decode(body)
            JSON.parseToJsonElement(String(inflate(packed) ?: return null, Charsets.UTF_8)) as? JsonObject
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: SerializationException) {
            null
        } ?: return null
        val design = WidgetDesign.entries.firstOrNull { it.name == (json[DESIGN_FIELD] as? JsonPrimitive)?.contentOrNull } ?: return null
        val values = checked(json[VALUES_FIELD] as? JsonObject ?: return null) ?: return null
        val shared = WidgetProfiles.settingsFrom(design, values, defaults)
        return if (design == WidgetDesign.SYSTEM) current.copy(design = design, system = shared) else current.copy(design = design, material = shared)
    }

    /** [values] with the buttons only the sharer has taken out. */
    private fun shareable(values: JsonObject): JsonObject = buildJsonObject {
        for ((key, value) in values) {
            val text = (value as? JsonPrimitive)?.takeIf { it.isString }?.content
            if (key in SHORTCUT_KEYS && text !in SHAREABLE_SHORTCUTS) put(key, "None") else put(key, value)
        }
    }

    /**
     * [values] as safe to load, or null when one of them is not what the editor saves: a name it doesn't know is
     * dropped, a value of the wrong kind or out of its range refuses the whole code.
     */
    private fun checked(values: JsonObject): JsonObject? {
        val out = linkedMapOf<String, JsonPrimitive>()
        for ((key, element) in values) {
            val value = element as? JsonPrimitive ?: return null
            when {
                key == OUTLINE_THICKNESS -> if (value.whole() !in 0..MAX_OUTLINE_THICKNESS) return null
                key == CORNER_RADIUS -> if (value.whole() !in 0..MAX_CORNER_RADIUS) return null
                else -> when (WidgetSettingsStore.DESIGN_KEY_TYPES[key] ?: continue) {
                    Boolean::class -> if (value.isString || value.booleanOrNull == null) return null
                    Int::class -> {
                        val number = value.whole() ?: return null
                        if (key == "custom_hue" && number !in 0..360) return null
                        if (key in PERCENT_KEYS && number !in 0..100) return null
                    }
                    else -> {
                        if (!value.isString) return null
                        val text = value.content
                        if (text.length > MAX_STRING_LENGTH || !PLAIN_NAME.matches(text)) return null
                        if (key in SHORTCUT_KEYS && text !in SHAREABLE_SHORTCUTS) return null
                        if (key == "shortcut_order" && text.split(',').let { it.size != SLOTS.size || it.toSet() != SLOTS }) return null
                    }
                }
            }
            out[key] = value
        }
        return JsonObject(out)
    }

    /** The whole number this is, or null for text, a switch or a fraction. */
    private fun JsonPrimitive.whole(): Int? = if (isString) null else intOrNull

    private fun check(body: String): String =
        CRC32().apply { update(body.toByteArray(Charsets.US_ASCII)) }.value.toString(36).padStart(7, '0')

    private fun deflate(bytes: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(bytes)
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
        deflater.end()
        return out.toByteArray()
    }

    /** What [packed] unpacks to, or null when it isn't packed data or would unpack to more than a setup ever is. */
    private fun inflate(packed: ByteArray): ByteArray? {
        val inflater = Inflater()
        return try {
            inflater.setInput(packed)
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            while (!inflater.finished()) {
                val read = inflater.inflate(buffer)
                if (read == 0 && (inflater.needsInput() || inflater.needsDictionary())) return null
                out.write(buffer, 0, read)
                if (out.size() > MAX_JSON_BYTES) return null
            }
            out.toByteArray()
        } catch (_: DataFormatException) {
            null
        } finally {
            inflater.end()
        }
    }
}
