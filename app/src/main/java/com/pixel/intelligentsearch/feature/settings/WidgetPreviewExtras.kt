package com.pixel.intelligentsearch.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixel.intelligentsearch.R
import com.pixel.intelligentsearch.core.system.MediaSessionAccess
import com.pixel.intelligentsearch.feature.widget.FAVOURITE_ICON_DP
import com.pixel.intelligentsearch.feature.widget.WidgetBarShape
import com.pixel.intelligentsearch.feature.widget.createFavouriteAppBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The live preview's favourite apps row, as the widget draws it above the bar: each app's icon, drawn by the widget's
 * own [createFavouriteAppBitmap] with the bar's [accent] and pill colour ([plate]), centred in its share of the row.
 */
@Composable
internal fun WidgetPreviewFavourites(apps: List<String>, accent: Color, plate: Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Row(modifier = modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        for (packageName in apps) {
            val icon by produceState<ImageBitmap?>(null, packageName, accent, plate) {
                value = withContext(Dispatchers.IO) {
                    createFavouriteAppBitmap(context, packageName, accent.toArgb(), plate.toArgb())?.asImageBitmap()
                }
            }
            Box(modifier = Modifier.weight(1f).height(48.dp), contentAlignment = Alignment.Center) {
                icon?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.size(FAVOURITE_ICON_DP.dp)) }
            }
        }
    }
}

/**
 * The live preview's media row, as the widget draws it above the bar: the pill's colour and shape, the music note and
 * the buttons in the bar's [accent], and the title in its plain icon colour ([titleColor]). It shows what's playing
 * when the app may see it, else a sample title.
 */
@Composable
internal fun WidgetPreviewMedia(shape: WidgetBarShape, fill: Color, titleColor: Color, accent: Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val playing by produceState<MediaSessionAccess.NowPlaying?>(null) {
        value = withContext(Dispatchers.IO) { runCatching { MediaSessionAccess.nowPlaying(context) }.getOrNull() }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(shape.pillHeight.dp)
            .background(fill, RoundedCornerShape(shape.pillRadius.dp))
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(R.drawable.ic_music), contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            text = playing?.line ?: "Song Title · Artist",
            color = titleColor,
            fontSize = 14.sp,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        // The widget's buttons are 48dp touch targets overlapping by 8dp, so their icons are 40dp apart.
        Box(Modifier.width(40.dp).height(48.dp).padding(start = 12.dp), contentAlignment = Alignment.CenterStart) {
            Icon(
                painterResource(if (playing?.isPlaying == false) R.drawable.ic_media_play else R.drawable.ic_media_pause),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(24.dp)
            )
        }
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_media_next), contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
        }
    }
}
