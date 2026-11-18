package com.pixel.intelligentsearch.feature.tabs.gallery

import android.graphics.BlendMode
import android.graphics.Path
import com.pixel.intelligentsearch.feature.tabs.gallery.Scene.Companion.alpha

/*
 * The wallpaper each built-in look comes with, drawn on the phone (no pictures are packed into the app).
 *
 * Four families, so the set reads as one collection:
 * - Material shapes (Classic, Tonal Ring, Soft Square, Bold): large flat forms with paper-cut depth.
 * - Light fields (Wallpaper Glass, Pastel, Neon): pools and ribbons of light.
 * - Paper and folds (Paper, Mono): quiet stacked sheets and dunes.
 * - Landscapes (Ink, Bare, Sunset and the four seasons): layered hills. The seasons are one valley through the year.
 *
 * Each is composed for a phone: the interest sits in the upper half, the status bar's strip stays plain, and the
 * lower half, where the search bar and the icons sit, is calm and gives the look's bar something to stand on.
 */

/**
 * One look's wallpaper. [focus] is the height (0..1) a card's window centers on; [base] shows until it is drawn.
 * A wallpaper that has to differ between the themes has a twin for the light one, under its id with ".light".
 */
internal class Wallpaper(
    val id: String,
    val focus: Float,
    val base: Int,
    /** The theme color this wallpaper gives on the reference screen: what a card uses until the phone has worked it out. */
    val seed: Int,
    val design: Scene.() -> Unit
)

private fun c(rgb: Int): Int = rgb or 0xFF000000.toInt()

/* ── Material shapes ── */

/** A disc with the soft shade it casts, [dx], [dy] off: the paper-cut look these four share. */
private fun Scene.disc(x: Float, y: Float, r: Float, shader: android.graphics.Shader, shade: Int, alpha: Float, spread: Float, dx: Float, dy: Float) {
    roundShade(x, y, r * 2, r * 2, r, shade, alpha, spread, dx = dx, dy = dy)
    draw(circle(x, y, r), shader = shader)
}

/** Classic: clear morning blue. Overlapping discs, one warm sun. */
private val classic: Scene.() -> Unit = {
    wash(0f to c(0x8DB4FA), 0.45f to c(0x4F82EA), 1f to c(0x2A55C4))
    // The pale disc the sun sits on.
    disc(0.98f, 0.12f, 82f, linear(0.35f, 0f, 1.1f, 0.5f, c(0xD3E3FF), c(0x86AEF7)), c(0x16327F), 0.22f, 10f, -2f, 3f)
    disc(1.85f, 0.30f, 70f, linear(1.4f, 0.1f, 2.2f, 0.6f, c(0xB9D2FF), c(0x6F9BF2)), c(0x16327F), 0.22f, 10f, -2f, 3f)
    disc(0.64f, 0.235f, 13f, linear(0.52f, 0.18f, 0.76f, 0.29f, c(0xFFDCC4), c(0xFF9E80)), c(0x1B3A8F), 0.30f, 5.5f, -1.2f, 2.4f)
    // The mid blue disc coming in from the left.
    disc(-0.02f, 0.44f, 66f, linear(0f, 0.2f, 0.6f, 0.7f, c(0x3F74E8), c(0x2752C8)), c(0x0F2670), 0.34f, 10f, 2f, -1f)
    // The deep arc the search bar stands on.
    disc(0.62f, 1.16f, 128f, linear(0.2f, 0.58f, 0.9f, 1f, c(0x1F47B8), c(0x122E8A)), c(0x0A1B57), 0.38f, 11f, 0f, -3f)
    disc(2.35f, 1.2f, 120f, linear(1.8f, 0.6f, 2.6f, 1f, c(0x2550C4), c(0x153394)), c(0x0A1B57), 0.38f, 11f, 0f, -3f)
}

/** Tonal Ring: one green, ring inside ring, lightest at the heart. */
private val ring: Scene.() -> Unit = {
    val x = 0.80f
    val y = 0.245f
    val radii = floatArrayOf(330f, 262f, 206f, 162f, 126f, 96f, 70f, 47f, 28f, 13f)
    // Mid greens where the search bar sits, so its rim reads in the light theme and the dark.
    val tones = intArrayOf(0x123F2C, 0x1B5238, 0x286846, 0x3A7F56, 0x4F9668, 0x68AB7C, 0x86BF92, 0xA6D2AA, 0xC6E4C3, 0xE6F5DB)
    for (i in radii.indices) {
        // Each ring is drawn as a ring: the middle is left for the smaller ones, so nothing is painted twice.
        val band = circle(x, y, radii[i])
        if (i < radii.lastIndex) {
            band.fillType = Path.FillType.EVEN_ODD
            band.addCircle(x * w, y * h, (radii[i + 1] - 5f) * u, Path.Direction.CW)
        }
        if (i > 0) roundShade(x, y, radii[i] * 2, radii[i] * 2, radii[i], c(0x062A1C), 0.30f, 4.5f + radii[i] * 0.024f, dx = -0.8f, dy = 1.6f)
        val lit = Scene.mix(c(tones[i]), c(0xFFFFFF), 0.07f)
        val low = Scene.mix(c(tones[i]), c(0x000000), 0.06f)
        draw(band, shader = linear(x - 0.5f, y - 0.22f, x + 0.3f, y + 0.25f, lit, low))
    }
}

/** Soft Square: warm clay, rounded squares turning as they open out. */
private val square: Scene.() -> Unit = {
    val x = 0.70f
    val y = 0.255f
    val sides = floatArrayOf(640f, 500f, 392f, 306f, 238f, 182f, 136f, 97f, 64f, 36f)
    val tones = intArrayOf(0x4A2015, 0x5F2A1B, 0x793823, 0x95482D, 0xB05C3B, 0xC8744E, 0xDB8F68, 0xEAAB87, 0xF4C8A8, 0xFBE3CD)
    fun turn(i: Int) = 9f + i * 6.5f
    for (i in sides.indices) {
        val tile = roundRect(x, y, sides[i], sides[i], sides[i] * 0.24f, turn = turn(i))
        if (i < sides.lastIndex) {
            // The middle is left for the smaller tiles (each lies wholly inside the one before it).
            val next = sides[i + 1] - 10f
            tile.fillType = Path.FillType.EVEN_ODD
            tile.addPath(roundRect(x, y, next, next, next * 0.24f, turn = turn(i + 1)))
        }
        if (i > 0) roundShade(x, y, sides[i], sides[i], sides[i] * 0.24f, c(0x2A0F08), 0.32f, 4.5f + sides[i] * 0.014f, turn = turn(i), dx = -0.8f, dy = 1.8f)
        val lit = Scene.mix(c(tones[i]), c(0xFFFFFF), 0.07f)
        val low = Scene.mix(c(tones[i]), c(0x000000), 0.07f)
        draw(tile, shader = linear(x - 0.5f, y - 0.22f, x + 0.3f, y + 0.25f, lit, low))
    }
}

/** Bold: flat primaries, big and sure of themselves. Red leads, on the screen and off it. */
private val bold: Scene.() -> Unit = {
    wash(0f to c(0xF0463A), 1f to c(0xDC3038))
    draw(circle(1.02f, -0.01f, 40f), c(0xFFF1DC))
    val wine = c(0x6E0F1C)
    disc(0.17f, 0.20f, 35f, linear(0f, 0.05f, 0.45f, 0.36f, c(0x2C52F2), c(0x1C3BCB)), wine, 0.36f, 8f, 1.5f, 3f)
    roundShade(0.79f, 0.37f, 27f, 66f, 13.5f, wine, 0.36f, 8f, turn = 24f, dx = 1.5f, dy = 3f)
    draw(roundRect(0.79f, 0.37f, 27f, 66f, 13.5f, turn = 24f), shader = linear(0.6f, 0.22f, 1f, 0.5f, c(0xFFD23F), c(0xFFB81F)))
    disc(0.44f, 0.47f, 6f, linear(0.38f, 0.44f, 0.5f, 0.5f, c(0xFFF6E8), c(0xFFEBD0)), wine, 0.34f, 4.5f, 1f, 2f)
    // Past the screen's edge, for the home screen to slide to.
    disc(1.72f, 0.40f, 30f, linear(1.5f, 0.25f, 2f, 0.55f, c(0x2C52F2), c(0x1C3BCB)), wine, 0.36f, 8f, 1.5f, 3f)
    roundShade(1.36f, 0.17f, 24f, 60f, 12f, wine, 0.36f, 8f, turn = -30f, dx = 1.5f, dy = 3f)
    draw(roundRect(1.36f, 0.17f, 24f, 60f, 12f, turn = -30f), shader = linear(1.2f, 0f, 1.6f, 0.3f, c(0xFFD23F), c(0xFFB81F)))
    // The dark ground the search bar stands on: nearly black, so it never outweighs the red.
    disc(0.42f, 1.205f, 124f, linear(0f, 0.62f, 0.8f, 1f, c(0x231A26), c(0x140E17)), c(0x5A0A18), 0.42f, 10f, 0f, -3f)
    disc(2.3f, 1.25f, 126f, linear(1.8f, 0.66f, 2.6f, 1f, c(0x231A26), c(0x140E17)), c(0x5A0A18), 0.42f, 10f, 0f, -3f)
}

/* ── Light fields ── */

/** Wallpaper Glass: an aurora of teal and violet, so a see-through bar has color moving behind it. */
private val glass: Scene.() -> Unit = {
    wash(0f to c(0x0A3550), 0.5f to c(0x0C5C6C), 1f to c(0x0B4A64))
    glow(0.10f, 0.20f, 95f, c(0x1FE0C6), 0.90f)
    glow(1.00f, 0.30f, 90f, c(0x8266FF), 0.90f)
    glow(0.55f, 0.02f, 70f, c(0x2C9BFF), 0.65f)
    glow(0.30f, 0.50f, 80f, c(0x16C2AE), 0.60f)
    glow(0.95f, 0.64f, 85f, c(0x6A58F0), 0.65f)
    glow(1.55f, 0.18f, 95f, c(0x1FE0C6), 0.75f)
    glow(2.05f, 0.42f, 90f, c(0x8266FF), 0.75f)
    // Color carries on down the screen, so the see-through bar always has some behind it.
    glow(0.12f, 0.80f, 95f, c(0x19C9B6), 0.70f)
    glow(0.80f, 0.96f, 90f, c(0x3E8CF5), 0.60f)
    glow(1.60f, 0.84f, 95f, c(0x19C9B6), 0.60f)
    // Ribbons of light crossing the field: wide and faint, with a bright thread through them.
    ribbon(wave(0.20f, 0.20f, 0.045f, 0.55f, 0.10f), 30f, c(0xB8FFF0), 0.26f)
    ribbon(wave(0.33f, 0.16f, 0.050f, 0.48f, 0.42f), 23f, c(0xC9B8FF), 0.26f)
    ribbon(wave(0.27f, 0.18f, 0.040f, 0.60f, 0.25f), 6.5f, c(0xFFFFFF), 0.22f)
    ribbon(wave(0.60f, 0.12f, 0.045f, 0.42f, 0.70f), 38f, c(0x8FF5E6), 0.18f)
    ribbon(wave(0.80f, 0.08f, 0.035f, 0.50f, 0.05f), 29f, c(0xB9A8FF), 0.18f)
    ribbon(wave(0.70f, 0.10f, 0.040f, 0.46f, 0.82f), 6f, c(0xFFFFFF), 0.15f)
}

/** Pastel: lilac air with soft bubbles of peach and mint. */
private val pastel: Scene.() -> Unit = {
    wash(0f to c(0xF7DCE6), 0.38f to c(0xE2CFF3), 0.72f to c(0xC6B5EA), 1f to c(0xB3A0E0))
    glow(0.90f, 0.10f, 75f, c(0xFFD2B4), 0.9f)
    glow(0.05f, 0.28f, 70f, c(0xC3F0DE), 0.8f)
    glow(0.62f, 0.44f, 60f, c(0xC4DBFF), 0.55f)
    glow(0.22f, 0.04f, 55f, c(0xFFC2DE), 0.7f)
    glow(1.60f, 0.22f, 80f, c(0xC3F0DE), 0.7f)
    glow(2.10f, 0.10f, 70f, c(0xFFD2B4), 0.8f)
    fun bubble(x: Float, y: Float, r: Float, tint: Int) {
        val disc = circle(x, y, r)
        // A bubble is see-through, so its shade is a whole blurred disc (small enough to be quick) and not just an edge.
        draw(disc, c(0x7A5FB8), alpha = 0.14f, blur = r * 0.22f, dy = r * 0.10f)
        draw(disc, shader = radial(x - r * 0.0035f, y - r * 0.0016f, r * 1.25f, alpha(tint, 0.62f), alpha(tint, 0.30f), alpha(tint, 0.16f)))
        draw(disc, c(0xFFFFFF), alpha = 0.55f, stroke = 0.28f)
    }
    bubble(0.70f, 0.215f, 26f, c(0xFFFFFF))
    bubble(0.26f, 0.150f, 15f, c(0xFFE9F3))
    bubble(0.40f, 0.365f, 19f, c(0xF2ECFF))
    bubble(0.90f, 0.405f, 10f, c(0xFFF3E6))
    bubble(0.13f, 0.455f, 7f, c(0xFFFFFF))
    bubble(1.30f, 0.30f, 22f, c(0xFFFFFF))
    bubble(1.75f, 0.17f, 14f, c(0xFFE9F3))
    bubble(2.00f, 0.38f, 18f, c(0xF2ECFF))
}

/** Neon: deep night, one ring of violet light and its glow on the floor. */
private val neon: Scene.() -> Unit = {
    wash(0f to c(0x07041A), 0.45f to c(0x0B0620), 1f to c(0x04020C))
    fun halo(x: Float, y: Float, r: Float, strength: Float) {
        glow(x, y, r * 3.4f, c(0x5B1FE0), 0.42f * strength)
        glow(x, y, r * 2.1f, c(0x8A2BFF), 0.40f * strength)
        val tube = circle(x, y, r)
        // The dark inside the ring, a touch deeper than the night around it.
        draw(circle(x, y, r * 0.985f), shader = radial(x, y, r, c(0x05020F), c(0x05020F), alpha(c(0x05020F), 0.82f)))
        val hue = sweep(x, y, c(0xB05CFF), c(0xFF5CD6), c(0xC86BFF), c(0x7B6BFF), c(0xB05CFF))
        draw(tube, shader = hue, alpha = 0.55f * strength, stroke = r * 0.30f, blur = r * 0.22f)
        draw(tube, shader = hue, alpha = 0.95f * strength, stroke = r * 0.085f, blur = r * 0.045f)
        draw(tube, c(0xFBEBFF), alpha = 0.95f * strength, stroke = r * 0.028f, blur = r * 0.008f)
    }
    halo(0.5f, 0.285f, 33f, 1f)
    halo(1.72f, 0.36f, 20f, 0.8f)
    // Its light on the floor.
    glow(0.5f, 0.62f, 78f, c(0x7A2BFF), 0.30f, squash = 0.22f)
    glow(0.5f, 0.62f, 34f, c(0xD08BFF), 0.22f, squash = 0.10f)
    glow(1.72f, 0.62f, 50f, c(0x7A2BFF), 0.22f, squash = 0.22f)
}

/* ── Paper and folds ── */

/** Paper: warm white sheets laid over one another, and one round cutout. */
private val paper: Scene.() -> Unit = {
    wash(0f to c(0xF4ECDF), 1f to c(0xF1E7D8))
    val umber = c(0x6B5130)
    disc(0.72f, 0.20f, 15f, linear(0.55f, 0.13f, 0.9f, 0.27f, c(0xFFFDF8), c(0xF8F0E2)), umber, 0.20f, 5f, -0.8f, 1.6f)
    disc(1.78f, 0.15f, 11f, linear(1.6f, 0.1f, 1.95f, 0.2f, c(0xFFFDF8), c(0xF8F0E2)), umber, 0.20f, 5f, -0.8f, 1.6f)
    val heights = floatArrayOf(0.285f, 0.365f, 0.445f, 0.535f, 0.635f, 0.755f)
    val tilts = floatArrayOf(0.10f, -0.09f, 0.07f, -0.06f, 0.05f, -0.03f)
    // The lower sheets are a shade deeper, so a white bar has an edge against them.
    val sheets = intArrayOf(0xEFE6D6, 0xF6EFE3, 0xE9DFCD, 0xEEE4D3, 0xE2D6C2, 0xDACDB7)
    for (i in heights.indices) {
        val sheet = ridge(heights[i], 0.022f, 300 + i, stretch = 2.4f, rough = 0.25f, tilt = tilts[i])
        ridgeShade(sheet, umber, 0.115f, 4.6f)
        val tone = c(sheets[i])
        draw(sheet.path, shader = vertical(heights[i] - 0.06f, heights[i] + 0.3f, Scene.mix(tone, c(0xFFFFFF), 0.25f), tone))
    }
}

/** Mono: dunes at night under a small moon, in grays only. */
private val mono: Scene.() -> Unit = {
    wash(0f to c(0x1B1B1B), 0.22f to c(0x2B2B2B), 0.34f to c(0x3A3A3A), 1f to c(0x0A0A0A))
    glow(0.70f, 0.135f, 60f, c(0xFFFFFF), 0.16f)
    val moon = circle(0.70f, 0.135f, 8.5f)
    draw(moon, shader = linear(0.6f, 0.1f, 0.8f, 0.17f, c(0xF4F4F4), c(0xC9C9C9)))
    glow(1.85f, 0.2f, 60f, c(0xFFFFFF), 0.08f)
    val heights = floatArrayOf(0.30f, 0.37f, 0.455f, 0.55f, 0.66f, 0.79f)
    val crests = intArrayOf(0x9C9C9C, 0x808080, 0x676767, 0x505050, 0x3A3A3A, 0x282828)
    val lows = intArrayOf(0x3C3C3C, 0x2E2E2E, 0x222222, 0x181818, 0x101010, 0x090909)
    for (i in heights.indices) {
        val dune = ridge(heights[i], 0.030f, 410 + i, stretch = 1.5f + i * 0.12f, rough = 0.3f, tilt = if (i % 2 == 0) 0.05f else -0.06f)
        ridgeShade(dune, c(0x000000), 0.30f, 5.4f)
        draw(dune.path, shader = vertical(heights[i] - 0.035f, heights[i] + 0.13f, c(crests[i]), Scene.mix(c(crests[i]), c(lows[i]), 0.6f), c(lows[i])))
    }
}

/* ── Landscapes ── */

/** Ink: an ink-wash range in violet ink on bright paper, with a red sun. */
private val ink: Scene.() -> Unit = {
    wash(0f to c(0xF4F2F6), 0.35f to c(0xFAF7F0), 1f to c(0xFBF8F2))
    glow(0.70f, 0.185f, 40f, c(0xFFD3C0), 0.55f)
    draw(circle(0.70f, 0.185f, 7.4f), shader = linear(0.62f, 0.155f, 0.78f, 0.215f, c(0xF25C3E), c(0xDB3E2B)))
    glow(1.9f, 0.16f, 30f, c(0xFFD3C0), 0.4f)
    // Three birds crossing the sun's side of the sky.
    fun bird(x: Float, y: Float, size: Float) {
        val wings = Path().apply {
            moveTo(x * w - size * u, y * h - size * 0.25f * u)
            quadTo(x * w - size * 0.45f * u, y * h - size * 0.62f * u, x * w, y * h)
            quadTo(x * w + size * 0.5f * u, y * h - size * 0.7f * u, x * w + size * 1.1f * u, y * h - size * 0.38f * u)
        }
        draw(wings, c(0x3A2466), alpha = 0.85f, stroke = size * 0.17f)
    }
    bird(0.33f, 0.245f, 1.9f)
    bird(0.42f, 0.272f, 1.5f)
    bird(0.26f, 0.283f, 1.2f)
    bird(1.4f, 0.22f, 1.7f)
    val paperTone = c(0xFBF8F2)
    val heights = floatArrayOf(0.415f, 0.475f, 0.55f, 0.64f)
    val amps = floatArrayOf(0.060f, 0.070f, 0.078f, 0.062f)
    val inks = intArrayOf(0xD5CCE6, 0xAE9ED2, 0x7458AC, 0x3A2270)
    for (i in heights.indices) {
        val range = ridge(heights[i], amps[i], 517 + i * 7, stretch = 0.95f + i * 0.14f, rough = 0.40f, peaks = true)
        // Each range is dark at its crest and thins into mist, as a brush runs out of ink.
        draw(
            range.path,
            shader = vertical(
                heights[i] - amps[i], heights[i] + 0.15f + i * 0.035f,
                c(inks[i]), Scene.mix(c(inks[i]), paperTone, 0.40f), Scene.mix(c(inks[i]), paperTone, 0.74f),
                Scene.mix(c(inks[i]), paperTone, 0.93f), paperTone
            )
        )
    }
}

/**
 * Bare: still water and a far shore in dusky rose, with nothing to compete with icons that have no bar. Its icons are
 * light in the dark theme and dark in the light one, so it comes as dusk for the first and as first light for the second.
 */
private fun Scene.stillWater(sky: Array<Pair<Float, Int>>, sun: Int, sunAlpha: Float, shoreTop: Int, shoreLow: Int, glint: Int, glintAlpha: Float) {
    wash(*sky)
    glow(0.60f, 0.462f, 80f, sun, sunAlpha, squash = 0.17f)
    glow(1.80f, 0.462f, 80f, sun, sunAlpha * 0.8f, squash = 0.17f)
    // The far shore, and what the water keeps of it.
    val shore = ridge(0.462f, 0.010f, 610, stretch = 0.9f, rough = 0.5f)
    within(Path().apply { addRect(0f, 0f, full, 0.4655f * h, Path.Direction.CW) }) {
        draw(shore.path, shader = vertical(0.44f, 0.47f, shoreTop, shoreLow))
    }
    glow(0.5f, 0.468f, 120f, shoreLow, 0.32f, squash = 0.035f)
    glow(1.7f, 0.468f, 120f, shoreLow, 0.32f, squash = 0.035f)
    // One line of light on the water.
    glow(0.60f, 0.484f, 42f, glint, glintAlpha, squash = 0.03f)
}

private val bare: Scene.() -> Unit = {
    stillWater(
        arrayOf(0f to c(0x1F1826), 0.30f to c(0x30243A), 0.455f to c(0x6B4A5C), 0.468f to c(0x45303F), 0.62f to c(0x2B2030), 1f to c(0x1C1622)),
        sun = c(0xF0B49C), sunAlpha = 0.50f, shoreTop = c(0x17111C), shoreLow = c(0x231A28), glint = c(0xF7CDBA), glintAlpha = 0.20f
    )
}

private val bareLight: Scene.() -> Unit = {
    stillWater(
        arrayOf(0f to c(0xECE1EA), 0.30f to c(0xF3E9EE), 0.455f to c(0xFDE6DA), 0.468f to c(0xF2E4E8), 0.62f to c(0xEEE3EA), 1f to c(0xE6DBE5)),
        sun = c(0xFFD2BC), sunAlpha = 0.75f, shoreTop = c(0xC2A9BC), shoreLow = c(0xD3BFCE), glint = c(0xFFFFFF), glintAlpha = 0.55f
    )
}

/** Sunset: the sun going down behind dark ridges, the sky plum to gold. */
private val sunset: Scene.() -> Unit = {
    wash(0f to c(0x2B1250), 0.20f to c(0x6E1F66), 0.36f to c(0xC43B5C), 0.48f to c(0xF2793F), 0.575f to c(0xFFC25A), 1f to c(0xFFC25A))
    // Thin cloud, lit from below.
    glow(0.25f, 0.33f, 60f, c(0xFF8E6B), 0.38f, squash = 0.06f)
    glow(0.80f, 0.27f, 70f, c(0xE0558A), 0.34f, squash = 0.05f)
    glow(0.55f, 0.41f, 80f, c(0xFFB067), 0.34f, squash = 0.045f)
    glow(1.60f, 0.31f, 70f, c(0xFF8E6B), 0.34f, squash = 0.06f)
    glow(0.58f, 0.555f, 85f, c(0xFFE28A), 0.75f)
    draw(circle(0.58f, 0.555f, 14f), shader = vertical(0.50f, 0.60f, c(0xFFFBE0), c(0xFFE08A)))
    val heights = floatArrayOf(0.578f, 0.615f, 0.665f, 0.735f, 0.83f)
    val crests = intArrayOf(0xB04A5E, 0x80305A, 0x57204E, 0x381440, 0x220B2E)
    val lows = intArrayOf(0x8A3558, 0x5F2250, 0x3E1643, 0x270D33, 0x150620)
    for (i in heights.indices) {
        val hill = ridge(heights[i], 0.016f + i * 0.007f, 710 + i, stretch = 0.75f + i * 0.2f, rough = 0.5f, tilt = if (i % 2 == 0) -0.02f else 0.03f)
        draw(hill.path, shader = vertical(heights[i] - 0.03f, heights[i] + 0.16f, c(crests[i]), c(lows[i])))
    }
}

/** What changes in the valley from season to season. */
private class Valley(
    val sky: Array<Pair<Float, Int>>,
    val sunX: Float, val sunY: Float, val sunSize: Float, val sun: Int, val light: Int, val lightSize: Float, val lightAlpha: Float,
    val peakTop: Int, val peakLow: Int,
    /** Five hills, far to near: the color at the crest, then the color down in the fold before the next hill. */
    val hills: IntArray,
    val furrow: Int?, val furrowAlpha: Float,
    /** Tree crowns: shade, body, highlight. */
    val crown: IntArray, val trunk: Int,
    val haze: Int, val hazeAlpha: Float,
    val extras: Scene.() -> Unit = {}
)

/** One valley, the same hills and the same trees, in a season's light. */
private fun Scene.valley(s: Valley) {
    wash(*s.sky)
    glow(s.sunX, s.sunY, s.lightSize, s.light, s.lightAlpha)
    if (s.sunSize > 0f) draw(circle(s.sunX, s.sunY, s.sunSize), s.sun)
    val peaks = ridge(0.437f, 0.042f, 13, stretch = 0.85f, rough = 0.42f, peaks = true)
    draw(peaks.path, shader = vertical(0.385f, 0.49f, s.peakTop, s.peakLow))
    // Haze where the hills begin.
    glow(0.5f, 0.478f, 170f, s.haze, s.hazeAlpha, squash = 0.10f)
    glow(1.75f, 0.478f, 170f, s.haze, s.hazeAlpha, squash = 0.10f)
    val lines = listOf(
        ridge(0.482f, 0.013f, 21, stretch = 0.9f),
        ridge(0.530f, 0.021f, 22, stretch = 0.8f, tilt = -0.03f),
        ridge(0.602f, 0.027f, 23, stretch = 0.95f, tilt = 0.05f),
        ridge(0.700f, 0.031f, 24, stretch = 1.2f, tilt = -0.04f),
        ridge(0.818f, 0.029f, 25, stretch = 1.45f, tilt = 0.03f)
    )
    val tops = floatArrayOf(0.465f, 0.505f, 0.57f, 0.66f, 0.785f)
    val folds = floatArrayOf(0.535f, 0.61f, 0.71f, 0.83f, 1.0f)
    // Trees stand on the second, third and fourth hills: x, then size.
    val stands = listOf(
        floatArrayOf(0.13f, 1.5f, 0.19f, 2.0f, 0.24f, 1.4f, 0.74f, 1.7f, 0.80f, 2.3f, 0.86f, 1.5f, 1.22f, 1.8f, 1.30f, 1.4f, 1.78f, 2.1f, 2.05f, 1.6f),
        floatArrayOf(0.36f, 3.0f, 0.44f, 2.3f, 0.93f, 3.2f, 1.48f, 2.8f, 1.56f, 2.2f, 2.12f, 3.0f),
        floatArrayOf(0.07f, 4.4f, 0.62f, 3.6f, 1.12f, 4.2f, 1.86f, 4.0f)
    )
    for (i in lines.indices) {
        val hill = lines[i]
        draw(hill.path, shader = vertical(tops[i], folds[i], s.hills[i * 2], s.hills[i * 2 + 1]))
        if (s.furrow != null && (i == 1 || i == 2)) furrows(hill, if (i == 1) 9 else 8, s.furrow, s.furrowAlpha, first = if (i == 1) 0.9f else 1.3f)
        if (i in 1..3) {
            val stand = stands[i - 1]
            for (k in stand.indices step 2) tree(hill, stand[k], stand[k + 1], s.crown, s.trunk)
        }
    }
    s.extras(this)
}

/** A round tree standing on [hill] at x: a trunk and a crown of three discs, lit from the upper left. */
private fun Scene.tree(hill: Ridge, x: Float, size: Float, crown: IntArray, trunk: Int) {
    val px = x * w
    val foot = hill.y(px) + size * 0.25f * u
    val cy = foot - size * 1.75f * u
    val stem = Path().apply { moveTo(px, foot); lineTo(px, cy) }
    draw(stem, trunk, stroke = size * 0.24f)
    fun disc(dx: Float, dy: Float, r: Float) = Path().apply { addCircle(px + dx * size * u, cy + dy * size * u, r * size * u, Path.Direction.CW) }
    draw(disc(0.55f, 0.32f, 0.72f), crown[0])
    draw(disc(-0.60f, 0.28f, 0.70f), crown[1])
    draw(disc(0f, -0.10f, 1.0f), crown[1])
    draw(disc(-0.26f, -0.34f, 0.56f), crown[2])
}

/** Harvest: the valley in fall. Cream sky, fields of burnt orange, a low sun. */
private val harvest: Scene.() -> Unit = {
    valley(
        Valley(
            sky = arrayOf(0f to c(0xFFF1DC), 0.30f to c(0xFFE7C4), 0.50f to c(0xFBD29A)),
            sunX = 0.26f, sunY = 0.335f, sunSize = 10f, sun = c(0xFFF9EA), light = c(0xFFDB8E), lightSize = 70f, lightAlpha = 0.85f,
            peakTop = c(0xE8C49A), peakLow = c(0xE3B279),
            hills = intArrayOf(
                c(0xE3AE66), c(0xEBC489), c(0xD98636), c(0xE2A05A), c(0xB8531C), c(0xC96F33), c(0x82361A), c(0x9A4A26), c(0x55210F), c(0x33130A)
            ),
            furrow = c(0xFFD9A0), furrowAlpha = 0.30f,
            crown = intArrayOf(c(0xA8421A), c(0xD9692A), c(0xF2A545)), trunk = c(0x4A1E0E),
            haze = c(0xFFE9C4), hazeAlpha = 0.34f
        )
    )
}

/** First Frost: the valley on a winter dawn. Snow in blue shadow, the first pink on the peaks. */
private val frost: Scene.() -> Unit = {
    valley(
        Valley(
            sky = arrayOf(0f to c(0xA9BFEA), 0.24f to c(0xCDD6F2), 0.42f to c(0xF4DCE2), 0.52f to c(0xFBE8DB)),
            sunX = 0.70f, sunY = 0.465f, sunSize = 0f, sun = 0, light = c(0xFFDCC6), lightSize = 80f, lightAlpha = 0.8f,
            peakTop = c(0xF6DDE2), peakLow = c(0xB9C8EA),
            hills = intArrayOf(
                c(0xDCE5F6), c(0xEEF2FA), c(0xC3D2EF), c(0xDAE3F5), c(0xA2B8E4), c(0xBFCFEE), c(0x7F9AD4), c(0x9DB3E0), c(0x5E7DC0), c(0x42609F)
            ),
            furrow = null, furrowAlpha = 0f,
            crown = intArrayOf(c(0xA9BFE6), c(0xDCE7F8), c(0xFFFFFF)), trunk = c(0x3F5594),
            haze = c(0xFFFFFF), hazeAlpha = 0.42f,
            extras = {
                // The moon, still up.
                val moon = circle(0.25f, 0.205f, 5.2f)
                moon.op(circle(0.25f, 0.205f, 5.2f).apply { offset(2.3f * u, -1.2f * u) }, Path.Op.DIFFERENCE)
                draw(moon, c(0xFFFFFF), alpha = 0.92f)
            }
        )
    )
}

/** Bloom: the valley in spring. New green, and every tree in blossom. */
private val bloom: Scene.() -> Unit = {
    valley(
        Valley(
            sky = arrayOf(0f to c(0xC4DFF6), 0.28f to c(0xE9E2F2), 0.46f to c(0xFFE2EA), 0.52f to c(0xFFEDE6)),
            sunX = 0.74f, sunY = 0.30f, sunSize = 0f, sun = 0, light = c(0xFFF7E8), lightSize = 75f, lightAlpha = 0.85f,
            peakTop = c(0xD9DDF0), peakLow = c(0xC6DEC8),
            hills = intArrayOf(
                c(0xBFE3B0), c(0xD6EECB), c(0x93CC84), c(0xB2DBA4), c(0x68AD60), c(0x86C17C), c(0x44884A), c(0x5C9E5E), c(0x2B6537), c(0x184427)
            ),
            furrow = c(0xD8F2C4), furrowAlpha = 0.22f,
            crown = intArrayOf(c(0xF58FB0), c(0xFFB9CF), c(0xFFE3EC)), trunk = c(0x5A3A30),
            haze = c(0xFFFFFF), hazeAlpha = 0.38f,
            extras = {
                // Petals let go on the wind.
                val spots = floatArrayOf(
                    0.16f, 0.36f, 30f, 0.31f, 0.41f, -20f, 0.52f, 0.33f, 55f, 0.66f, 0.43f, 10f, 0.83f, 0.37f, -40f, 0.42f, 0.25f, 25f,
                    0.92f, 0.22f, 70f, 0.08f, 0.21f, -60f, 0.24f, 0.49f, 40f, 0.59f, 0.50f, -15f, 1.3f, 0.34f, 30f, 1.62f, 0.42f, -35f, 1.95f, 0.29f, 50f
                )
                for (k in spots.indices step 3) {
                    val size = 0.75f + (k % 4) * 0.16f
                    draw(oval(spots[k], spots[k + 1], size, size * 0.52f, spots[k + 2]), c(if (k % 2 == 0) 0xFFC4D6 else 0xFFFFFF), alpha = 0.92f)
                }
            }
        )
    )
}

/** High Summer: the valley at midday. A high sun, fields of gold, deep green below. */
private val summer: Scene.() -> Unit = {
    valley(
        Valley(
            sky = arrayOf(0f to c(0x2A86DC), 0.26f to c(0x5EACEE), 0.44f to c(0xAEDAF6), 0.52f to c(0xE6F4F6)),
            sunX = 0.70f, sunY = 0.175f, sunSize = 8.5f, sun = c(0xFFFCE6), light = c(0xFFF0A0), lightSize = 62f, lightAlpha = 0.8f,
            peakTop = c(0xA9C8EC), peakLow = c(0xB9DCC8),
            hills = intArrayOf(
                c(0xA9D380), c(0xC3E0A0), c(0xF7D650), c(0xFBE384), c(0xECA91F), c(0xF3C24A), c(0x4A963B), c(0x63AC52), c(0x2B682D), c(0x174521)
            ),
            furrow = c(0xFFF2A8), furrowAlpha = 0.36f,
            crown = intArrayOf(c(0x1F5A2A), c(0x2F7A35), c(0x55A04A)), trunk = c(0x3A2A18),
            haze = c(0xFFFFFF), hazeAlpha = 0.28f,
            extras = {
                // Fair-weather cloud: flat underneath, heaped above.
                fun cloud(x: Float, y: Float, size: Float) {
                    val puffs = floatArrayOf(-1.5f, 0f, 0.8f, -0.6f, -0.55f, 1.05f, 0.5f, -0.7f, 1.15f, 1.5f, -0.1f, 0.85f)
                    val body = Path()
                    for (k in puffs.indices step 3) {
                        body.addCircle(x * w + puffs[k] * size * u, y * h + puffs[k + 1] * size * u, puffs[k + 2] * size * u, Path.Direction.CW)
                    }
                    val keep = Path().apply { addRect(-full, 0f, full * 2, y * h + size * 0.42f * u, Path.Direction.CW) }
                    within(keep) { draw(body, shader = vertical(y - 0.02f, y + 0.012f, c(0xFFFFFF), c(0xE6F1FA))) }
                }
                cloud(0.22f, 0.300f, 5.2f)
                cloud(0.88f, 0.385f, 3.6f)
                cloud(1.45f, 0.26f, 5.8f)
                cloud(1.98f, 0.36f, 4.0f)
            }
        )
    )
}

/** Every built-in look's wallpaper, by look id. */
internal val WALLPAPERS: Map<String, Wallpaper> = listOf(
    Wallpaper("classic", 0.30f, c(0x5072C9), c(0x3768DD), classic),
    Wallpaper("glass", 0.30f, c(0x2C82A4), c(0x219DA7), glass),
    Wallpaper("ring", 0.30f, c(0x6EA47D), c(0x67A579), ring),
    Wallpaper("square", 0.30f, c(0xBF7F61), c(0xC17450), square),
    Wallpaper("ink", 0.46f, c(0xEAE4E6), c(0x523E80), ink),
    Wallpaper("paper", 0.30f, c(0xEBE2D3), c(0xF5EEE2), paper),
    Wallpaper("bare", 0.51f, c(0x302333), c(0x402E43), bare),
    Wallpaper("bare.light", 0.51f, c(0xEEE2E7), c(0xCDB7C8), bareLight),
    Wallpaper("bold", 0.30f, c(0x88424D), c(0xE23839), bold),
    Wallpaper("mono", 0.30f, c(0x303030), c(0x4285F4), mono),
    Wallpaper("neon", 0.30f, c(0x1A0D34), c(0x8555DC), neon),
    Wallpaper("sunset", 0.56f, c(0x763851), c(0x4C185B), sunset),
    Wallpaper("pastel", 0.26f, c(0xD3C3E6), c(0xB6A3E1), pastel),
    Wallpaper("harvest", 0.56f, c(0xC49770), c(0xE5AE6B), harvest),
    Wallpaper("frost", 0.56f, c(0xAFBBDD), c(0x728EC8), frost),
    Wallpaper("bloom", 0.56f, c(0xA0BBA5), c(0x45894B), bloom),
    Wallpaper("summer", 0.56f, c(0x7FA88F), c(0x4B973C), summer),
).plus(MORE_WALLPAPERS).associateBy { it.id }
