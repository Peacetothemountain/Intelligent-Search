package com.pixel.intelligentsearch.feature.search.today.cards.shape

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixel.intelligentsearch.feature.search.today.CardShape
import com.pixel.intelligentsearch.feature.search.today.cards.SiteData

/*
 * A website card wider than it is tall: the page's header image down the start side, its domain, title and
 * description beside it.
 *
 * NG Designs
 */

/** How much of a website box the header image takes. */
private const val IMAGE_SHARE = 0.44f

/**
 * Draws website card [data] as a box filling the size it is given, with the page's 24 dp corners: the header image
 * (a gradient of the site's accent when it has none) beside the domain, title and description. After a failed read it
 * keeps what it had, dimmed, and says when it was read. A [private] card is covered by stripes until [revealed]. The
 * caller sets the card's content description.
 */
@Composable
internal fun SiteCardBox(data: SiteData, modifier: Modifier = Modifier, private: Boolean = false, revealed: Boolean = false) {
    val colors = rememberShapedColors()
    val locale = LocalConfiguration.current.locales[0]
    val accent = Color(data.accent)
    val covered = private && !revealed
    val ago = rememberAgo(data.readAt)
    CoveredBox(covered, CardShape, modifier.clip(CardShape).background(colors.card)) {
        Row(
            Modifier
                .fillMaxSize()
                .clearAndSetSemantics { }
                .boxDrawIn(data.url, !covered)
        ) {
            val art = Modifier.weight(IMAGE_SHARE).fillMaxHeight().alpha(if (data.failed) 0.78f else 1f)
            val image = data.image
            if (image != null) {
                Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = art)
            } else {
                Box(
                    art.background(
                        Brush.linearGradient(listOf(mix(accent, Color.White, 0.35f), accent, mix(accent, Color.Black, 0.6f)))
                    )
                )
            }
            Column(
                Modifier
                    .weight(1f - IMAGE_SHARE)
                    .fillMaxHeight()
                    .padding(14.dp)
            ) {
                // What was read before dims after a failed read; the note that says so doesn't, so it stays readable.
                val kept = Modifier.alpha(if (data.failed) 0.78f else 1f)
                Text(data.domain.uppercase(locale), style = labelStyle(), color = colors.sub, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = kept)
                Text(
                    data.title.ifBlank { data.domain },
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 21.sp),
                    color = colors.on, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp).then(kept)
                )
                if (data.failed) {
                    Text(updatedNote(ago), style = smallStyle(), color = colors.warn, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                } else if (data.description.isNotBlank()) {
                    Text(data.description, style = smallStyle(), color = colors.sub, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}
