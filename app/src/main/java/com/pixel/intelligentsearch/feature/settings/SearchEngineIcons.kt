package com.pixel.intelligentsearch.feature.settings

import com.pixel.intelligentsearch.core.data.settings.get
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.geometry.Offset
import com.pixel.intelligentsearch.R
import android.content.Context
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.*
import androidx.compose.foundation.Image
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.*

// -----------------------------------------------------------------------------------------
// SEARCH APP COLORFUL ICON HELPERS
// -----------------------------------------------------------------------------------------
@Composable
fun GoogleOfficialAppIcon(modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val googleDrawable = remember(context) {
        runCatching {
            context.packageManager.getApplicationIcon("com.google.android.googlequicksearchbox")
        }.getOrNull()
    }
    if (googleDrawable != null) {
        val bitmap = remember(googleDrawable) {
            runCatching { googleDrawable.toBitmap(width = 96, height = 96).asImageBitmap() }.getOrNull()
        }
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = "Google", modifier = modifier.clip(RoundedCornerShape(6.dp)))
            return
        }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White)
            .border(0.5.dp, Color(0xFFE0E0E0), RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = androidx.compose.ui.res.painterResource(id = com.pixel.intelligentsearch.R.drawable.ic_g_logo_colored),
            contentDescription = "Google",
            modifier = Modifier.padding(2.dp),
            tint = Color.Unspecified
        )
    }
}

@Composable
fun DuckDuckGoOfficialAppIcon(modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val ddgDrawable = remember(context) {
        runCatching {
            context.packageManager.getApplicationIcon("com.duckduckgo.mobile.android")
        }.getOrNull()
    }
    if (ddgDrawable != null) {
        val bitmap = remember(ddgDrawable) {
            runCatching { ddgDrawable.toBitmap(width = 96, height = 96).asImageBitmap() }.getOrNull()
        }
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = "DuckDuckGo", modifier = modifier.clip(RoundedCornerShape(6.dp)))
            return
        }
    }
    Box(
        modifier = modifier
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(MaterialTheme.colorScheme.error),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(3.dp)) {
            val w = size.width
            val h = size.height
            val headPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.5f, h * 0.15f)
                cubicTo(w * 0.25f, h * 0.15f, w * 0.15f, h * 0.45f, w * 0.28f, h * 0.72f)
                cubicTo(w * 0.35f, h * 0.86f, w * 0.65f, h * 0.86f, w * 0.72f, h * 0.72f)
                cubicTo(w * 0.85f, h * 0.45f, w * 0.75f, h * 0.15f, w * 0.5f, h * 0.15f)
                close()
            }
            drawPath(headPath, Color.White)
            val beakPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.5f, h * 0.48f)
                lineTo(w * 0.78f, h * 0.55f)
                lineTo(w * 0.5f, h * 0.68f)
                close()
            }
            drawPath(beakPath, Color(0xFFF9A825))
            drawCircle(Color(0xFF212121), radius = w * 0.055f, center = Offset(w * 0.42f, h * 0.38f))
            drawCircle(Color.White, radius = w * 0.02f, center = Offset(w * 0.405f, h * 0.365f))
            val bowPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.36f, h * 0.82f)
                lineTo(w * 0.64f, h * 0.94f)
                lineTo(w * 0.64f, h * 0.82f)
                lineTo(w * 0.36f, h * 0.94f)
                close()
            }
            drawPath(bowPath, Color(0xFF4CAF50))
        }
    }
}

@Composable
fun BingOfficialAppIcon(modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bingDrawable = remember(context) {
        runCatching {
            context.packageManager.getApplicationIcon("com.microsoft.bing")
        }.getOrNull() ?: runCatching {
            context.packageManager.getApplicationIcon("com.microsoft.copilot")
        }.getOrNull()
    }
    if (bingDrawable != null) {
        val bitmap = remember(bingDrawable) {
            runCatching { bingDrawable.toBitmap(width = 96, height = 96).asImageBitmap() }.getOrNull()
        }
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = "Bing", modifier = modifier.clip(RoundedCornerShape(6.dp)))
            return
        }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = listOf(MaterialTheme.colorScheme.primary, Color(0xFF00A4EF))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
            val scale = size.minDimension / 24f
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(3.605f * scale, 0f)
                lineTo(8.4f * scale, 1.686f * scale)
                lineTo(8.4f * scale, 18.56f * scale)
                lineTo(15.153f * scale, 14.665f * scale)
                lineTo(11.843f * scale, 13.11f * scale)
                lineTo(9.753f * scale, 7.91f * scale)
                lineTo(20.393f * scale, 11.648f * scale)
                lineTo(20.393f * scale, 17.083f * scale)
                lineTo(8.403f * scale, 24f * scale)
                lineTo(3.605f * scale, 21.33f * scale)
                close()
            }
            drawPath(path, Color.White)
        }
    }
}

@Composable
fun TorOfficialAppIcon(modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val torDrawable = remember(context) {
        runCatching {
            context.packageManager.getApplicationIcon("org.torproject.torbrowser")
        }.getOrNull() ?: runCatching {
            context.packageManager.getApplicationIcon("org.torproject.torbrowser_alpha")
        }.getOrNull()
    }
    if (torDrawable != null) {
        val bitmap = remember(torDrawable) {
            runCatching { torDrawable.toBitmap(width = 96, height = 96).asImageBitmap() }.getOrNull()
        }
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = "Tor Project", modifier = modifier.clip(RoundedCornerShape(6.dp)))
            return
        }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF7D4698)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(3.dp)) {
            val w = size.width
            val h = size.height
            val cx = w * 0.5f
            val cy = h * 0.55f

            val stemPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(cx - w * 0.08f, cy - h * 0.35f)
                cubicTo(cx - w * 0.04f, cy - h * 0.48f, cx + w * 0.04f, cy - h * 0.48f, cx + w * 0.08f, cy - h * 0.35f)
                close()
            }
            drawPath(stemPath, Color(0xFFC594D8))

            drawCircle(
                color = Color.White.copy(alpha = 0.95f),
                radius = w * 0.34f,
                center = androidx.compose.ui.geometry.Offset(cx, cy),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.07f)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.85f),
                radius = w * 0.22f,
                center = androidx.compose.ui.geometry.Offset(cx, cy),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.06f)
            )
            drawCircle(
                color = Color.White,
                radius = w * 0.10f,
                center = androidx.compose.ui.geometry.Offset(cx, cy)
            )
        }
    }
}

@Composable
fun CustomSearchAppIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = "Custom Search",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(3.dp)
        )
    }
}

@Composable
fun SearchAppColorfulIcon(
    appName: String,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val normalized = appName.trim()

    when {
        normalized.equals("Google", ignoreCase = true) -> {
            GoogleOfficialAppIcon(modifier = modifier)
        }
        normalized.equals("DuckDuckGo", ignoreCase = true) -> {
            DuckDuckGoOfficialAppIcon(modifier = modifier)
        }
        normalized.equals("Bing", ignoreCase = true) -> {
            BingOfficialAppIcon(modifier = modifier)
        }
        normalized.contains("Tor", ignoreCase = true) -> {
            TorOfficialAppIcon(modifier = modifier)
        }
        normalized.equals("Custom", ignoreCase = true) || normalized.isBlank() -> {
            CustomSearchAppIcon(modifier = modifier)
        }
        else -> {
            // Matching a typed name means scanning installed apps: do it off the main thread, and only once
            // typing pauses (a new name cancels the pending lookup).
            val bitmap by produceState<ImageBitmap?>(initialValue = null, normalized) {
                delay(300)
                value = withContext(Dispatchers.IO) { resolveAppIconBitmap(context, normalized) }
            }
            bitmap?.let {
                Image(bitmap = it, contentDescription = appName, modifier = modifier.clip(RoundedCornerShape(6.dp)))
            } ?: CustomSearchAppIcon(modifier = modifier)
        }
    }
}

/** The icon of the installed app whose package or launcher label is [name], at 96 px. Call off the main thread. */
private fun resolveAppIconBitmap(context: Context, name: String): ImageBitmap? {
    val pm = context.packageManager
    val drawable = runCatching { pm.getApplicationIcon(name) }.getOrNull() ?: runCatching {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(launcherIntent, 0).firstOrNull {
            it.activityInfo.packageName.equals(name, ignoreCase = true) ||
                it.loadLabel(pm).toString().equals(name, ignoreCase = true)
        }?.loadIcon(pm)
    }.getOrNull() ?: return null
    return runCatching { drawable.toBitmap(width = 96, height = 96).asImageBitmap() }.getOrNull()
}
