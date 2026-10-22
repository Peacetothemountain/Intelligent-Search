package com.pixel.intelligentsearch.feature.search

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixel.intelligentsearch.core.ai.AiAnswerState
import com.pixel.intelligentsearch.core.ai.AiProvider
import com.pixel.intelligentsearch.core.data.SavedLink
import com.pixel.intelligentsearch.core.theme.GoogleSansFlex
import com.pixel.intelligentsearch.di.appEntryPoint
import androidx.compose.ui.graphics.asImageBitmap
import com.pixel.intelligentsearch.core.ui.AnimatedCopyChip
import com.pixel.intelligentsearch.core.ui.expressive.ExpressiveCornerTokens
import com.pixel.intelligentsearch.core.ui.expressive.SquircleShape
import com.pixel.intelligentsearch.feature.settings.bouncyClickable
import com.pixel.intelligentsearch.feature.settings.expressiveRowClickable

/**
 * A suggested action at the top of the results ("Start 15 min Timer", "Call Mom", "Ask Claude"): its icon on a
 * primary circle, a bold title and one line of detail on the primary container pill, and an arrow, pressing like
 * every result row.
 */
@Composable
internal fun DirectActionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    /** Gestures on the pill itself, under the press (the suggestions' swipe to dismiss). */
    gestures: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(32.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f), shape)
            .clip(shape)
            .then(gestures)
            .bouncyClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = GoogleSansFlex
            )
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                fontSize = 13.sp,
                fontFamily = GoogleSansFlex,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(20.dp)
        )
    }
}

/** "Ask Claude": the row that sends [question] to [provider], and only when it is tapped. */
@Composable
internal fun AskAiRow(provider: AiProvider, question: String, onAsk: () -> Unit) {
    DirectActionRow(
        title = "Ask ${provider.displayName}",
        subtitle = question,
        icon = Icons.Filled.AutoAwesome,
        onClick = onAsk
    )
}

/**
 * The AI answer in a card like the other instant answers: the provider and model, the answer as it streams (with
 * **bold**, `code` and bullet points drawn), then Copy and "Continue in" the assistant's app or site. A failure says
 * what went wrong in plain words.
 */
@Composable
internal fun AiAnswerCard(state: AiAnswerState, model: String, onContinue: () -> Unit) {
    val shape = remember { SquircleShape(ExpressiveCornerTokens.ExtraLarge) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .shadow(2.dp, shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.07f), shape)
            .padding(20.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f), ExpressiveCornerTokens.ShapeFull),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.provider.displayName.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = model,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = state.question,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            when (state) {
                is AiAnswerState.Asking -> Text(
                    text = "Asking ${state.provider.displayName}…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
                is AiAnswerState.Answer -> {
                    val colors = MaterialTheme.colorScheme
                    val styled = remember(state.text, colors) { markdownLite(state.text, codeBackground = colors.surfaceContainerHighest) }
                    Text(
                        text = styled,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { if (state.done) liveRegion = LiveRegionMode.Polite }
                    )
                    if (state.truncated) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "The answer was cut short. Continue in ${state.provider.displayName} for more.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is AiAnswerState.Failed -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                ) {
                    Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = state.problem.message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (state is AiAnswerState.Answer && state.done) {
                    AnimatedCopyChip(textToCopy = state.text, clipLabel = "${state.provider.displayName} Answer")
                }
                AssistChip(
                    onClick = onContinue,
                    label = { Text("Continue in ${state.provider.displayName}") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    shape = ExpressiveCornerTokens.ShapeFull,
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        leadingIconContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                )
            }
        }
    }
}

/**
 * The little Markdown answers use, drawn rather than shown as symbols: **bold**, `code`, "#" headings (bold) and
 * "-" or "*" bullet points (•). Anything else is plain text.
 */
internal fun markdownLite(text: String, codeBackground: androidx.compose.ui.graphics.Color): AnnotatedString = buildAnnotatedString {
    val lines = text.replace("\r\n", "\n").split('\n')
    lines.forEachIndexed { index, rawLine ->
        var line = rawLine
        var heading = false
        val trimmed = line.trimStart()
        when {
            trimmed.startsWith("#") -> {
                heading = true
                line = trimmed.trimStart('#').trimStart()
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> line = "•  " + trimmed.substring(2)
        }
        if (heading) pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
        var i = 0
        var bold = false
        while (i < line.length) {
            when {
                line.startsWith("**", i) -> {
                    if (bold) pop() else pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    bold = !bold
                    i += 2
                }
                line[i] == '`' && line.indexOf('`', i + 1) > i -> {
                    val end = line.indexOf('`', i + 1)
                    pushStyle(SpanStyle(background = codeBackground))
                    append(line.substring(i + 1, end))
                    pop()
                    i = end + 1
                }
                else -> {
                    append(line[i])
                    i++
                }
            }
        }
        if (bold) pop()
        if (heading) pop()
        if (index < lines.lastIndex) append('\n')
    }
}

/**
 * A Saved Link among the results: its icon (fetched from the site when the user asked) in the matte circle, or the
 * name's first letter, then its name and site. Opens in the browser, or the site's own app.
 */
@Composable
internal fun SavedLinkRow(link: SavedLink, icon: ImageBitmap?, onLongClick: (() -> Unit)?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .moreOptions(onLongClick)
            .expressiveRowClickable(onLongClick = onLongClick, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SavedLinkIcon(link, icon, 40.dp)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(link.name, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(link.host, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A Saved Link's picture: its site icon in a matte circle, or its first letter on the primary container. */
@Composable
internal fun SavedLinkIcon(link: SavedLink, icon: ImageBitmap?, size: androidx.compose.ui.unit.Dp) {
    if (icon != null) {
        Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(size).clip(CircleShape).matteRing())
    } else {
        Box(
            modifier = Modifier.size(size).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = monogramOf(link.name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/** [link]'s saved site icon, read off the main thread; null while it loads and when it has none. */
@Composable
internal fun rememberSavedLinkIcon(link: SavedLink, version: Int = 0): ImageBitmap? {
    val context = androidx.compose.ui.platform.LocalContext.current
    val icons = remember(context) { runCatching { context.appEntryPoint.savedLinkIcons() }.getOrNull() }
    return androidx.compose.runtime.produceState<ImageBitmap?>(null, link.url, version) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            icons?.load(link.url)?.asImageBitmap()
        }
    }.value
}

/** The letter a link without an icon shows: its name's first letter or digit, upper case. */
internal fun monogramOf(name: String): String =
    name.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "#"
