package com.pixel.intelligentsearch.feature.search

import com.pixel.intelligentsearch.core.ui.AppIconResult
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.feature.settings.bouncyClickable
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixel.intelligentsearch.core.data.*
import com.pixel.intelligentsearch.core.theme.GoogleSansFlex
import com.pixel.intelligentsearch.core.ui.expressive.ExpressiveMotionTokens

@Composable
fun SearchPill(iconRes: Int? = null, iconBitmap: AppIconResult? = null, title: String, scale: Float = 1f, onClick: () -> Unit) {
    val hPadding = (12 * scale).dp
    val vPadding = (8 * scale).dp
    val iconSize = (18 * scale).dp
    val textSize = (14 * scale).sp

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val cornerRadius by animateDpAsState(
        targetValue = if (isPressed) 12.dp else 24.dp,
        animationSpec = ExpressiveMotionTokens.morphSpring(),
        label = "pill_corner_morph"
    )

    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }

    Row(
        modifier = Modifier
            .padding(end = (8 * scale).dp)
            .graphicsLayer {
                this.shape = shape
                clip = true
            }
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isPressed) 0.65f else 0.4f), shape)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (isPressed) 0.35f else 0.15f), shape)
            .bouncyClickable(interactionSource = interactionSource, onClick = onClick)
            .padding(horizontal = hPadding, vertical = vPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconBitmap != null) {
            Image(
                bitmap = iconBitmap.bitmap,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                colorFilter = if (iconBitmap.isMonochrome) androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant) else null
            )
        } else if (iconRes != null) {
            Image(painter = painterResource(id = iconRes), contentDescription = null, modifier = Modifier.size(iconSize))
        }
        Spacer(modifier = Modifier.width((8 * scale).dp))
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = textSize,
            fontWeight = FontWeight.Medium,
            fontFamily = GoogleSansFlex
        )
    }
}
