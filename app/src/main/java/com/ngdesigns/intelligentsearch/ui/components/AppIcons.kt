package com.ngdesigns.intelligentsearch.ui.components

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.graphics.createBitmap
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.materialkolor.ktx.themeColorOrNull
import com.ngdesigns.intelligentsearch.data.device.Target
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A loaded app icon and the colour Material picks from it, for content-based colour. */
class LoadedIcon(val bitmap: ImageBitmap, val seed: Color?)

/**
 * App icons, loaded off the main thread and kept in a small memory cache. Work and Private Space apps keep their
 * profile badge. With a chosen [shape] the icon's own layers are drawn unmasked and clipped to it, rather than
 * re-masking an already masked icon.
 */
object AppIcons {
    private val cache = object : LruCache<String, LoadedIcon>(24 * 1024) {
        override fun sizeOf(key: String, value: LoadedIcon) = value.bitmap.width * value.bitmap.height * 4 / 1024
    }

    fun cached(target: Target, sizePx: Int, unmasked: Boolean): LoadedIcon? = cache.get("${keyOf(target)}:$sizePx:$unmasked")

    suspend fun load(context: Context, target: Target, sizePx: Int, unmasked: Boolean): LoadedIcon? = withContext(Dispatchers.IO) {
        val key = "${keyOf(target)}:$sizePx:$unmasked"
        cache.get(key)?.let { return@withContext it }
        val drawable = drawableFor(context, target) ?: return@withContext null
        val bitmap = render(drawable, sizePx, unmasked).asImageBitmap()
        LoadedIcon(bitmap, bitmap.themeColorOrNull()).also { cache.put(key, it) }
    }

    private fun keyOf(target: Target) = when (target) {
        is Target.App -> "${target.component.flattenToShortString()}@${target.user.hashCode()}"
        is Target.Shortcut -> "${target.packageName}/${target.shortcutId}@${target.user.hashCode()}"
        else -> target.toString()
    }

    private fun drawableFor(context: Context, target: Target): Drawable? {
        val apps = context.getSystemService(LauncherApps::class.java)
        val density = context.resources.displayMetrics.densityDpi
        return runCatching {
            when (target) {
                is Target.App -> apps.getActivityList(target.packageName, target.user)
                    .firstOrNull { it.componentName == target.component }?.getBadgedIcon(density)
                is Target.Shortcut -> {
                    val q = LauncherApps.ShortcutQuery().setPackage(target.packageName).setShortcutIds(listOf(target.shortcutId))
                        .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST)
                    apps.getShortcuts(q, target.user)?.firstOrNull()?.let { apps.getShortcutBadgedIconDrawable(it, density) }
                        ?: apps.getActivityList(target.packageName, target.user).firstOrNull()?.getBadgedIcon(density)
                }
                else -> null
            }
        }.getOrNull()
    }

    private fun render(drawable: Drawable, size: Int, unmasked: Boolean): Bitmap {
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        if (unmasked && drawable is AdaptiveIconDrawable) {
            // Adaptive layers are 108 dp with the visible 72 dp in the middle: scale so the middle fills the bitmap.
            val inset = (size * 0.25f).toInt()
            listOfNotNull(drawable.background, drawable.foreground).forEach {
                it.setBounds(-inset, -inset, size + inset, size + inset)
                it.draw(canvas)
            }
        } else {
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
        }
        return bitmap
    }
}

/** The Material shapes users can pick for icons, by name; "" keeps the system's own icon shape. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
object IconShapes {
    val options: List<Pair<String, RoundedPolygon>> by lazy {
        listOf(
            "Circle" to MaterialShapes.Circle, "Square" to MaterialShapes.Square, "Cookie" to MaterialShapes.Cookie9Sided,
            "Clover" to MaterialShapes.Clover4Leaf, "Sunny" to MaterialShapes.Sunny, "Gem" to MaterialShapes.Gem,
            "Pill" to MaterialShapes.Pill, "Arch" to MaterialShapes.Arch, "Flower" to MaterialShapes.Flower,
            "Puffy" to MaterialShapes.Puffy, "Soft burst" to MaterialShapes.SoftBurst, "Pentagon" to MaterialShapes.Pentagon,
        )
    }

    fun polygon(name: String): RoundedPolygon? = options.firstOrNull { it.first == name }?.second
}

/**
 * An app or shortcut icon at [size]. While it loads (or when there is none) a tonal placeholder in [shape] holds
 * the space, so rows never jump.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppIcon(target: Target, size: Dp, modifier: Modifier = Modifier, shapeName: String = "", onLoaded: (LoadedIcon) -> Unit = {}) {
    val context = LocalContext.current
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { size.roundToPx() }
    val polygon = remember(shapeName) { IconShapes.polygon(shapeName) }
    val shape: Shape? = polygon?.toShape()
    val icon by produceState(AppIcons.cached(target, px, polygon != null), target, px, polygon) {
        value = AppIcons.load(context, target, px, unmasked = polygon != null)?.also(onLoaded)
    }
    val clipped = if (shape != null) modifier.size(size).clip(shape) else modifier.size(size)
    val current = icon
    if (current != null) {
        Image(current.bitmap, contentDescription = null, modifier = clipped)
    } else {
        Box(clipped.background(MaterialTheme.colorScheme.surfaceContainerHighest, shape ?: MaterialShapes.Circle.toShape()))
    }
}

/** A person's initial in a tonal Material shape: the avatar for contacts without a photo. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Monogram(name: String, size: Dp, modifier: Modifier = Modifier, polygon: RoundedPolygon = MaterialShapes.Clover4Leaf) {
    val shape = polygon.toShape()
    Box(
        modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.tertiaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().firstOrNull()?.uppercase() ?: "?",
            style = if (size >= 56.dp) MaterialTheme.typography.headlineSmallEmphasized else MaterialTheme.typography.titleMediumEmphasized,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}
